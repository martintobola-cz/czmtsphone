package cz.mts.phone.helpers

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.compose.extensions.config
import cz.mts.base.extensions.*
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.helpers.PERMISSION_CALL_PHONE
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.base.helpers.isOreoPlus
import cz.mts.base.helpers.isQPlus
import cz.mts.phone.R
import cz.mts.phone.activities.DialerActivity
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.activities.mtsGlobalAll.handleActiveSimSlot
import cz.mts.phone.activities.mtsGlobalAll.iActiveSimSlot
import cz.mts.phone.activities.mtsGlobalAll.iActiveSimSlotColor
import cz.mts.phone.activities.mtsGlobalAll.iActiveSimSlotIDreal
import cz.mts.phone.activities.mtsGlobalAll.numberOfReadySim
import cz.mts.phone.activities.mtsGlobalAll.resetVars
import cz.mts.phone.activities.mtsGlobalAll.sActiveSimSlotCountry
import cz.mts.phone.activities.mtsGlobalAll.sActiveSimSlotID
import cz.mts.phone.activities.mtsGlobalAll.sActiveSimSlotLabel
import cz.mts.phone.activities.mtsGlobalAll.sActiveSimSlotOperator
import cz.mts.phone.activities.mtsGlobalAll.sActiveSimSlotPhoneNumber
import cz.mts.phone.activities.mtsGlobalAll.showMyAlertDialog
import cz.mts.phone.extensions.telecomManager2
import cz.mts.phone.extensions.telephonyService

/**
 * Pomocná třída pro vytáčení: tísňová čísla, MMI/USSD kódy, IMEI a běžné hovory.
 *
 * Pořadí kontrol v [CallHelper.callNumber] je záměrné:
 *  1) tísňové číslo  (bez blokace, bez kontroly SIM)
 *  2) *#06#          (IMEI)
 *  3) prázdné číslo / neplatný slot
 *  4) MMI kódy       (bez normalizace, blokace a numberForRecents)
 *  5) běžná čísla    (blokace, kontrola SIM, launchCallIntentNew)
 */
object CallHelper {

    const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
    const val EXTRA_INTERNAL_CALL = "cz.mts.phone.EXTRA_INTERNAL_CALL"

    // ------------------------------------------------------------------
    // Veřejné API
    // ------------------------------------------------------------------

    fun callNumber(activity: BaseSimpleActivity, sNumber: String, name: String, iSimSlot: Int) {

        resetVars()

        // 1) TÍSŇOVÁ ČÍSLA – vždy první
        if (isEmergency(activity, sNumber)) {
            placeEmergencyCall(activity, sNumber)
            return
        }

        // 2) IMEI
        if (sNumber == "*#06#") {
            showIMEIDialog(activity)
            return
        }

        // 3) základní validace
        if (sNumber.isEmpty()) {
            val sBody = activity.getString(R.string.call_error1_number_mts) + " " +
                    activity.getString(R.string.call_empty_string_mts)
            showMyAlertDialog(activity, sBody, "Error", true, 0)
            return
        }

        if (iSimSlot > 1) {
            showMyAlertDialog(activity, "index_call_ID > 1 ", "SIM error", true, 0)
            return
        }

        // 4) MMI kódy – žádná normalizace, žádná blokace, žádné numberForRecents
        if (isMmiCode(sNumber)) {
            if (numberOfReadySim(activity) <= 0) {
                showNoSimDialog(activity)
                return
            }
            val handle = if (iSimSlot in 0..1 && iActiveSimSlot[iSimSlot] == 1) {
                handleActiveSimSlot[iSimSlot]
            } else null

            if (handle == null) {
                showMyAlertDialog(activity, "index_call_ID $iSimSlot not ready !", "Error", true, 0)
                return
            }
            placeMmiCall(activity, sNumber, handle)
            return
        }

        // 5) BĚŽNÁ ČÍSLA
        val number = sNumber // sem případně přijde normalizace (jen pro běžná čísla)
        val region = activity.getCallRegion(handleActiveSimSlot.getOrNull(iSimSlot))
        if (activity.isNumberBlocked(number, activity.getBlockedNumbers(), region = region, includeNegativeRules = false)) {
            val sTitle = activity.getString(R.string.call_error1_title_mts)
            val sBody = activity.getString(R.string.call_error1_text_mts)
            showMyAlertDialog(
                activity,
                "\n" + activity.getString(R.string.call_error1_number_mts) + " " +
                        numberForRecents(number, activity.config.formatPhoneNumbers) + " " + sBody,
                sTitle, true, 0
            )
            return
        }

        if (numberOfReadySim(activity) > 0 && iSimSlot >= 0) {
            val handleForCall = handleActiveSimSlot[iSimSlot]

            if (iActiveSimSlot[iSimSlot] == 1 && handleForCall != null) {
                if (iSaveDebugMode == 1) showDebugDialog(activity, number, iSimSlot)
                activity.launchCallIntentNew(number, handleForCall)
            } else {
                showMyAlertDialog(activity, "index_call_ID $iSimSlot not ready !", "Error", true, 0)
            }
        } else {
            showNoSimDialog(activity)
        }
    }

