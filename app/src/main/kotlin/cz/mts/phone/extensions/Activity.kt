package cz.mts.phone.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import cz.mts.base.extensions.launchActivityIntent
import cz.mts.base.extensions.launchViewContactIntent
import cz.mts.base.helpers.PERMISSION_READ_PHONE_STATE
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.phone.activities.DialpadActivity
import cz.mts.phone.activities.SimpleActivity
import cz.mts.base.helpers.MTS_PHONE
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.phone.activities.EditLocalContactActivity
import cz.mts.phone.activities.mtsGlobalAll
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
        mtsGlobalAll.iSaveDebugMode == 1 -> {
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

    val sNumber = recent.specificNumber?.takeIf { it.isNotBlank() }
        ?: recent.phoneNumber

    val weakSelf = WeakReference(this)

    getCallContact(this.applicationContext, null, normalizeDigitsOnly(sNumber)) { contact ->
        if (contact.id.toInt() == 0) return@getCallContact

        weakSelf.get()?.runOnUiThread {
            weakSelf.get()?.startContactDetailsIntentID(contact.id, contact.source)
        }
    }
}

// Používá se na zařízeních s více SIM kartami
@SuppressLint("MissingPermission")
fun SimpleActivity.getHandleToUse(
    intent: Intent?,
    phoneNumber: String,
    forceSimSelector: Boolean = false,
    callback: (handle: PhoneAccountHandle?) -> Unit
) {
    handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
        if (!granted) {
            callback(null)
            return@handlePermission
        }

        val handleFromIntent: PhoneAccountHandle? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(
                    TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                    PhoneAccountHandle::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)
            }

        when {
            phoneNumber.isBlank() -> callback(null)

            handleFromIntent != null -> callback(handleFromIntent)

            // Výchozí handle není k dispozici — otevřeme dialpad pro výběr SIM
            else -> {
                val dialIntent = Intent(this, DialpadActivity::class.java).apply {
                    putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                }
                startActivity(dialIntent)
                callback(null)
            }
        }
    }
}
