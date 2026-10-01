package cz.mts.phone.recorder

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


// Pozor na procesy: ShellService/ShellAudioPipeline běží v JINÉM procesu (shell UID),
// takže tam existuje samostatná instance tohoto objektu. Jeho záznamy si appka
// stáhne přes IShellService.drainLogs() -> [drainForTransfer] (shell strana)
// a [importFromTransfer] (app strana) - viz ShizukuConnectionManager.pullShellLogs().
// Pokud shell proces spadne dřív, než se logy stáhnou, jeho záznamy se ztratí.

object RecorderLog {

    private const val MAX_ENTRIES = 3000
    private const val MAX_STACK_LINES = 25
    private const val FIELD_SEPARATOR = '\u0001'

    private data class Entry(
        val timeMs: Long,
        val process: String,
        val level: Char,
        val tag: String,
        val message: String
    )

    private val lock = Any()
    private val entries = ArrayList<Entry>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** Označení procesu ve výpisu: "app" (výchozí) nebo "shell". */
    @Volatile
    private var processLabel = "app"

    fun setProcessLabel(label: String) {
        processLabel = label
    }

    // ---- Zápis ------------------------------------------------------------

    fun d(tag: String, message: String, throwable: Throwable? = null) = add('D', tag, message, throwable)
    fun i(tag: String, message: String, throwable: Throwable? = null) = add('I', tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null) = add('W', tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = add('E', tag, message, throwable)

    private fun add(level: Char, tag: String, message: String, throwable: Throwable?) {
        val fullMessage = if (throwable == null) message else message + "\n" + stackTraceOf(throwable)
        val entry = Entry(System.currentTimeMillis(), processLabel, level, tag, fullMessage)
        synchronized(lock) {
            entries.add(entry)
            trimIfNeeded()
        }
    }

    /** Všechny záznamy jako naformátované řádky, chronologicky (app + shell proces dohromady). */
    fun getLines(): List<String> {
        val snapshot = synchronized(lock) { entries.toList() }
        return snapshot.sortedBy { it.timeMs }.map { format(it) }
    }

    /** Celý log jako jeden text (řádky oddělené \n). */
    fun getText(): String = getLines().joinToString("\n")

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    // ---- Přenos mezi procesy ------------------------------------------------

    /**
     * Shell strana: vrátí všechny záznamy v přenosovém formátu a vyprázdní log,
     * aby se při opakovaném stahování nic nezdvojilo.
     */
    fun drainForTransfer(): List<String> {
        val drained = synchronized(lock) {
            val copy = entries.toList()
            entries.clear()
            copy
        }
        return drained.map {
            "${it.timeMs}$FIELD_SEPARATOR${it.process}$FIELD_SEPARATOR${it.level}" +
                "$FIELD_SEPARATOR${it.tag}$FIELD_SEPARATOR${it.message}"
        }
    }

    /** App strana: sloučí záznamy stažené ze shell procesu do tohoto logu (seřazeno podle času). */
    fun importFromTransfer(lines: List<String>) {
        val parsed = lines.mapNotNull { parse(it) }
        if (parsed.isEmpty()) return
        synchronized(lock) {
            entries.addAll(parsed)
            entries.sortBy { it.timeMs }
            trimIfNeeded()
        }
    }

    // ---- Interní ------------------------------------------------------------

    private fun trimIfNeeded() {
        val overflow = entries.size - MAX_ENTRIES
        if (overflow > 0) entries.subList(0, overflow).clear()
    }

    private fun parse(line: String): Entry? {
        val parts = line.split(FIELD_SEPARATOR, limit = 5)
        if (parts.size < 5) return null
        val time = parts[0].toLongOrNull() ?: return null
        val level = parts[2].firstOrNull() ?: return null
        return Entry(time, parts[1], level, parts[3], parts[4])
    }

    private fun format(entry: Entry): String {
        val time = Instant.ofEpochMilli(entry.timeMs).atZone(ZoneId.systemDefault()).format(timeFormatter)
        return "$time [${entry.process}] ${entry.level}/${entry.tag}: ${entry.message}"
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString().lineSequence().take(MAX_STACK_LINES).joinToString("\n").trimEnd()
    }
}
