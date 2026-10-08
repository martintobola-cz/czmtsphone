package cz.mts.phone.recorder

import android.content.Context
import java.io.File

/**
 * Jediné místo, které určuje složku s nahrávkami hovorů.
 * Používá ji [CallRecordingManager] (zápis) i RecordedFilesActivity (výpis / mazání / export).
 *
 * Nahrávky jsou jen v interním úložišti (filesDir). Starší verze ukládaly do
 * getExternalFilesDir(null)/recordings - tyto soubory jednorázově přesune [migrateLegacyIfNeeded].
 */
object RecordingPaths {

    private const val DIR_NAME = "recordings"
    private const val PREFS = "recording_paths"
    private const val KEY_LEGACY_MIGRATED = "legacy_external_migrated"

    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Jednorázový přesun nahrávek z externí app-specific složky do [dir].
     * Blokující (kopírování) - volat z IO vlákna. Idempotentní a thread-safe; po úspěšném
     * dokončení si uloží příznak a další volání jsou jen levná kontrola.
     * Při jakékoliv chybě zůstane zdrojový soubor na místě a příznak se nenastaví,
     * takže se přesun zkusí znovu při příštím volání.
     */
    @Synchronized
    fun migrateLegacyIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return

        // Externí úložiště nedostupné -> nevíme, jestli tam něco je, zkusíme příště.
        val externalBase = try {
            appContext.getExternalFilesDir(null)
        } catch (e: Exception) {
            null
        } ?: return

        val legacyDir = File(externalBase, DIR_NAME)
        val target = dir(appContext)
        var allMoved = true

        if (legacyDir.isDirectory) {
            for (src in legacyDir.listFiles().orEmpty().filter { it.isFile }) {
                if (!moveFile(src, uniqueTarget(target, src.name))) allMoved = false
            }
            if (allMoved) legacyDir.delete() // smaže jen prázdnou složku
        }

        if (allMoved) prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }

    /** Pokud už v cíli soubor stejného jména je, přidá se suffix, ať se nic nepřepíše. */
    private fun uniqueTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$i$ext")
            i++
        }
        return candidate
    }

    /** rename (rychlé, v rámci jednoho oddílu) -> jinak kopie + ověření velikosti + smazání zdroje. */
    private fun moveFile(src: File, dst: File): Boolean {
        return try {
            if (src.renameTo(dst)) return true
            src.copyTo(dst, overwrite = false)
            if (dst.length() == src.length()) {
                src.delete()
            } else {
                dst.delete()
                false
            }
        } catch (e: Exception) {
            runCatching { dst.delete() } // nedokončená kopie, zdroj zůstává
            false
        }
    }
}