package cz.mts.phone.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import cz.mts.base.dialogs.ChangeDateTimeFormatDialog
import cz.mts.base.dialogs.RadioGroupDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.*
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.helpers.FastPhoneNumberFormatter.invalidateRegionCache
import cz.mts.base.models.RadioItem
import cz.mts.phone.R
import cz.mts.phone.databinding.ActivitySettingsBinding
import cz.mts.phone.dialogs.ManageVisibleTabsDialog
import cz.mts.phone.extensions.canLaunchAccountsConfiguration
import cz.mts.phone.extensions.launchAccountsConfiguration
import cz.mts.phone.helpers.OverlayColors
import cz.mts.phone.helpers.SmsQuickReplyOverlay
import java.util.Locale
import kotlin.system.exitProcess
import cz.mts.base.extensions.baseConfig as config


class SettingsActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null
    private var smsOverlay: SmsQuickReplyOverlay? = null
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    // ── Activity Result launchery – deklarovány před onCreate ────────────────

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

        // Listenery patří do onCreate – registrují se jen jednou
        setupClickListeners()
        setupAllowRecordCalls()

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
                settingsTransfersSectionLabel,
                settingsPrivacyPolicySectionLabel
            ).forEach { it.setTextColor(getProperPrimaryColor()) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }




    private fun refreshUiValues() {
        binding.apply {
            settingsUseEnglishHolder.beVisibleIf(
                Locale.getDefault().language != "en" && !isTiramisuPlus()
            )
            settingsUseEnglish.isChecked = config.useEnglish

            settingsLanguage.text = Locale.getDefault().displayLanguage
            settingsLanguageHolder.beVisibleIf(isTiramisuPlus())

            settingsManageBlockedNumbersLabel.text =getString(R.string.manage_blocked_numbers)
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
            settingsHideNumberForSavedContact.isChecked = config.hideNumberForSavedContact
            settingsAllowRecordCalls.isChecked = config.allowRecordCalls
            settingsHighlightCallButtons.isChecked = config.highlightCallButtons
            settingsCallingAccountsHolder.beVisibleIf(canLaunchAccountsConfiguration())
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
        setupHideNumberForSavedContact()
        setupTransfers()
        setupPrivacyPolicy()
        setupHighlightCallButtons()
        setupCallingAccounts()

    }

    private fun setupCallingAccounts() {
        binding.settingsCallingAccountsHolder.setOnClickListener {
            launchAccountsConfiguration()
        }
    }
    private fun setupHighlightCallButtons() {
        binding.settingsHighlightCallButtonsHolder.setOnClickListener {
            binding.settingsHighlightCallButtons.toggle()
            config.highlightCallButtons = binding.settingsHighlightCallButtons.isChecked
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            config.easterEggMode = false
            if (iSaveDebugMode == 2) iSaveDebugMode = 0
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
            if (isTiramisuPlus()) {
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
        if (isUpsideDownCakePlus()) {
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

    private val authKeyActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val allowed = result.resultCode == RESULT_OK
            binding.settingsAllowRecordCalls.isChecked = allowed
            config.allowRecordCalls = allowed
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

    private fun setupHideNumberForSavedContact() {
        binding.settingsHideNumberForSavedContactHolder.setOnClickListener {
            binding.settingsHideNumberForSavedContact.toggle()
            config.hideNumberForSavedContact = binding.settingsHideNumberForSavedContact.isChecked
        }
    }

    // ── Přenosy (import / export) – vlastní obrazovka ────────────────────────

    private fun setupTransfers() {
        binding.settingsTransferImportHolder.setOnClickListener {
            TransferActivity.start(this, TransferActivity.MODE_IMPORT)
        }
        binding.settingsTransferExportHolder.setOnClickListener {
            TransferActivity.start(this, TransferActivity.MODE_EXPORT)
        }
    }

    // ── Privacy policy – otevře obrazovku jako z About (s tlačítkem Odebrat souhlas) ──

    private fun setupPrivacyPolicy() {
        binding.settingsRevokeConsentHolder.setOnClickListener {
            startActivity(
                Intent(this, PrivacyPolicyActivity::class.java)
                    .putExtra(PrivacyPolicyActivity.EXTRA_FROM_ABOUT, true)
            )
        }
    }

    private fun setupAllowRecordCalls() {
        binding.settingsAllowRecordCallsHolder.setOnClickListener {
            if (binding.settingsAllowRecordCalls.isChecked) {
                // vypnutí nevyžaduje potvrzení
                binding.settingsAllowRecordCalls.isChecked = false
                config.allowRecordCalls = false
            } else {
                // zapnutí jde vždy přes ShizukuAuthKeyActivity – switch se nastaví
                // na true až po úspěšném uložení klíče (viz authKeyActivityLauncher)
                authKeyActivityLauncher.launch(Intent(this, ShizukuAuthKeyActivity::class.java))
            }
        }
    }
}
