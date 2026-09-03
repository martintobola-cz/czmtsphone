package cz.mts.phone.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.widget.RemoteViews
import cz.mts.base.extensions.notificationManager
import cz.mts.base.extensions.setText
import cz.mts.base.extensions.hasPermission
import cz.mts.base.helpers.ACCEPT_CALL
import cz.mts.base.helpers.CALLUUID
import cz.mts.base.helpers.DECLINE_CALL
import cz.mts.base.helpers.NOTIFICATION_SOURCE
import cz.mts.base.helpers.PERMISSION_POST_NOTIFICATIONS
import cz.mts.phone.R
import cz.mts.phone.receivers.CallActionReceiver
import cz.mts.phone.receivers.NotificationDismissedReceiver
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.SOURCE_CALL
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.activities.mtsGlobalAll.fakeAvatar
import cz.mts.phone.extensions.getStateCompat
import cz.mts.phone.models.CallContact
import android.view.View

class CallNotificationManagerMTs2(private val context: Context) {

    private val CALL_NOTIFICATION_ID = 42
    private val ACCEPT_CALL_CODE = 0
    private val DECLINE_CALL_CODE = 1
    private val notificationManager = context.notificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)
    private val SimpleContactsAvatarHelper = SimpleContactsHelper(context)


    private var notificationState: NotificationState? = null
    private data class NotificationState(
        val primaryRun: Boolean,
        val fullscreenUI: Boolean,
        val ringing: Boolean,
        val canBeAccepted: Boolean
    )
    private data class CallIntents(
        val openApp: PendingIntent,
        val accept: PendingIntent,
        val decline: PendingIntent,
        val dismiss: PendingIntent
    )

    /**
     * Standardni textova metadata notifikace (setContentTitle/setContentText),
     * ktera pouziva system, Wear OS a dalsi zarizeni neumejici vykreslit RemoteViews.
     * Naplnuje se jako vedlejsi efekt uvnitr buildCollapsedView, aby se hodnoty
     * nepocitaly znovu z callerName/callerNumber/simText/textId.
     */
    private data class WearableTexts(
        val title: String,
        val text: String
    )
    private var wearableTexts = WearableTexts("", "")


    fun updateNotificationWithContact(
        call : Call,
        callContact: CallContact
    ) {
        val sUUID = CallManager.getIdByCall(call) ?: return

        if (CallActivityUI.sUUIDonNotification != sUUID) return
        if (isCallStateDisconnecting(call)) return

        val state = notificationState ?: return

        //pouze příchozí hovor
        val bPriorityHigh = ( (state.primaryRun || state.fullscreenUI) && (state.ringing) )
        // vždy HIGH (call musí být viditelný/warning)

        val channelId = when (bPriorityHigh) {
            true -> "mts_phone_call_high_priority"
            else -> "mts_phone_call"
        }

        val channelName = when (bPriorityHigh){
            true -> context.getString(R.string.call_notification_channel_high_priority)
            else -> context.getString(R.string.call_notification_channel)
        }

        val importance = when (bPriorityHigh) {
            true -> IMPORTANCE_HIGH
            else -> IMPORTANCE_DEFAULT
        }

        ensureChannel(channelId, channelName, importance)

        val intents = createCallIntents(sUUID, "from_updateNotificationWithContact")
        val collapsedView = buildCollapsedView(state.canBeAccepted, callContact, "", intents,  call )

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(wearableTexts.title)
            .setContentText(wearableTexts.text)
            .setContentIntent(intents.openApp)
            // call musí být vysoké priority, aby systém rozpoznal jako CALL
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
//            .setUsesChronometer(callState == Call.STATE_ACTIVE)
//            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true)
            .setCustomBigContentView(collapsedView)
            .setCustomContentView(collapsedView)
            .setChannelId(channelId)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setGroup("mts_phone_group")
            .setSound(null)

        // Pozor: systém může fullScreenIntent ignorovat
        if (bPriorityHigh) {
            builder.setFullScreenIntent(intents.openApp, true)
        }
        if ((Build.VERSION.SDK_INT <= 33) || (bPriorityHigh == false) || (state.fullscreenUI == false)) {
            builder.setDeleteIntent(intents.dismiss) //údajně když to tam je v HIGH prioritě, může degradovat fullscreen intent
        }

        val notification = builder.build()

        // zkontrolujeme, že se stav hovoru nezměnil mezi začátkem budování a notifikací
        if (state.canBeAccepted || !isCallStateDisconnecting(call)) {
            notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            if (sUUID.isNotEmpty()) {
                CallActivityUI.sUUIDonNotification = sUUID
                CallActivityUI.bUUIDonNotificationConference = CallManager.isConference(call)
            }
        }
    }

    fun startNotificationAsync(
        call : Call,
        bFullscreenUI: Boolean,
        bPrimaryRun: Boolean,
        bRinging: Boolean,
        sNumber: String,
        bCanBeAccepted: Boolean
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(PERMISSION_POST_NOTIFICATIONS)) {
            return
        }

        if (isCallStateDisconnecting(call)) return

        val sUUID = CallManager.getIdByCall(call) ?: ""

        //pouze příchozí hovor
        val bPriorityHigh = ( (bPrimaryRun || bFullscreenUI) && (bRinging) )
        // vždy HIGH (call musí být viditelný/warning)

        val channelId = when (bPriorityHigh) {
            true -> "mts_phone_call_high_priority"
            else -> "mts_phone_call"
        }

        val channelName = when (bPriorityHigh){
            true -> context.getString(R.string.call_notification_channel_high_priority)
            else -> context.getString(R.string.call_notification_channel)
        }

        val importance = when (bPriorityHigh) {
            true -> IMPORTANCE_HIGH
            else -> IMPORTANCE_DEFAULT
        }

        ensureChannel(channelId, channelName, importance)

        val callerName = if (sNumber.isNotBlank()) sNumber else context.getString(R.string.unknown_caller)
        val intents = createCallIntents(sUUID, "from_startNotificationAsync")
        val collapsedView = buildCollapsedView(bCanBeAccepted, null, callerName, intents, call)

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(wearableTexts.title)
            .setContentText(wearableTexts.text)
            .setContentIntent(intents.openApp)
            // call musí být vysoké priority, aby systém rozpoznal jako CALL
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
  //          .setUsesChronometer(false)
  //          .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true)
            .setCustomBigContentView(collapsedView)
            .setCustomContentView(collapsedView)
            .setChannelId(channelId)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setGroup("mts_phone_group")
            .setSound(null)

        // Pozor: systém může fullScreenIntent ignorovat
        if (bPriorityHigh) {
            builder.setFullScreenIntent(intents.openApp, true)
        }
        if ((Build.VERSION.SDK_INT <= 33) || (bPriorityHigh == false) || (bFullscreenUI == false)) {
            builder.setDeleteIntent(intents.dismiss) //údajně když to tam je v HIGH prioritě, může degradovat fullscreen intent
        }

        val notification = builder.build()

        notificationState = NotificationState(
            primaryRun = bPrimaryRun,
            fullscreenUI = bFullscreenUI,
            ringing = bRinging,
            canBeAccepted = bCanBeAccepted
        )

        // zkontrolujeme, že se stav hovoru nezměnil mezi začátkem budování a notifikací
        //příchozí může změnit stav a notifikace se vypíše (kvůli esimek)
        if (bCanBeAccepted || !isCallStateDisconnecting(call)) {
            notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            if (sUUID.isNotEmpty()) {
                CallActivityUI.sUUIDonNotification = sUUID
                CallActivityUI.bUUIDonNotificationConference = CallManager.isConference(call)
            }
        }
    }

    @SuppressLint("NewApi")
    fun doNotification(call: Call, bFullscreenUI: Boolean = false, bPrimaryRun: Boolean = false, bRinging : Boolean = false, bCanBeAccepted : Boolean = false) {

        notificationState = null

        if (isCallStateDisconnecting(call)) return

        //val sUUIDonUI = if (CallActivityUI.isVisible()) CallActivityUI.sUUIDonUI
        //                else ""
        val sUUID = CallManager.getIdByCall(call) ?: ""
        //pokud máme hovor na UI a snažíme se tady poslat notifikaci jiného hovoru
        //if (sUUIDonUI != sUUID && sUUIDonUI.isNotEmpty()) return

        val callState = call.getStateCompat()

        // pokud nemáme permission (Android 13+), nevoláme notify — caller by měl o tohle vědět
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(PERMISSION_POST_NOTIFICATIONS)) {
           return
        }

        getCallContact(context.applicationContext, call) { callContact ->

            //pouze příchozí hovor
            val bPriorityHigh = ( (bPrimaryRun || bFullscreenUI) && (bRinging) )
            // vždy HIGH (call musí být viditelný/warning)

            val channelId = when (bPriorityHigh) {
                true -> "mts_phone_call_high_priority"
                else -> "mts_phone_call"
            }

            val channelName = when (bPriorityHigh){
                true -> context.getString(R.string.call_notification_channel_high_priority)
                else -> context.getString(R.string.call_notification_channel)
            }


            val importance = when (bPriorityHigh) {
                true -> IMPORTANCE_HIGH
                else -> IMPORTANCE_DEFAULT
            }

            ensureChannel(channelId, channelName, importance)


            val intents = createCallIntents(sUUID, "from_doNotification")
            val collapsedView = buildCollapsedView(bCanBeAccepted, callContact, "", intents, call)

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setContentTitle(wearableTexts.title)
                .setContentText(wearableTexts.text)
                .setContentIntent(intents.openApp)
                // call musí být vysoké priority, aby systém rozpoznal jako CALL
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(false)
//                .setUsesChronometer(callState == Call.STATE_ACTIVE)
//                .setWhen(System.currentTimeMillis())
                .setOnlyAlertOnce(true)
                .setCustomContentView(collapsedView)
                .setChannelId(channelId)
                .setCustomBigContentView(collapsedView)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setGroup("mts_phone_group")
                .setSound(null)

            // Pozor: systém může fullScreenIntent ignorovat
            if (bPriorityHigh) {
                builder.setFullScreenIntent(intents.openApp, true)
            }
            if ((Build.VERSION.SDK_INT <= 33) || (bPriorityHigh == false) || (bFullscreenUI == false)) {
                builder.setDeleteIntent(intents.dismiss) //údajně když to tam je v HIGH prioritě, může degradovat fullscreen intent
            }

            val notification = builder.build()
            val callStateNow = call.getStateCompat()
            // zkontrolujeme, že se stav hovoru nezměnil mezi začátkem budování a notifikací
            if (callStateNow == callState) {
                notificationManager.notify(CALL_NOTIFICATION_ID, notification)
                if (sUUID.isNotEmpty()) {
                    CallActivityUI.sUUIDonNotification = sUUID
                    CallActivityUI.bUUIDonNotificationConference = CallManager.isConference(call)
                }
            }
        }
    }


    private fun ensureChannel(
        channelId: String,
        channelName: String,
        importance: Int
    ) {
        val existing = notificationManager.getNotificationChannel(channelId)
        if (existing != null) return

        NotificationChannel(channelId, channelName, importance).apply {
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }
    }


    private val pendingIntentFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun createCallIntents(uuid: String, source: String): CallIntents {

        val openIntent =
            CallActivityUIstart.getStartIntent(context, source, uuid)

        val openApp =
            PendingIntent.getActivity(context, 0, openIntent, pendingIntentFlags)

        val acceptIntent =
            Intent(context, CallActionReceiver::class.java).apply {
                action = ACCEPT_CALL
                putExtra(CALLUUID, uuid)
            }

        val declineIntent =
            Intent(context, CallActionReceiver::class.java).apply {
                action = DECLINE_CALL
                putExtra(CALLUUID, uuid)
            }

        val dismissIntent =
            Intent(context, NotificationDismissedReceiver::class.java).apply {
                putExtra(CALLUUID, uuid)
                putExtra(NOTIFICATION_SOURCE, SOURCE_CALL)
            }

        return CallIntents(
            openApp = openApp,
            accept = PendingIntent.getBroadcast(context, ACCEPT_CALL_CODE, acceptIntent, pendingIntentFlags),
            decline = PendingIntent.getBroadcast(context, DECLINE_CALL_CODE, declineIntent, pendingIntentFlags),
            dismiss = PendingIntent.getBroadcast(context, 0, dismissIntent, pendingIntentFlags)
        )
    }

    private fun buildCollapsedView(
        canBeAccepted : Boolean,
        callContact : CallContact? = null,
        sNumber : String,
        intents: CallIntents,
        call : Call
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.call_notification).apply {

            val sUnknown = context.getString(R.string.unknown_caller)
            var callerName = sNumber.ifBlank { sUnknown }
            var callerNumber = callContact?.number ?: ""
            var sSpamEmoji = ""
            var bSpamEmoji = false
            if (callContact != null) {
                bSpamEmoji = ((callContact.id.toInt() == 0) && (CacheContacts.bSpamChecking))
                callerName = if (callContact.name.isNotEmpty()) callContact.name else sUnknown
            }
            if ((callerNumber.isBlank()) || (normalizeDigitsOnly(callerNumber) == normalizeDigitsOnly(callerName)))
                callerNumber = ""

            val isConfenerce =  CallManager.isConference(call)
            val isUnknown = (callerName.equals(sUnknown))

            if ((bSpamEmoji) && (!isConfenerce) && (!isUnknown)) {
                sSpamEmoji = CallManager.getSpamEmojiByCall(call) ?: ""
            }

            if ((sSpamEmoji.isNotBlank()) && (!isConfenerce) ) {
               if  (callerNumber.isBlank()) {callerName += " " + sSpamEmoji}
                else callerNumber += " " + sSpamEmoji
            }

            if (isConfenerce) {
                callerNumber = ""
            }

            setText(R.id.notification_phone_number, callerNumber)
            setText(R.id.notification_caller_name, callerName)

            setViewVisibility(
                R.id.notification_phone_number,
                if ( callerNumber.isBlank() || callerName.isNotBlank() ) View.GONE else View.VISIBLE
            ) //callerName.isNotBlank() protože více řádků nezobrazuje hezky notifikaci, takže nejsou třeba vidět tlačítka, proto je lepší zobrazovat jen jméno z kontaktu bez čísla...

            val callstate = call.getStateCompat()
            val textId = if (canBeAccepted) R.string.call_type1_mts
                         else if (callstate == Call.STATE_HOLDING) R.string.call_on_hold
                         else if (callstate == Call.STATE_CONNECTING || callstate == Call.STATE_DIALING) R.string.dialing
                         else if (callstate == Call.STATE_ACTIVE) R.string.mts_active_call
                         else R.string.mts_none

            setText(R.id.notification_call_status, context.getString(textId))

            setViewVisibility(
                R.id.notification_accept_call,
                if (canBeAccepted) View.VISIBLE else View.GONE
            )
            setViewVisibility(
                R.id.notification_buttons_gap,
                if (canBeAccepted) View.VISIBLE else View.GONE
            )

            setViewVisibility(R.id.notification_decline_call, View.VISIBLE)

            setOnClickPendingIntent(R.id.notification_holder, intents.openApp)
            setOnClickPendingIntent(R.id.notification_content_holder, intents.openApp)

            setInt(R.id.notification_accept_call,  "setBackgroundResource", R.drawable.bg_notification_button_light)
            setInt(R.id.notification_decline_call, "setBackgroundResource", R.drawable.bg_notification_button_light)

            setImageViewResource(R.id.notification_accept_call, R.drawable.ic_phone_green_vector)
            setImageViewResource(R.id.notification_decline_call, R.drawable.ic_phone_down_red_vector)

            setOnClickPendingIntent(R.id.notification_decline_call, intents.decline)
            setOnClickPendingIntent(R.id.notification_accept_call, intents.accept)

            val iSimSlot = CallManager.getSimSlotByCall(call)
            val simText = if ((iSimSlot) == 1) "📞¹ "
                          else if ((iSimSlot) == 2) "📞² "
                          else ""
            setText(R.id.notification_sim_info, simText)
            setViewVisibility(
                R.id.notification_sim_info,
                if (iSimSlot != 0) View.VISIBLE else View.GONE
            )

            val callContactAvatar = if (isConfenerce) fakeAvatar(context, R.drawable.conferenceavatar)
                                    else if (mtsGlobalAll.iSaveDebugMode == 2) fakeAvatar(context, R.drawable.karlavatar)
                                    else if (isUnknown) fakeAvatar(context, R.drawable.anonymousavatar)
                                    else callContactAvatarHelper.getCallContactAvatar(callContact, false) ?: fakeAvatar(context, R.drawable.fakeavatar)
            setImageViewBitmap(
                R.id.notification_thumbnail,
                SimpleContactsAvatarHelper.getCircularBitmap(callContactAvatar)
            )

            // Wearable/systemove metadata (setContentTitle/setContentText) - sestavime
            // podle stejnych pravidel viditelnosti jako pro RemoteViews vyse, aby
            // hodinky a system videly stejny obsah jako telefonni notifikace.
            val wearableTitle = callerName
            val wearableText = buildString {
                if (iSimSlot != 0) append(simText)
                // zrcadlime viditelnost notification_phone_number vyse
                if (callerNumber.isNotBlank() && callerName.isBlank()) {
                    append(callerNumber)
                    append(" ")
                }
                append(context.getString(textId))
            }
            wearableTexts = WearableTexts(title = wearableTitle, text = wearableText)
        }


    private fun applySimAwareIcons(views: RemoteViews, call: Call) : Int {
        val slot = CallManager.getSimSlotByCall(call)

        // Výchozí ikonky (žádný SIM slot, nebo slot 0 = single SIM)
        val acceptIconRes = R.drawable.ic_phone_green_vector
        val declineIconRes = R.drawable.ic_phone_down_red_vector

//        if (slot != 0) {
//            val index = slot - 1  // zpět na 0-based
//            acceptIconRes = when (index) {
//                0 -> R.drawable.ic_call_accept_simone
//                1 -> R.drawable.ic_call_accept_simtwo
//                else -> R.drawable.ic_call_accept
//            }
///        }
        views.setImageViewResource(R.id.notification_accept_call, acceptIconRes)
        views.setImageViewResource(R.id.notification_decline_call, declineIconRes)

        return slot
    }


    fun cancelNotification() {
        notificationState = null
        notificationManager.cancel(CALL_NOTIFICATION_ID)
        CallActivityUI.sUUIDonNotification = ""
        CallActivityUI.bUUIDonNotificationConference = false

        }

    private fun isCallStateDisconnecting(call : Call?) : Boolean {
        if (call == null) return true
        val callState = call.getStateCompat()
        return (callState == Call.STATE_DISCONNECTING || callState == Call.STATE_DISCONNECTED)
    }


}