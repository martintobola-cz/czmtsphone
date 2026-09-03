package cz.mts.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import cz.mts.base.compose.extensions.config
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.isAppInstalled
import cz.mts.base.helpers.DIALPAD_TONE_LENGTH_MS
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.phone.activities.mtsGlobalAll.checkNumberForRating
import cz.mts.phone.extensions.getAvailableSIMCardLabels
import cz.mts.phone.extensions.getStateCompat
import cz.mts.phone.extensions.hasCapability
import cz.mts.phone.extensions.isIncoming
import cz.mts.phone.models.AudioRoute
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

data class CallEntry(
    val id: String,
    val emoji: String = "",
    val notificationDone : Int = 0,
    val simSlot: Int = 0,   // 0 = neznámo / single SIM, 1 = SIM1, 2 = SIM2
    val simColor: Int = 0,      // barva SIM karty
    val simIndexId: Int = 0     // index
)


class CallManager {
    companion object {
        @SuppressLint("StaticFieldLeak")
        var inCallService: InCallService? = null
        private val calls = mutableListOf<Call>()
        private val callIds = mutableMapOf<Call, CallEntry>()
        private val listeners = CopyOnWriteArraySet<CallManagerListener>()

        fun clearCallsLists(){
             calls.clear()
             callIds.clear()
            }

        fun isConference(call : Call?): Boolean =
            call != null && (
                call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE) ||
                    call.children.isNotEmpty()
        )

        fun buildOrderedCallIdList(noActive: Boolean = true, excludedCallId: String, thisIdFirst : String): List<String> {

            fun Call.toIdOrNull(idFirstId: String): String? {
                val id = callIds[this]?.id ?: return null
                return when (id) {
                    excludedCallId -> null
                    idFirstId -> null
                    else -> id
                }
            }


            val idFirst = if (excludedCallId == thisIdFirst) ""
                          else thisIdFirst
            val first = mutableListOf<String>()
            first.add(idFirst)

            val active = mutableListOf<String>()
            val outgoing = mutableListOf<String>()
            val ringing  = mutableListOf<String>()
            val holding  = mutableListOf<String>()
            val others   = mutableListOf<String>()

            for (call in calls) {

                // CHILD konference ignorujeme
                if (
                    call.parent != null ||
                    calls.any { isConference(it) && it.children.contains(call) }
                ) {
                    continue
                }

                val state = call.getStateCompat()
                val id = call.toIdOrNull(idFirst) ?: continue

                if (
                    state == Call.STATE_DISCONNECTING
                    || state == Call.STATE_DISCONNECTED
                    || state == Call.STATE_AUDIO_PROCESSING
                    || state == Call.STATE_NEW
                    || state == Call.STATE_PULLING_CALL
                    || state == Call.STATE_SELECT_PHONE_ACCOUNT
                ) {
                    continue
                }

                when (state) {
                    Call.STATE_CONNECTING,
                    Call.STATE_DIALING -> outgoing += id

                    Call.STATE_SIMULATED_RINGING, Call.STATE_RINGING -> ringing += id
                    Call.STATE_HOLDING -> holding += id

                    Call.STATE_ACTIVE -> active += id

                    else -> others += id
                }
            }

            if (idFirst.isEmpty()) {
                return if (noActive) outgoing + ringing + holding + others
                else active + outgoing + ringing + holding + others
            } else {
                return if (noActive) first + outgoing + ringing + holding + others
                else first + active + outgoing + ringing + holding + others
            }
        }


        fun getCallById(callId: String): Call? {
            return if (callId.isNotEmpty()) callIds.entries.firstOrNull { it.value.id == callId }?.key
            else null
        }

        fun getIdByCall(call: Call?): String? {
            return if (call == null) null
            else callIds[call]?.id
        }

        fun getSimSlotByCall(call: Call?): Int =
            if (call == null) 0 else callIds[call]?.simSlot ?: 0

        fun getSimColorByCall(call: Call?): Int =
            if (call == null) 0 else callIds[call]?.simColor ?: 0

        fun getSimIndexIdByCall(call: Call?): Int =
            if (call == null) 0 else callIds[call]?.simIndexId ?: 0

