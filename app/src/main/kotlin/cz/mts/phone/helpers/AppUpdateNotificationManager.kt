package cz.mts.phone.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import cz.mts.base.extensions.hasPermission
import cz.mts.base.helpers.*
import cz.mts.phone.R
import cz.mts.phone.activities.MainActivity
import cz.mts.phone.extensions.appVersionCode
import cz.mts.phone.extensions.appVersionName
import cz.mts.phone.extensions.notificationManager
import cz.mts.phone.receivers.NotificationDismissedReceiver

class AppUpdateNotificationManager(private val context: Context) {

    companion object {
        private const val UPDATE_NOTIFICATION_ID     = 101
        private const val CHANNEL_ID                 = "mts_phone_app_update"
        private const val OPEN_APP_PENDING_CODE      = 200
        private const val OPEN_STORE_PENDING_CODE    = 201
        private const val DISMISS_CODE               = 202
        private const val OPEN_WEB_PENDING_CODE      = 203
        private const val OPEN_APP_NEWS_PENDING_CODE = 204
        private const val OPEN_APP_PERM_PENDING_CODE = 205
    }

    enum class ClickAction {
        OPEN_APP,
        OPEN_APP_NEWS,
        OPEN_APP_MISSED_PERM,
        OPEN_STORE_PHONE,
        OPEN_STORE_CALLFILTER,
        OPEN_MY_WEB

    }

    private val notificationManager = context.notificationManager

    fun showSpecialNotification(
        clickAction: ClickAction = ClickAction.OPEN_APP
    ) {
        if (isTiramisuPlus() &&
            !context.hasPermission(PERMISSION_POST_NOTIFICATIONS)
        ) return

        ensureChannel()

        val contentIntent = createContentIntent(clickAction)

        val title   =  if (clickAction == ClickAction.OPEN_APP_MISSED_PERM) {context.getString(R.string.permission_required)}
                       else {context.getString(R.string.update_done) + context.appVersionName}
        val message = if (clickAction == ClickAction.OPEN_APP_MISSED_PERM) {"cz.mts.phone: " + context.getString(R.string.not_permissions_info)}
                      else {"cz.mts.phone: " + context.getString(R.string.last_update_info)}

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_info_call)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setDeleteIntent(createDismissIntent())
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .build()

        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(UPDATE_NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.other_info_notification_channel),
            IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }
    }

    val pendingIntentFlags: Int
        get() = if (isSPlus()) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun createDismissIntent(): PendingIntent {
        val intent = Intent(context, NotificationDismissedReceiver::class.java).apply {
            putExtra(NOTIFICATION_SOURCE, SOURCE_UPDATE)
        }
        return PendingIntent.getBroadcast(context, DISMISS_CODE, intent, pendingIntentFlags)
    }

    private fun createContentIntent(clickAction: ClickAction): PendingIntent {

        return when (clickAction) {
            ClickAction.OPEN_APP -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                PendingIntent.getActivity(context, OPEN_APP_PENDING_CODE, intent, pendingIntentFlags)
            }

            ClickAction.OPEN_APP_NEWS -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("from_news_notification", true)
                }
                PendingIntent.getActivity(context, OPEN_APP_NEWS_PENDING_CODE, intent, pendingIntentFlags)
            }

            ClickAction.OPEN_APP_MISSED_PERM -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("from_missed_perm_notification", true)
                }
                PendingIntent.getActivity(context, OPEN_APP_PERM_PENDING_CODE, intent, pendingIntentFlags)
            }

            ClickAction.OPEN_STORE_PHONE -> {
                val resolvedIntent = PlayStoreIntentHelper.createOpenStoreIntent(context, "cz.mts.phone")
                PendingIntent.getActivity(context, OPEN_STORE_PENDING_CODE, resolvedIntent, pendingIntentFlags)
            }
            ClickAction.OPEN_STORE_CALLFILTER -> {
                val resolvedIntent = PlayStoreIntentHelper.createOpenStoreIntent(context, "cz.mts.callfilter")
                PendingIntent.getActivity(context, OPEN_STORE_PENDING_CODE, resolvedIntent, pendingIntentFlags)
            }
            ClickAction.OPEN_MY_WEB -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mts.speccy.cz/mtsphone-news.htm#v" + context.appVersionCode.toInt().toString())).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PendingIntent.getActivity(context, OPEN_WEB_PENDING_CODE, intent, pendingIntentFlags)
            }
        }
    }
}
