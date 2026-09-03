package cz.mts.base.activities

import  android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cz.mts.base.R
import cz.mts.base.compose.alert_dialog.rememberAlertDialogState
import cz.mts.base.compose.extensions.enableEdgeToEdgeSimple
import cz.mts.base.compose.extensions.onEventValue
import cz.mts.base.compose.screens.ManageBlockedNumbersScreen
import cz.mts.base.compose.theme.AppThemeSurface
import cz.mts.base.dialogs.AddOrEditBlockedNumberAlertDialog
import cz.mts.base.dialogs.ExportBlockedNumbersDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.*
import cz.mts.base.models.BlockedNumber
import java.io.FileOutputStream
import java.io.OutputStream

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

    private val config by lazy { baseConfig }

    private val blockedNumberMimeTypes = buildList {
        add("text/plain")
        if (!isQPlus()) add("application/octet-stream")
    }.toTypedArray()

    // ─── ActivityResultLaunchers ──────────────────────────────────────────────

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) tryImportBlockedNumbersFromFile(uri)
    }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val outputStream = contentResolver.openOutputStream(uri)
            exportBlockedNumbersTo(outputStream)
        }
    }

    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()
    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""
    override fun getRepositoryName() = null

    private val manageBlockedNumbersViewModel by viewModels<ManageBlockedNumbersViewModel>()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeSimple()
        setContent {
            val context = LocalContext.current
            val blockedNumbers by manageBlockedNumbersViewModel.blockedNumbers
                .collectAsStateWithLifecycle()

            LaunchedEffect(blockedNumbers) {
                if (blockedNumbers?.any { it.number.isBlockedNumberPattern() } == true) {
                    maybeSetDefaultCallerIdApp()
                }
            }

            val isBlockingHiddenNumbers by config.isBlockingHiddenNumbers
                .collectAsStateWithLifecycle(initialValue = config.blockHiddenNumbers)
            val isBlockingUnknownNumbers by config.isBlockingUnknownNumbers
                .collectAsStateWithLifecycle(initialValue = config.blockUnknownNumbers)
            val showCheckmarksOnSwitches by config.showCheckmarksOnSwitchesFlow
                .collectAsStateWithLifecycle(initialValue = config.showCheckmarksOnSwitches)

            val isCheckSpamSelected by produceState(
                initialValue = isAppInstalled("callfilter.app") || isAppInstalled("cz.mts.callfilter")
            ) {
                value = isAppInstalled("callfilter.app") || isAppInstalled("cz.mts.callfilter")
            }

            val isDialer = remember {
                config.appId.startsWith(MY_APP_NAME_GOOGLE_ID)
            }
            val isDefaultDialer: Boolean = onEventValue {
                context.isDefaultDialer()
            }

            AppThemeSurface {
                var clickedBlockedNumber by remember { mutableStateOf<BlockedNumber?>(null) }
                val addBlockedNumberDialogState = rememberAlertDialogState()

                addBlockedNumberDialogState.DialogMember {
                    AddOrEditBlockedNumberAlertDialog(
                        alertDialogState = addBlockedNumberDialogState,
                        blockedNumber = clickedBlockedNumber,
                        deleteBlockedNumber = { blockedNumber ->
                            deleteBlockedNumber(blockedNumber)
                            updateBlockedNumbers()
                        }
                    ) { blockedNumber ->
                        addBlockedNumber(blockedNumber)
                        clickedBlockedNumber = null
                        updateBlockedNumbers()
                    }
                }

                ManageBlockedNumbersScreen(
                    goBack = ::finish,
                    onAdd = {
                        clickedBlockedNumber = null
                        addBlockedNumberDialogState.show()
                    },
                    onImportBlockedNumbers = ::tryImportBlockedNumbers,
                    onExportBlockedNumbers = ::tryExportBlockedNumbers,
                    setAsDefault = ::maybeSetDefaultCallerIdApp,
                    isDialer = isDialer,
                    isCheckSpamSelected = isCheckSpamSelected,
                    hasGivenPermissionToBlock = isDefaultDialer,
                    isBlockUnknownSelected = isBlockingUnknownNumbers,
                    showCheckmarksOnSwitches = showCheckmarksOnSwitches,
                    onBlockUnknownSelectedChange = { isChecked ->
                        config.blockUnknownNumbers = isChecked
                        onCheckedSetCallerIdAsDefault(isChecked)
                    },
                    isHiddenSelected = isBlockingHiddenNumbers,
                    onHiddenSelectedChange = { isChecked ->
                        config.blockHiddenNumbers = isChecked
                        onCheckedSetCallerIdAsDefault(isChecked)
                    },
                    blockedNumbers = blockedNumbers,
                    onDelete = { selectedKeys ->
                        deleteBlockedNumbers(blockedNumbers, selectedKeys)
                    },
                    onEdit = { blockedNumber ->
                        clickedBlockedNumber = blockedNumber
                        addBlockedNumberDialogState.show()
                    },
                    onCopy = { blockedNumber ->
                        copyToClipboard(blockedNumber.number)
                    },
                    onCheckSpamSelectedChange = { isChecked ->
                        if (isChecked && !isCheckSpamSelected) {
                            try {
                                startActivity(
                                    PlayStoreIntentHelper.createOpenStoreIntent(this, "callfilter.app")
                                )
                            } catch (_: ActivityNotFoundException) {
                                // Uživatel nemá Obchod Play – tiché ignorování
                            }
                        }
                    }
                )
            }
        }
        if (savedInstanceState == null) {          // jen při prvním vytvoření, ne po rotaci
            when (intent.getStringExtra(EXTRA_ACTION)) {
                ACTION_IMPORT -> tryImportBlockedNumbers()
                ACTION_EXPORT -> tryExportBlockedNumbers()
            }
        }
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        //window.setSystemBarsAppearance(getProperBackgroundColor(), customNavBarLightIcons)
    }

    // ─── Blokovaná čísla – operace ────────────────────────────────────────────

    private fun deleteBlockedNumbers(
        blockedNumbers: ImmutableList<BlockedNumber>?,
        selectedKeys: Set<Long>,
    ) {
        if (blockedNumbers.isNullOrEmpty()) return
        blockedNumbers
            .filter { it.id in selectedKeys }
            .forEach { deleteBlockedNumber(it.number) }
        manageBlockedNumbersViewModel.updateBlockedNumbers()
    }

    private fun updateBlockedNumbers() {
        manageBlockedNumbersViewModel.updateBlockedNumbers()
    }

    // ─── Import ───────────────────────────────────────────────────────────────

    private fun tryImportBlockedNumbers() {
        try {
            openDocument.launch(blockedNumberMimeTypes)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun tryImportBlockedNumbersFromFile(uri: Uri) {
        when (uri.scheme) {
            "file" -> importBlockedNumbers(uri.path ?: return)
            "content" -> {
                val tempFile = getTempFile("blocked", "blocked_numbers.txt")
                if (tempFile == null) {
                    toast(R.string.unknown_error_occurred)
                    return
                }
                try {
                    // Oba streamy uzavřeny přes use{} – zabrání resource leaku
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(tempFile).use { out ->
                            inputStream.copyTo(out)
                        }
                    } ?: run {
                        toast(R.string.unknown_error_occurred)
                        return
                    }
                    importBlockedNumbers(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
            else -> toast(R.string.invalid_file_format)
        }
    }

    private fun importBlockedNumbers(path: String) {
        ensureBackgroundThread {
            val result = BlockedNumbersImporter(this).importBlockedNumbers(path)
            // toast musí být na hlavním vlákně
            runOnUiThread {
                toast(
                    when (result) {
                        BlockedNumbersImporter.ImportResult.IMPORT_OK -> R.string.importing_successful
                        BlockedNumbersImporter.ImportResult.IMPORT_FAIL -> R.string.no_items_found
                    }
                )
            }
            updateBlockedNumbers()
        }
    }

    // ─── Export ───────────────────────────────────────────────────────────────

    private fun tryExportBlockedNumbers() {
        ExportBlockedNumbersDialog(
            activity = this,
            path = baseConfig.lastBlockedNumbersExportPath,
            hidePath = true,
        ) { file ->
            try {
                createDocument.launch(file.name)
            } catch (_: ActivityNotFoundException) {
                toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun exportBlockedNumbersTo(outputStream: OutputStream?) {
        ensureBackgroundThread {
            val blockedNumbers = getBlockedNumbers()
            if (blockedNumbers.isEmpty()) {
                // toast na hlavním vlákně
                runOnUiThread { toast(R.string.no_entries_for_exporting) }
            } else {
                BlockedNumbersExporter.exportBlockedNumbers(blockedNumbers, outputStream) { result ->
                    runOnUiThread {
                        toast(
                            when (result) {
                                ExportResult.EXPORT_OK -> R.string.exporting_successful
                                else -> R.string.exporting_failed
                            }
                        )
                    }
                }
            }
        }
    }

    // ─── Caller ID / výchozí volač ────────────────────────────────────────────

    private fun onCheckedSetCallerIdAsDefault(isChecked: Boolean) {
        if (isChecked) maybeSetDefaultCallerIdApp()
    }

    private fun maybeSetDefaultCallerIdApp() {
        if (isQPlus() && baseConfig.appId.startsWith(MY_APP_NAME_GOOGLE_ID)) {
            setDefaultCallerIdApp()
        }
    }

    // ─── ViewModel ───────────────────────────────────────────────────────────

    internal class ManageBlockedNumbersViewModel(
        private val application: Application,
    ) : AndroidViewModel(application) {

        private val _blockedNumbers = MutableStateFlow<ImmutableList<BlockedNumber>?>(null)
        val blockedNumbers = _blockedNumbers.asStateFlow()

        init {
            updateBlockedNumbers()
        }

        fun updateBlockedNumbers() {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    application.getBlockedNumbersWithContact { list ->
                        _blockedNumbers.update { list.toImmutableList() }
                    }
                }
            }
        }
    }
}
