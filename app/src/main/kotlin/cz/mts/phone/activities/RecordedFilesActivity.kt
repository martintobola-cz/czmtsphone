package cz.mts.phone.activities

import android.content.res.ColorStateList
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import cz.mts.base.dialogs.ExportFileDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.helpers.PopupMenuColorizer
import cz.mts.base.helpers.TreeExporter
import cz.mts.phone.R
import cz.mts.phone.adapters.RecordedFile
import cz.mts.phone.adapters.RecordedFilesAdapter
import cz.mts.phone.databinding.ActivityRecordedFilesBinding
import cz.mts.phone.recorder.RecordingPaths
import cz.mts.phone.recorder.SimpleAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException


class RecordedFilesActivity : SimpleActivity() {

    companion object {
        private const val KEY_PENDING_EXPORT_PATH = "pending_export_path"
        private const val KEY_PENDING_EXPORT_PATHS = "pending_export_paths"

        // Generický typ, aby DocumentsUI nepřidávalo vlastní příponu. Název (včetně přípony) dodáváme sami.
        private const val EXPORT_MIME_TYPE = "application/octet-stream"
    }

    override var customNavBarLightIcons: Boolean? = null

    private var isLoading = false
    private val durationCache = HashMap<String, Long>()
    private var pendingExportFiles: List<File> = emptyList()


    private val binding by viewBinding(ActivityRecordedFilesBinding::inflate)

    private val player = SimpleAudioPlayer(
        onStateChanged = { updatePlayerBar() },
        onError = {
            val sError = it.message.toString()
            toast(sError)
            if (iSaveDebugMode == 1) copyToClipboard(sError, false)
        }
    )

    // true během tahání za kuličku – to už pozici neposouvá ticker
    private var isSeeking = false

