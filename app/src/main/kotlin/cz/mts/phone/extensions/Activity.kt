package cz.mts.phone.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import cz.mts.base.extensions.launchActivityIntent
import cz.mts.base.extensions.launchViewContactIntent
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.helpers.MTS_PHONE
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.helpers.isTiramisuPlus
import cz.mts.phone.activities.DialpadActivity
import cz.mts.phone.activities.EditLocalContactActivity
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.helpers.CallHelper
import cz.mts.phone.helpers.getCallContact
import cz.mts.phone.models.RecentCall
import java.lang.ref.WeakReference

fun SimpleActivity.launchCreateNewContactIntent() {
    Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        launchActivityIntent(this)
    }
}

fun Activity.startContactDetailsIntentID(longID: Long, source: String) {
    if (longID !in 1..Int.MAX_VALUE.toLong()) return
    val id = longID.toInt()

    when {
        // Interní kontakty v databázi SQL — vlastní edit
        source == MTS_PHONE -> {
            val intent = Intent(this, EditLocalContactActivity::class.java).apply {
                putExtra(EditLocalContactActivity.CONTACT_ID, id)
                putExtra(EditLocalContactActivity.CONTACT_SOURCE, 1)
            }
            startActivity(intent)
        }

        // Debug mode
        iSaveDebugMode == 1 -> {
            val intent = Intent(this, EditLocalContactActivity::class.java).apply {
                putExtra(EditLocalContactActivity.CONTACT_ID, id)
                putExtra(EditLocalContactActivity.CONTACT_SOURCE, 0)
            }
            startActivity(intent)
        }

        // Standardní kontakty v telefonu
        else -> {
            ensureBackgroundThread {
                val lookupKey = SimpleContactsHelper(this).getContactLookupKey(id.toString())
                val publicUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                    lookupKey
                )
                runOnUiThread {
                    launchViewContactIntent(publicUri)
                }
            }
        }
    }
}

fun Activity.startContactDetailsIntentY(recent: RecentCall) {
    if (recent.isUnknownNumber) return

    val sNumber = recent.specificNumber.takeIf { it.isNotBlank() }
        ?: recent.phoneNumber

    val weakSelf = WeakReference(this)

    getCallContact(this.applicationContext, null, normalizeDigitsOnly(sNumber)) { contact ->
        if (contact.id.toInt() == 0) return@getCallContact

        weakSelf.get()?.runOnUiThread {
            weakSelf.get()?.startContactDetailsIntentID(contact.id, contact.source)
        }
    }
}

    /**
     * Vrátí handle SIM, přes kterou se má volat.
     *
     * - Handle je v intentu (vlastní hovor z launchCallIntentNew) → callback(handle).
     * - Jinak (nebo při forceSimSelector) se otevře číselník s předvyplněným číslem,
     *   uživatel zvolí SIM sám a callback dostane null.
     *
     * Čtení extra z intentu oprávnění nevyžaduje, proto už není závislé na READ_PHONE_STATE
     * (dřív při zamítnutí oprávnění hovor potichu skončil).
     */
    fun SimpleActivity.getHandleToUse(
        intent: Intent?,
        phoneNumber: String,   // dekódované číslo, ne Uri.toString()
        forceSimSelector: Boolean = false,
        callback: (handle: PhoneAccountHandle?) -> Unit
    ) {
        if (phoneNumber.isBlank()) {
            callback(null)
            return
        }

        val handleFromIntent: PhoneAccountHandle? =
            if (isTiramisuPlus()) {
                intent?.getParcelableExtra(
                    TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                    PhoneAccountHandle::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)
            }

        if (handleFromIntent != null && !forceSimSelector) {
            callback(handleFromIntent)
            return
        }

        try {
            startActivity(
                Intent(this, DialpadActivity::class.java)
                    .putExtra(CallHelper.EXTRA_PHONE_NUMBER, phoneNumber)
            )
        } catch (_: Exception) {
        }
        callback(null)
    }

