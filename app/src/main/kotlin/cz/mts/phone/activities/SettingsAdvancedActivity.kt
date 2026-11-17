package cz.mts.phone.activities

import android.os.Bundle
import android.view.Menu
import cz.mts.base.extensions.*
import cz.mts.base.helpers.*
import cz.mts.phone.databinding.ActivityAdvancedSettingsBinding
import cz.mts.base.extensions.baseConfig as config


class SettingsAdvancedActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null
    private val binding by viewBinding(ActivityAdvancedSettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(callBehaviorNestedScrollview))
            setupMaterialScrollListener(callBehaviorNestedScrollview, callBehaviorAppbar)
        }

        // Listenery se registrují jen jednou
        setupClickListeners()
    }

    override fun onResume() {
        customNavBarLightIcons = isLightBackground(getProperBackgroundColor())
        super.onResume()
        setupTopAppBar(binding.callBehaviorAppbar, NavigationIcon.Arrow)

        // onResume pouze aktualizuje zobrazené hodnoty, ne listenery
        refreshUiValues()

        updateTextColors(binding.callBehaviorHolder)

        binding.callBehaviorSectionLabel.setTextColor(getProperPrimaryColor())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun refreshUiValues() {
        binding.apply {
            settingsHideNumberForSavedContact.isChecked = config.hideNumberForSavedContact
            settingsDisableProximitySensor.isChecked = config.disableProximitySensor
            settingsHighlightCallButtons.isChecked = config.highlightCallButtons
            callBehaviorAllowScreenOn.isChecked = config.allowScreenOn
            callBehaviorNoFinishDialpad.isChecked = config.noFinishDialPadActivity
            callBehaviorVibrateOnAccept.isChecked = config.vibrateOnAcceptCall
            callBehaviorVibrateOnEnd.isChecked = config.vibrateOnEndCall
            callBehaviorAfterCallClearSearchAndSetDefaultTab.isChecked = config.afterCallClearSearchAndSetDefaultTab
            usePopupMenuColorizerTab.isChecked = config.usePopupMenuColorizer
            showCheckmarksOnSwitches.isChecked = config.showCheckmarksOnSwitches
        }
    }

    // ── Listenery – voláno jednou z onCreate ─────────────────────────────────

    private fun setupClickListeners() {
        setupDisableProximitySensor()
        setupHideNumberForSavedContact()
        setupHighlightCallButtons()
        setupAllowScreenOn()
        setupNoFinishDialpad()
        setupVibrateOnAccept()
        setupVibrateOnEnd()
        setupAfterCallClearSearchAndSetDefaultTab()
        setupUsePopupMenuColorizer()
        setUpShowCheckmarksOnSwitches()
    }

    private fun setupAllowScreenOn() {
        binding.callBehaviorAllowScreenOnHolder.setOnClickListener {
            binding.callBehaviorAllowScreenOn.toggle()
            config.allowScreenOn = binding.callBehaviorAllowScreenOn.isChecked
        }
    }

    private fun setupNoFinishDialpad() {
        binding.callBehaviorNoFinishDialpadHolder.setOnClickListener {
            binding.callBehaviorNoFinishDialpad.toggle()
            config.noFinishDialPadActivity = binding.callBehaviorNoFinishDialpad.isChecked
        }
    }

    private fun setupVibrateOnAccept() {
        binding.callBehaviorVibrateOnAcceptHolder.setOnClickListener {
            binding.callBehaviorVibrateOnAccept.toggle()
            config.vibrateOnAcceptCall = binding.callBehaviorVibrateOnAccept.isChecked
        }
    }

    private fun setupVibrateOnEnd() {
        binding.callBehaviorVibrateOnEndHolder.setOnClickListener {
            binding.callBehaviorVibrateOnEnd.toggle()
            config.vibrateOnEndCall = binding.callBehaviorVibrateOnEnd.isChecked
        }
    }

    private fun setupAfterCallClearSearchAndSetDefaultTab() {
        binding.callBehaviorAfterCallClearSearchAndSetDefaultTabHolder.setOnClickListener {
            binding.callBehaviorAfterCallClearSearchAndSetDefaultTab.toggle()
            config.afterCallClearSearchAndSetDefaultTab = binding.callBehaviorAfterCallClearSearchAndSetDefaultTab.isChecked
        }
    }

    private fun setupDisableProximitySensor() {
        binding.settingsDisableProximitySensorHolder.setOnClickListener {
            binding.settingsDisableProximitySensor.toggle()
            config.disableProximitySensor = binding.settingsDisableProximitySensor.isChecked
        }
    }

    private fun setupHighlightCallButtons() {
        binding.settingsHighlightCallButtonsHolder.setOnClickListener {
            binding.settingsHighlightCallButtons.toggle()
            config.highlightCallButtons = binding.settingsHighlightCallButtons.isChecked
        }
    }

    private fun setupHideNumberForSavedContact() {
        binding.settingsHideNumberForSavedContactHolder.setOnClickListener {
            binding.settingsHideNumberForSavedContact.toggle()
            config.hideNumberForSavedContact = binding.settingsHideNumberForSavedContact.isChecked
        }
    }

    private fun setupUsePopupMenuColorizer() {
        binding.usePopupMenuColorizerHolder.setOnClickListener {
            binding.usePopupMenuColorizerTab.toggle()
            config.usePopupMenuColorizer = binding.usePopupMenuColorizerTab.isChecked
        }
    }

    private fun setUpShowCheckmarksOnSwitches() {
        binding.showCheckmarksOnSwitchesHolder.setOnClickListener {
            binding.showCheckmarksOnSwitches.toggle()
            config.showCheckmarksOnSwitches = binding.showCheckmarksOnSwitches.isChecked
        }
    }
}
