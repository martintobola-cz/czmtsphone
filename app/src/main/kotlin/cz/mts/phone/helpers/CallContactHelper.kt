package cz.mts.phone.helpers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import cz.mts.base.extensions.getPhoneNumberTypeText
import cz.mts.base.helpers.PhoneNumberHelper.areSamePhoneNumber
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.phone.R
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.models.CallContact

fun getCallContact(context: Context, call: Call?, sNumberToSearch: String = "", callback: (CallContact) -> Unit) {

    val bIsFromCall : Boolean = if ((call == null) && (sNumberToSearch.isNotBlank())) false
    else true

    var number = ""
    var presentation = 3

    if (!bIsFromCall) {
        number = sNumberToSearch
    } else {
        if (call == null) {
            callback(CallContact(0,"", "", "", "", 3, ""))
            return
        }

        presentation = call.details?.handlePresentation ?: 3

        if (CallManager.isConference(call)) {
            callback(CallContact(0, context.getString(R.string.conference), "", "", "", presentation, ""))
            return
        }

        val handle = try {
            call.details?.handle?.toString()
        } catch (_: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(CallContact(0,"", "", "", "", 3, ""))
            return
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            number = uri.substringAfter("tel:")
        }
    }

    if ( number.isBlank() || number.equals(context.getString(R.string.unknown_caller))){
        callback(CallContact(0,"", "", "", "", 3, ""))
        return
    }

    ensureBackgroundThread {
        CacheContacts.getCachedContacts(context) { _ -> //sice nepoužíváme, ale potřebujeme to zavolat jinak se nic nanakešuje

            val contact = CacheContacts.findContactByPhoneNumber(number)

            val sNumber = if (context.config.formatPhoneNumbers) numberForRecents(number)
            else number

            // FIX: lokální proměnné pro sestavení výsledku — zabraňuje race condition
            // při vícenásobném zavolání funkce, kdy by obě vlákna mutovala sdílenou instanci
            var contactId = 0L
            var contactSource = ""
            var contactPhotoUri = ""
            var numberLabel = ""
            var name = ""

            if (contact != null) {
                contactSource = contact.source
                contactId = contact.rawId.toLong()
                contactPhotoUri = contact.photoUri

                var specificType = ""
                val count = contact.phoneNumbers.size
                if (count > 1) {
                    val phone = contact.phoneNumbers.firstOrNull { areSamePhoneNumber(it.value , number) }
                    if (phone != null) {
                        specificType = context.getPhoneNumberTypeText(phone.type, phone.label)
                    }
                }
                numberLabel = specificType

                name = contact.getNameToDisplay().ifBlank { number }
            }

            val resolvedNumber = sNumber.ifBlank { number }
            val resolvedName = name.ifBlank { resolvedNumber }

            // FIX: nová immutable instance těsně před callbackem místo mutace
            // sdíleného objektu vytvořeného před bg blokem
            val callContact = CallContact(
                id = contactId,
                name = resolvedName,
                number = resolvedNumber,
                numberLabel = numberLabel,
                photoUri = contactPhotoUri,
                presentation = presentation,
                source = contactSource,
            )

            if (bIsFromCall) {
                callback(callContact)
            } else {
                Handler(Looper.getMainLooper()).post {
                    callback(callContact)
                }
            }
        }
    }

}
