package cz.mts.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.mts.base.helpers.CALLUUID
import cz.mts.phone.helpers.CallManager
import cz.mts.base.helpers.NOTIFICATION_SOURCE
import cz.mts.base.helpers.SOURCE_CALL
import cz.mts.base.helpers.SOURCE_UPDATE

class NotificationDismissedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val source = intent?.getStringExtra(NOTIFICATION_SOURCE) ?: SOURCE_CALL

        when (source) {
            //notifikace v Androidu 12+ byla uživatelem swipnuta (odstraněna)
            //do Androidu 12 to systém nedovolí, od 12 ano, museli bychom použít službu na pozadí pro notifikace...
            //.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // takže než se to předělá na službu tak prostě rozjetý hovor odmítneme :)

            SOURCE_CALL -> {
                val sUUID = intent?.getStringExtra(CALLUUID) ?: ""
                try { CallManager.reject(sUUID) } catch (_: Exception) {}
            }
            //swipnul update notifikaci
            SOURCE_UPDATE -> {

            }
        }
    }


}

