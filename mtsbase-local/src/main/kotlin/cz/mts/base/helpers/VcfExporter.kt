package cz.mts.base.helpers

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.MediaStore
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.property.Address
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.property.Categories
import ezvcard.property.Email
import ezvcard.property.FormattedName
import ezvcard.property.Impp
import ezvcard.property.Organization
import ezvcard.property.Photo
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Title
import ezvcard.util.PartialDate
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getByteArray
import cz.mts.base.extensions.getDateTimeFromDateString
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.Clipboard.copyTextToClipboard
import cz.mts.base.models.contacts.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate

class VcfExporter {

    private var contactsExported = 0
    private var contactsFailed = 0
    private var sInfo: String = ""

    suspend fun exportContacts(
        activity: BaseSimpleActivity,
        outputStream: OutputStream?,
        contacts: ArrayList<Contact>,
        showExportingToast: Boolean,
        exportOnlyContactsWithNumbers: Boolean,
    ): ExportResult = withContext(Dispatchers.IO)
     {
        if (outputStream == null) return@withContext ExportResult.EXPORT_FAIL

//        if (showExportingToast) {
 //           activity.toast(R.string.exporting)
  //      }

        val cards = ArrayList<VCard>()

        for (contact in contacts) {

            // Přeskočit kontakty bez čísla, pokud je to nastaveno
            if (exportOnlyContactsWithNumbers && contact.phoneNumbers.isEmpty()) continue

            try {
                val card = VCard()

                // --- JMÉNO ---
                val formattedName = arrayOf(
                    contact.prefix,
                    contact.firstName,
                    contact.middleName,
                    contact.surname,
                    contact.suffix
                ).filter { it.isNotEmpty() }.joinToString(separator = " ")
                card.formattedName = FormattedName(formattedName)

                StructuredName().apply {
                    prefixes.add(contact.prefix)
                    given = contact.firstName
                    additionalNames.add(contact.middleName)
                    family = contact.surname
                    suffixes.add(contact.suffix)
                    card.structuredName = this
                }

                // --- PŘEZDÍVKA ---
                if (contact.nickname.isNotEmpty()) {
                    card.setNickname(contact.nickname)
                }

                // --- TELEFONY ---
                contact.phoneNumbers.forEach {
                    val phoneNumber = Telephone(it.value)
                    phoneNumber.parameters.addType(getPhoneNumberTypeLabel(it.type, it.label))
                    card.addTelephoneNumber(phoneNumber)
                }

                // --- EMAILY ---
                contact.emails.forEach {
                    val email = Email(it.value)
                    email.parameters.addType(getEmailTypeLabel(it.type, it.label))
                    card.addEmail(email)
                }

                // --- UDÁLOSTI ---
                contact.events.forEach { event ->
                    if (event.type == Event.TYPE_ANNIVERSARY || event.type == Event.TYPE_BIRTHDAY) {
                        val dateTime = event.value.getDateTimeFromDateString(false)
                        if (event.value.startsWith("--")) {
                            val partial = PartialDate.builder()
                                .month(dateTime.monthValue)
                                .date(dateTime.dayOfMonth)
                                .build()
                            if (event.type == Event.TYPE_BIRTHDAY) {
                                card.birthdays.add(Birthday(partial))
                            } else {
                                card.anniversaries.add(Anniversary(partial))
                            }
                        } else {
                            val date = LocalDate.of(
                                dateTime.year,
                                dateTime.monthValue,
                                dateTime.dayOfMonth
                            )
                            if (event.type == Event.TYPE_BIRTHDAY) {
                                card.birthdays.add(Birthday(date))
                            } else {
                                card.anniversaries.add(Anniversary(date))
                            }
                        }
                    }
                }

                // --- ADRESY ---
                // FIX: opravená logika – pokud má kontakt strukturovaná pole, použij je;
                //      jinak fallback na surovou hodnotu (value).
                contact.addresses.forEach {
                    val address = Address()
                    val hasStructured = listOf(
                        it.country, it.region, it.city,
                        it.postcode, it.pobox, it.street, it.neighborhood
                    ).any { field -> !field.isNullOrEmpty() }

                    if (hasStructured) {
                        address.country = it.country
                        address.region = it.region
                        address.locality = it.city
                        address.postalCode = it.postcode
                        address.poBox = it.pobox
                        address.streetAddress = it.street
                        address.extendedAddress = it.neighborhood
                    } else {
                        address.streetAddress = it.value
                    }
                    address.parameters.addType(getAddressTypeLabel(it.type, it.label))
                    card.addAddress(address)
                }

                // --- IM / SOCIÁLNÍ SÍTĚ ---
                contact.IMs.forEach {
                    val impp = when (it.type) {
                        Im.PROTOCOL_AIM         -> Impp.aim(it.value)
                        Im.PROTOCOL_YAHOO       -> Impp.yahoo(it.value)
                        Im.PROTOCOL_MSN         -> Impp.msn(it.value)
                        Im.PROTOCOL_ICQ         -> Impp.icq(it.value)
                        Im.PROTOCOL_SKYPE       -> Impp.skype(it.value)
                        Im.PROTOCOL_GOOGLE_TALK -> Impp(HANGOUTS, it.value)
                        Im.PROTOCOL_QQ          -> Impp(QQ, it.value)
                        Im.PROTOCOL_JABBER      -> Impp(JABBER, it.value)
                        else                    -> Impp(it.label, it.value)
                    }
                    card.addImpp(impp)
                }

                // --- POZNÁMKY ---
                if (contact.notes.isNotEmpty()) {
                    card.addNote(contact.notes)
                }

                // --- ORGANIZACE + TITUL ---
                if (contact.organization.isNotEmpty()) {
                    val organization = Organization()
                    organization.values.add(contact.organization.company)
                    card.organization = organization
                    card.titles.add(Title(contact.organization.jobPosition))
                }

                // --- WEBOVÉ STRÁNKY ---
                contact.websites.forEach {
                    card.addUrl(it)
                }

                // --- FOTO ---
                // FIX: preferuj photoUri (plná fotka), fallback na thumbnailUri
                val photoUriStr = contact.photoUri.takeIf { it.isNotEmpty() }
                    ?: contact.thumbnailUri.takeIf { it.isNotEmpty() }

                if (photoUriStr != null) {
                    try {
                        val photoByteArray = MediaStore.Images.Media.getBitmap(
                            activity.contentResolver,
                            Uri.parse(photoUriStr)
                        ).getByteArray()
                        card.addPhoto(Photo(photoByteArray, ImageType.JPEG))
                    } catch (photoEx: Exception) {
                        sInfo += "\nFoto ${contact.firstName}: ${photoEx.message}"
                    }
                }

                // --- SKUPINY ---
                if (contact.groups.isNotEmpty()) {
                    val groupList = Categories()
                    contact.groups.forEach { groupList.values.add(it.title) }
                    card.categories = groupList
                }

                cards.add(card)
                contactsExported++

            } catch (e: Exception) {
                // FIX: contactsFailed se nyní skutečně zvyšuje
                sInfo += "\n${contact.firstName} ${contact.surname}: ${e.message}"
                contactsFailed++
            }
        }

        try {
            Ezvcard.write(cards).version(VCardVersion.V4_0).go(outputStream)
        } catch (e: Exception) {
            sInfo += "\nWrite error: ${e.message}"
        }

        sInfo = "Exported: $contactsExported, Failed: $contactsFailed\n$sInfo"

        if (showExportingToast) {
            activity.toast(sInfo)
        }

        if (contactsFailed > 0)
        copyTextToClipboard(activity, "Export", sInfo)

         return@withContext when {
             contactsExported == 0 -> ExportResult.EXPORT_FAIL
             contactsFailed > 0    -> ExportResult.EXPORT_PARTIAL
             else                  -> ExportResult.EXPORT_OK
         }
     }


