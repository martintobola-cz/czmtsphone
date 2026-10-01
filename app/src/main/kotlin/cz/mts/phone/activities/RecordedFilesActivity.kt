package cz.mts.phone.activities

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import cz.mts.base.dialogs.ExportFileDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.helpers.PopupMenuColorizer
import cz.mts.phone.R
import cz.mts.phone.adapters.RecordedFile
import cz.mts.phone.adapters.RecordedFilesAdapter
import cz.mts.phone.databinding.ActivityRecordedFilesBinding
import cz.mts.phone.recorder.SimpleAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class RecordedFilesActivity : SimpleActivity() {

    companion object {
        private const val KEY_PENDING_EXPORT_PATH = "pending_export_path"

        // Generický typ, aby DocumentsUI nepřidávalo vlastní příponu. Název (včetně přípony) dodáváme sami.
        private const val EXPORT_MIME_TYPE = "application/octet-stream"
    }

    override var customNavBarLightIcons: Boolean? = null

    private var isLoading = false

    private val binding by viewBinding(ActivityRecordedFilesBinding::inflate)

    private val player = SimpleAudioPlayer(onError = { copyToClipboard(it.message.toString()) })

    // Soubor čekající na výběr cíle (launcher vrací jen Uri). Přežívá rotaci přes onSaveInstanceState.
    private var pendingExportFile: File? = null

    private val exportFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE)) { uri ->
            val source = pendingExportFile
            pendingExportFile = null
            if (uri != null && source != null) exportFileTo(source, uri)
        }

    private val filesAdapter by lazy {
        RecordedFilesAdapter(
            activity = this,
            recyclerView = binding.recordedFilesList,
            itemClick = ::openFile,
            onDelete = ::deleteRecordedFiles,
            onExport = ::exportRecordedFile
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        pendingExportFile = savedInstanceState?.getString(KEY_PENDING_EXPORT_PATH)?.let(::File)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(recordedFilesList))
            setupMaterialScrollListener(recordedFilesList, recordedFilesAppbar)
            recordedFilesList.adapter = filesAdapter
        }

        setupToolbar()
        refreshFiles()
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBarWithBackHandling()
        updateTextColors(binding.recordedFilesCoordinator)
        updateMenuItemColors(binding.recordedFilesToolbar.menu)

        // po změně tématu přebarvit i řádky seznamu
        filesAdapter.updateTextColor(getProperTextColor())
        filesAdapter.updatePrimaryColor()
        filesAdapter.updateBackgroundColor(getProperBackgroundColor())
        filesAdapter.notifyDataSetChanged()
    }

    // ─── Toolbar (nahoře) - stejné barvení jako v ManageBlockedNumbersActivity ─────

    private fun setupToolbar() {
        binding.recordedFilesToolbar.apply {
            inflateMenu(R.menu.menu_recorded_files)

            val colorizerEnabled = baseConfig.usePopupMenuColorizer && !isDynamicTheme()
            if (colorizerEnabled) {
                PopupMenuColorizer.colorizeTitles(menu, baseConfig.popupMenuTextColor)
                PopupMenuColorizer.attachOverflowColorHook(
                    toolbar = this,
                    context = this@RecordedFilesActivity,
                    getTextColor = { baseConfig.popupMenuTextColor },
                    getBackgroundColor = { baseConfig.popupMenuBackgroundColor },
                    isColoringEnabled = { colorizerEnabled },
                    isDebugEnabled = { iSaveDebugMode == 1 }
                )
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.recorded_files_refresh -> refreshFiles()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }
    }

    /**
     * Stejný princip jako v ShizukuAuthKeyActivity – šipka zpět jde přes onBackPressedCompat().
     * Volej tímhle, ne přímo setupTopAppBar().
     */
    private fun setupTopAppBarWithBackHandling() {
        setupTopAppBar(
            topAppBar = binding.recordedFilesAppbar,
            navigationIcon = NavigationIcon.Arrow,
            onNavigationClick = {
                if (!onBackPressedCompat()) {
                    finish()
                }
            }
        )
    }

    // Zatím žádný prompt – false = nech systém aktivitu zavřít. Hák je připravený pro pozdější použití.
    override fun onBackPressedCompat(): Boolean = false

    /** Složka s nahrávkami. Pokud recorder ukládá jinde, změň jen tady. */
    private fun getRecordsDir(): File {
        val base = getExternalFilesDir(null) ?: filesDir
        return File(base, "recordings")
    }

    /** Znovu načte obsah složky (na IO vlákně) a překreslí seznam. Řazení řeší adaptér. */
    fun refreshFiles() {
        if (isLoading) return
        isLoading = true
        binding.progressIndicatorFiles.beVisible()

        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { loadFiles() }
                filesAdapter.updateItems(items)
                binding.recordedFilesEmpty.beVisibleIf(items.isEmpty())
            } finally {
                isLoading = false
                binding.progressIndicatorFiles.beGone()
            }
        }
    }

    private fun loadFiles(): List<RecordedFile> {
        return try {
            getRecordsDir().listFiles()
                .orEmpty()
                .filter { it.isFile }
                .map { RecordedFile(it.absolutePath, it.name, it.length(), it.lastModified()) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Volá adaptér po potvrzení dialogu (ConfirmationDialog je v adaptéru). */
    private fun deleteRecordedFiles(items: List<RecordedFile>) {
        if (items.any { player.currentPath == it.path }) player.stop()

        lifecycleScope.launch {
            val failedCount = withContext(Dispatchers.IO) {
                items.count { item ->
                    try {
                        val file = File(item.path)
                        !(!file.exists() || file.delete())
                    } catch (e: SecurityException) {
                        true
                    }
                }
            }

            if (failedCount > 0) {
                toast(R.string.recorded_files_delete_failed)
            }
            refreshFiles()
        }
    }

    /** Dialog s předvyplněným původním názvem -> výběr cíle (SAF) -> [exportFileTo]. */
    private fun exportRecordedFile(item: RecordedFile) {
        val file = File(item.path)
        if (!file.exists()) {
            toast(R.string.recorded_files_missing)
            refreshFiles()
            return
        }

        ExportFileDialog(
            activity = this,
            titleRes = R.string.recorded_files_export,
            defaultFilename = file.nameWithoutExtension,
        ) { filename ->
            pendingExportFile = file
            val extension = file.extension
            exportFileLauncher.launch(if (extension.isEmpty()) filename else "$filename.$extension")
        }
    }

    /** Zkopíruje soubor do místa, které uživatel vybral (na IO vlákně). */
    private fun exportFileTo(source: File, uri: Uri) {
        if (!source.exists()) {
            toast(R.string.recorded_files_missing)
            refreshFiles()
            return
        }

        toast(R.string.exporting)
        binding.progressIndicatorFiles.beVisible()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = contentResolver.openOutputStream(uri)
                        ?: throw IOException("Cannot open output stream")
                    output.use { out -> source.inputStream().use { it.copyTo(out) } }
                }
                toast(R.string.exporting_successful)
            } catch (e: Exception) {
                showErrorToast(e)
            } finally {
                binding.progressIndicatorFiles.beGone()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingExportFile?.let { outState.putString(KEY_PENDING_EXPORT_PATH, it.absolutePath) }
    }

    private fun openFile(item: RecordedFile) {
        val file = File(item.path)
        if (!file.exists()) {
            toast(R.string.recorded_files_missing)
            refreshFiles()
            return
        }
        player.toggle(file)
    }

    override fun onStop() {
        player.stop()
        super.onStop()
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }
}