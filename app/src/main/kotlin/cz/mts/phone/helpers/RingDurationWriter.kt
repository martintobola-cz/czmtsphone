package cz.mts.base.helpers

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import cz.mts.base.extensions.hasPermission

object RingDurationWriter {
    private val handler = Handler(Looper.getMainLooper())
    private val delaysMs = longArrayOf(250, 750, 1500, 3000, 6000) // celkem ~11 s

    fun schedule(context: Context, creationMs: Long, ringSec: Int) {
        if (creationMs <= 0L || ringSec <= 0) return   // -1 = neznámo, 0 = nemá smysl zapisovat
        val app = context.applicationContext
        if (!app.hasPermission(PERMISSION_WRITE_CALL_LOG)) return
        attempt(app, creationMs, ringSec, 0)
    }

    @Volatile var onWritten: (() -> Unit)? = null

    private fun attempt(app: Context, creationMs: Long, ringSec: Int, idx: Int) {
        handler.postDelayed({
            ensureBackgroundThread {
                val ok = writeRing(app, creationMs, ringSec)
                if (ok) handler.post { onWritten?.invoke() }
                else if (idx + 1 < delaysMs.size) attempt(app, creationMs, ringSec, idx + 1)
            }
        }, delaysMs[idx])
    }

    private fun writeRing(ctx: Context, creationMs: Long, ringSec: Int): Boolean {
        val cr = ctx.contentResolver
        val values = ContentValues().apply { put(CallLog.Calls.DURATION, ringSec) }
        val missed = CallLog.Calls.MISSED_TYPE.toString()
        val noDuration = "(${CallLog.Calls.DURATION} = 0 OR ${CallLog.Calls.DURATION} IS NULL)"

        // 1) přesná shoda DATE == creationTimeMillis
        val exact = cr.update(
            CallLog.Calls.CONTENT_URI, values,
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} = ? AND $noDuration",
            arrayOf(missed, creationMs.toString())
        )
        if (exact > 0) return true

        // 2) fallback: nejbližší zmeškaný řádek bez duration v toleranci ±2 s (OEM odchylky)
        val id = cr.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID, CallLog.Calls.DATE),
            "${CallLog.Calls.TYPE} = ? AND $noDuration AND ${CallLog.Calls.DATE} BETWEEN ? AND ?",
            arrayOf(missed, (creationMs - 2000).toString(), (creationMs + 2000).toString()),
            null
        )?.use { c ->
            var best = -1L; var bestDiff = Long.MAX_VALUE
            while (c.moveToNext()) {
                val diff = kotlin.math.abs(c.getLong(1) - creationMs)
                if (diff < bestDiff) { bestDiff = diff; best = c.getLong(0) }
            }
            best
        } ?: -1L
        if (id < 0) return false

        return cr.update(
            ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, id), values, null, null
        ) > 0
    }
}
