package cz.mts.base.helpers

import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Event
//import android.provider.MediaStore
import cz.mts.base.extensions.contactsDB
//import cz.mts.base.extensions.getByteArray
import cz.mts.base.extensions.getEmptyContact
import cz.mts.base.models.SimpleContact
import cz.mts.base.models.contacts.Contact
import cz.mts.base.models.contacts.Group
import cz.mts.base.models.contacts.LocalContact
import cz.mts.base.models.contacts.Organization

class LocalContactsHelper(val context: Context) {
    fun getAllContacts(favoritesOnly: Boolean = false): ArrayList<Contact> {
        val contacts = if (favoritesOnly) context.contactsDB.getFavoriteContacts() else context.contactsDB.getContacts()
        val storedGroups = ContactsHelper(context).getStoredGroupsSync()
        return contacts.mapNotNullTo(ArrayList()) { convertLocalContactToContact(it, storedGroups) }
    }

    fun getContactWithId(id: Int): Contact? {
        val storedGroups = ContactsHelper(context).getStoredGroupsSync()
        return convertLocalContactToContact(context.contactsDB.getContactWithId(id), storedGroups)
    }

    fun insertOrUpdateContact(contact: Contact): Boolean {
        val localContact = convertContactToLocalContact(contact)
        return context.contactsDB.insertOrUpdate(localContact) > 0
    }
/**
    fun getPhotoContactWithId(id: Int): Bitmap? {
        val contactPhoto = context.contactsDB.getPhotoContactWithId(id)
        return if (contactPhoto == null) {
            null
        } else {
            try {
                BitmapFactory.decodeByteArray(contactPhoto, 0, contactPhoto.size)
            } catch (e: OutOfMemoryError) {
                null
            }
        }
    }


    fun addContactsToGroup(contacts: ArrayList<Contact>, groupId: Long) {
        contacts.forEach {
            val localContact = convertContactToLocalContact(it)
            val newGroups = localContact.groups
            newGroups.add(groupId)
            newGroups.distinct()
            localContact.groups = newGroups
            context.contactsDB.insertOrUpdate(localContact)
        }
    }

    fun removeContactsFromGroup(contacts: ArrayList<Contact>, groupId: Long) {
        contacts.forEach {
            val localContact = convertContactToLocalContact(it)
            val newGroups = localContact.groups
            newGroups.remove(groupId)
            localContact.groups = newGroups
            context.contactsDB.insertOrUpdate(localContact)
        }
    }

    fun deleteContactIds(ids: MutableList<Int>) {
        ids.chunked(30).forEach {
            context.contactsDB.deleteContactIds(it)
        }
    }

    fun toggleFavorites(ids: Array<Int>, addToFavorites: Boolean) {
        val isStarred = if (addToFavorites) 1 else 0
        ids.forEach {
            context.contactsDB.updateStarred(isStarred, it)
        }
    }

    fun updateRingtone(id: Int, ringtone: String) {
        context.contactsDB.updateRingtone(ringtone, id)
    }

    private fun getPhotoByteArray(uri: String): ByteArray {
        if (uri.isEmpty()) {
            return ByteArray(0)
        }

        val photoUri = Uri.parse(uri)
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, photoUri)

        val fullSizePhotoData = bitmap.getByteArray()
        bitmap.recycle()

        return fullSizePhotoData
    }


    fun getPrivateSimpleContactsSync(favoritesOnly: Boolean, withPhoneNumbersOnly: Boolean) = getAllContacts(favoritesOnly).mapNotNull {
        convertContactToSimpleContact(it, withPhoneNumbersOnly)
    }
**/

    private fun convertLocalContactToContact(localContact: LocalContact?, storedGroups: ArrayList<Group>): Contact? {
        if (localContact == null) {
            return null
        }

        return context.getEmptyContact().apply {
            id = localContact.id!!
            prefix = localContact.prefix
            firstName = localContact.firstName
            middleName = localContact.middleName
            surname = localContact.surname
            suffix = localContact.suffix
            nickname = localContact.nickname
            phoneNumbers = localContact.phoneNumbers
            emails = localContact.emails
            addresses = localContact.addresses
            events = localContact.events
            source = MTS_PHONE
            starred = localContact.starred
            contactId = localContact.id!!
            thumbnailUri = localContact.photoUri
            photo = null
            photoUri = localContact.photoUri
            notes = localContact.notes
            groups = storedGroups.filterTo(ArrayList()) { localContact.groups.contains(it.id) }
            organization = Organization(localContact.company, localContact.jobPosition)
            websites = localContact.websites
            IMs = localContact.IMs
            ringtone = localContact.ringtone
            rawId = localContact.id!!
        }
    }

    fun convertContactToLocalContact(contact: Contact): LocalContact {
        return getEmptyLocalContact().apply {
            id = if (contact.id <= FIRST_CONTACT_ID) null else contact.id
            prefix = contact.prefix
            firstName = contact.firstName
            middleName = contact.middleName
            surname = contact.surname
            suffix = contact.suffix
            nickname = contact.nickname
            photo = null
            photoUri = contact.photoUri
            phoneNumbers = contact.phoneNumbers
            emails = contact.emails
            events = contact.events
            starred = contact.starred
            addresses = contact.addresses
            notes = contact.notes
            groups = contact.groups.mapNotNullTo(ArrayList()) { it.id }
            company = contact.organization.company
            jobPosition = contact.organization.jobPosition
            websites = contact.websites
            IMs = contact.IMs
            ringtone = contact.ringtone
        }
    }


    companion object {
        fun convertContactToSimpleContact(contact: Contact?, withPhoneNumbersOnly: Boolean): SimpleContact? {
            return if (contact == null || (withPhoneNumbersOnly && contact.phoneNumbers.isEmpty())) {
                null
            } else {
                val birthdays = contact.events.mapNotNullTo(ArrayList()) { if (it.type == Event.TYPE_BIRTHDAY) it.value else null }
                val anniversaries = contact.events.mapNotNullTo(ArrayList()) { if (it.type == Event.TYPE_ANNIVERSARY) it.value else null }
                SimpleContact(contact.id, contact.id, contact.getNameToDisplay(), contact.photoUri, contact.phoneNumbers, birthdays, anniversaries)
            }
        }
    }
}
