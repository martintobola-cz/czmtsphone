package cz.mts.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.mts.phone.helpers.BlockedCallManager

class BlockedCallDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BlockedCallManager.clear(context)
    }
}