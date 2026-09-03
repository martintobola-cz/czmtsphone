package cz.mts.base.helpers

import android.content.Context
import java.io.File

object LocalContactPhotoStorage {

    private const val DIR = "local_photos"

    fun save(context: Context, bytes: ByteArray): String {
        return try {
            val dir = File(context.filesDir, DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "contact_${java.util.UUID.randomUUID()}.jpg")
            file.outputStream().use { it.write(bytes) }

            file.toURI().toString()
        } catch (e: Exception) {
            "" // Caller už pracuje s .orEmpty(), takže prázdný string je bezpečný fallback
        }
    }

    fun delete(context: Context, photoUri: String) {
        if (photoUri.isEmpty()) return
        try {
            val file = File(java.net.URI(photoUri))
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            // Špatně formátované URI nebo chyba při mazání – tiše ignorujeme
        }
    }

    fun deleteAll(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }
}
