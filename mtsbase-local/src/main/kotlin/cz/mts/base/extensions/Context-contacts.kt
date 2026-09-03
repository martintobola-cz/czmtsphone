package cz.mts.base.extensions

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import cz.mts.base.R
import cz.mts.base.databases.ContactsDatabase
import cz.mts.base.helpers.ContactsHelper
import cz.mts.base.helpers.DEFAULT_MIMETYPE
import cz.mts.base.helpers.PERMISSION_READ_CONTACTS
import cz.mts.base.helpers.PERMISSION_WRITE_CONTACTS
import cz.mts.base.helpers.PhoneNumberHelper.normalizeNumberE164
import cz.mts.base.helpers.MTS_PHONE
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.interfaces.ContactsDao
import cz.mts.base.interfaces.GroupsDao
import cz.mts.base.models.contacts.Contact
import cz.mts.base.models.contacts.ContactSource
import cz.mts.base.models.contacts.Organization

val Context.contactsDB: ContactsDao get() = ContactsDatabase.getInstance(applicationContext).ContactsDao()

val Context.groupsDB: GroupsDao get() = ContactsDatabase.getInstance(applicationContext).GroupsDao()

fun Context.getEmptyContact(): Contact {
    val originalContactSource = if (hasContactPermissions()) baseConfig.lastUsedContactSource else MTS_PHONE
    val organization = Organization("", "")
    return Contact(
        0, "", "", "", "", "", "", "", ArrayList(), ArrayList(), ArrayList(), ArrayList(), originalContactSource, 0, 0, "",
        null, "", ArrayList(), organization, ArrayList(), ArrayList(), DEFAULT_MIMETYPE, null
    )
}


fun Context.hasContactPermissions() = hasPermission(PERMISSION_READ_CONTACTS) && hasPermission(PERMISSION_WRITE_CONTACTS)

fun Context.getVisibleContactSources(): ArrayList<String> {
    val sources = getAllContactSources()
    val ignoredContactSources = baseConfig.ignoredContactSources
    return ArrayList(sources).filter { !ignoredContactSources.contains(it.getFullIdentifier()) }
        .map { it.name }.toMutableList() as ArrayList<String>
}

fun Context.getAllContactSources(): ArrayList<ContactSource> {
    val sources = ContactsHelper(this).getDeviceContactSources()
    // Přidáme private source jen pokud tam není (neměl by tam být nikdy...)
    if (sources.none { it.type == MTS_PHONE }) {
        sources.add(getPrivateContactSource())
    }
    return ArrayList(sources) // převedeme na ArrayList
}


fun Context.getPrivateContactSource() = ContactSource(MTS_PHONE, MTS_PHONE, getString(R.string.phone_storage_hidden))

fun Context.isContactBlocked(contact: Contact, callback: (Boolean) -> Unit) {
    val phoneNumbers = contact.phoneNumbers.map { normalizeNumberE164(it.value, null, false) }
    getBlockedNumbersWithContact { blockedNumbersWithContact ->
        val blockedNumbers = blockedNumbersWithContact.map { it.number }
        val allNumbersBlocked = phoneNumbers.all { it in blockedNumbers }
        callback(allNumbersBlocked)
    }
}

@TargetApi(Build.VERSION_CODES.N)
fun Context.blockContact(contact: Contact): Boolean {
    var contactBlocked = true
    ensureBackgroundThread {
        contact.phoneNumbers.forEach {
            val numberBlocked = addBlockedNumber(it.value)
            contactBlocked = contactBlocked && numberBlocked
        }
    }

    return contactBlocked
}

@TargetApi(Build.VERSION_CODES.N)
fun Context.unblockContact(contact: Contact): Boolean {
    var contactUnblocked = true
    ensureBackgroundThread {
        contact.phoneNumbers.forEach {
            val numberUnblocked = deleteBlockedNumber(it.value)
            contactUnblocked = contactUnblocked && numberUnblocked
        }
    }

    return contactUnblocked
}
