package cz.mts.phone.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import cz.mts.base.dialogs.ConfirmationAdvancedDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.helpers.SAVE_DISCARD_PROMPT_INTERVAL
import cz.mts.phone.R
import cz.mts.phone.databinding.ActivityShizukuAuthKeyBinding
import cz.mts.phone.recorder.RecorderLog
import cz.mts.phone.recorder.ShizukuAuthKeyStore
import cz.mts.phone.recorder.ShizukuConnectionManager
import cz.mts.phone.recorder.scrcpy.ScrcpyConfig
import cz.mts.phone.recorder.scrcpy.ServerExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import cz.mts.base.dialogs.ConfirmationNeutralDialog

class ShizukuAuthKeyActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null

    private var lastSavePromptTS = 0L
    private var isTestingConnection = false

    /** True, jakmile je scrcpy-server k dispozici (v APK nebo naimportovaný uživatelem). */
    private var serverReady = false

    private val binding by viewBinding(ActivityShizukuAuthKeyBinding::inflate)

    private val pickServer = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            revertSwitch()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ServerExtractor.importFromUri(
                    this@ShizukuAuthKeyActivity,
                    uri
                )
            }
            when (result) {
                is ServerExtractor.ImportResult.Success -> {
                    toast(R.string.scrcpy_sucess)
                    onServerReady()
                }
                is ServerExtractor.ImportResult.HashMismatch -> {
                    toast(R.string.scrcpy_error)
                    showScrcpyMissingDialog()
                }
                is ServerExtractor.ImportResult.Error -> {
                    toast("Error: ${result.cause.message}")
                    revertSwitch()
                }
                is ServerExtractor.ImportResult.PathNull -> {
                    toast("Error: ${result.sMessage}")
                    revertSwitch()
                }
            }
        }
    }

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
        customNavBarLightIcons = isLightBackground(getProperBackgroundColor())
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
                if (authKeyAgreeSwitch.isChecked) {
                    checkScrcpyServer()
                } else {
                    serverReady = false
                    updateInputVisibility(false)
                    refreshMenuItems()
                }
            }

            authKeyEditText.addTextChangedListener {
                refreshMenuItems()
            }
        }
    }

    private fun canSave(): Boolean =
        !isTestingConnection && serverReady &&
            binding.authKeyAgreeSwitch.isChecked && !binding.authKeyEditText.text.isNullOrBlank()

    private fun updateInputVisibility(visible: Boolean) {
        binding.authKeyInputHolder.beVisibleIf(visible)
        if (visible) {
            binding.authKeyEditText.requestFocus()
        }
    }

    // --- scrcpy-server: kontrola a import ---

    private fun checkScrcpyServer() {
        lifecycleScope.launch {
            val ready = withContext(Dispatchers.IO) {
                ServerExtractor.isServerAvailable(
                    this@ShizukuAuthKeyActivity,
                )
            }
            if (iSaveDebugMode == 1) showScrcpyMissingDialog()
            else {if (ready) onServerReady() else showScrcpyMissingDialog()}
        }
    }

    private fun onServerReady() {
        serverReady = true
        updateInputVisibility(true)
        refreshMenuItems()
    }

    private fun revertSwitch() {
        serverReady = false
        binding.authKeyAgreeSwitch.isChecked = false
        updateInputVisibility(false)
        refreshMenuItems()
    }

    private fun showScrcpyMissingDialog() {
        ConfirmationNeutralDialog(
            activity = this,
            message = getString(R.string.scrcpy_missing_message, ScrcpyConfig.SCRCPY_VERSION),
            dialogTitle = getString(R.string.scrcpy_missing_title),
            positive = R.string.scrcpy_pick_file,
            neutral = R.string.scrcpy_download,
            negative = R.string.cancel,
            cancelOnTouchOutside = false,
            onNeutral = { openUrl(ScrcpyConfig.SERVER_DOWNLOAD_URL) },
            onNegative = { revertSwitch() },
            onPositive = { pickServer.launch(arrayOf("*/*")) }
        )
    }

    private fun openUrl(url: String) =
        startActivitySafely(Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun startActivitySafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No app found to handle this action")
        }
    }
    // --- uložení klíče ---

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
                    val sError = e.message.toString()
                    toast(sError)
                    if (iSaveDebugMode == 1) copyToClipboard(sError, false)
                }
            }.onFailure { e ->
                // Klíč se NEUKLÁDÁ a switch v SettingsActivity zůstane vypnutý
                // (finish() se nevolá, RESULT_CANCELED zůstává v platnosti).
                val sError = e.message.toString()
                toast(sError)
                if (iSaveDebugMode == 1) copyToClipboard(sError, false)
            }
        }
    }
}
