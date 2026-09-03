package cz.mts.phone.services

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccount
import android.telecom.DisconnectCause
import android.telephony.SubscriptionManager
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.base.extensions.hasPermission
import cz.mts.base.helpers.PERMISSION_POST_NOTIFICATIONS
import cz.mts.phone.R
import cz.mts.phone.extensions.audioManager
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.getStateCompat
import cz.mts.phone.extensions.isDndActive
import cz.mts.phone.extensions.isIncoming
import cz.mts.phone.extensions.isOutgoing
import cz.mts.phone.extensions.keyguardManager
import cz.mts.phone.extensions.powerManager
import cz.mts.phone.extensions.subscriptionManager
import cz.mts.phone.extensions.telecomManager
import cz.mts.phone.helpers.CallActivityUI
import cz.mts.phone.helpers.CallActivityUIstart
import cz.mts.phone.helpers.CallFilterResult
import cz.mts.phone.helpers.CallManager
import cz.mts.phone.helpers.CallManagerListener
import cz.mts.phone.helpers.CallNotificationManagerMTs2
import cz.mts.phone.helpers.MissedCallManager
import cz.mts.phone.helpers.PauseWaiter
import cz.mts.phone.helpers.RecentsQueryLimits
import cz.mts.phone.helpers.getCallContact
import cz.mts.phone.models.AudioRoute
import kotlin.collections.set

class CallService : InCallService(), CallManagerListener {

    private val callNotificationManager by lazy { CallNotificationManagerMTs2(this) }
    private var bFullscreenUI = false
    private val handler = Handler(Looper.getMainLooper())
    private val CAPABILITY_EMBEDDED_SIM = 0x00004000
    private val callCallbacks = mutableMapOf<Call, Call.Callback>()
    private var bMoreThanOneCall = false
    private var savedRingerMode: Int? = null


