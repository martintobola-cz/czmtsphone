package cz.mts.phone.activities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.*
import cz.mts.base.helpers.*
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.models.BlockedNumber
import cz.mts.phone.R
import cz.mts.phone.adapters.BlockedNumbersAdapter
import cz.mts.phone.databinding.ActivityManageBlockedNumbersBinding
import cz.mts.phone.dialogs.AddOrEditBlockedNumberDialog
import cz.mts.phone.helpers.BlockedNumbersImportExportHelper

class ManageBlockedNumbersActivity : BaseSimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null

    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_IMPORT = "action_import"
        const val ACTION_EXPORT = "action_export"

        fun createImportIntent(context: Context) =
            Intent(context, ManageBlockedNumbersActivity::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_IMPORT)
            }

        fun createExportIntent(context: Context) =
            Intent(context, ManageBlockedNumbersActivity::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_EXPORT)
            }
    }

    private val binding by viewBinding(ActivityManageBlockedNumbersBinding::inflate)

    private val config by lazy { baseConfig }

    // config.appId.startsWith(MY_APP_NAME_GOOGLE_ID) se nemění za běhu, stačí spočítat jednou
    private val isDialer by lazy { config.appId.startsWith(MY_APP_NAME_GOOGLE_ID) }

    private var callerIdRequestAttempted = true

    private var adapter: BlockedNumbersAdapter? = null
    private var blockedNumbers: List<BlockedNumber> = emptyList()

    private val importExportHelper = BlockedNumbersImportExportHelper(this) { updateBlockedNumbers() }

    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()
    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""
    override fun getRepositoryName() = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        callerIdRequestAttempted = true

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(manageBlockedNumbersList))
            setupMaterialScrollListener(manageBlockedNumbersList, manageBlockedNumbersAppbar)
        }

        setupToolbar()
        setupSwitches()
        setupRecycler()
        updateTextColors(binding.manageBlockedNumbersSwitchesHolder)
        //binding.manageBlockedNumbersSwitchesHolder.setupViewBackground(this)
        binding.manageBlockedNumbersSwitchesHolder.setBackgroundColor(getProperBackgroundColor())

        // placeholderHolder není potomkem switchesHolder, takže mu updateTextColors() výše nic nenastaví
        binding.manageBlockedNumbersPlaceholder.setTextColor(getProperTextColor())

        updateBlockedNumbers(true)

        if (savedInstanceState == null) {          // jen při prvním vytvoření, ne po rotaci
            when (intent.getStringExtra(EXTRA_ACTION)) {
                ACTION_IMPORT -> importExportHelper.tryImportBlockedNumbers()
                ACTION_EXPORT -> importExportHelper.tryExportBlockedNumbers()
            }
        }

        callerIdRequestAttempted = false
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBar(binding.manageBlockedNumbersAppbar, NavigationIcon.Arrow)

        // isDefaultDialer / stav callfilter appky se mohl od posledního onResume změnit
        // (uživatel se mohl vrátit ze systémového dialogu nebo z Obchodu Play)
        binding.manageBlockedNumbersCheckSpamSwitch.isChecked = isCheckSpamAppInstalled()
        val isBlockingChecked = binding.manageBlockedNumbersBlockHiddenSwitch.isChecked
                                || binding.manageBlockedNumbersBlockUnknownSwitch.isChecked
                                || getBlockedNumbers().isNotEmpty()
        onCheckedSetCallerIdAsDefault(isBlockingChecked)

        updateUiState()
    }

    // ─── Toolbar (nahoře) ───────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.manageBlockedNumbersToolbar.apply {
            inflateMenu(R.menu.menu_manage_blocked_numbers)

            val colorizerEnabled = config.usePopupMenuColorizer && !isDynamicTheme()
            if (colorizerEnabled) {
                PopupMenuColorizer.colorizeTitles(menu, config.popupMenuTextColor)
                PopupMenuColorizer.attachOverflowColorHook(
                    toolbar = this,
                    context = this@ManageBlockedNumbersActivity,
                    getTextColor = { config.popupMenuTextColor },
                    getBackgroundColor = { config.popupMenuBackgroundColor },
                    isColoringEnabled = { colorizerEnabled },
                    isDebugEnabled = { iSaveDebugMode == 1 }
                )
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.add_a_blocked_number -> openAddBlockedNumberDialog(null)
                    R.id.import_blocked_numbers -> importExportHelper.tryImportBlockedNumbers()
                    R.id.export_blocked_numbers -> importExportHelper.tryExportBlockedNumbers()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }
    }

    // ─── Tři přepínače (block unknown / hidden / check spam) ───────────────────

    private fun setupSwitches() {
        binding.apply {
            manageBlockedNumbersBlockUnknownLabel.setText(
                if (isDialer) R.string.block_unknown_calls else R.string.block_unknown_messages
            )
            manageBlockedNumbersBlockHiddenLabel.setText(
                if (isDialer) R.string.block_hidden_calls else R.string.block_hidden_messages
            )
            manageBlockedNumbersCheckSpamLabel.setText(
                if (isDialer) R.string.callfilter_info else R.string.block_hidden_messages
            )

            manageBlockedNumbersBlockUnknownSwitch.isChecked = config.blockUnknownNumbers
            manageBlockedNumbersBlockHiddenSwitch.isChecked = config.blockHiddenNumbers
            manageBlockedNumbersCheckSpamSwitch.isChecked = isCheckSpamAppInstalled()

            manageBlockedNumbersBlockUnknownHolder.setOnClickListener {
                manageBlockedNumbersBlockUnknownSwitch.toggle()
            }
            manageBlockedNumbersBlockHiddenHolder.setOnClickListener {
                manageBlockedNumbersBlockHiddenSwitch.toggle()
            }
            manageBlockedNumbersCheckSpamHolder.setOnClickListener {
                // read-only ve stavu ON – deaktivace jde jen přes reálný stav appky (viz onResume)
                if (!manageBlockedNumbersCheckSpamSwitch.isChecked) {
                    manageBlockedNumbersCheckSpamSwitch.toggle()
                }
            }

            manageBlockedNumbersBlockUnknownSwitch.setOnCheckedChangeListener { _, isChecked ->
                config.blockUnknownNumbers = isChecked
                callerIdRequestAttempted = false
                onCheckedSetCallerIdAsDefault(isChecked)
            }
            manageBlockedNumbersBlockHiddenSwitch.setOnCheckedChangeListener { _, isChecked ->
                config.blockHiddenNumbers = isChecked
                callerIdRequestAttempted = false
                onCheckedSetCallerIdAsDefault(isChecked)
            }
            manageBlockedNumbersCheckSpamSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !isCheckSpamAppInstalled()) {
                    try {
                        startActivity(
                            PlayStoreIntentHelper.createOpenStoreIntent(this@ManageBlockedNumbersActivity, "callfilter.app")
                        )
                    } catch (_: ActivityNotFoundException) {
                        // Uživatel nemá Obchod Play – tiché ignorování
                    }
                    // appka pořád není nainstalovaná -> switch se nesmí "zaseknout" na ON
                    manageBlockedNumbersCheckSpamSwitch.isChecked = false
                }
            }
        }
    }

    private fun isCheckSpamAppInstalled() =
        isAppInstalled("callfilter.app") || isAppInstalled("cz.mts.callfilter")

    // ─── Recycler / adapter ─────────────────────────────────────────────────────

    private fun setupRecycler() {
        adapter = BlockedNumbersAdapter(
            activity = this,
            recyclerView = binding.manageBlockedNumbersList,
            itemClick = { openAddBlockedNumberDialog(it as BlockedNumber) },
            itemDelete = { updateBlockedNumbers() }
        )
        binding.manageBlockedNumbersList.adapter = adapter
    }

    private fun openAddBlockedNumberDialog(blockedNumber: BlockedNumber?) {

        AddOrEditBlockedNumberDialog(
            activity = this,
            blockedNumber = blockedNumber,
            onDelete = { numberToDelete ->
                ensureBackgroundThread {
                    deleteBlockedNumber(numberToDelete)
                    runOnUiThread { updateBlockedNumbers() }
                }
            },
            onSave = { newNumber ->
                ensureBackgroundThread {
                    // při editaci na jiné číslo nejdřív smažeme staré - BlockedNumbers content
                    // provider nemá update, jen insert/delete (viz cz.mts.base.extensions)
                    val oldNumber = blockedNumber?.number
                    if (oldNumber != null && oldNumber != newNumber) {
                        deleteBlockedNumber(oldNumber)
                    }
                    addBlockedNumber(newNumber)
                    runOnUiThread { updateBlockedNumbers(false) }
                }
            }
        )
    }

    private fun updateUiState() {
        val hasPermission = isDefaultDialer()
        binding.apply {
            manageBlockedNumbersSwitchesHolder.beVisibleIf(hasPermission)

            when {
                !hasPermission -> {
                    manageBlockedNumbersList.beGone()
                    manageBlockedNumbersPlaceholderHolder.beVisible()
                    manageBlockedNumbersPlaceholder.setText(R.string.must_make_default_dialer)
                    manageBlockedNumbersPlaceholderButton.setText(R.string.set_as_default)
                    manageBlockedNumbersPlaceholderButton.setOnClickListener { maybeSetDefaultCallerIdApp() }
                }

                blockedNumbers.isEmpty() -> {
                    manageBlockedNumbersList.beGone()
                    manageBlockedNumbersPlaceholderHolder.beVisible()
                    manageBlockedNumbersPlaceholder.setText(R.string.not_blocking_anyone)
                    manageBlockedNumbersPlaceholderButton.setText(R.string.add_a_blocked_number)
                    manageBlockedNumbersPlaceholderButton.setOnClickListener { openAddBlockedNumberDialog(null) }
                }

                else -> {
                    manageBlockedNumbersPlaceholderHolder.beGone()
                    manageBlockedNumbersList.beVisible()
                }
            }
        }
    }

    // ─── Blokovaná čísla – načtení / refresh ───────────────────────────────────

    private fun updateBlockedNumbers(bOnCreate : Boolean = true) {
        ensureBackgroundThread {
            application.getBlockedNumbersWithContact { list ->
                runOnUiThread {

                    blockedNumbers = list
                    adapter?.updateItems(list)
                    updateUiState()

                    if (list.isNotEmpty())  {
                        callerIdRequestAttempted = bOnCreate
                        maybeSetDefaultCallerIdApp()
                    }
                }
            }
        }
    }


    private fun onCheckedSetCallerIdAsDefault(isChecked: Boolean) {
        if (isChecked) maybeSetDefaultCallerIdApp()
    }

    private fun maybeSetDefaultCallerIdApp() {
        if (callerIdRequestAttempted) return
        if (isQPlus() && baseConfig.appId.startsWith(MY_APP_NAME_GOOGLE_ID)) {
            callerIdRequestAttempted = true
            setDefaultCallerIdApp()
        }
    }
}