    private val progressTicker = object : Runnable {
        override fun run() {
            updatePlayerProgress()
            if (player.isPlaying) binding.recordedFilesPlayerBar.postDelayed(this, 250)
        }
    }

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
            onExport = ::exportRecordedFiles,
            onStopPlayback = { player.stop() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        pendingExportFiles = savedInstanceState?.getStringArrayList(KEY_PENDING_EXPORT_PATHS)
            ?.map(::File).orEmpty()

        pendingExportFile = savedInstanceState?.getString(KEY_PENDING_EXPORT_PATH)?.let(::File)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(recordedFilesList, recordedFilesPlayerBar))
            setupMaterialScrollListener(recordedFilesList, recordedFilesAppbar)
            recordedFilesList.adapter = filesAdapter
        }

        setupPlayerBar()
        setupToolbar()
        refreshFiles()
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBarWithBackHandling()
        updateTextColors(binding.recordedFilesCoordinator)
        updateMenuItemColors(binding.recordedFilesToolbar.menu)
        updatePlayerBarColors()
        updateEmptyStateAppearance()

        // po změně tématu přebarvit i řádky seznamu
        filesAdapter.updateTextColor(getProperTextColor())
        filesAdapter.updatePrimaryColor()
        filesAdapter.updateBackgroundColor(getProperBackgroundColor())
        filesAdapter.notifyDataSetChanged()
    }

    private fun updateEmptyStateAppearance() = binding.apply {
        recordedFilesEmpty.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())
        recordedFilesEmpty.setTextColor(getProperTextColor().adjustAlpha(0.6f))
    }

    private fun showEmptyState(show: Boolean) = binding.apply {
        recordedFilesEmpty.beVisibleIf(show)
        recordedFilesEmptyImage.beVisibleIf(show)
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

    /** Znovu načte obsah složky (na IO vlákně) a překreslí seznam. Řazení řeší adaptér. */
    fun refreshFiles() {
        if (isLoading) return
        isLoading = true
        showEmptyState(false) // během načítání prázdný stav neukazovat
        binding.progressIndicatorFiles.beVisible()

        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { loadFiles() }
                filesAdapter.updateItems(items)
                showEmptyState(items.isEmpty())
            } finally {
                isLoading = false
                binding.progressIndicatorFiles.beGone()
            }
        }
    }

    private fun loadFiles(): List<RecordedFile> {
        return try {
            RecordingPaths.migrateLegacyIfNeeded(this) // loadFiles běží na IO vlákně
            RecordingPaths.dir(this).listFiles()
                .orEmpty()
                .filter { it.isFile }
                .map {
                    val duration = durationCache.getOrPut("${it.absolutePath}:${it.lastModified()}") {
                        readDurationMs(it)
                    }
                    RecordedFile(it.absolutePath, it.name, it.length(), it.lastModified(), duration)
                }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
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


    private val exportTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            val files = pendingExportFiles
            pendingExportFiles = emptyList()
            if (treeUri != null && files.isNotEmpty()) exportFilesToTree(files, treeUri)
        }

    private fun exportRecordedFiles(items: List<RecordedFile>) {
        val files = items.map { File(it.path) }.filter { it.exists() }
        if (files.size != items.size) {
            toast(R.string.recorded_files_missing)
            refreshFiles()
            if (files.isEmpty()) return
        }
        if (files.size == 1) {
            exportRecordedFile(items.first { it.path == files[0].absolutePath }) // stávající flow
        } else {
            pendingExportFiles = files
            exportTreeLauncher.launch(null)
        }
    }


    private fun exportFilesToTree(files: List<File>, treeUri: Uri) {
        toast(R.string.exporting)
        binding.progressIndicatorFiles.beVisible()

        lifecycleScope.launch {
            try {
                val result = TreeExporter.copyFilesToTree(this@RecordedFilesActivity, files, treeUri)
                if (result.isSuccess) toast(R.string.exporting_successful)
                else {
                    toast(R.string.exporting_failed)
                    copyToClipboard(result.failedNames.toString(), false)
                   }
            } finally {
                binding.progressIndicatorFiles.beGone()
            }
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
        if (pendingExportFiles.isNotEmpty())
            outState.putStringArrayList(KEY_PENDING_EXPORT_PATHS, ArrayList(pendingExportFiles.map { it.absolutePath }))
    }

    private fun openFile(item: RecordedFile) {
        val file = File(item.path)
        if (!file.exists()) {
            toast(R.string.recorded_files_missing)
            refreshFiles()
            return
        }
        player.toggle(file)
        // označit řádek + vyvolat CAB, ať je jasné, co hraje
        filesAdapter.selectAndShowActionMenu(item)
    }

    // ─── Spodní mini ovladač ───────────────────────────────────────────────

    private fun setupPlayerBar() = binding.apply {
        recordedFilesPlayerPlayPause.setOnClickListener { player.togglePause() }

        recordedFilesPlayerSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // za tahu průběžně ukazuj čas, kam se kulička chystá
                if (fromUser) recordedFilesPlayerPosition.text = formatPlayerTime(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isSeeking = false
                player.seekTo(seekBar.progress)
                updatePlayerBar() // znovu rozjede ticker, pokud se hraje
            }
        })

        // Seznam se nesmí schovat pod ovladač. Okraj = výška ovladače minus spodní padding,
        // který seznamu už dalo setupEdgeToEdge (jinak by pod ním vznikla zbytečná mezera).
        recordedFilesPlayerBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateListBottomMargin()
        }
    }

    private fun updateListBottomMargin() {
        val bar = binding.recordedFilesPlayerBar
        val list = binding.recordedFilesList
        val margin = if (bar.visibility == android.view.View.VISIBLE) {
            (bar.height - list.paddingBottom).coerceAtLeast(0)
        } else {
            0
        }
        val lp = list.layoutParams as ViewGroup.MarginLayoutParams
        if (lp.bottomMargin != margin) {
            lp.bottomMargin = margin
            list.layoutParams = lp
        }
    }

    private fun updatePlayerBarColors() = binding.apply {
        val primary = getProperPrimaryColor()
        recordedFilesPlayerBar.setBackgroundColor(getProperBackgroundColor())
        recordedFilesPlayerPlayPause.setColorFilter(getProperTextColor())
        ColorStateList.valueOf(primary).let {
            recordedFilesPlayerSeek.progressTintList = it
            recordedFilesPlayerSeek.thumbTintList = it
        }
    }

    /** Zavolá přehrávač při každé změně stavu. */
    private fun updatePlayerBar() {
        val bar = binding.recordedFilesPlayerBar
        bar.removeCallbacks(progressTicker)

        val hasFile = player.currentPath != null
        bar.beVisibleIf(hasFile)
        if (!hasFile) return

        val ready = player.isPrepared
        val duration = player.durationMs

        binding.recordedFilesPlayerPlayPause.apply {
            isEnabled = ready
            setImageResource(if (player.isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play)
        }
        binding.recordedFilesPlayerSeek.apply {
            // bez známé délky (některé ogg ji neumí říct) nemá posouvání smysl
            isEnabled = ready && duration > 0
            max = duration
        }
        binding.recordedFilesPlayerDuration.text = formatPlayerTime(duration)

        updatePlayerProgress()
        if (player.isPlaying) bar.postDelayed(progressTicker, 250)
    }

    private fun updatePlayerProgress() {
        if (isSeeking) return
        val position = player.positionMs
        binding.recordedFilesPlayerSeek.progress = position
        binding.recordedFilesPlayerPosition.text = formatPlayerTime(position)
    }

    private fun formatPlayerTime(ms: Int): String {
        val totalSeconds = ms / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    override fun onStop() {
        binding.recordedFilesPlayerBar.removeCallbacks(progressTicker)
        player.stop()
        super.onStop()
    }

    override fun onDestroy() {
        binding.recordedFilesPlayerBar.removeCallbacks(progressTicker)
        player.stop()
        super.onDestroy()
    }
}