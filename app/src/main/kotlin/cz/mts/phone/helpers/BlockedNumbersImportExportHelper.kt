package cz.mts.phone.helpers

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.dialogs.ExportFileDialog
import cz.mts.base.extensions.addBlockedNumber
import cz.mts.base.extensions.getBlockedNumbers
import cz.mts.base.extensions.showErrorToast
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.BlockedNumbersJson
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.phone.R
import java.io.OutputStream

/**
 * Import/export blokovaných čísel (.json), vytažené z ManageBlockedNumbersActivity,
 * aby šlo volat i z jiných obrazovek.
 *
 * Formát a parsování/serializace řeší BlockedNumbersJson (cz.mts.base.helpers),
 * stejně jako v Compose verzi (ManageBlockedNumbersActivity_json).
 *
 * POZOR: musí se vytvořit jako field/property hostitelské aktivity (ne až v onStart
 * nebo později) - registerForActivityResult to vyžaduje, stejně jako to bylo
 * doteď přímo v ManageBlockedNumbersActivity.
 */
class BlockedNumbersImportExportHelper(
    private val activity: BaseSimpleActivity,
    private val onFinished: () -> Unit = {},
) {
    private val blockedNumberMimeTypes = arrayOf(BlockedNumbersJson.MIME_TYPE)

    private val openDocument = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) tryImportBlockedNumbersFromFile(uri)
    }

    private val createDocument = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(BlockedNumbersJson.MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            val outputStream = activity.contentResolver.openOutputStream(uri)
            exportBlockedNumbersTo(outputStream)
        }
    }

    // ─── Import ───────────────────────────────────────────────────────────

    fun tryImportBlockedNumbers() {
        try {
            openDocument.launch(blockedNumberMimeTypes)
        } catch (_: ActivityNotFoundException) {
            activity.toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun tryImportBlockedNumbersFromFile(uri: Uri) {
        ensureBackgroundThread {
            try {
                val entries = activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BlockedNumbersJson.read(inputStream)
                } ?: throw BlockedNumbersJson.InvalidFormatException("Unable to open input file")

                if (entries.isEmpty()) {
                    activity.runOnUiThread { activity.toast(R.string.no_items_found) }
                    return@ensureBackgroundThread
                }

                val existingKeys = activity.getBlockedNumbers()
                    .mapTo(HashSet()) { blockedNumber ->
                        blockedNumberImportKey(
                            value = blockedNumber.number,
                            isPattern = BlockedNumbersJson.isPatternValue(blockedNumber.number),
                        )
                    }

                var importedCount = 0
                entries.forEach { entry ->
                    val isPattern = entry.type == BlockedNumbersJson.EntryType.PATTERN
                    val key = blockedNumberImportKey(entry.value, isPattern)

                    if (existingKeys.add(key) && activity.addBlockedNumber(entry.value, isPattern)) {
                        importedCount++
                    }
                }

                activity.runOnUiThread {
                    activity.toast(
                        if (importedCount > 0) R.string.importing_successful
                        else R.string.no_items_found
                    )
                    onFinished()
                }
            } catch (_: BlockedNumbersJson.InvalidFormatException) {
                activity.runOnUiThread { activity.toast(R.string.invalid_file_format) }
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
        }
    }

    private fun blockedNumberImportKey(value: String, isPattern: Boolean): String {
        return if (isPattern) {
            "pattern:${value.trim()}"
        } else {
            "exact:${value.filter { it.isDigit() }}"
        }
    }

    // ─── Export ───────────────────────────────────────────────────────────

    fun tryExportBlockedNumbers() {
        ExportFileDialog(activity) { filename ->
            try {
                createDocument.launch("$filename.${BlockedNumbersJson.FILE_EXTENSION}")
            } catch (_: ActivityNotFoundException) {
                activity.toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
        }
    }

    private fun exportBlockedNumbersTo(outputStream: OutputStream?) {
        ensureBackgroundThread {
            val blockedNumbers = activity.getBlockedNumbers()
            if (blockedNumbers.isEmpty()) {
                activity.runOnUiThread { activity.toast(R.string.no_entries_for_exporting) }
                return@ensureBackgroundThread
            }

            val stream = outputStream
            if (stream == null) {
                activity.runOnUiThread { activity.toast(R.string.exporting_failed) }
                return@ensureBackgroundThread
            }

            try {
                stream.use {
                    BlockedNumbersJson.write(blockedNumbers, it)
                }
                activity.runOnUiThread { activity.toast(R.string.exporting_successful) }
            } catch (e: Exception) {
                activity.runOnUiThread { activity.toast(R.string.exporting_failed) }
            }
        }
    }
}
