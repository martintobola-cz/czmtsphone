package cz.mts.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import cz.mts.phone.helpers.MissedCallManager

/**
 * Existence tohoto receiveru říká Telecomu, že zmeškané hovory řešíme sami,
 * takže nevytvoří vlastní systémovou notifikaci.
 */
class ShowMissedCallsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return

        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, -1)
        // count == 0 => Telecom oznamuje vymazání (cancelMissedCallsNotification apod.)
        if (count == 0) {
            MissedCallManager.clear(context)
        }
        // count > 0 záměrně ignorujeme: notifikaci už vytvořil CallService.onCallRemoved
    }
}