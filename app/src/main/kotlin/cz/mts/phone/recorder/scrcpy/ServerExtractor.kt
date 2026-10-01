package cz.mts.phone.recorder.scrcpy

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.WorkerThread
import cz.mts.phone.BuildConfig
import cz.mts.phone.recorder.RecorderLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

private const val TAG = "ServerExtractor"

/**
 * Rozbalí bundlovaný scrcpy-server.jar z APK assets a zapíše ho do sdíleného
 * úložiště, aby si ho mohl přečíst i spustit privilegovaný shell proces.
 * Adaptováno ze ShizuCallRecorder.
 */
object ServerExtractor {

    /**
     * Ujistí se, že soubor na [serverPath] existuje a má očekávaný SHA-256.
     * Pokud chybí nebo se hash neshoduje, rozbalí ho znovu z assets.
     */
    @WorkerThread
    fun ensureServerFile(context: Context, serverPath: String): Boolean {
        val file = File(serverPath)
        if (file.exists() && verifyServerHash(file)) {
            RecorderLog.d(TAG, "Server file already present and verified: $serverPath")
            return true
        }
        RecorderLog.d(TAG, "Server file missing or hash mismatch, extracting from assets...")
        return extractFromAssets(context, file)
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
                RecorderLog.w(TAG, "Extraction finished, but hash verification FAILED")
            }
            verified
        } catch (e: Exception) {
            RecorderLog.w(TAG, "Asset extraction failed: ${e.message}")
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