        fun getSimSlotByCallId(callId: String): Int =
            callIds.entries.firstOrNull { it.value.id == callId }?.value?.simSlot ?: 0

        fun getSpamEmojiByCallId(callId: String): String? {
            if (callId.isBlank()) return null
            return callIds.entries
                .firstOrNull { it.value.id == callId }
                ?.value?.emoji
        }

        fun getSpamEmojiByCall(call: Call?): String? {
            return if (call == null) null
            else  callIds[call]?.emoji
        }

        //počet použitelných hovorů na zásobníku
        fun getAliveCallsCount(): Int {

            val conferenceChildren = calls
                .filter { isConference(it) }
                .flatMap { it.children }
                .toSet()

            return calls.count { call ->
                val state = call.getStateCompat()

                // odfiltrovat mrtvé
                if (
                    state == Call.STATE_DISCONNECTING
                    || state == Call.STATE_DISCONNECTED
                    || state == Call.STATE_AUDIO_PROCESSING
                    || state == Call.STATE_NEW
                    || state == Call.STATE_PULLING_CALL
                    || state == Call.STATE_SELECT_PHONE_ACCOUNT
                ) {
                    return@count false
                }

                // child nebo conference-bound hovor se nepočítá
                if (
                    call.parent != null ||
                    conferenceChildren.contains(call)
                ) {
                    return@count false
                }

                true
            }
        }

        private fun getConferenceChildren(): Set<Call> =
            calls.filter { isConference(it) }
                .flatMap { it.children }
                .toSet()


        fun getCallForUIblind(): Call? {
            // seřazený fallback (active, outgoing, ringing, holding, ostatní)
            val callId = buildOrderedCallIdList(false, excludedCallId = "", "")
                .firstOrNull()
                ?: return null
            return getCallById(callId)
        }


        //aktivní hovor, prý může být jen jeden jediný...
        fun getCallForMeActive(): Call? {

            val conferenceChildren = calls
                .filter { isConference(it) }
                .flatMap { it.children }
                .toSet()

            return calls.find { call ->
                call.getStateCompat() == Call.STATE_ACTIVE &&
                    call.parent == null &&
                    !conferenceChildren.contains(call)
            }
        }



        fun getConferenceCalls(): List<Call> {
            return calls.find { isConference(it) }?.children ?: emptyList()
        }

        fun hasActiveAndHoldCall(): Boolean {
            val conferenceChildren = getConferenceChildren()

            val topLevelCalls = calls.filter { call ->
                call.parent == null &&
                    !conferenceChildren.contains(call) &&
                    call.getStateCompat() != Call.STATE_DISCONNECTING &&
                    call.getStateCompat() != Call.STATE_DISCONNECTED
            }


            val hasActive = topLevelCalls.any { it.getStateCompat() == Call.STATE_ACTIVE }
            val hasHold   = topLevelCalls.any { it.getStateCompat() == Call.STATE_HOLDING }

            return hasActive && hasHold
        }


