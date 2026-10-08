package cz.mts.phone.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.widget.CompoundButtonCompat
import androidx.lifecycle.lifecycleScope
import cz.mts.base.dialogs.ExportFileDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.*
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.phone.R
import cz.mts.phone.databinding.ActivityTransferBinding
import cz.mts.phone.dialogs.FilterContactSourceDialogMTs
import cz.mts.phone.dialogs.NameTypeDialog
import cz.mts.phone.helpers.BlockedNumbersImportExportHelper
import cz.mts.phone.helpers.RecentsHelper
import cz.mts.phone.models.RecentCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.OutputStream
import cz.mts.base.extensions.baseConfig as config


/**
 * Univerzální obrazovka pro import i export. Položky jsou vždy stejné, liší se jen akce.
 * Spuštění: TransferActivity.start(activity, TransferActivity.MODE_IMPORT / MODE_EXPORT)
 */
class TransferActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null

    companion object {
        const val EXTRA_MODE = "transfer_mode"
        const val MODE_IMPORT = 0
        const val MODE_EXPORT = 1

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

        fun start(activity: android.app.Activity, mode: Int) {
            activity.startActivity(
                Intent(activity, TransferActivity::class.java).putExtra(EXTRA_MODE, mode)
            )
        }
    }

    private val binding by viewBinding(ActivityTransferBinding::inflate)

    private val isExport by lazy { intent.getIntExtra(EXTRA_MODE, MODE_IMPORT) == MODE_EXPORT }

    // ── Activity Result launchery – musí být deklarovány před onCreate ───────

    private val importExportHelper = BlockedNumbersImportExportHelper(this)

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
                binding.progressIndicatorTransfer.beVisible()  // ← show
                lifecycleScope.launch {
                    val recents = withContext(Dispatchers.IO) {
                        RecentsHelper(this@TransferActivity).loadAllRecentsForExport()
                    }
                    exportCallHistory(recents, uri)
                    binding.progressIndicatorTransfer.beGone()  // ← hide
                }
            }
        }

    private val exportContactsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(CONTACT_VCF_FILE_TYPE)) { uri ->
            if (uri != null) exportContacts(uri)
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



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(transferNestedScrollview))
            setupMaterialScrollListener(transferNestedScrollview, transferAppbar)
            transferToolbar.title = getString(if (isExport) R.string.transfer_export else R.string.transfer_import)
        }

        setupClickListeners()
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBar(binding.transferAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.transferHolder)

        // viditelnost řádků závisí na režimu (a na debug módu, ten se může změnit v About)
        binding.apply {
            transferContactsSimHolder.beVisibleIf(!isExport)                       // na SIM se nezapisuje
            transferSettingsHolder.beVisibleIf(isExport || iSaveDebugMode == 1)    // import nastavení jen v debug módu
        }
    }

    // ── Položky – stejné pro import i export, liší se jen akce ───────────────

    private fun setupClickListeners() {
        binding.apply {
            transferContactsHolder.setOnClickListener {
                if (isExport) exportContactsAction() else importContactsLauncher.launch(IMPORT_CONTACT_VCF_FILE_TYPE_TYPES.toTypedArray())
            }
            transferContactsSimHolder.setOnClickListener {
                chooseSimAndImport() // jen import; v exportu je řádek skrytý
            }
            transferBlockedNumbersHolder.setOnClickListener {
                if (isExport) importExportHelper.tryExportBlockedNumbers() else importExportHelper.tryImportBlockedNumbers()
            }
            transferCallHistoryHolder.setOnClickListener {
                if (isExport) exportCallHistoryAction() else importCallHistoryLauncher.launch(IMPORT_CALL_HISTORY_FILE_TYPES.toTypedArray())
            }
            transferSettingsHolder.setOnClickListener {
                if (isExport) exportSettingsAction() else importPrefsLauncher.launch(arrayOf("application/json"))
            }
        }
    }

    private fun exportContactsAction() {
        ExportFileDialog(
            activity = this,
            titleRes = R.string.export_contacts,
            defaultFilename = "contacts_${getCurrentFormattedDateTime()}",
        ) { filename -> exportContactsLauncher.launch("$filename.vcf") }
    }

    private fun exportCallHistoryAction() {
        ExportFileDialog(
            activity = this,
            titleRes = R.string.export_call_history,
            defaultFilename = "call_history_${getCurrentFormattedDateTime()}",
        ) { filename -> exportCallHistoryLauncher.launch("$filename.json") }
    }

    private fun exportSettingsAction() {
        ExportFileDialog(
            activity = this,
            titleRes = R.string.export_settings,
            defaultFilename = "mts_prefs_backup_${getCurrentFormattedDateTime()}",
        ) { filename -> exportPrefsLauncher.launch("$filename.json") }
    }

    // ── Kontakty (SIM) ───────────────────────────────────────────────────────

    private data class SimChoice(val subId: Int, val title: String)

    private fun chooseSimAndImport() {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (!granted) return@handlePermission

            mtsGlobalAll.numberOfReadySim(this)
            val choices = ArrayList<SimChoice>()
            for (i in 0..1) {
                val subId = mtsGlobalAll.iActiveSimSlotIDreal[i]
                if (subId == -123456789) continue
                val operator = mtsGlobalAll.sActiveSimSlotOperator[i].trim()
                val title = if (operator.isEmpty()) "SIM${i + 1}" else "SIM${i + 1} ($operator)"
                choices.add(SimChoice(subId, title))
            }

            if (choices.isEmpty()) {
                toast(R.string.transfer_no_active_sim)
            } else {
                // dialog se ukáže vždy, i když je jen jedna SIM
                showSelectSimDialog(choices) { subIds -> startSimImport(subIds) }
            }
        }
    }

    /** Vlastní dialog: informační text + checkbox pro každou SIM + OK / Zrušit. Nic není předvybráno. */
    private fun showSelectSimDialog(choices: List<SimChoice>, onConfirm: (List<Int>) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_select_sim, null)
        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()
        val density = resources.displayMetrics.density

        view.findViewById<TextView>(R.id.selectSimMessage).apply {
            setText(R.string.transfer_sim_import_message)
            setTextColor(textColor)
        }

        val selected = HashSet<Int>()
        var dialog: AlertDialog? = null

        fun updateOkButton() {
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.let {
                val enabled = selected.isNotEmpty()
                it.isEnabled = enabled
                it.alpha = if (enabled) 1f else 0.4f
            }
        }

        val list = view.findViewById<LinearLayout>(R.id.selectSimList)
        choices.forEach { choice ->
            list.addView(AppCompatCheckBox(this).apply {
                text = choice.title
                textSize = 16f
                setTextColor(textColor)
                CompoundButtonCompat.setButtonTintList(this, ColorStateList.valueOf(primaryColor))
                val pad = (8 * density).toInt()
                setPadding(pad, pad, pad, pad)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(choice.subId) else selected.remove(choice.subId)
                    updateOkButton()
                }
            })
        }

        val roundedBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 64f
            setColor(getProperBackgroundColor())
        }

        val d = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val subIds = choices.map { it.subId }.filter { it in selected }
                if (subIds.isNotEmpty()) onConfirm(subIds)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        d.window?.setBackgroundDrawable(roundedBackground)
        d.show()
        dialog = d

        // getButton() je dostupné až po show()
        listOf(d.getButton(AlertDialog.BUTTON_POSITIVE), d.getButton(AlertDialog.BUTTON_NEGATIVE)).forEach {
            it?.setTextColor(primaryColor)
            it?.isAllCaps = false
            it?.setTypeface(it.typeface, Typeface.BOLD)
        }
        updateOkButton()
    }

    private fun startSimImport(subIds: List<Int>) {
        runOnUiThread { binding.progressIndicatorTransfer.beVisible() }
        lifecycleScope.launch {
            // SIM se importují postupně; při vybrání obou se po sobě zobrazí dva souhrnné toasty
            subIds.forEach { subId ->
                SimImporter.import(
                    activity = this@TransferActivity,
                    subId = subId,
                    showToast = true,
                    onProgress = { /* progress.current / progress.total */ }
                )
            }
            binding.progressIndicatorTransfer.beGone()
        }
    }

    // ── Import / Export (přesunuto ze SettingsActivity beze změny logiky) ────

    private fun importCallHistory(uri: Uri) {
        binding.progressIndicatorTransfer.beVisible()  // ← show
        try {
            val jsonString = contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() }
            val objects = Json.decodeFromString<List<RecentCall>>(jsonString)

            if (objects.isEmpty()) {
                binding.progressIndicatorTransfer.beGone()
                toast(R.string.no_entries_for_importing)
                return
            }
            RecentsHelper(this).restoreRecentCalls(
                activity = this,
                calls = objects,
                showImportingToast = true
            ) { result ->
                runOnUiThread {
                    binding.progressIndicatorTransfer.beGone()

                    if (result is RecentsHelper.RestoreRecentsResult.Error) {
                        showErrorToast(Exception(result.message))
                    }
                }
            }
        } catch (_: SerializationException) {
            binding.progressIndicatorTransfer.beGone()
            toast(R.string.invalid_file_format)
        } catch (_: IllegalArgumentException) {
            binding.progressIndicatorTransfer.beGone()
            toast(R.string.invalid_file_format)
        } catch (e: Exception) {
            binding.progressIndicatorTransfer.beGone()
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
        runOnUiThread { binding.progressIndicatorTransfer.beVisible() }
        lifecycleScope.launch {
            VcfImporter.import(
                activity = this@TransferActivity,
                uri = uri,
                showExportingToast = true,
                accountName = sourceName,
                accountType = sourceType,
                onProgress = { /* progress callback – progress.current / progress.total */ }
            )
            binding.progressIndicatorTransfer.beGone()
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
        runOnUiThread { binding.progressIndicatorTransfer.beVisible() }
        val onlyWithNumbers = config.showOnlyContactsWithNumbers
        ContactsHelper(this).getContacts(getAll = false) { allContacts ->
            lifecycleScope.launch {
                VcfExporter().exportContacts(
                    activity = this@TransferActivity,
                    outputStream = outputStream,
                    contacts = ArrayList(allContacts),
                    showExportingToast = true,
                    exportOnlyContactsWithNumbers = onlyWithNumbers
                )
                binding.progressIndicatorTransfer.beGone()
            }
        }
    }


    // export / import nastavení (SharedPreferences)
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
