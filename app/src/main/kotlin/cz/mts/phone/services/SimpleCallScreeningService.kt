package cz.mts.phone.services

import android.telecom.Call
import android.telecom.CallScreeningService
import cz.mts.base.extensions.getCallRegion
import cz.mts.base.extensions.isNumberBlocked
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.phone.R
import cz.mts.phone.extensions.isIncoming
import cz.mts.phone.helpers.BlockedCallManager
import cz.mts.phone.helpers.CacheContacts.findContactByPhoneNumber
import cz.mts.phone.helpers.CacheContacts.getCachedContacts
import cz.mts.phone.helpers.CallHelper.isMmiCode
import cz.mts.base.extensions.baseConfig as config


class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val isNullOrEmpty = number.isBlank()
        val hasSpecial = if (!isNullOrEmpty) isMmiCode(number)
                         else false

        // Region (země SIM, přes kterou hovor jde) se zjišťuje jen když je číslo; skrytá čísla řeší jen vlastní přepínače.
        val region = if (!isNullOrEmpty) getCallRegion(callDetails.accountHandle) else null

        // Negativní pravidla (!+420*) jen pro příchozí hovory, jinak by blokovala i vlastní odchozí hovory do zahraničí.
        val isIncoming = callDetails.isIncoming()

        when
        {
            //odchozí hovory (speciální kódy)
            !isIncoming && !isNullOrEmpty && hasSpecial -> respondToCall(isIncoming, number, callDetails, isBlocked = false)

            //číslo prázdné a v nastavení zapnuto blokování neznámých čísel nebo blokování neznámých čísel
            isNullOrEmpty && (config.blockHiddenNumbers || config.blockUnknownNumbers) -> respondToCall(isIncoming, number, callDetails, isBlocked = true)

            //máme nějaké číslo a isNumberBlocked vyšlo pozitivně (= má se blokovat)
            //isNumberBlocked neni neprůstřelné !!!
            //krátké čísla, případně vhodný spoofing (kdy se nedaří převést na E164) anebo *# v čísle se VŽDY pouští!!!
            !isNullOrEmpty && isNumberBlocked(number, region = region, includeNegativeRules = isIncoming) -> respondToCall(isIncoming, number, callDetails, isBlocked = true)

            //máme nějaké číslo a chceme dle konfigu povolit jen kontakty
            //POZOR, předchozí podmínka může zablokovat klidně i číslo z kontaktů pokud je na blacklistu
            //       a také pokud např. blokujeme !+420* tak zablokuje vše co nezačíná +420 bez ohledu na kontakty!!!
            !isNullOrEmpty && config.blockUnknownNumbers -> {
                getCachedContacts(this) { _ ->
                    val notExists = findContactByPhoneNumber(number) == null
                    respondToCall(isIncoming, number, callDetails, isBlocked = notExists)
                }
            }

            else -> respondToCall(isIncoming, number, callDetails, isBlocked = false)

        }
    }

    private fun respondToCall(isIncoming: Boolean, number: String, callDetails: Call.Details, isBlocked: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(isBlocked)
            .setRejectCall(isBlocked)
            .setSkipCallLog(isBlocked)
            .setSkipNotification(isBlocked)
            .build()

        respondToCall(callDetails, response)

        if (isBlocked && isIncoming) {
            val displayNumber = if (number.isBlank()) getString(R.string.unknown_caller)
            else numberForRecents(number, config.formatPhoneNumbers)
            BlockedCallManager.registerBlockedCall(this, displayNumber)
        }
    }
}
