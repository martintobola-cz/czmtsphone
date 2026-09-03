package cz.mts.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.mts.phone.helpers.MissedCallManager

class MissedCallDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MissedCallManager.clear(context)
    }
}