    //máme nějaký spamresult, takže aktualizace notifikace, pokud nějaká existuje...
    override fun onCallFilterResult(call : Call, result: CallFilterResult) {
        Handler(Looper.getMainLooper()).post {
            // check spam vrátil result
            val state = call.getStateCompat()
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) return@post
            val sIDCALL = CallManager.getIdByCall(call) ?: return@post
            val sIDCALLonNotification = CallActivityUI.sUUIDonNotification
            //tento hovor právě teď není zobrazen v notifikaci
            if (sIDCALLonNotification.isNotBlank() && sIDCALLonNotification != sIDCALL) return@post
            val iNotificationDone = CallManager.getNotificationDone(sIDCALL)
            //hovor už byl touto službou zpracován
            //pokud spamcheck byl rychlejší než iNotificationDone=1 nebo 2, tak výsledek už zapracuje notifikace 2 a tady se nebude vůbec řešit
            if (iNotificationDone >= 2) {
                val bCanBeAccepted = state == Call.STATE_RINGING //|| state == Call.STATE_SIMULATED_RINGING
                if (CallActivityUI.sUUIDonUI.isBlank()) callNotificationManager.doNotification(call, bFullscreenUI, true, bCanBeAccepted, bCanBeAccepted)
                else callNotificationManager.doNotification(call, false, false, false, bCanBeAccepted)
            }
        }
    }

    //toto neřešíme, to je čistě věc CallActivity
    override fun onAudioStateChanged(audioState: AudioRoute) {
    }


    //pro každý nový call je registrován nový listener!!!
    // jedna callservice, ale více listenerů (pro každý call)
    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {

            if (state == Call.STATE_ACTIVE) {
                restoreRingerMode()
                callNotificationManager.doNotification(call,false, false, false, false)
                showUI(false, true, "from_incall_service_high_priority", call)
            }
            else if (state == Call.STATE_HOLDING) {
                if (CallActivityUI.isVisible())
                    showUI(false, true, "from_incall_service_nochange", call)
            }
        }

        //změna z pohledu konferenčního hovoru
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
            showUI(false, CallActivityUI.isVisible(), "from_incall_service_nochange", call)
        }

        //změna z pohledu konkrétního hovoru, volá se pro každý child
        override fun onParentChanged(call: Call, parent: Call?) {
            showUI(false, CallActivityUI.isVisible(), "from_incall_service_nochange", call)
        }
    }


    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        CallManager.onCallAdded(applicationContext, call)
        CallManager.inCallService = this

        call.registerCallback(callListener)
        callCallbacks[call] = callListener


        val isIncoming = call.isIncoming()
        val isOutgoing = call.isOutgoing()

        if (CallManager.getAliveCallsCount() > 1) bMoreThanOneCall = true

        // Determine eSIM in an async-safe way (retry few times if PhoneAccount not ready)
        determineEsimThenProceed(call, isIncoming, isOutgoing)
    }

    private fun determineEsimThenProceed(call: Call, isIncoming: Boolean, isOutgoing: Boolean) {
        tryDetermineEsim(call, 0) { isEsim ->
            val callState = call.getStateCompat()
            if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING)  {
                return@tryDetermineEsim
            }

           val handle = call.details?.handle
           val number = handle?.schemeSpecificPart ?: ""

           proceedCallFlow(call, isIncoming, isOutgoing, isEsim, number)
        }
    }


    private fun proceedCallFlow(call: Call, isIncoming: Boolean, isOutgoing: Boolean, isEsim: Boolean, sNumber : String) {
        var forceRinging = false
        if (isIncoming && isEsim) forceRinging = true

        // wait for RINGING (non-blocking)
        PauseWaiter.waitUntil(
            intervalMs = 20,
            maxAttempts = 45, // ~900ms
            condition = { forceRinging || call.getStateCompat() == Call.STATE_RINGING} //|| call.getStateCompat() == Call.STATE_SIMULATED_RINGING} //u esim se prý stavu ringing nemusíme vůbec dočkat, takže jakože už zvoní...
        ) {
            // abort if call gone
            val state = call.getStateCompat()
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) return@waitUntil

            val bCanBeAccepted = isIncoming && state != Call.STATE_ACTIVE

            val isScreenOff = !powerManager.isInteractive
            val isDeviceLocked = keyguardManager.isDeviceLocked ||
                (keyguardManager.isKeyguardLocked && keyguardManager.isKeyguardSecure)
            val alwaysShowFullscreen = config.alwaysShowFullscreen

            bFullscreenUI = isOutgoing
                || isScreenOff
                || isDeviceLocked
                || alwaysShowFullscreen
                || !hasPermission(PERMISSION_POST_NOTIFICATIONS)

            if (isIncoming) forceRinging = true //patch to force highpriority
            if (isDndActive) {  //patch aby nebyl vyzváněcí tón pokud je aktivní řežim nerušit
                forceRinging = false
                if (isIncoming) silenceIfDnd()
            }


            if (CallManager.getAliveCallsCount() == 1) {

           // 1a) send HIGH notification
            CallManager.setNotificationDone(call, 1)
            callNotificationManager.startNotificationAsync(
                call = call,
                bFullscreenUI = bFullscreenUI,
                bPrimaryRun = true,
                bRinging = forceRinging,
                sNumber = sNumber,
                bCanBeAccepted = bCanBeAccepted
            )
            //1b) na pozadí najdi kontakt a updatuj údaje (fotka, jméno, typ čísla)
            getCallContact(applicationContext, call) { callContact ->
                CallManager.setNotificationDone(call, 2)
                callNotificationManager.updateNotificationWithContact(
                    call = call,
                    callContact = callContact
                )
            }

            var isStarted : Boolean
            // 2) small delay, then start activity
            handler.postDelayed({
                val state = call.getStateCompat()
                if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) return@postDelayed
                if (bFullscreenUI) {
                    isStarted = CallActivityUIstart.showInCallUI(applicationContext, "from_incall_service", call)
                    if (!isStarted) //někdy z neznámých důvodů hodí vyjímku, takže ...
                        CallActivityUIstart.showInCallUI(applicationContext, "from_incall_service", call) //... druhý pokus
                }
            }, 25L)

            // 3) minimize notification after short while
            handler.postDelayed({
                //je-li fullscreen, musíme notifikaci za 300ms degradovat prioritu, aby se nezobrazovala přes call activity
                //teoreticky by nemělo nastat, že se udělá toto (3) a až pak naběhne 1b, protože callNotificationManager.doNotification
                //volá stejnou funkci getCallContact(applicationContext, call) pro zjištění jména a fotky, taže by neměl doběhout nikdy rychleji :)
                if (bFullscreenUI)
                {
                    //degradovat jen když fullscreen naběhl
                    if (CallActivityUI.isVisible()) {
                        CallManager.setNotificationDone(call, 3)
                        callNotificationManager.doNotification(call, false, false, false, bCanBeAccepted)
                    }

                }
            }, 300L)

        } else {  //více živých hovorů a právě se přidal tento "call" jako další do fronty
                CallManager.setNotificationDone(call, 4)
                CallActivityUIstart.showInCallUI(applicationContext, "from_incall_service_new_inout_call", call) //vyvolá onNewIntent nebo onCreate a ukáže overlay
            }
        }
    }

    /**
     * Try to determine whether the Call is on an eSIM.
     * This does up to 3 attempts to get PhoneAccount (30ms apart), then falls back to SubscriptionInfo.
     * callback will be invoked on main thread.
     */
    private fun tryDetermineEsim(call: Call, attempt: Int, callback: (Boolean) -> Unit) {
        val telecom = applicationContext.telecomManager
        try {
            val accountHandle = call.details?.accountHandle
            val account: PhoneAccount? = try {
                telecom?.getPhoneAccount(accountHandle)
            } catch (_: Exception) {
                null
            }

            if (account != null) {
                val isEsimAccount = try {
                    account.hasCapabilities(CAPABILITY_EMBEDDED_SIM)
                } catch (_: Exception) {
                    false
                }
                callback(isEsimAccount)
                return
            } else {
                // If account is null and we haven't exhausted attempts, retry shortly.
                if (attempt < 3) {
                    handler.postDelayed({
                        tryDetermineEsim(call, attempt + 1, callback)
                    }, 30L)
                    return
                }
            }

            // fallback via SubscriptionManager - try to map PhoneAccount -> subscriptionId
            val subId = getSubscriptionIdForCall(applicationContext, call)
            if (subId != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                val sm = applicationContext.subscriptionManager
                try {
                    val info = sm?.getActiveSubscriptionInfo(subId)
                    if (info?.isEmbedded == true) {
                        callback(true)
                        return
                    }
                } catch (_: Exception) {
                    // ignore and continue
                }
            }

            // default: not eSIM
            callback(false)
        } catch (_: Throwable) {
            // any unexpected error -> fallback false
            callback(false)
        }
    }


    private fun getSubscriptionIdForCall(context: Context, call: Call): Int? {
        val telecom = context.telecomManager
        val subManager = context.subscriptionManager ?: return null
        val accountHandle = call.details?.accountHandle ?: return null

        val phoneAccount = try {
            telecom?.getPhoneAccount(accountHandle)
        } catch (_: Exception) {
            null
        } ?: return null

        // Extract subscriptionId from extras (this is how Google Dialer does it)
        val subId = phoneAccount.extras
            ?.getInt("android.telephony.subscriptionId", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID

        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null

        // Validate the subscriptionId exists among active SIMs
        try {
        val subs = subManager.activeSubscriptionInfoList ?: return null
        for (info in subs) {
            if (info.subscriptionId == subId) {
                return info.subscriptionId
            }
        }
        } catch (_: Exception) {}

        return null
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        restoreRingerMode()
     //   if (call.isConference()) RecentsQueryLimits.setRefreshState(true) //jinak v recents observer konferenci nezobrazí

        val sCallRemovedId = CallManager.getIdByCall(call) ?: ""
        val isIncoming = call.isIncoming()
        val disconnectCause = call.details?.disconnectCause
        val handle = call.details?.handle

        val wasNotAnswered = disconnectCause?.code == DisconnectCause.MISSED
        if (isIncoming && wasNotAnswered) {
            val numberX = handle?.schemeSpecificPart
            val number = if (numberX.isNullOrBlank()) getString(R.string.unknown_caller)
                         else numberForRecents(numberX, config.formatPhoneNumbers)
            val sEmoji = CallManager.getSpamEmojiByCall(call) ?: ""
            val iSim = CallManager.getSimSlotByCall(call)
            MissedCallManager.registerMissedCall(applicationContext, number, sEmoji, iSim)
        }

        callCallbacks.remove(call)?.let { callback ->
            call.unregisterCallback(callback)
        }

        val callsOnStack = CallManager.onCallRemoved(call)
        // žádné hovory
        if (callsOnStack < 1) {
            CallManager.inCallService = null
            CallManager.clearCallsLists()
            showUI(true, true, "no_call", null)
            return
        }
        // kandidát na zobrazení v UI
        var callForUI = CallManager.getCallForUIblind()  //aktivní anebo první z buildOrderedCallIdList
        if (callForUI == null) {
            showUI(true, true, "no_call", null)
            return
        }
        // pokud je UI viditelné a odstraněný hovor byl právě ten na UI, takže musíme vybrat jiný
        if (CallActivityUI.isVisible() || CallActivityUI.isPaused()) {
            val callOnUIid = CallManager.getIdByCall(callForUI).orEmpty()
            if (callOnUIid.isNotEmpty() && sCallRemovedId.isNotEmpty() && callOnUIid == sCallRemovedId) {
                val callIdList = CallManager.buildOrderedCallIdList(false, sCallRemovedId, "")
                callForUI = callIdList.firstOrNull()
                    ?.let { CallManager.getCallById(it) }
            }
        }
        // pořád nic k zobrazení?
        if (callForUI == null) {
            showUI(true, true, "no_call", null)
            return
        }
        // aktualizace notifikace + UI
        val canBeAccepted = callForUI.getStateCompat() == Call.STATE_RINGING //|| callForUI.getStateCompat() == Call.STATE_SIMULATED_RINGING
        callNotificationManager.doNotification(callForUI, false, false, false, canBeAccepted)
        CallActivityUIstart.showInCallUI(applicationContext,"from_incall_service_remove", callForUI)
    }

    private fun showUI(bCancellNotify : Boolean, bShowUI : Boolean = true, sExtra : String, call: Call?) {
        if (bCancellNotify) callNotificationManager.cancelNotification()
        if (bShowUI) {
            if (CallActivityUI.isDestroyed() && call == null) return
            else CallActivityUIstart.showInCallUI(applicationContext, sExtra, call)
        }
    }


    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState?.let { CallManager.onAudioStateChanged(it) }
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.removeListener(this)
        CallManager.inCallService = null
        CallManager.clearCallsLists()

        callCallbacks.forEach { (call, cb) -> call.unregisterCallback(cb) }
        callCallbacks.clear()

        //pokud jsme dělali více hovorů, tak uděláme fullrefresh, protože malý refresh nemusí ukázat všechno
        if (bMoreThanOneCall) RecentsQueryLimits.setRefreshState(true)

        restoreRingerMode()
        showUI(true, CallActivityUI.isVisible(), "no_call", null)
    }


    private fun hasDndPolicyAccess(): Boolean {
    val nm = applicationContext.getSystemService(NotificationManager::class.java)
    return nm?.isNotificationPolicyAccessGranted == true
}

private fun silenceIfDnd() {
    if (!isDndActive) return
    if (!hasDndPolicyAccess()) return // nemáme právo měnit ringer mode při aktivním DND
    val am = applicationContext.audioManager
    if (savedRingerMode == null) {
        try {
            savedRingerMode = am.ringerMode
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (_: SecurityException) {
            savedRingerMode = null
        }
    }
}

private fun restoreRingerMode() {
    val mode = savedRingerMode ?: return
    savedRingerMode = null
    if (!isDndActive || hasDndPolicyAccess()) {
        try {
            applicationContext.audioManager.ringerMode = mode
        } catch (_: SecurityException) {
            // appka nemá DND policy access, nemá smysl to zkoušet
        }
    }
}

}
