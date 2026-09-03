package cz.mts.phone.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cz.mts.base.helpers.TAB_CALL_HISTORY
import cz.mts.phone.R
import cz.mts.phone.activities.MainActivity
import cz.mts.phone.extensions.notificationManager
import cz.mts.phone.receivers.MissedCallDismissReceiver

object MissedCallManager {

    private const val CHANNEL_ID = "missed_calls"
    private const val GROUP_ID = "missed_call_group"
    private const val NOTIF_ID = 9001

    // Zabezpečeno proti souběžnému přístupu z více vláken
    private val missedCalls = mutableListOf<String>()

    fun registerMissedCall(context: Context, number: String, sSpamEmoji: String, iSim : Int) {
        getCallContact(context, null, number) { callContact ->
            synchronized(missedCalls) {
                var sTextToDisplay : String
                if (callContact.id.toInt() != 0) {
                    sTextToDisplay = callContact.name.ifEmpty { number }

                } else {
                    if (CacheContacts.bSpamChecking) {
                        sTextToDisplay = if (sSpamEmoji.isNotBlank()) "$number $sSpamEmoji" else number
                    } else {
                        sTextToDisplay = number
                    }
                }
                if (iSim == 1) { sTextToDisplay = "📞¹ " + sTextToDisplay }
                else if (iSim == 2) { sTextToDisplay = "📞² " + sTextToDisplay }
                missedCalls.add(sTextToDisplay)
            }
            showNotification(context)
        }
    }

    fun clear(context: Context) {
        synchronized(missedCalls) {
            missedCalls.clear()
        }
        context.notificationManager.cancel(NOTIF_ID)
    }

    private fun ensureNotificationChannel(context: Context) {
        // Kanál se vytvoří pouze jednou; opakované volání createNotificationChannel je bezpečné,
        // ale přesun do samostatné funkce zlepšuje přehlednost
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.missed_call1_mts),
            NotificationManager.IMPORTANCE_HIGH
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
        synchronized(missedCalls) {
            count = missedCalls.size
            snapshot = missedCalls.reversed() // nejnovější první
        }

        val title = "${context.getString(R.string.missed_call1_mts)} ($count)"

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
        snapshot.forEach { inboxStyle.addLine(it) }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("start_tab", TAB_CALL_HISTORY)
            putExtra("from_missed_call_notification", true)
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pi = PendingIntent.getActivity(context, 0, intent, pendingFlags)
        val dismissPi = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, MissedCallDismissReceiver::class.java),
            pendingFlags
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_missed_vector)
            .setContentTitle(title)
            .setContentText(snapshot.first()) // collapsed zobrazí nejnovější
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

    fun hasActiveNotifications(context: Context): Boolean {
        return context.notificationManager.activeNotifications.any { it.id == NOTIF_ID }
    }
}
