package cz.mts.phone.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import cz.mts.base.adapters.MyRecyclerViewAdapter
import cz.mts.base.extensions.areSystemAnimationsEnabled
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.getColorStateList
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.hasPermission
import cz.mts.base.extensions.normalizeString
import cz.mts.base.extensions.underlineText
import cz.mts.base.helpers.MY_APP_NAME_GOOGLE_ID
import cz.mts.base.helpers.PERMISSION_READ_CONTACTS
import cz.mts.base.helpers.getProperText
import cz.mts.base.models.contacts.Contact
import cz.mts.phone.R
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.adapters.ContactsAdapter
import cz.mts.phone.databinding.FragmentContactsBinding
import cz.mts.phone.databinding.FragmentLettersLayoutBinding
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.launchCreateNewContactIntent
import cz.mts.phone.extensions.setupWithContacts
import cz.mts.phone.extensions.startContactDetailsIntentID
import cz.mts.phone.helpers.CacheContacts
import cz.mts.phone.interfaces.RefreshItemsListener

class ContactsFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.LettersInnerBinding>(context, attributeSet),
    RefreshItemsListener {

    private lateinit var binding: FragmentLettersLayoutBinding
    private var allContacts = ArrayList<Contact>()
    private var sSearchText = ""

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentLettersLayoutBinding.bind(FragmentContactsBinding.bind(this).contactsFragment)
        innerBinding = LettersInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }
        binding.fragmentPlaceholder.text = context.getString(placeholderResId)

        val placeholderActionResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.create_new_contact
        } else {
            R.string.request_access
        }

        binding.fragmentPlaceholder2.apply {
            text = context.getString(placeholderActionResId)
            underlineText()
            setOnClickListener {
                if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
                    activity?.launchCreateNewContactIntent()
                } else {
                    requestReadContactsPermission()
                }
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.apply {
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)
            fragmentPlaceholder.setTextColor(textColor)
            fragmentPlaceholder2.setTextColor(properPrimaryColor)

            letterFastscroller.textColor = textColor.getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        CacheContacts.getCachedContacts(context) { contacts ->
            allContacts = contacts
            activity?.runOnUiThread {
                gotContacts(contacts)
                callback?.invoke()
            }
        }
    }

    override fun refreshSearch() {
        activity?.runOnUiThread {
            onSearchQueryChanged(sSearchText)
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        setupLetterFastScroller(contacts)

        if (contacts.isEmpty()) {
            binding.apply {
                fragmentPlaceholder.beVisible()
                fragmentPlaceholder2.beVisible()
                fragmentList.beGone()
            }
            return
        }

        binding.apply {
            fragmentPlaceholder.beGone()
            fragmentPlaceholder2.beGone()
            fragmentList.beVisible()
        }

        if (binding.fragmentList.adapter == null) {
            val currentActivity = activity ?: return

            ContactsAdapter(
                activity = currentActivity,
                contacts = contacts,
                recyclerView = binding.fragmentList,
                refreshItemsListener = this,
                itemClick = {
                    mtsGlobalAll.showNumberPickerDialog(activity, it as Contact, -2)
                },
                profileIconClick = {
                    val contactTemp = it as Contact
                    activity?.startContactDetailsIntentID(contactTemp.rawId.toLong(), contactTemp.source)
                }
            ).apply {
                binding.fragmentList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                binding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(contacts)
        }
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        binding.letterFastscroller.setupWithContacts(binding.fragmentList, contacts)
    }

    override fun onSearchClosed() {
        sSearchText = ""
        binding.fragmentPlaceholder.beVisibleIf(allContacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastScroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        // Speciální interní filtr – zobrazí pouze kontakty bez telefonního čísla
        if (text == "MTS_bFilterOnlyContactsWithoutNumbers") {
            val filtered = allContacts.filterTo(ArrayList()) { it.phoneNumbers.isEmpty() }
            binding.fragmentPlaceholder.beVisibleIf(filtered.isEmpty())
            (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(filtered, "")
            setupLetterFastScroller(filtered)
            return
        }

        sSearchText = text
        val fixedText = text.trim().replace("\\s+".toRegex(), " ")
        val fixedTextNormalized = fixedText.normalizeString()
        val shouldNormalize = true //fixedText.normalizeString() == fixedText  Tomáš a tomas budu povazovat za totez, proto zakomentováno
        val filtered = allContacts.filter { contact ->
            val nameMatch =
                getProperText(contact.getNameToDisplay(), shouldNormalize).contains(fixedTextNormalized, true) ||
                    getProperText(contact.nickname, shouldNormalize).contains(fixedTextNormalized, true) ||
                    (fixedText.toLongOrNull() != null && contact.doesContainPhoneNumber(fixedText, true))

            if (!nameMatch && context.config.searchInAllContactFields) {
                contact.emails.any { it.value.contains(fixedTextNormalized, true) } ||
                    contact.addresses.any { getProperText(it.value, shouldNormalize).contains(fixedTextNormalized, true) } ||
                    contact.IMs.any { it.value.contains(fixedTextNormalized, true) } ||
                    getProperText(contact.notes, shouldNormalize).contains(fixedTextNormalized, true) ||
                    getProperText(contact.organization.company, shouldNormalize).contains(fixedTextNormalized, true) ||
                    getProperText(contact.organization.jobPosition, shouldNormalize).contains(fixedTextNormalized, true) ||
                    contact.websites.any { it.contains(fixedTextNormalized, true) }
            } else {
                nameMatch

            }
        }.toCollection(ArrayList())

        filtered.sortBy {
            val nameToDisplay = it.getNameToDisplay()
            val properName = getProperText(nameToDisplay, shouldNormalize)
            !properName.startsWith(fixedText, true) &&
                !properName.contains(fixedText, true)
//            !getProperText(nameToDisplay, shouldNormalize).startsWith(fixedText, true) && !nameToDisplay.contains(fixedText, true)
        }


        binding.fragmentPlaceholder.beVisibleIf(filtered.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(filtered, fixedText)
        setupLetterFastScroller(filtered)
    }

    private fun requestReadContactsPermission() {
        activity?.handlePermission(PERMISSION_READ_CONTACTS) { granted ->
            if (granted) {
                binding.fragmentPlaceholder.text = context.getString(R.string.no_contacts_found)
                binding.fragmentPlaceholder2.text = context.getString(R.string.create_new_contact)
                CacheContacts.getCachedContacts(context) { contacts ->
                    activity?.runOnUiThread { gotContacts(contacts) }
                }
            } else if (!context.hasPermission(PERMISSION_READ_CONTACTS)) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", MY_APP_NAME_GOOGLE_ID, null)
                }
                context.startActivity(intent)
            }
        }
    }
}
