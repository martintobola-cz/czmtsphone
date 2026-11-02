package cz.mts.phone.helpers

import android.content.Context
import cz.mts.base.helpers.PhoneNumberHelper
import java.io.File

/**
 * Seznam čísel, která uživatel úspěšně nahlásil jako spam.
 * Uloženo v textovém souboru (jedno číslo na řádek, nejstarší nahoře) ve filesDir aplikace.
 * Soubor se při prvním použití načte do paměti, zápis je jen přidání řádku na konec.
 */
object ReportedNumbers {

    private const val FILE_NAME = "reported_spam.txt"
    private val lock = Any()
    private var cache: LinkedHashSet<String>? = null

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    private fun load(context: Context): LinkedHashSet<String> {
        cache?.let { return it }
        val set = LinkedHashSet<String>()
        val f = file(context)
        if (f.exists()) {
            f.forEachLine { line ->
                val n = line.trim()
                if (n.isNotEmpty()) set.add(n)
            }
        }
        cache = set
        return set
    }

    /** Jednotný tvar čísla pro porovnávání: jen číslice a případné + na začátku (bez mezer a pomlček). */
    fun key(number: String): String = PhoneNumberHelper.normalizeDigitsOnly(number)

    /** Čte soubor při prvním volání – volat mimo hlavní vlákno. */
    fun contains(context: Context, number: String): Boolean =
        synchronized(lock) { load(context).contains(key(number)) }

    /** Přidá číslo. Vrací true, jen když bylo nově uloženo. */
    fun add(context: Context, number: String): Boolean =
        synchronized(lock) {
            val k = key(number)
            if (k.isBlank()) return false
            val set = load(context)
            if (!set.add(k)) return false
            try {
                file(context).appendText(k + "\n")
            } catch (e: Exception) {
                set.remove(k)
                return false
            }
            true
        }

    /** Všechna nahlášená čísla, nejnovější první (pro seznam v aktivitě). */
    fun getAll(context: Context): List<String> =
        synchronized(lock) { load(context).toList().asReversed() }
}
