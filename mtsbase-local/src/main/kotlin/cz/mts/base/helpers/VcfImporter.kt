package cz.mts.base.helpers

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.*
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.contactsDB
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.Clipboard.copyTextToClipboard
import cz.mts.base.models.PhoneNumber
import cz.mts.base.models.contacts.Address as MtsAddress
import cz.mts.base.models.contacts.Contact
import cz.mts.base.models.contacts.Email
import cz.mts.base.models.contacts.Event as MtsEvent
import cz.mts.base.models.contacts.IM as MtsIM
import cz.mts.base.models.contacts.Organization
import ezvcard.Ezvcard
import ezvcard.VCard
//import ezvcard.parameter.ImppType
import ezvcard.property.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

import ezvcard.property.StructuredName as VcfStructuredName
//import ezvcard.property.Organization as VcfOrganization
import ezvcard.property.Email as VcfEmail

import android.provider.ContactsContract.CommonDataKinds.StructuredName as AndroidStructuredName
import android.provider.ContactsContract.CommonDataKinds.Organization as AndroidOrganization
import android.provider.ContactsContract.CommonDataKinds.Photo as AndroidPhoto
import android.provider.ContactsContract.CommonDataKinds.Email as AndroidEmail
import android.provider.ContactsContract.CommonDataKinds.Note as AndroidNote
import android.provider.ContactsContract.CommonDataKinds.Nickname as AndroidNickname

object VcfImporter {

    // =========================
    // API
    // =========================

    data class Progress(val current: Int, val total: Int)

    sealed class Result {
        data class Success(val imported: Int, val failed: Int) : Result()
        data class Error(val message: ExportResult) : Result()
    }