        //zde zapisuje jen callService
        fun onCallAdded(context : Context, call: Call) {

            CacheContacts.bSpamChecking = context.isAppInstalled( "callfilter.app") ||
                                          context.isAppInstalled( "cz.mts.callfilter")

            //this.call = call

            //přidá se hovor a vygeneruje se jeho unikátní IDstring
            calls.add(call)
            // onCallAdded
            val simInfo = resolveSimInfo(context, call)
            callIds[call] = CallEntry(
                id = UUID.randomUUID().toString(),
                simSlot = simInfo.slot,
                simColor = simInfo.color,
                simIndexId = simInfo.indexId
            )

            if (isConference(call)) {
                return
            }

            val isIncoming = call.isIncoming()

            val handle = try {
                call.details?.handle?.toString()
            } catch (_: NullPointerException) {
                null
            }

            if (handle == null) {
                return
            }

            var number = ""
            val uri = Uri.decode(handle)
            if (uri.startsWith("tel:")) {
                number = uri.substringAfter("tel:")
            }

            if (number.isBlank()) {
                return
            }

            //ukládání pro číselník pokud odešlu prázdnou hodnotu...
            if (!isIncoming) {
                context.config.lastOutgoingCallNumberSim = simInfo.slot
                context.config.lastOutgoingCallNumber = number
            }

                //vyhledat a nakešovat kontakt
                getCallContact(context, call) { callContact ->
                    val isUnknownContact = callContact.id.toInt() == 0
                    //pokud je příchozí a nemáme ho jako uložený kontakt, tak ihned zjistit spam status
                    if (isIncoming && isUnknownContact && CacheContacts.bSpamChecking) {
                        getCallFilterInfo(context, normalizeDigitsOnly(number)) { result ->
                            if (result != null) {
                                if ((getIdByCall(call)) != null) //pokud je null, hovor už neexistuje
                                {
                                    val emoji = checkNumberForRating(result, number, false)
                                    val existing = callIds[call]
                                    if (existing != null) {
                                        callIds[call] = existing.copy(emoji = emoji)
                                        // a dáme echo CallActivity a Callservices
                                        for (listener in listeners) { listener.onCallFilterResult(call, result) }
                                    }
                                }
                            return@getCallFilterInfo
                            }
                        }
                    }
                }



            //for (listener in listeners) {
            //    listener.onPrimaryCallChanged(call)
            //}

     //       call.registerCallback(object : Call.Callback() {
     //           override fun onStateChanged(call: Call, state: Int) {
     //               updateState()
     //           }

     //           override fun onDetailsChanged(call: Call, details: Call.Details) {
     //               updateState()
     //           }

     //           override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
     //               updateState()
     //           }
     //       })
        }

        //pouze a jen callService
        fun onCallRemoved(call: Call) : Int {
            calls.remove(call)
            callIds.remove(call)
            return calls.count()
            //updateState()
        }

        fun onAudioStateChanged(audioState: CallAudioState) {
            val route = AudioRoute.fromRoute(audioState.route) ?: return
            for (listener in listeners) {
                listener.onAudioStateChanged(route)
            }
        }

        fun accept(sUUID: String) {
            accept(getCallById(sUUID))}

        fun accept(callToAnswer: Call?) {
            //val c = callToAnswer ?: getCallFromNotificaton() ?: getCallForUIblind()
            val activeCall = getCallForMeActive()
            if  (activeCall != null) activeCall.hold() //pokud je něco aktivního tak ho podržíme
            callToAnswer?.answer(VideoProfile.STATE_AUDIO_ONLY)
            //o zbytek se postará call service, která spustí UI (onNewIntent, či oncreate)
        }

        fun toggleHold(call: Call?): Boolean {
            if (call == null) return false
            return when (call.getStateCompat()) {
                Call.STATE_ACTIVE -> {
                    call.hold()
                    true
                }
                Call.STATE_HOLDING -> {
//                    getCallForMeActive()?.hold()
                    call.unhold()
                    false
                }
                else -> false
            }
        }

        private data class SimInfo(val slot: Int, val color: Int, val indexId: Int)

        private fun resolveSimInfo(context: Context, call: Call): SimInfo  {
            return try {
                val simLabels = context.getAvailableSIMCardLabels()
                    .sortedBy { it.indexid }
                    .take(2)

                if (simLabels.size <= 1) return SimInfo(0, 0, 0)

                simLabels.forEachIndexed { index, sim ->
                    if (sim.handle == call.details?.accountHandle) {
                        return SimInfo(index + 1, sim.color, sim.indexid)
                    }
                }
                SimInfo(0, 0, 0)
            } catch (_: Exception) { SimInfo(0, 0, 0) }
        }


        fun reject(sUUID: String) {
            reject(getCallById(sUUID))}

