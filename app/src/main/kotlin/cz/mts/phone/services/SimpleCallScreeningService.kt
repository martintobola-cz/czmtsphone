package cz.mts.phone.services

import android.telecom.Call
import android.telecom.CallScreeningService
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.extensions.isNumberBlocked
import cz.mts.phone.helpers.CacheContacts.findContactByPhoneNumber
import cz.mts.phone.helpers.CacheContacts.getCachedContacts


class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val isNullOrEmpty = number.isNullOrEmpty()
        when
        {
            isNullOrEmpty && (config.blockHiddenNumbers || config.blockUnknownNumbers) -> respondToCall(callDetails, isBlocked = true)

            !isNullOrEmpty && isNumberBlocked(number) -> respondToCall(callDetails, isBlocked = true)

            !isNullOrEmpty && config.blockUnknownNumbers -> {
                getCachedContacts(this) { _ ->
                    val notExists = findContactByPhoneNumber(number) == null
                    respondToCall(callDetails, isBlocked = notExists)
                }
            }

            else -> respondToCall(callDetails, isBlocked = false)

        }
    }


    private fun respondToCall(callDetails: Call.Details, isBlocked: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(isBlocked)
            .setRejectCall(isBlocked)
            .setSkipCallLog(isBlocked)
            .setSkipNotification(isBlocked)
            .build()

        respondToCall(callDetails, response)
    }
}
