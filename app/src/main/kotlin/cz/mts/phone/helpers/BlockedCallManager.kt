package cz.mts.phone.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import cz.mts.base.helpers.TAB_CALL_HISTORY
import cz.mts.base.helpers.isSPlus
import cz.mts.phone.R
import cz.mts.phone.activities.MainActivity
import cz.mts.phone.extensions.notificationManager
import cz.mts.phone.receivers.BlockedCallDismissReceiver

object BlockedCallManager {

    private const val CHANNEL_ID = "blocked_calls"
    private const val GROUP_ID = "blocked_call_group"
    private const val NOTIF_ID = 9002

    private val blockedCalls = mutableListOf<String>()

    fun registerBlockedCall(context: Context, number: String, iSim: Int = 0, sCnap: String = "") {
        getCallContact(context, null, number) { callContact ->
            val displayContact = if (callContact.number.isBlank()) callContact else callContact.copy(number = number)
            val display = buildCallDisplayFromData(
                context = context,
                contact = displayContact,
                cnap = sCnap,
                spamCheck = CacheContacts.bSpamChecking,
                spamEmoji = ""
            )
            synchronized(blockedCalls) {
                var sTextToDisplay = display.missedCallText
                if (iSim == 1) sTextToDisplay = "📞¹ $sTextToDisplay"
                else if (iSim == 2) sTextToDisplay = "📞² $sTextToDisplay"
                blockedCalls.add(sTextToDisplay)
            }
            showNotification(context)
        }
    }

    fun clear(context: Context) {
        synchronized(blockedCalls) { blockedCalls.clear() }
        context.notificationManager.cancel(NOTIF_ID)
    }

    private fun ensureNotificationChannel(context: Context) {
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.blocked_calls_mts),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(true)
            setSound(null, null)
        }
        context.notificationManager.createNotificationChannel(ch)
    }

    private fun showNotification(context: Context) {
        val nm = context.notificationManager
        ensureNotificationChannel(context)

        val count: Int
        val snapshot: List<String>
        synchronized(blockedCalls) {
            count = blockedCalls.size
            snapshot = blockedCalls.reversed()
        }

        val title = "${context.getString(R.string.blocked_calls_mts)} ($count)"

        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
        snapshot.forEach { inboxStyle.addLine(it) }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("start_tab", TAB_CALL_HISTORY)
//            putExtra("from_blocked_call_notification", true)
        }

        val pendingFlags = if (isSPlus()) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // jiný requestCode než u zmeškaných, ať se PendingIntenty nepřepisují
        val pi = PendingIntent.getActivity(context, 1, intent, pendingFlags)
        val dismissPi = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, BlockedCallDismissReceiver::class.java),
            pendingFlags
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cross_vector)
            .setContentTitle(title)
            .setContentText(snapshot.first())
            .setStyle(inboxStyle)
            .setAutoCancel(true)
            .setSilent(true)
            .setGroup(GROUP_ID)
            .setNumber(count)
            .setShowWhen(true)
            .setContentIntent(pi)
            .setDeleteIntent(dismissPi)
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    fun hasActiveNotifications(context: Context): Boolean =
        context.notificationManager.activeNotifications.any { it.id == NOTIF_ID }
}
