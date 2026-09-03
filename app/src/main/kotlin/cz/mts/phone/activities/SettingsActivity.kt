package cz.mts.phone.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import cz.mts.phone.databinding.ActivitySettingsBinding
import cz.mts.phone.dialogs.ExportCallHistoryDialog
import cz.mts.phone.dialogs.ManageVisibleTabsDialog
import cz.mts.phone.extensions.canLaunchAccountsConfiguration
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.launchAccountsConfiguration
import cz.mts.phone.helpers.RecentsHelper
import cz.mts.phone.models.RecentCall
import cz.mts.phone.R
import java.util.Locale
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import cz.mts.base.activities.ManageBlockedNumbersActivity
import cz.mts.base.dialogs.ChangeDateTimeFormatDialog
import cz.mts.base.dialogs.RadioGroupDialog
import cz.mts.base.extensions.addLockedLabelIfNeeded
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.getFontSizeText
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.showErrorToast
import cz.mts.base.extensions.toast
import cz.mts.base.extensions.updateTextColors
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.ContactsHelper
import cz.mts.base.helpers.FONT_SIZE_EXTRA_LARGE
import cz.mts.base.helpers.FONT_SIZE_LARGE
import cz.mts.base.helpers.FONT_SIZE_MEDIUM
import cz.mts.base.helpers.FONT_SIZE_SMALL
import cz.mts.base.helpers.FastPhoneNumberFormatter.invalidateRegionCache
import cz.mts.base.helpers.MTS_NONE
import cz.mts.base.helpers.MTS_PHONE
import cz.mts.base.helpers.isNougatPlus
import cz.mts.base.helpers.isQPlus
import cz.mts.base.helpers.isTiramisuPlus
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.helpers.TAB_CALL_HISTORY
import cz.mts.base.helpers.TAB_CONTACTS
import cz.mts.base.helpers.TAB_FAVORITES
import cz.mts.base.helpers.TAB_LAST_USED
import cz.mts.base.helpers.VcfExporter
import cz.mts.base.helpers.VcfImportSource
import cz.mts.base.helpers.VcfImporter
import cz.mts.base.models.RadioItem
import cz.mts.phone.dialogs.FilterContactSourceDialogMTs
import cz.mts.phone.dialogs.NameTypeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getSharedPrefs
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.phone.helpers.OverlayColors
import cz.mts.phone.helpers.SmsQuickReplyOverlay


class SettingsActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null
    companion object {
        private const val CALL_HISTORY_FILE_TYPE = "application/json"
        private const val CONTACT_VCF_FILE_TYPE = "text/x-vcard"

        private val IMPORT_CALL_HISTORY_FILE_TYPES = buildList {
            add("application/json")
            if (!isQPlus()) add("application/octet-stream")
        }

        private val IMPORT_CONTACT_VCF_FILE_TYPE_TYPES = buildList {
            add("text/x-vcard")
            if (!isQPlus()) add("application/octet-stream")
        }
    }
    private var smsOverlay: SmsQuickReplyOverlay? = null
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    // ── Activity Result launchery – deklarovány před onCreate ────────────────

    private val importCallHistoryLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                toast(R.string.importing)
                importCallHistory(uri)
            }
        }

    private val importContactsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importContacts(uri)
        }

    private val exportCallHistoryLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(CALL_HISTORY_FILE_TYPE)) { uri ->
            if (uri != null) {
                toast(R.string.exporting)
                binding.progressIndicatorSettings.beVisible()  // ← show
                lifecycleScope.launch {
                    val recents = withContext(Dispatchers.IO) {
                        RecentsHelper(this@SettingsActivity).loadAllRecentsForExport()
                    }
                    exportCallHistory(recents, uri)
                    binding.progressIndicatorSettings.beGone()  // ← hide
                }
            }
        }

    private val exportContactsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(CONTACT_VCF_FILE_TYPE)) { uri ->
            if (uri != null) exportContacts(uri)
        }


    override fun onBackPressedCompat(): Boolean {

        if (smsOverlay != null) {
            smsOverlay?.dismiss()
            smsOverlay = null
            return true
        }
        return false
    }


        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(settingsNestedScrollview))
            setupMaterialScrollListener(settingsNestedScrollview, settingsAppbar)
        }

        setupOptionsMenu()
        refreshMenuItems()

        // Listenery patří do onCreate – registrují se jen jednou
        setupClickListeners()

        setupSwhowDeclineAndSMSbutton(config.swhowDeclineAndSMSbutton)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stačí jen zde – onDestroy se volá po onPause, duplicita odstraněna
        invalidateRegionCache()
        dismissSmsOverlay()
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        // onResume pouze aktualizuje zobrazené hodnoty, ne listenery
        refreshUiValues()

        updateTextColors(binding.settingsHolder)

        binding.apply {
            arrayOf(
                settingsColorCustomizationSectionLabel,
                settingsGeneralSettingsLabel,
                settingsStartupLabel,
                settingsCallsLabel,
                settingsDialpadSectionLabel,
                settingsMigrationSectionLabel
            ).forEach { it.setTextColor(getProperPrimaryColor()) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    // ── Options menu ─────────────────────────────────────────────────────────

    private fun setupOptionsMenu() {
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.calling_accounts -> launchAccountsConfiguration()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun refreshMenuItems() {
        binding.settingsToolbar.menu.apply {
            findItem(R.id.calling_accounts).isVisible = canLaunchAccountsConfiguration()
        }
    }

    // ── UI hodnoty – voláno z onResume ───────────────────────────────────────

    private fun refreshUiValues() {
        binding.apply {
            settingsUseEnglishHolder.beVisibleIf(
                Locale.getDefault().language != "en" && !isTiramisuPlus()
            )
            settingsUseEnglish.isChecked = config.useEnglish

            settingsLanguage.text = Locale.getDefault().displayLanguage
            settingsLanguageHolder.beVisibleIf(isTiramisuPlus())

            settingsManageBlockedNumbersLabel.text =
                addLockedLabelIfNeeded(R.string.manage_blocked_numbers)
            settingsManageBlockedNumbersHolder.beVisibleIf(isNougatPlus())

            settingsFontSize.text = getFontSizeText()
            settingsDefaultTab.text = getDefaultTabText()

            settingsOpenDialpadAtLaunch.isChecked = config.openDialPadAtLaunch
            settingsGroupSubsequentCalls.isChecked = config.groupSubsequentCalls
            settingsStartNameWithSurname.isChecked = config.startNameWithSurname
            settingsFormatPhoneNumbers.isChecked = config.formatPhoneNumbers
            settingsEnableT9.isChecked = config.enableT9dialpad
            settingsShowOnlyContactsWithNumbers.isChecked = config.showOnlyContactsWithNumbers
            settingsDialpadVibration.isChecked = config.dialpadVibration
            settingsHideDialpadNumbers.isChecked = config.hideDialpadNumbers
            settingsDialpadBeeps.isChecked = config.dialpadBeeps
            settingsShowCallConfirmation.isChecked = config.showCallConfirmation
            settingsDisableProximitySensor.isChecked = config.disableProximitySensor
            settingsDisableSwipeToAnswer.isChecked = config.disableSwipeToAnswer
            settingsAlwaysShowFullscreen.isChecked = config.alwaysShowFullscreen
            settingsSwhowDeclineAndSmsButton.isChecked = config.swhowDeclineAndSMSbutton
            settingsShakeCallEffect.isChecked = config.shakeEffectConfirmingCall
        }
    }

    // ── Listenery – voláno jednou z onCreate ─────────────────────────────────

    private fun setupClickListeners() {
        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupManageBlockedNumbers()
        setupManageSpeedDial()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupManageShownTabs()
        setupDefaultTab()
        setupDialPadOpen()
        setupGroupSubsequentCalls()
        setupStartNameWithSurname()
        setupFormatPhoneNumbers()
        setupEnableT9dialpad()
        setupShowOnlyContactsWithNumbers()
        setupDialpadVibrations()
        setupDialpadNumbers()
        setupDialpadBeeps()
        setupShowCallConfirmation()
        setupDisableProximitySensor()
        setupDisableSwipeToAnswer()
        setupAlwaysShowFullscreen()
        setupSwhowDeclineAndSMSbutton()
        setupShakeEffect()
        setupCallsExport()
        setupCallsImport()
        setupContactsExport()
        setupContactsImport()
        setupBlockedNumbersExport()
        setupBlockedNumbersImport()
        setupAllPerfsExport()
        setupAllPerfsImport()

    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            config.easterEggMode = false
            mtsGlobalAll.iSaveDebugMode = 0
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        binding.settingsLanguageHolder.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launchChangeAppLanguageIntent()
            }
        }
    }

    private fun setupManageBlockedNumbers() {
        binding.settingsManageBlockedNumbersHolder.setOnClickListener {
            Intent(this, ManageBlockedNumbersActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupManageSpeedDial() {
        binding.settingsManageSpeedDialHolder.setOnClickListener {
            Intent(this, ManageSpeedDialActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupChangeDateTimeFormat() {
        binding.settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupFontSize() {
        binding.settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large))
            )

            RadioGroupDialog(
                     activity = this,
                     items    = items,
                     checkedItemId = config.fontSize,
                     itemTextSize = { item ->
                         when (item.id) {
                             FONT_SIZE_SMALL  -> pxToSp(resources.getDimension(R.dimen.smaller_text_size))
                             FONT_SIZE_MEDIUM -> pxToSp(resources.getDimension(R.dimen.bigger_text_size))
                             FONT_SIZE_LARGE  -> pxToSp(resources.getDimension(R.dimen.big_text_size))
                             else             -> pxToSp(resources.getDimension(R.dimen.extra_big_text_size))
                         }
                         }
                     ) { 
                         config.fontSize = it as Int
                         binding.settingsFontSize.text = getFontSizeText()
                       }
        }
    }

    private fun pxToSp(px: Float): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, px, resources.displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            px / resources.displayMetrics.scaledDensity
        }
    private fun setupManageShownTabs() {
        binding.settingsManageTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_CONTACTS, getString(R.string.contacts_tab)),
                RadioItem(TAB_FAVORITES, getString(R.string.favorites_tab)),
                RadioItem(TAB_CALL_HISTORY, getString(R.string.call_history_tab)),
                RadioItem(TAB_LAST_USED, getString(R.string.last_used_tab))
            )
            RadioGroupDialog(this, items, config.defaultTab) {
                config.defaultTab = it as Int
                binding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (config.defaultTab) {
            TAB_CONTACTS -> R.string.contacts_tab
            TAB_FAVORITES -> R.string.favorites_tab
            TAB_CALL_HISTORY -> R.string.call_history_tab
            else -> R.string.last_used_tab
        }
    )

    private fun setupDialPadOpen() {
        binding.settingsOpenDialpadAtLaunchHolder.setOnClickListener {
            binding.settingsOpenDialpadAtLaunch.toggle()
            config.openDialPadAtLaunch = binding.settingsOpenDialpadAtLaunch.isChecked
        }
    }

    private fun setupGroupSubsequentCalls() {
        binding.settingsGroupSubsequentCallsHolder.setOnClickListener {
            binding.settingsGroupSubsequentCalls.toggle()
            config.groupSubsequentCalls = binding.settingsGroupSubsequentCalls.isChecked
        }
    }

    private fun setupStartNameWithSurname() {
        binding.settingsStartNameWithSurnameHolder.setOnClickListener {
            binding.settingsStartNameWithSurname.toggle()
            config.startNameWithSurname = binding.settingsStartNameWithSurname.isChecked
        }
    }

    private fun setupFormatPhoneNumbers() {
        binding.settingsFormatPhoneNumbersHolder.setOnClickListener {
            binding.settingsFormatPhoneNumbers.toggle()
            config.formatPhoneNumbers = binding.settingsFormatPhoneNumbers.isChecked
        }
    }

    private fun setupEnableT9dialpad() {
        binding.settingsEnableT9Holder.setOnClickListener {
            binding.settingsEnableT9.toggle()
            config.enableT9dialpad = binding.settingsEnableT9.isChecked
        }
    }

    private fun setupShowOnlyContactsWithNumbers() {
        binding.settingsShowOnlyContactsWithNumbersHolder.setOnClickListener {
            binding.settingsShowOnlyContactsWithNumbers.toggle()
            config.showOnlyContactsWithNumbers =
                binding.settingsShowOnlyContactsWithNumbers.isChecked
        }
    }

    private fun setupDialpadVibrations() {
        binding.settingsDialpadVibrationHolder.setOnClickListener {
            binding.settingsDialpadVibration.toggle()
            config.dialpadVibration = binding.settingsDialpadVibration.isChecked
        }
    }

    private fun setupDialpadNumbers() {
        binding.settingsHideDialpadNumbersHolder.setOnClickListener {
            binding.settingsHideDialpadNumbers.toggle()
            config.hideDialpadNumbers = binding.settingsHideDialpadNumbers.isChecked
        }
    }

    private fun setupDialpadBeeps() {
        binding.settingsDialpadBeepsHolder.setOnClickListener {
            binding.settingsDialpadBeeps.toggle()
            config.dialpadBeeps = binding.settingsDialpadBeeps.isChecked
        }
    }

    private fun setupShowCallConfirmation() {
        binding.settingsShowCallConfirmationHolder.setOnClickListener {
            binding.settingsShowCallConfirmation.toggle()
            config.showCallConfirmation = binding.settingsShowCallConfirmation.isChecked
        }
    }

    private fun setupDisableProximitySensor() {
        binding.settingsDisableProximitySensorHolder.setOnClickListener {
            binding.settingsDisableProximitySensor.toggle()
            config.disableProximitySensor = binding.settingsDisableProximitySensor.isChecked
        }
    }

    private fun setupDisableSwipeToAnswer() {
        binding.settingsDisableSwipeToAnswerHolder.setOnClickListener {
            binding.settingsDisableSwipeToAnswer.toggle()
            config.disableSwipeToAnswer = binding.settingsDisableSwipeToAnswer.isChecked
        }
    }

    private fun setupAlwaysShowFullscreen() {
        binding.settingsAlwaysShowFullscreenHolder.setOnClickListener {
            binding.settingsAlwaysShowFullscreen.toggle()
            config.alwaysShowFullscreen = binding.settingsAlwaysShowFullscreen.isChecked
        }
    }

    // Na úrovni třídy – launcher pro žádost o oprávnění
    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // Uživatel právě povolil – zapneme přepínač a uložíme nastavení
                binding.settingsSwhowDeclineAndSmsButton.isChecked = true
                config.swhowDeclineAndSMSbutton = true
                updateEditSmsTemplatesVisibility(true)
            } else {
                // Zamítnuto – checkbox vrátíme zpět na false
                binding.settingsSwhowDeclineAndSmsButton.isChecked = false
                config.swhowDeclineAndSMSbutton = false
                updateEditSmsTemplatesVisibility(false)
                // Pokud uživatel zaškrtl "Never ask again", nabídneme systémové nastavení
                if (!shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {
                    openAppSettings()
                }
            }
        }



    private fun setupSwhowDeclineAndSMSbutton(checkPermissionOnStart: Boolean = false) {
        // Kontrola při startu – mimo listener
        if (checkPermissionOnStart &&
            checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }

        // Zobraz/skryj „upravit SMS texty" podle aktuálního stavu
        updateEditSmsTemplatesVisibility(config.swhowDeclineAndSMSbutton)

        // Listener se registruje vždy jednou
        binding.settingsSwhowDeclineAndSmsButtonHolder.setOnClickListener {
            val newValue = !binding.settingsSwhowDeclineAndSmsButton.isChecked
            if (newValue) {
                if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    binding.settingsSwhowDeclineAndSmsButton.isChecked = true
                    config.swhowDeclineAndSMSbutton = true
                    updateEditSmsTemplatesVisibility(true)
                } else {
                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                }
            } else {
                binding.settingsSwhowDeclineAndSmsButton.isChecked = false
                config.swhowDeclineAndSMSbutton = false
                updateEditSmsTemplatesVisibility(false)
            }
        }

        // Klik na „upravit SMS texty" → otevře overlay v edit módu
        binding.settingsEditSmsTemplatesHolder.setOnClickListener {
            openSmsTemplatesEditor()
        }
    }

    private fun updateEditSmsTemplatesVisibility(visible: Boolean) {
        binding.settingsEditSmsTemplatesHolder.beVisibleIf(visible)
    }

    private fun openSmsTemplatesEditor() {
        if (smsOverlay != null) return  // už zobrazeno
        val overlay = SmsQuickReplyOverlay(this).apply {
            mode = SmsQuickReplyOverlay.Mode.EDIT
            listener = object : SmsQuickReplyOverlay.Listener {
                override fun onDeclineWithSms(message: String) { dismissSmsOverlay()}
                override fun onDismissed() { dismissSmsOverlay() }
            }
        }
        overlay.show(binding.root)

        overlay.applyColors(
            OverlayColors(

                //windowBackground: Int = Color.parseColor("#CC000000"),
                //activeRowBackground: Int = Color.parseColor("#33FFFFFF"),
                // textColor: Int = Color.WHITE,
                iconTint = getProperPrimaryColor(),
                //titleColor = getProperPrimaryColor()
                //dividerColor: Int? = null,
                // val dimTextColor: Int? = null

            ))

        smsOverlay = overlay
        toast(R.string.sms_edit_hint_double_tap)
    }

    private fun dismissSmsOverlay() {
        smsOverlay?.dismiss()
        smsOverlay = null
    }

    /** Otevře detail aplikace v systémovém nastavení. */
    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    private fun setupShakeEffect() {
        binding.settingsShakeCallEffectHolder.setOnClickListener {
            binding.settingsShakeCallEffect.toggle()
            config.shakeEffectConfirmingCall = binding.settingsShakeCallEffect.isChecked
        }
    }

    private fun setupCallsExport() {
        binding.settingsExportCallsHolder.setOnClickListener {
            ExportCallHistoryDialog(this, 0) { filename ->
                exportCallHistoryLauncher.launch("$filename.json")
            }
        }
    }

    private fun setupCallsImport() {
        binding.settingsImportCallsHolder.setOnClickListener {
            importCallHistoryLauncher.launch(IMPORT_CALL_HISTORY_FILE_TYPES.toTypedArray())
        }
    }

    private fun setupContactsExport() {
        binding.settingsExportContactsHolder.setOnClickListener {
            ExportCallHistoryDialog(this, 1) { filename ->
                exportContactsLauncher.launch("$filename.vcf")
            }
        }
    }

    private fun setupContactsImport() {
        binding.settingsImportContactsHolder.setOnClickListener {
            importContactsLauncher.launch(IMPORT_CONTACT_VCF_FILE_TYPE_TYPES.toTypedArray())
        }
    }

    // ── Import / Export ──────────────────────────────────────────────────────

    private fun importCallHistory(uri: Uri) {
        binding.progressIndicatorSettings.beVisible()  // ← show
        try {
            val jsonString = contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() }
            val objects = Json.decodeFromString<List<RecentCall>>(jsonString)

            if (objects.isEmpty()) {
                binding.progressIndicatorSettings.beGone()
                toast(R.string.no_entries_for_importing)
                return
            }
            RecentsHelper(this).restoreRecentCalls(
                activity = this,
                calls = objects,
                showImportingToast = true
            ) { result ->
                runOnUiThread {
                    binding.progressIndicatorSettings.beGone()

                    if (result is RecentsHelper.RestoreRecentsResult.Error) {
                        showErrorToast(Exception(result.message))
                    }
                }
            }
        } catch (_: SerializationException) {
            binding.progressIndicatorSettings.beGone()
            toast(R.string.invalid_file_format)
        } catch (_: IllegalArgumentException) {
            binding.progressIndicatorSettings.beGone()
            toast(R.string.invalid_file_format)
        } catch (e: Exception) {
            binding.progressIndicatorSettings.beGone()
            showErrorToast(e)
        }
    }

    private fun exportCallHistory(recents: List<RecentCall>, uri: Uri) {
        if (recents.isEmpty()) {
            toast(R.string.no_entries_for_exporting)
            return
        }
        try {
            contentResolver.openOutputStream(uri)!!.use {
                it.write(Json.encodeToString(recents).toByteArray())
            }
            toast(R.string.exporting_successful)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    /**
     * Vstupní bod pro import kontaktů – výběr cílového účtu + samotný import.
     */
    fun importContacts(uri: Uri) {
        VcfImportSource.sSourceName = ""
        VcfImportSource.sSourceType = ""

        FilterContactSourceDialogMTs(this, true) {
            val sourceName = VcfImportSource.sSourceName
            val sourceType = VcfImportSource.sSourceType

            when {
                sourceName == MTS_NONE && sourceType == MTS_NONE -> {
                    // Uživatel chce nový účet – zobraz dialog pro zadání názvu a typu
                    NameTypeDialog.show(this, object : NameTypeDialog.NameTypeDialogCallback {
                        override fun onConfirmed(name: String, type: String) {
                            // Čitelná pozitivní podmínka místo 4 negací
                            val isValid = name.isNotBlank()
                                && type.isNotBlank()
                                && type != MTS_PHONE
                                && name != MTS_PHONE
                            if (isValid) startVcfImport(uri, name, type)
                            else toast(R.string.invalid_name_or_type)
                        }
                    })
                }
                sourceName.isNotBlank() && sourceType.isNotBlank() ->
                    startVcfImport(uri, sourceName, sourceType)
                else ->
                    toast(R.string.invalid_target_for_import)
            }
        }
    }

    private fun startVcfImport(uri: Uri, sourceName: String, sourceType: String) {
        runOnUiThread { binding.progressIndicatorSettings.beVisible() }
        lifecycleScope.launch {
            VcfImporter.import(
                activity = this@SettingsActivity,
                uri = uri,
                showExportingToast = true,
                accountName = sourceName,
                accountType = sourceType,
                onProgress = { /* progress callback – progress.current / progress.total */ }
            )
            binding.progressIndicatorSettings.beGone()
        }
    }

    fun exportContacts(uri: Uri) {
        // Oprava: openOutputStream může vrátit null – ošetřeno try/catch
        try {
            val outputStream = contentResolver.openOutputStream(uri)
                ?: run { showErrorToast(Exception("Cannot open output stream")); return }
            exportVisibleContacts(outputStream)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun exportVisibleContacts(outputStream: OutputStream) {
        runOnUiThread { binding.progressIndicatorSettings.beVisible() }
        val onlyWithNumbers = binding.settingsShowOnlyContactsWithNumbers.isChecked
        ContactsHelper(this).getContacts(getAll = false) { allContacts ->
            lifecycleScope.launch {
                VcfExporter().exportContacts(
                    activity = this@SettingsActivity,
                    outputStream = outputStream,
                    contacts = ArrayList(allContacts),
                    showExportingToast = true,
                    exportOnlyContactsWithNumbers = onlyWithNumbers
                )
                binding.progressIndicatorSettings.beGone()
            }
        }
    }

    private fun setupBlockedNumbersExport() {
        binding.settingsExportBlockedNumbersHolder.setOnClickListener {
            startActivity(ManageBlockedNumbersActivity.createExportIntent(this))
        }
    }

    private fun setupBlockedNumbersImport() {
        binding.settingsImportBlockedNumbersHolder.setOnClickListener {
            startActivity(ManageBlockedNumbersActivity.createImportIntent(this))
        }
    }

    private fun setupAllPerfsExport() {
        binding.settingsExportPerfsHolder.setOnClickListener {
        exportPrefsLauncher.launch("mts_prefs_backup.json")
    }
    }

    private fun setupAllPerfsImport() {
        if (mtsGlobalAll.iSaveDebugMode == 1) {
            binding.settingsImportPerfsHolder.setOnClickListener {
                importPrefsLauncher.launch(arrayOf("application/json"))
            }
        }
    }

    // launcher
    private val exportPrefsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportSharedPrefs(uri)
        }

    private val importPrefsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importSharedPrefs(uri)
        }


    // export
    private fun exportSharedPrefs(uri: Uri) {
        try {
            val json = org.json.JSONObject().apply {
                getSharedPrefs().all.forEach { (key, value) -> put(key, value) }
            }.toString(2)
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            toast(R.string.exporting_successful)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    // import
    private fun importSharedPrefs(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() }
            val obj = org.json.JSONObject(json)
            val editor = getSharedPrefs().edit()
            obj.keys().forEach { key ->
                when (val value = obj.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int     -> editor.putInt(key, value)
                    is Long    -> editor.putLong(key, value)
                    is Float   -> editor.putFloat(key, value)
                    is String  -> editor.putString(key, value)
                }
            }
            editor.apply()
            toast(R.string.importing_successful)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}
