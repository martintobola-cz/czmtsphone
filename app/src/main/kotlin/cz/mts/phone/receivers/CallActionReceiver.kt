package cz.mts.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.mts.base.helpers.ACCEPT_CALL
import cz.mts.base.helpers.CALLUUID
import cz.mts.phone.helpers.CallManager
import cz.mts.base.helpers.DECLINE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sUUID = intent.getStringExtra(CALLUUID) ?: ""

        when (intent.action) {
            ACCEPT_CALL -> CallManager.accept(sUUID)
            DECLINE_CALL -> CallManager.reject(sUUID)
        }
    }
}
