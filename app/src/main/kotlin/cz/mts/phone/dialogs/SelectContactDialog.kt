package cz.mts.phone.dialogs

import android.graphics.Color
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.adapters.ContactsAdapter
import cz.mts.phone.databinding.DialogSelectContactBinding
import cz.mts.phone.extensions.setupWithContacts
import cz.mts.base.extensions.beInvisible
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.getColorStateList
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.extensions.viewBinding
import cz.mts.base.models.contacts.Contact
import cz.mts.base.views.MySearchMenu
import cz.mts.base.extensions.hideKeyboard
import cz.mts.base.extensions.isGone
import cz.mts.base.extensions.normalizeString

class SelectContactDialog(
    private val activity: SimpleActivity,
    private val contacts: List<Contact>,
    private val callback: (selectedContact: Contact) -> Unit
) {
    private val binding by activity.viewBinding(DialogSelectContactBinding::inflate)

    private var dialog: AlertDialog? = null

    init {
        binding.apply {
            letterFastscroller.textColor = activity.getProperTextColor().getColorStateList()
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = activity.getProperPrimaryColor().getContrastColor()
            letterFastscrollerThumb.thumbColor = activity.getProperPrimaryColor().getColorStateList()

            setupLetterFastScroller(contacts)
            configureSearchView()

            selectContactList.adapter = ContactsAdapter(
                activity = activity,
                contacts = contacts.toMutableList(),
                recyclerView = selectContactList,
                highlightText = "",
                allowLongClick = false,
                itemClick = {
                    callback(it as Contact)
                    dialog?.dismiss()
                }
            )
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(R.string.cancel, null)
            .setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    backPressed()
                    true
                } else {
                    false
                }
            }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.choose_contact) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        binding.letterFastscroller.setupWithContacts(binding.selectContactList, contacts)
    }

    private fun configureSearchView() = with(binding.contactSearchView) {
        updateHintText(context.getString(R.string.search_contacts))
        binding.topToolbarSearch.imeOptions = EditorInfo.IME_ACTION_DONE
        setupMenu()
        setSearchViewListeners()
        updateSearchViewUi()
    }

    private fun MySearchMenu.updateSearchViewUi() {
        requireToolbar().beInvisible()
        updateColors()
        setBackgroundColor(Color.TRANSPARENT)
        binding.searchBarContainer.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun MySearchMenu.setSearchViewListeners() {
        onSearchOpenListener = {
            updateSearchViewLeftIcon(R.drawable.ic_cross_vector)
        }
        onSearchClosedListener = {
            binding.topToolbarSearch.clearFocus()
            activity.hideKeyboard(binding.topToolbarSearch)
            updateSearchViewLeftIcon(R.drawable.ic_search_vector)
        }
        onSearchTextChangedListener = { text ->
            filterContactListBySearchQuery(text)
        }
    }

    private fun updateSearchViewLeftIcon(iconResId: Int) =
        with(binding.root.findViewById<ImageView>(R.id.top_toolbar_search_icon)) {
            post { setImageResource(iconResId) }
        }

    private fun filterContactListBySearchQuery(query: String) {
        val adapter = binding.selectContactList.adapter as? ContactsAdapter ?: return
        val trimmedQuery = query.trim()

        val filteredContacts = if (trimmedQuery.isEmpty()) {
            contacts
        } else {
            val normalizedQuery = trimmedQuery.normalizeString()
            contacts.filter { contact ->
                contact.getNameToDisplay().normalizeString().contains(normalizedQuery, ignoreCase = true)
            }
        }

        checkPlaceholderVisibility(filteredContacts)
        adapter.updateItems(filteredContacts, highlightText = trimmedQuery)

        // FastScroller zobrazujeme pouze bez aktivního filtru
        if (trimmedQuery.isEmpty()) {
            setupLetterFastScroller(contacts)
            binding.letterFastscroller.beVisible()
            binding.letterFastscrollerThumb.beVisible()
        } else {
            binding.letterFastscroller.beInvisible()
            binding.letterFastscrollerThumb.beInvisible()
        }

        binding.selectContactList.scrollToPosition(0)
    }

    private fun checkPlaceholderVisibility(contacts: List<Contact>) = with(binding) {
        contactsEmptyPlaceholder.beVisibleIf(contacts.isEmpty())

        if (contactSearchView.isSearchOpen) {
            contactsEmptyPlaceholder.text = activity.getString(R.string.no_items_found)
        }

        letterFastscroller.beVisibleIf(contactsEmptyPlaceholder.isGone())
        letterFastscrollerThumb.beVisibleIf(contactsEmptyPlaceholder.isGone())
    }

    private fun backPressed() {
        if (binding.contactSearchView.isSearchOpen) {
            binding.contactSearchView.closeSearch()
        } else {
            dialog?.dismiss()
        }
    }
}