    /**
     * Žádná použitelná SIM. Po potvrzení dialogu zkontroluje, zda je aplikace
     * výchozí dialer; pokud ne, nabídne systémový dialog pro nastavení role.
     */
    private fun showNoSimDialog(activity: BaseSimpleActivity) {
        showMyAlertDialog(
            activity,
            activity.getString(R.string.none_simslot_mts),
            "SIM", true, 0
        ) {
            if (!activity.isDefaultDialer()) {
                activity.launchSetDefaultDialerIntent { granted ->
                    if (granted) {
                        mtsGlobalAll.numberOfReadySim(activity)
                    }
                }
            }
        }
    }
    /**
     * IMEI:
     *  - Android 10+ (Q): běžná aplikace IMEI získat nemůže (getImei hází SecurityException),
     *    proto otevřeme systémové "Informace o zařízení".
     *  - Android 8–9: getImei(slot) s oprávněním READ_PHONE_STATE.
     *  - Android < 8: zastaralé getDeviceId(slot).
     */
    fun showIMEIDialog(context: Context) {
        if (isQPlus()) {
            openDeviceInfoSettings(context)
            return
        }

        try {
            val telephonyService = context.telephonyService
            val imeiList = mutableListOf<String>()
            val slotCount = telephonyService.phoneCount

            for (slotIndex in 0 until slotCount) {
                val imei: String? = if (isOreoPlus()) {
                    telephonyService.getImei(slotIndex)
                } else {
                    @Suppress("DEPRECATION")
                    telephonyService.getDeviceId(slotIndex)
                }
                imei?.let { imeiList.add("SIM ${slotIndex + 1}:  $it") }
            }

            if (imeiList.isEmpty()) {
                imeiList.add(context.getString(R.string.none_simslot2_mts))
            }
            showMyAlertDialog(context, imeiList.joinToString("\n"), "IMEI", true, 0)
        } catch (e: SecurityException) {
            // chybí READ_PHONE_STATE nebo systém IMEI nevydá – zkus aspoň nastavení
            openDeviceInfoSettings(context)
        } catch (e: Exception) {
            showMyAlertDialog(context, e.message.toString(), "IMEI", true, 0)
        }
    }

    // ------------------------------------------------------------------
    // Rozpoznání čísel
    // ------------------------------------------------------------------

    /** MMI/USSD: cokoli, co obsahuje * nebo #. (*31#číslo je normální hovor s prefixem.) */
    fun isMmiCode(number: String): Boolean =
        number.contains('*') || number.contains('#')

    fun isEmergency(context: Context, number: String): Boolean {
        val n = number.trim()
        if (n.isEmpty() || isMmiCode(n)) return false
        try {
            if (isQPlus() &&
                context.telephonyService.isEmergencyNumber(n)) return true
        } catch (_: Exception) { }
        try {
            @Suppress("DEPRECATION")
            if (PhoneNumberUtils.isEmergencyNumber(n)) return true
        } catch (_: Exception) { }
        return n == "112" || n == "911"
    }

    // ------------------------------------------------------------------
    // Vytáčení
    // ------------------------------------------------------------------