    // =========================
    // HELPERS
    // =========================

    private fun getPhoneNumberTypeLabel(type: Int, label: String) = when (type) {
        Phone.TYPE_MOBILE   -> CELL
        Phone.TYPE_HOME     -> HOME
        Phone.TYPE_WORK     -> WORK
        Phone.TYPE_MAIN     -> PREF
        Phone.TYPE_FAX_WORK -> WORK_FAX
        Phone.TYPE_FAX_HOME -> HOME_FAX
        Phone.TYPE_PAGER    -> PAGER
        Phone.TYPE_OTHER    -> OTHER
        else                -> label
    }

    private fun getEmailTypeLabel(type: Int, label: String) = when (type) {
        CommonDataKinds.Email.TYPE_HOME   -> HOME
        CommonDataKinds.Email.TYPE_WORK   -> WORK
        CommonDataKinds.Email.TYPE_MOBILE -> MOBILE
        CommonDataKinds.Email.TYPE_OTHER  -> OTHER
        else                              -> label
    }

    private fun getAddressTypeLabel(type: Int, label: String) = when (type) {
        StructuredPostal.TYPE_HOME  -> HOME
        StructuredPostal.TYPE_WORK  -> WORK
        StructuredPostal.TYPE_OTHER -> OTHER
        else                        -> label
    }
}
