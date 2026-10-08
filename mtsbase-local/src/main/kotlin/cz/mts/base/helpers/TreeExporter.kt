package cz.mts.base.helpers

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object TreeExporter {

    // Generický typ, aby provider nepřidával vlastní příponu. Název včetně přípony dodáváme sami.
    private const val DEFAULT_MIME_TYPE = "application/octet-stream"

    data class Result(val total: Int, val failedNames: List<String>) {
        val failedCount get() = failedNames.size
        val successCount get() = total - failedNames.size
        val isSuccess get() = failedNames.isEmpty()
    }

    /**
     * Zkopíruje [files] do složky vybrané přes OpenDocumentTree. Běží na IO vlákně.
     * Při kolizi názvu provider soubor přejmenuje (název (1).ext), nepřepisuje.
     * Selhání jednoho souboru nezastaví ostatní.
     */
    suspend fun copyFilesToTree(
        context: Context,
        files: List<File>,
        treeUri: Uri,
        mimeType: String = DEFAULT_MIME_TYPE,
    ): Result = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
        if (dir == null || !dir.canWrite()) {
            return@withContext Result(files.size, files.map { it.name })
        }

        val failed = mutableListOf<String>()
        for (src in files) {
            try {
                val target = dir.createFile(mimeType, src.name)
                val output = target?.let { context.contentResolver.openOutputStream(it.uri) }
                if (output == null) {
                    failed += src.name
                } else {
                    output.use { out -> src.inputStream().use { it.copyTo(out) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += src.name
            }
        }
        Result(files.size, failed)
    }
}