    suspend fun import(
        activity: BaseSimpleActivity,
        uri: Uri,
        showExportingToast: Boolean,
        accountName: String?,
        accountType: String?,
        onProgress: (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {

        val input = try {
            activity.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("open file error !")
        } catch (e: Exception) {
            copyTextToClipboard(activity, "Import", e.message.toString())
            return@withContext Result.Error(ExportResult.IMPORT_FAIL)
        }

        val cards = try {
            Ezvcard.parse(input).all()
        } catch (e: Exception) {
            copyTextToClipboard(activity, "Import", e.message.toString())
            return@withContext Result.Error(ExportResult.IMPORT_FAIL)
        }

        var imported = 0
        var failed = 0
        val total = cards.size
        var sInfo = ""

        //if (showExportingToast) {
        //    activity.toast(R.string.importing)
        //}

        cards.forEachIndexed { index, card ->
            try {
                insertContact(activity, activity.contentResolver, card, accountName, accountType)
                imported++
            } catch (e: Exception) {
                sInfo += "\r\n${index}/${total} error:\r\n${e.message}"
                failed++
            }
            onProgress(Progress(index + 1, total))
        }

        val sInfo2 = "Imported: $imported (total $total)"
        if (failed > 0)
            copyTextToClipboard(activity, "Import", sInfo2 + sInfo)

        if (showExportingToast) {
            activity.toast(sInfo2)
        }

        Result.Success(imported, failed)
    }

    // =========================
    // INSERT CONTACT
    // =========================

    private fun insertContact(
        activity: BaseSimpleActivity,
        resolver: ContentResolver,
        card: VCard,
        accountName: String?,
        accountType: String?
    ) {
        if (accountName == MTS_PHONE && accountType == MTS_PHONE) {
            insertLocalContact(activity, card)
            return
        }

        val ops = ArrayList<ContentProviderOperation>()

        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_NAME, accountName)
            .withValue(RawContacts.ACCOUNT_TYPE, accountType)
            .build()

        val raw = 0

        // --- JMÉNO ---
        card.structuredName?.let { name: VcfStructuredName ->
            ops += insert(raw, AndroidStructuredName.CONTENT_ITEM_TYPE) {
                withValue(AndroidStructuredName.GIVEN_NAME, name.given)
                withValue(AndroidStructuredName.FAMILY_NAME, name.family)
                withValue(AndroidStructuredName.MIDDLE_NAME, name.additionalNames.firstOrNull())
                withValue(AndroidStructuredName.PREFIX, name.prefixes.firstOrNull())
                withValue(AndroidStructuredName.SUFFIX, name.suffixes.firstOrNull())
            }
        }

        // --- PŘEZDÍVKA ---
        card.nickname?.values?.firstOrNull()?.let { nick ->
            ops += insert(raw, AndroidNickname.CONTENT_ITEM_TYPE) {
                withValue(AndroidNickname.NAME, nick)
            }
        }

        // --- TELEFONY ---
        card.telephoneNumbers.forEach { tel ->
            ops += insert(raw, Phone.CONTENT_ITEM_TYPE) {
                withValue(Phone.NUMBER, tel.text)
                withValue(Phone.TYPE, mapPhoneType(tel))
            }
        }

        // --- EMAILY ---
        card.emails.forEach { email: VcfEmail ->
            ops += insert(raw, AndroidEmail.CONTENT_ITEM_TYPE) {
                withValue(AndroidEmail.ADDRESS, email.value)
                withValue(AndroidEmail.TYPE, mapEmailType(email))
            }
        }

        // --- ADRESY (všechna pole včetně neighborhood, pobox, region) ---
        card.addresses.forEach { addr ->
            ops += insert(raw, StructuredPostal.CONTENT_ITEM_TYPE) {
                withValue(StructuredPostal.STREET, addr.streetAddress.orEmpty())
                withValue(StructuredPostal.NEIGHBORHOOD, addr.extendedAddress.orEmpty())
                withValue(StructuredPostal.POBOX, addr.poBox.orEmpty())
                withValue(StructuredPostal.CITY, addr.locality.orEmpty())
                withValue(StructuredPostal.POSTCODE, addr.postalCode.orEmpty())
                withValue(StructuredPostal.REGION, addr.region.orEmpty())
                withValue(StructuredPostal.COUNTRY, addr.country.orEmpty())
                withValue(StructuredPostal.TYPE, mapAddressType(addr))
            }
        }

        // --- ORGANIZACE + TITUL v jednom záznamu ---
        // Vkládáme jako jediný řádek, aby firma a pozice zůstaly svázané.
        val orgCompany = card.organizations.firstOrNull()?.values?.firstOrNull().orEmpty()
        val orgTitle = card.titles.firstOrNull()?.value.orEmpty()
        if (orgCompany.isNotEmpty() || orgTitle.isNotEmpty()) {
            ops += insert(raw, AndroidOrganization.CONTENT_ITEM_TYPE) {
                withValue(AndroidOrganization.COMPANY, orgCompany)
                withValue(AndroidOrganization.TITLE, orgTitle)
                withValue(AndroidOrganization.TYPE, AndroidOrganization.TYPE_WORK)
            }
        }

        // --- POZNÁMKY (všechny, spojené do jednoho záznamu) ---
        val allNotes = card.notes.mapNotNull { it.value }.filter { it.isNotEmpty() }
        if (allNotes.isNotEmpty()) {
            ops += insert(raw, AndroidNote.CONTENT_ITEM_TYPE) {
                withValue(AndroidNote.NOTE, allNotes.joinToString("\n\n"))
            }
        }

        // --- UDÁLOSTI: narozeniny ---
        card.birthdays.firstOrNull()?.let { bday ->
            val dateStr = bday.date?.toString() ?: bday.partialDate?.toString()
            if (dateStr != null) {
                ops += insert(raw, Event.CONTENT_ITEM_TYPE) {
                    withValue(Event.TYPE, Event.TYPE_BIRTHDAY)
                    withValue(Event.START_DATE, dateStr)
                }
            }
        }

        // --- UDÁLOSTI: výročí ---
        card.anniversaries.firstOrNull()?.let { ann ->
            val dateStr = ann.date?.toString() ?: ann.partialDate?.toString()
            if (dateStr != null) {
                ops += insert(raw, Event.CONTENT_ITEM_TYPE) {
                    withValue(Event.TYPE, Event.TYPE_ANNIVERSARY)
                    withValue(Event.START_DATE, dateStr)
                }
            }
        }

        // --- IM / SOCIÁLNÍ SÍTĚ ---
        card.impps.forEach { impp ->
            val protocol = mapImppProtocol(impp)
            val handle = impp.handle.orEmpty()
            if (handle.isNotEmpty()) {
                ops += insert(raw, Im.CONTENT_ITEM_TYPE) {
                    withValue(Im.DATA, handle)
                    withValue(Im.TYPE, Im.TYPE_OTHER)
                    withValue(Im.PROTOCOL, protocol)
                    // Pokud je protokol CUSTOM, zapíšeme i popisek
                    if (protocol == Im.PROTOCOL_CUSTOM) {
                        withValue(Im.CUSTOM_PROTOCOL, impp.uri?.scheme.orEmpty())
                    }
                }
            }
        }

        // Ve VcfImporter, v insertLocalContact i insertContact

        val photo = card.photos.firstOrNull()
        val photoBytes: ByteArray? = photo?.data
            ?: photo?.url?.let { url ->
                // Stáhneme fotku z URL (jen pokud je to http/https)
                try {
                    if (url.startsWith("http")) {
                        java.net.URL(url).openStream().use { it.readBytes() }
                    } else null
                } catch (e: Exception) { null }
            }

        // --- FOTO (první, resized) ---
        photoBytes?.let { data ->
            val resized = resizePhotoIfNeeded(data, MAX_PHOTO_BYTES_SYSTEM)
            ops += insert(raw, AndroidPhoto.CONTENT_ITEM_TYPE) {
                withValue(AndroidPhoto.PHOTO, resized)
            }
        }

        // --- WEBOVÉ STRÁNKY (všechny) ---
        card.urls.forEach { url ->
            if (url.value.isNotEmpty()) {
                ops += insert(raw, Website.CONTENT_ITEM_TYPE) {
                    withValue(Website.URL, url.value)
                    withValue(Website.TYPE, Website.TYPE_OTHER)
                }
            }
        }

        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    // =========================
    // INSERT LOCAL CONTACT
    // =========================

    private fun insertLocalContact(activity: BaseSimpleActivity, card: VCard) {
        val helper = LocalContactsHelper(activity)
        val name = card.structuredName

        val contact = Contact(id = 0, contactId = 0)

        // --- JMÉNO ---
        contact.prefix = name?.prefixes?.firstOrNull().orEmpty()
        contact.firstName = name?.given.orEmpty()
        contact.middleName = name?.additionalNames?.firstOrNull().orEmpty()
        contact.surname = name?.family.orEmpty()
        contact.suffix = name?.suffixes?.firstOrNull().orEmpty()

        // --- PŘEZDÍVKA ---
        contact.nickname = card.nickname?.values?.firstOrNull().orEmpty()

        val photo = card.photos.firstOrNull()
        val photoBytes: ByteArray? = photo?.data
            ?: photo?.url?.let { url ->
                // Stáhneme fotku z URL (jen pokud je to http/https)
                try {
                    if (url.startsWith("http")) {
                        java.net.URL(url).openStream().use { it.readBytes() }
                    } else null
                } catch (e: Exception) { null }
            }

        // --- FOTO ---
        contact.photo = null
        contact.photoUri = photoBytes?.let { bytes ->
            val resized = resizePhotoIfNeeded(bytes, MAX_PHOTO_BYTES_LOCAL)
            LocalContactPhotoStorage.save(activity, resized)
        }.orEmpty()

        // --- TELEFONY ---
        contact.phoneNumbers = card.telephoneNumbers.map { tel ->
            PhoneNumber(
                value = tel.text,
                normalizedNumber = PhoneNumberHelper.normalizeDigitsOnly(tel.text),
                type = mapPhoneType(tel),
                label = "",
                isPrimary = false
            )
        }.toCollection(ArrayList())

        // --- EMAILY ---
        contact.emails = card.emails.map { email ->
            Email(
                value = email.value,
                type = mapEmailType(email),
                label = ""
            )
        }.toCollection(ArrayList())

        // --- ADRESY (všechna pole) ---
        contact.addresses = card.addresses.map { addr ->
            val street = addr.streetAddress.orEmpty()
            val neighborhood = addr.extendedAddress.orEmpty()
            val pobox = addr.poBox.orEmpty()
            val city = addr.locality.orEmpty()
            val postcode = addr.postalCode.orEmpty()
            val region = addr.region.orEmpty()
            val country = addr.country.orEmpty()

            // Sestavíme lidsky čitelnou hodnotu stejně jako v EditLocalContactActivity
            val lineStreet = listOf(street, pobox, neighborhood)
                .filter { it.isNotEmpty() }.joinToString(" ")
            val lineLocality = listOf(postcode, city)
                .filter { it.isNotEmpty() }.joinToString(" ")
            val fullAddress = listOf(lineStreet, lineLocality, region, country)
                .filter { it.isNotEmpty() }.joinToString("\n")

            MtsAddress(
                value = fullAddress,
                type = mapAddressType(addr),
                label = "",
                country = country,
                region = region,
                city = city,
                postcode = postcode,
                pobox = pobox,
                street = street,
                neighborhood = neighborhood
            )
        }.toCollection(ArrayList())

        // --- POZNÁMKY (všechny, spojené) ---
        contact.notes = card.notes.mapNotNull { it.value }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

        // --- ORGANIZACE + TITUL ---
        val orgCompany = card.organizations.firstOrNull()?.values?.firstOrNull().orEmpty()
        val orgTitle = card.titles.firstOrNull()?.value.orEmpty()
        contact.organization = Organization(company = orgCompany, jobPosition = orgTitle)

        // --- WEBOVÉ STRÁNKY (všechny) ---
        contact.websites = card.urls
            .map { it.value }
            .filter { it.isNotEmpty() }
            .toCollection(ArrayList())

        // --- UDÁLOSTI: narozeniny + výročí ---
        val events = ArrayList<MtsEvent>()

        card.birthdays.firstOrNull()?.let { bday ->
            val dateStr = bday.date?.toString() ?: bday.partialDate?.toString()
            if (dateStr != null) {
                events.add(MtsEvent(dateStr, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY))
            }
        }

        card.anniversaries.firstOrNull()?.let { ann ->
            val dateStr = ann.date?.toString() ?: ann.partialDate?.toString()
            if (dateStr != null) {
                events.add(MtsEvent(dateStr, ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY))
            }
        }

        contact.events = events

        // --- IM / SOCIÁLNÍ SÍTĚ ---
        contact.IMs = card.impps.mapNotNull { impp ->
            val handle = impp.handle.orEmpty()
            if (handle.isEmpty()) return@mapNotNull null
            val protocol = mapImppProtocol(impp)
            // Pro CUSTOM protokol uložíme scheme jako label
            val label = if (protocol == Im.PROTOCOL_CUSTOM) impp.uri?.scheme.orEmpty() else ""
            MtsIM(value = handle, type = protocol, label = label)
        }.toCollection(ArrayList())

        // --- SKUPINY + ZDROJ ---
        contact.groups = arrayListOf()
        contact.starred = 0
        contact.source = MTS_PHONE

        // Vložení do DB
        activity.contactsDB.insertOrUpdate(helper.convertContactToLocalContact(contact))
    }

    // =========================
    // HELPERS
    // =========================

    private fun insert(
        raw: Int,
        mime: String,
        block: ContentProviderOperation.Builder.() -> Unit
    ): ContentProviderOperation =
        ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, raw)
            .withValue(Data.MIMETYPE, mime)
            .apply(block)
            .build()

    private fun mapPhoneType(t: Telephone): Int {
        val types = t.parameters.types
        return when {
            types.contains("CELL")   -> Phone.TYPE_MOBILE
            types.contains("WORK")   -> Phone.TYPE_WORK
            types.contains("HOME")   -> Phone.TYPE_HOME
            types.contains("FAX") && types.contains("WORK") -> Phone.TYPE_FAX_WORK
            types.contains("FAX") && types.contains("HOME") -> Phone.TYPE_FAX_HOME
            types.contains("FAX")    -> Phone.TYPE_FAX_WORK
            types.contains("PAGER")  -> Phone.TYPE_PAGER
            types.contains("MAIN")   -> Phone.TYPE_MAIN
            else                     -> Phone.TYPE_OTHER
        }
    }

    private fun mapEmailType(e: VcfEmail): Int {
        val types = e.parameters.types
        return when {
            types.contains("WORK")   -> AndroidEmail.TYPE_WORK
            types.contains("HOME")   -> AndroidEmail.TYPE_HOME
            types.contains("CELL")   -> AndroidEmail.TYPE_MOBILE
            else                     -> AndroidEmail.TYPE_OTHER
        }
    }

    private fun mapAddressType(a: Address): Int {
        val types = a.parameters.types
        return when {
            types.contains("WORK")   -> StructuredPostal.TYPE_WORK
            types.contains("HOME")   -> StructuredPostal.TYPE_HOME
            else                     -> StructuredPostal.TYPE_OTHER
        }
    }

    /**
     * Mapuje ez-vcard Impp URI schéma na Android Im.PROTOCOL_* konstanty.
     * Pokud protokol není rozpoznán, vrátí Im.PROTOCOL_CUSTOM.
     *
     * ez-vcard ukládá IMPP jako URI: "skype:username", "xmpp:user@server", apod.
     * impp.uri?.scheme vrací část před dvojtečkou (lowercase).
     */
    private fun mapImppProtocol(impp: Impp): Int {
        // ez-vcard občas parsuje typ z ImppType parametru (pro vCard 3.0)
        val typeHint = impp.types.firstOrNull()?.value?.lowercase().orEmpty()

        return when (impp.uri?.scheme?.lowercase().orEmpty()) {
            "aim"         -> Im.PROTOCOL_AIM
            "msn", "msnmsgr", "msnim" -> Im.PROTOCOL_MSN
            "yahoo", "ymsgr" -> Im.PROTOCOL_YAHOO
            "skype"       -> Im.PROTOCOL_SKYPE
            "qq"          -> Im.PROTOCOL_QQ
            "gtalk", "google-talk", "xmpp" -> Im.PROTOCOL_GOOGLE_TALK
            "icq"         -> Im.PROTOCOL_ICQ
            "jabber"      -> Im.PROTOCOL_JABBER
            else -> when (typeHint) {
                "aim"     -> Im.PROTOCOL_AIM
                "msn"     -> Im.PROTOCOL_MSN
                "yahoo"   -> Im.PROTOCOL_YAHOO
                "skype"   -> Im.PROTOCOL_SKYPE
                "qq"      -> Im.PROTOCOL_QQ
                "gtalk", "google-talk" -> Im.PROTOCOL_GOOGLE_TALK
                "icq"     -> Im.PROTOCOL_ICQ
                "jabber"  -> Im.PROTOCOL_JABBER
                else      -> Im.PROTOCOL_CUSTOM
            }
        }
    }

    // =========================
    // FOTO – resize
    // =========================

    // =========================
// FOTO – resize
// =========================

    const val MAX_PHOTO_DIMENSION      = 512           // px (sdílené)
    const val MAX_PHOTO_BYTES_SYSTEM   = 200 * 1024   // 200 KB – Android ContactsContract blob
    const val MAX_PHOTO_BYTES_LOCAL    = 1024 * 1024  //   1 MB – lokální DB / soubor

    fun resizePhotoIfNeeded(
        data: ByteArray,
        maxBytes: Int = MAX_PHOTO_BYTES_SYSTEM
    ): ByteArray {
        if (data.size <= maxBytes) return data

        val original = BitmapFactory.decodeByteArray(data, 0, data.size)
            ?: return data

        val scale = (MAX_PHOTO_DIMENSION.toFloat() / maxOf(original.width, original.height))
            .coerceAtMost(1.0f)
        val width  = (original.width  * scale).toInt()
        val height = (original.height * scale).toInt()

        val resized = Bitmap.createScaledBitmap(original, width, height, true)
        val stream  = ByteArrayOutputStream()
        var quality = 90
        do {
            stream.reset()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            quality -= 10
        } while (stream.size() > maxBytes && quality > 10)

        return stream.toByteArray()
    }
}
