package cz.mts.phone.dialogs

import androidx.appcompat.app.AlertDialog
import cz.mts.base.helpers.ContactsHelper
import cz.mts.base.models.contacts.Contact
import cz.mts.base.models.contacts.ContactSource
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.adapters.FilterContactSourcesAdapter
import cz.mts.phone.databinding.DialogFilterContactSourcesBinding
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.extensions.getVisibleContactSources
import cz.mts.base.extensions.viewBinding
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.helpers.MTS_NONE
import cz.mts.base.helpers.VcfImportSource

class FilterContactSourceDialogMTs(
    private val activity: SimpleActivity,
    private val bImport: Boolean,
    private val callback: () -> Unit
) {
    private val binding by activity.viewBinding(DialogFilterContactSourcesBinding::inflate)

    private var dialog: AlertDialog? = null

    @Volatile private var isContactSourcesReady = false
    @Volatile private var isContactsReady = false

    private var contactSources = ArrayList<ContactSource>()
    private var contacts = ArrayList<Contact>()

    init {
        val contactHelper = ContactsHelper(activity)

        contactHelper.getContactSources { sources ->
            synchronized(this) {
                sources.mapTo(contactSources) { it.copy() }
                isContactSourcesReady = true
            }
            processDataIfReady()
        }

        contactHelper.getContacts(getAll = true, getFastCountOnly = true) { result ->
            synchronized(this) {
                result.mapTo(contacts) { it.copy() }
                isContactsReady = true
            }
            processDataIfReady()
        }
    }

    @Synchronized
    private fun processDataIfReady() {
        if (!isContactSourcesReady) return

        val contactSourcesWithCount = contactSources.map { source ->
            val count = if (isContactsReady) {
                contacts.count { it.source == source.name }
            } else {
                -1
            }
            source.copy(count = count)
        }

        activity.runOnUiThread {
            val selectedSources = activity.getVisibleContactSources()
            binding.filterContactSourcesList.adapter =
                FilterContactSourcesAdapter(activity, contactSourcesWithCount, selectedSources)

            if (dialog == null) {
                activity.getAlertDialogBuilder()
                    .setPositiveButton(R.string.ok) { _, _ -> confirmContactSources() }
                    .setNegativeButton(R.string.cancel, null)
                    .apply {
                        activity.setupDialogStuff(binding.root, this) { alertDialog ->
                            dialog = alertDialog
                        }
                    }
            }
        }
    }

    private fun confirmContactSources() {
        val adapter = binding.filterContactSourcesList.adapter as FilterContactSourcesAdapter
        val selectedContactSources = adapter.getSelectedContactSources()

        // Porovnání přes identifier, ne přes equals() (který zahrnuje count)
        val selectedIdentifiers = selectedContactSources.map { it.getFullIdentifier() }.toHashSet()

        val ignoredContactSources = synchronized(this) { contactSources.toList() }
            .filter { !selectedIdentifiers.contains(it.getFullIdentifier()) }
            .map { it.getFullIdentifier() }
            .toHashSet()


        if (!bImport) {
            if (activity.getVisibleContactSources() != ignoredContactSources) {
                activity.config.ignoredContactSources = ignoredContactSources
                callback()
            }
        } else {
            when (selectedContactSources.size) {
                1 -> {
                    val source = selectedContactSources.first()
                    VcfImportSource.sSourceName = source.name
                    VcfImportSource.sSourceType = source.type
                }
                in 2..Int.MAX_VALUE -> {
                    VcfImportSource.sSourceName = ""
                    VcfImportSource.sSourceType = ""
                }
                else -> {
                    VcfImportSource.sSourceName = MTS_NONE
                    VcfImportSource.sSourceType = MTS_NONE
                }
            }
            callback()
        }

        dialog?.dismiss()
    }
}
