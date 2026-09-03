package cz.mts.phone.helpers

import android.content.Context
import cz.mts.base.helpers.ContactsHelper
import cz.mts.base.helpers.PhoneNumberHelper
import cz.mts.base.models.contacts.Contact
import cz.mts.phone.models.RecentCall


object CacheContacts {

    private val cachedContacts = ArrayList<Contact>()

    // phoneNumber -> contactId
    private val contactCacheSpeed = mutableMapOf<String, Int?>()

    // TRUE:
    // on create
    // on resume !!
    // refreshRecentCalls()
    // po odebrání/smazání kontaktu
    // po změně zdroje

    @Volatile
    var bInvalidateCache = true

    //jsou nainstalovány aplikace na čekování spamu (MainActivity řídí)
    @Volatile
    var bSpamChecking = false

    @Volatile
    var bAnimateRunnig = false

    private var isLoading = false
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    private fun loadContacts(context: Context, onLoaded: () -> Unit) {
        // Atomická kontrola + set pomocí synchronized
        synchronized(this) {
            if (isLoading) {
                pendingCallbacks += onLoaded
                return
            }
            isLoading = true
        }

        ContactsHelper(context).getContacts { contacts ->
            synchronized(this) {
                cachedContacts.clear()
                cachedContacts.addAll(contacts)
                contactCacheSpeed.clear()
                bInvalidateCache = false
                isLoading = false
            }
            // Callbacks zavolat vně synchronized aby nedošlo k deadlocku,
            // ale snapshot si vzít pod zámkem:
            val callbacks = synchronized(this) {
                val copy = pendingCallbacks.toList()
                pendingCallbacks.clear()
                copy
            }
            onLoaded()
            callbacks.forEach { it() }
        }
    }
    fun getCachedContacts(context: Context, onReady: (ArrayList<Contact>) -> Unit) {
        if (!bInvalidateCache) {
            // Snapshot pod zámkem!
            val snapshot = synchronized(this) { ArrayList(cachedContacts) }
            onReady(snapshot)
            return
        }
        loadContacts(context) {
            val snapshot = synchronized(this) { ArrayList(cachedContacts) }
            onReady(snapshot)
        }
    }

    fun findContactByPhoneNumber(phoneNumber: String): Contact? {
        if (phoneNumber.isBlank()) return null
        return synchronized(this) {
            val contactId = contactCacheSpeed.getOrPut(phoneNumber) {
                cachedContacts.firstOrNull { it.doesHavePhoneNumber(phoneNumber) }?.id
            }
            contactId?.let { id -> cachedContacts.firstOrNull { it.id == id } }
        }
    }

    fun findContactByCall(recentCall: RecentCall): Contact? {
        val number = recentCall.specificNumber.ifBlank { recentCall.phoneNumber }
        return findContactByPhoneNumber(number)
    }

    fun findContactById(contactId: Int): Contact? {
        if (contactId <= 0) return null
        return synchronized(this) {
            cachedContacts.firstOrNull { it.id == contactId }
        }
    }

    fun hasCrossSourceDuplicates(precise: Boolean = false, duplicatesLog: StringBuilder? = null): Boolean {
        val snapshot = synchronized(this) { ArrayList(cachedContacts) } // ← snapshot
        val numberToSources = mutableMapOf<String, MutableSet<String>>()

        for (contact in snapshot) { // ← iterujeme snapshot, ne živý list
            for (phoneNumber in contact.phoneNumbers) {
                val normalized = phoneNumber.normalizedNumber.ifBlank { phoneNumber.value }
                if (normalized.isBlank()) continue

                if (precise) {
                    val existingKey = numberToSources.keys.firstOrNull { key ->
                        PhoneNumberHelper.areSamePhoneNumber(key, normalized)
                    }
                    numberToSources
                        .getOrPut(existingKey ?: normalized) { mutableSetOf() }
                        .add(contact.source)
                } else {
                    numberToSources
                        .getOrPut(normalized) { mutableSetOf() }
                        .add(contact.source)
                }
            }
        }

        val hasDuplicates = numberToSources.values.any { sources -> sources.size > 1 }

        duplicatesLog?.let { log ->
            numberToSources
                .filter { (_, sources) -> sources.size > 1 }
                .forEach { (number, sources) ->
                    sources.forEach { source ->
                        log.append("$source;$number\n")
                    }
                }
        }

        return hasDuplicates
    }
}
