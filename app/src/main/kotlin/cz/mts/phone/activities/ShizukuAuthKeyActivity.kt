package cz.mts.phone.activities

import android.os.Bundle
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import cz.mts.base.dialogs.ConfirmationAdvancedDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.helpers.SAVE_DISCARD_PROMPT_INTERVAL
import cz.mts.phone.R
import cz.mts.phone.databinding.ActivityShizukuAuthKeyBinding
import cz.mts.phone.recorder.RecorderLog
import cz.mts.phone.recorder.ShizukuAuthKeyStore
import cz.mts.phone.recorder.ShizukuConnectionManager
import kotlinx.coroutines.launch

class ShizukuAuthKeyActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null

    private var lastSavePromptTS = 0L
    private var isTestingConnection = false

    private val binding by viewBinding(ActivityShizukuAuthKeyBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(authKeyNestedScrollview))
            setupMaterialScrollListener(authKeyNestedScrollview, authKeyAppbar)
        }

        setResult(RESULT_CANCELED)

        setupTexts()
        prefillLastKey()
        setupOptionsMenu()
        setupListeners()
        setupLogs()
        updateInputVisibility(false)
        refreshMenuItems()


    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBarWithBackPrompt()
        updateTextColors(binding.authKeyHolder)
        updateMenuItemColors(binding.authKeyToolbar.menu)
    }

    /**
     * Wrapper kolem setupTopAppBar, co zapojí promptSaveDiscard logiku na šipku zpět –
     * stejně jako v CustomizationActivity. Volej tímhle, ne přímo setupTopAppBar().
     */
    private fun setupTopAppBarWithBackPrompt() {
        setupTopAppBar(
            topAppBar = binding.authKeyAppbar,
            navigationIcon = NavigationIcon.Arrow,
            onNavigationClick = {
                if (!onBackPressedCompat()) {
                    finish()
                }
            }
        )
    }

    override fun onBackPressedCompat(): Boolean {
        return if (canSave() && System.currentTimeMillis() - lastSavePromptTS > SAVE_DISCARD_PROMPT_INTERVAL) {
            promptSaveDiscard()
            true
        } else {
            false
        }
    }

    private fun promptSaveDiscard() {
        lastSavePromptTS = System.currentTimeMillis()
        ConfirmationAdvancedDialog(
            activity = this,
            message = "",
            messageId = R.string.save_before_closing,
            positive = R.string.save,
            negative = R.string.discard
        ) {
            if (it) {
                onSaveClick()
            } else {
                finish()
            }
        }
    }

    private fun setupTexts() {
        binding.authKeyInfoText.text = getString(R.string.call_recording_info)
    }

    // ať uživatel nemusí klíč pokaždé přepisovat znovu, jen switch zapnout a uložit
    private fun prefillLastKey() {
        val lastKey = ShizukuAuthKeyStore.getAuthKey(this)
        if (!lastKey.isNullOrBlank()) {
            binding.authKeyEditText.setText(lastKey)
        }
    }

    private fun setupLogs() {
        binding.authKeyLogText.setText(RecorderLog.getText())
    }



    private fun setupOptionsMenu() {
        binding.authKeyToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save -> {
                    onSaveClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun refreshMenuItems() {
        binding.authKeyToolbar.menu.findItem(R.id.save).apply {
            isVisible = canSave()
            isEnabled = !isTestingConnection
        }
    }

    private fun setupListeners() {
        binding.apply {
            authKeyAgreeHolder.setOnClickListener {
                authKeyAgreeSwitch.toggle()
                updateInputVisibility(authKeyAgreeSwitch.isChecked)
                refreshMenuItems()
            }

            authKeyEditText.addTextChangedListener {
                refreshMenuItems()
            }
        }
    }

    private fun canSave(): Boolean =
        !isTestingConnection && binding.authKeyAgreeSwitch.isChecked && !binding.authKeyEditText.text.isNullOrBlank()

    private fun updateInputVisibility(visible: Boolean) {
        binding.authKeyInputHolder.beVisibleIf(visible)
        if (visible) {
            binding.authKeyEditText.requestFocus()
        }
    }

    private fun onSaveClick() {
        if (!canSave()) return

        val key = binding.authKeyEditText.text?.toString()?.trim().orEmpty()

        isTestingConnection = true
        refreshMenuItems()
        binding.progressIndicatorSettings.beVisible()

        lifecycleScope.launch {
            val result = ShizukuConnectionManager.testConnection(this@ShizukuAuthKeyActivity, key)

            isTestingConnection = false
            refreshMenuItems()
            binding.progressIndicatorSettings.beGone()

            result.onSuccess {
                try {
                    ShizukuAuthKeyStore.setAuthKey(this@ShizukuAuthKeyActivity, key)
                    toast(R.string.call_recording_key_saved)
                    setResult(RESULT_OK)
                    finish()
                } catch (e: Exception) {
                    copyToClipboard(e.message.toString())
                }
            }.onFailure { e ->
                // Klíč se NEUKLÁDÁ a switch v SettingsActivity zůstane vypnutý
                // (finish() se nevolá, RESULT_CANCELED zůstává v platnosti).
                copyToClipboard(e.message.toString())
            }
        }
    }
}
