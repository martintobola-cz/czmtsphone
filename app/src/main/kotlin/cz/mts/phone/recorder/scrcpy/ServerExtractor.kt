package cz.mts.phone.recorder.scrcpy

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import cz.mts.phone.BuildConfig
import cz.mts.phone.recorder.RecorderLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

private const val TAG = "ServerExtractor"

/**
 * Zajišťuje dostupnost scrcpy-server.jar ve sdíleném úložišti, aby si ho mohl
 * přečíst i spustit privilegovaný shell proces (UID 2000).
 *
 * Zdroje souboru:
 *  - bundled varianta: rozbalení z APK assets
 *  - external varianta: import souboru vybraného uživatelem (SAF)
 *
 * Adaptováno ze ShizuCallRecorder.
 */
object ServerExtractor {

    sealed interface ImportResult {
        data object Success : ImportResult
        data class PathNull(val sMessage: String) : ImportResult
        data class HashMismatch(val actual: String) : ImportResult
        data class Error(val cause: Throwable) : ImportResult
    }

    /** Je server zabalený v APK (varianta bundled)? */
    fun hasBundledAsset(context: Context): Boolean =
        runCatching {
            context.assets.list("")?.contains(BuildConfig.SCRCPY_SERVER_ASSET_NAME) == true
        }.getOrDefault(false)


    fun isServerAvailableNoHashControl(context: Context): Boolean {
        val serverPath = ScrcpyConfig.getServerPath(context) ?: return hasBundledAsset(context)
        val file = File(serverPath)
        return file.exists() || hasBundledAsset(context)
    }

    /** Je server připravený k použití (ověřený soubor na disku, nebo ho lze rozbalit z assets)? */
    @WorkerThread
    fun isServerAvailable(context: Context): Boolean {
        val serverPath = ScrcpyConfig.getServerPath(context) ?: return hasBundledAsset(context)
        val file = File(serverPath)
        return (file.exists() && verifyServerHash(file)) || hasBundledAsset(context)
    }

    /**
     * Ujistí se, že soubor na [serverPath] existuje a má očekávaný SHA-256.
     * Pokud chybí nebo se hash neshoduje, zkusí ho rozbalit z assets.
     * Pokud asset v APK není (external varianta), vrátí false a uživatel
     * musí soubor naimportovat přes [importFromUri].
     */
    @WorkerThread
    fun ensureServerFile(context: Context, serverPath: String): Boolean {
        val file = File(serverPath)
        if (file.exists() && verifyServerHash(file)) {
            RecorderLog.d(TAG, "Server file already present and verified: $serverPath")
            return true
        }
        if (!hasBundledAsset(context)) {
            RecorderLog.w(TAG, "Server file missing and not bundled in APK - user must import it")
            return false
        }
        RecorderLog.d(TAG, "Server file missing or hash mismatch, extracting from assets...")
        return extractFromAssets(context, file)
    }

    /**
     * Import souboru vybraného uživatelem (SAF). Hash se počítá během kopírování
     * do dočasného souboru, na cílovou cestu se soubor přesune až po ověření.
     */
    @WorkerThread
    @SuppressLint("SetWorldReadable")
    fun importFromUri(context: Context, uri: Uri): ImportResult {
        val serverPath = ScrcpyConfig.getServerPath(context)
        if (serverPath == null) {
            RecorderLog.d(TAG, "scrcpy-server path is null!")
            return ImportResult.PathNull("scrcpy-server path is null!")
        }
        val dest = File(serverPath)
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        return try {
            dest.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(uri)
                ?: return ImportResult.Error(IOException("Can not open file!"))
            input.use { ins ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n == -1) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(ScrcpyConfig.EXPECTED_SERVER_SHA256, ignoreCase = true)) {
                RecorderLog.w(
                    TAG,
                    "Import hash mismatch: expected=${ScrcpyConfig.EXPECTED_SERVER_SHA256} actual=$actual"
                )
                tmp.delete()
                return ImportResult.HashMismatch(actual)
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            dest.setReadable(true, false)
            RecorderLog.d(TAG, "Server imported and verified: ${dest.path}")
            ImportResult.Success
        } catch (e: Exception) {
            RecorderLog.e(TAG, "Import failed: ${e.message}", e)
            tmp.delete()
            ImportResult.Error(e)
        }
    }

    private fun extractFromAssets(context: Context, destFile: File): Boolean {
        return try {
            context.assets.open(BuildConfig.SCRCPY_SERVER_ASSET_NAME).use { inputStream ->
                writeFile(destFile, inputStream)
            }
            val verified = verifyServerHash(destFile)
            if (verified) {
                RecorderLog.d(TAG, "Server extracted and verified: ${destFile.path}")
            } else {
                RecorderLog.w(TAG, "Extraction finished, but hash verification FAILED - deleting file")
                destFile.delete()
            }
            verified
        } catch (e: Exception) {
            RecorderLog.w(TAG, "Asset extraction failed: ${e.message}")
            destFile.delete()
            false
        }
    }

    /** Nastaví soubor jako world-readable, aby ho přečetl i shell proces (UID 2000). */
    @SuppressLint("SetWorldReadable")
    private fun writeFile(destFile: File, input: InputStream) {
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { output ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead = input.read(buffer)
            while (bytesRead > 0) {
                output.write(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        destFile.setReadable(true, false)
    }

    /** Ověří SHA-256 [file] proti [ScrcpyConfig.EXPECTED_SERVER_SHA256]. */
    fun verifyServerHash(file: File): Boolean {
        if (!file.exists()) {
            RecorderLog.e(TAG, "Cannot verify: file not found at ${file.path}")
            return false
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            val matches = actualHash.equals(ScrcpyConfig.EXPECTED_SERVER_SHA256, ignoreCase = true)
            if (!matches) {
                RecorderLog.w(TAG, "SHA-256 mismatch: expected=${ScrcpyConfig.EXPECTED_SERVER_SHA256} actual=$actualHash")
            }
            matches
        } catch (e: Exception) {
            RecorderLog.e(TAG, "Hash verification error: ${e.message}", e)
            false
        }
    }
}