        fun reject(callToReject: Call? = null) {
            if (callToReject != null) {
                val state = callToReject.getStateCompat()
                if (state == Call.STATE_RINGING || state == Call.STATE_SIMULATED_RINGING) {
                    callToReject.reject(false, null)
                } else if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                    callToReject.disconnect()
                }
            }
        }

        fun swap(call1 : String, call2 : String) {
            if (call1.isNotBlank() && call2.isNotBlank()) {
                val call1 = getCallById(call1)
                val call2 = getCallById(call2)
                if (call1 != null && call2 != null) {
                    if (call1.parent != null || call2.parent != null) return

                    if (call1.getStateCompat() == Call.STATE_HOLDING && call2.getStateCompat() == Call.STATE_ACTIVE) {
                        call2.hold()
                        call1.unhold()
                    }
                    else if (call2.getStateCompat() == Call.STATE_HOLDING && call1.getStateCompat() == Call.STATE_ACTIVE) {
                        call1.hold()
                        call2.unhold()
                    }
                }
            }
        }

        fun merge(callId1: String, callId2: String) {
            val call1 = getCallById(callId1)
            val call2 = getCallById(callId2)

            if (call1 == null || call2 == null) return
            if (call1 == call2) return

            val state1 = call1.getStateCompat()
            val state2 = call2.getStateCompat()

            val call1IsConf = isConference(call1)
            val call2IsConf = isConference(call2)

            val parent: Call
            val child: Call

            when {
                // 1) jedna z nich je conference → ta je parent
                call1IsConf && !call2IsConf -> {
                    parent = call1
                    child = call2
                }

                call2IsConf && !call1IsConf -> {
                    parent = call2
                    child = call1
                }

                // 2) žádná conference → ACTIVE má přednost
                state1 == Call.STATE_ACTIVE && state2 != Call.STATE_ACTIVE -> {
                    parent = call1
                    child = call2
                }

                state2 == Call.STATE_ACTIVE && state1 != Call.STATE_ACTIVE -> {
                    parent = call2
                    child = call1
                }

                // 3) fallback – pořadí nerozhodnutelné
                else -> {
                    parent = call1
                    child = call2
                }
            }

            parent.mergeConferenceWith(child)
        }



        private fun Call.mergeConferenceWith(holdCall: Call) {
            // Pokud je aktuální hovor konferencí, přidej holdCall do této konference
            if (isConference(this)) {
                // Android nativně: mergeConference() na konferenci by mělo fungovat, ale některé verze vyžadují conference()
                val conferenceable = this.conferenceableCalls
                if (conferenceable.contains(holdCall)) {
                    this.conference(holdCall)
                } else {
                    // fallback – pokud nelze, zkus mergeConference()
                    if (this.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                        this.mergeConference()
                    }
                }
            } else {
                // Pokud to není konferenční hovor, zkus vytvořit konferenci mezi tímto a holdCall
                val conferenceable = this.conferenceableCalls
                if (conferenceable.contains(holdCall)) {
                    this.conference(holdCall)
                }
            }
        }

        fun addListener(listener: CallManagerListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: CallManagerListener) {
            listeners.remove(listener)
        }

        fun keypad(char: Char, callId : String) {
            val call = getCallById(callId)
            call?.playDtmfTone(char)
            Handler(Looper.getMainLooper()).postDelayed({
                call?.stopDtmfTone()
            }, DIALPAD_TONE_LENGTH_MS)
        }

        private fun getCallAudioState(): CallAudioState? {
            // Android 6 – Android 16
            return inCallService?.callAudioState
        }

        fun getCallAudioRoute() = AudioRoute.fromRoute(getCallAudioState()?.route)

        fun setAudioRoute(newRoute: Int) {
            inCallService?.setAudioRoute(newRoute)
        }

        fun setNotificationDone(call: Call, value: Int) {
            val existing = callIds[call] ?: return
            callIds[call] = existing.copy(notificationDone = value)
        }

        fun setNotificationDone(callId: String, value: Int) {
            val call = getCallById(callId) ?: return
            setNotificationDone(call, value)
        }

        fun getNotificationDone(callId: String): Int {
            return callIds.entries
                .firstOrNull { it.value.id == callId }
                ?.value?.notificationDone
                ?: 0
        }

    }
}

interface CallManagerListener {
 //   fun onStateChanged()
    fun onAudioStateChanged(audioState: AudioRoute)
    fun onCallFilterResult(call : Call, result: CallFilterResult)
  //  fun onPrimaryCallChanged(call: Call)
}

//sealed class PhoneState
//object NoCall : PhoneState()
//class SingleCall(val call: Call) : PhoneState()
//class TwoCalls(val active: Call, val onHold: Call) : PhoneState()
