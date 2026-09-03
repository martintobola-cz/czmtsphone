package cz.mts.phone.helpers

import android.os.Handler
import android.os.Looper

object PauseWaiter {

    private val handler = Handler(Looper.getMainLooper())

    fun waitUntil(
        intervalMs: Long,
        maxAttempts: Int,
        condition: () -> Boolean,
        onDone: () -> Unit
    ) {
        waitInternal(intervalMs, maxAttempts, 0, condition, onDone)
    }

    private fun waitInternal(
        intervalMs: Long,
        maxAttempts: Int,
        attempt: Int,
        condition: () -> Boolean,
        onDone: () -> Unit
    ) {
        if (condition()) {
            onDone()
            return
        }
        if (attempt >= maxAttempts) {
            onDone()
            return
        }

        handler.postDelayed({
            waitInternal(intervalMs, maxAttempts, attempt + 1, condition, onDone)
        }, intervalMs)
    }
}
