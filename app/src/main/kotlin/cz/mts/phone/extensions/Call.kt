package cz.mts.phone.extensions

import android.telecom.Call
import android.telecom.Call.STATE_CONNECTING
import android.telecom.Call.STATE_DIALING
import android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT
import cz.mts.base.helpers.isQPlus
import cz.mts.base.helpers.isSPlus

private val OUTGOING_CALL_STATES = setOf(
    STATE_CONNECTING,
    STATE_DIALING,
    STATE_SELECT_PHONE_ACCOUNT
)

private const val CALL_NOT_CONNECTED_TIME = 0L

@Suppress("DEPRECATION")
fun Call?.getStateCompat(): Int {
    return when {
        this == null -> Call.STATE_DISCONNECTED
        isSPlus() -> details.state
        else -> state
    }
}

fun Call?.getCallDuration(): Int {
    if (this == null) return 0

    val connectTimeMillis = details.connectTimeMillis
    if (connectTimeMillis == CALL_NOT_CONNECTED_TIME) return 0

    return ((System.currentTimeMillis() - connectTimeMillis) / 1000).toInt()
}

fun Call.isIncoming(): Boolean {
    return if (isQPlus()) {
        details.callDirection == Call.Details.DIRECTION_INCOMING
    } else {
        getStateCompat() == Call.STATE_RINGING
            //|| getStateCompat() == Call.STATE_SIMULATED_RINGING
    }
}

fun Call.isOutgoing(): Boolean {
    return if (isQPlus()) {
        details.callDirection == Call.Details.DIRECTION_OUTGOING
    } else {
        OUTGOING_CALL_STATES.contains(getStateCompat())
    }
}

fun Call.hasCapability(capability: Int): Boolean =
    (details.callCapabilities and capability) != 0