    /** Vytočí tísňové číslo. Nikdy nehází výjimku, vždy zkusí záložní cestu. */
    @SuppressLint("MissingPermission")
    private fun placeEmergencyCall(activity: BaseSimpleActivity, number: String) {
        val uri = Uri.fromParts("tel", number, null)

        if (iSaveDebugMode == 1) {
            activity.copyToClipboard(uri.toString(), false)
            return
        }

        // 1) preferovaná cesta
        try {
            activity.telecomManager2.placeCall(uri, Bundle())
            return
        } catch (e: SecurityException) {
            // chybí CALL_PHONE
        } catch (e: Exception) {
        }

        // 2) záloha: ACTION_CALL
        try {
            activity.startActivity(
                Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (e: Exception) {
        }

        // 3) poslední záchrana: vytáčení s předvyplněným číslem
        try {
            activity.startActivity(
                Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            showMyAlertDialog(activity, "Cannot place emergency call", "Error", true, 0)
        }
    }

    /** MMI/USSD jde přímo přes placeCall, stejně jako v systémovém dialeru. */
    @SuppressLint("MissingPermission")
    private fun placeMmiCall(
        activity: BaseSimpleActivity,
        number: String,
        handle: PhoneAccountHandle?
    ) {
        val uri = Uri.fromParts("tel", number, null) // '#' se zakóduje správně

        if (iSaveDebugMode == 1) {
            activity.copyToClipboard(uri.toString(), false)
            return
        }

        val extras = Bundle()
        if (handle != null) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }
        try {
            activity.telecomManager2.placeCall(uri, extras)
        } catch (e: SecurityException) {
            showMyAlertDialog(activity, "Missing CALL_PHONE permission", "Error", true, 0)
        } catch (e: Exception) {
            showMyAlertDialog(activity, e.message ?: "MMI error", "Error", true, 0)
        }
    }

    // ------------------------------------------------------------------
    // IMEI – nastavení
    // ------------------------------------------------------------------

    private fun openDeviceInfoSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showMyAlertDialog(context, e.message.toString(), "IMEI", true, 0)
        } catch (e: Exception) {
            showMyAlertDialog(context, e.message.toString(), "IMEI", true, 0)
        }
    }

    // ------------------------------------------------------------------
    // Debug dialog
    // ------------------------------------------------------------------

    private fun simInfo(index: Int): String =
        "\n\nindex_call_ID: $index" +
                "\nhandle_id = " + sActiveSimSlotID[index] +
                "\nidreal: " + iActiveSimSlotIDreal[index].toString() +
                "\nnumber: " + sActiveSimSlotPhoneNumber[index] +
                "\noperator: " + sActiveSimSlotOperator[index] +
                "\nlabel: " + sActiveSimSlotLabel[index] +
                "\ncolor: " + iActiveSimSlotColor[index].toString() +
                "\ncountry: " + sActiveSimSlotCountry[index] +
                "\nhandle: " + handleActiveSimSlot[index].toString()

    @SuppressLint("MissingPermission")
    private fun showDebugDialog(activity: BaseSimpleActivity, number: String, iSimSlot: Int) {
        var sHelpText = "( $iSimSlot index_call_ID )"

        if (iActiveSimSlot[0] == 1) sHelpText += simInfo(0)
        if (iActiveSimSlot[1] == 1) sHelpText += simInfo(1)

        var defaultHandle: PhoneAccountHandle? = null
        try {
            defaultHandle =
                activity.telecomManager2.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
        } catch (e: Exception) {
        }

        val defaultVoiceID = "\n\ndefault voice: " + SubscriptionManager.getDefaultVoiceSubscriptionId()
        val defaultOutgoingID = if (defaultHandle != null) "\ndefault OUT: " + defaultHandle.id else ""

        showMyAlertDialog(
            activity,
            sHelpText + defaultVoiceID + defaultOutgoingID,
            "Call... $number",
            true,
            0
        )
    }

    /**
     * Vlastní hovor: ACTION_CALL jde do DialerActivity (je-li aplikace výchozí dialer)
     * s vybraným handle SIM a příznakem EXTRA_INTERNAL_CALL.
     * Bez CALL_PHONE se nic nevytáčí (dřív se posílal ACTION_DIAL, který DialerActivity odmítla).
     */
    fun BaseSimpleActivity.launchCallIntentNew(recipient: String, handle: PhoneAccountHandle) {
        val activity = this
        handlePermission(PERMISSION_CALL_PHONE) { granted ->
            if (!granted) {
                showMyAlertDialog(activity, "Missing CALL_PHONE permission", "Error", true, 0)
                return@handlePermission
            }

            val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", recipient, null)).apply {
                putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                putExtra(EXTRA_INTERNAL_CALL, true)

                // explicitní třída místo skládání názvu balíčku (funguje i s příponou .debug)
                if (activity.isDefaultDialer()) {
                    setClass(activity, DialerActivity::class.java)
                }
            }
            launchActivityIntent(intent)
        }
    }

}
