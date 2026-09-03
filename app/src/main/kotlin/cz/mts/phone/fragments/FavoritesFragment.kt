package cz.mts.phone.fragments

import android.content.Context
import android.util.AttributeSet
import com.google.gson.Gson
import cz.mts.base.adapters.MyRecyclerViewAdapter
import cz.mts.base.extensions.areSystemAnimationsEnabled
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.getColorStateList
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.hasPermission
import cz.mts.base.extensions.normalizeString
import cz.mts.base.helpers.Converters
import cz.mts.base.helpers.PERMISSION_READ_CONTACTS
import cz.mts.base.helpers.VIEW_TYPE_GRID
import cz.mts.base.models.contacts.Contact
import cz.mts.base.views.MyGridLayoutManager
import cz.mts.base.views.MyLinearLayoutManager
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.adapters.ContactsAdapter
import cz.mts.phone.databinding.FragmentFavoritesBinding
import cz.mts.phone.databinding.FragmentLettersLayoutBinding
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.setupWithContacts
import cz.mts.phone.extensions.startContactDetailsIntentID
import cz.mts.phone.helpers.CacheContacts
import cz.mts.phone.interfaces.RefreshItemsListener

class FavoritesFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.LettersInnerBinding>(context, attributeSet),
    RefreshItemsListener {

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val gson = Gson()
    }

    private lateinit var binding: FragmentLettersLayoutBinding
    private var allContacts = ArrayList<Contact>()
    private var sSearchText = ""

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentLettersLayoutBinding.bind(FragmentFavoritesBinding.bind(this).favoritesFragment)
        innerBinding = LettersInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        binding.fragmentPlaceholder.text = context.getString(placeholderResId)
        binding.fragmentPlaceholder2.beGone()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.apply {
            fragmentPlaceholder.setTextColor(textColor)
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)

            letterFastscroller.textColor = textColor.getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        CacheContacts.getCachedContacts(context) { contacts ->
            val currentActivity = activity ?: return@getCachedContacts
            val favorites = contacts.filterTo(ArrayList()) { it.starred == 1 }

            allContacts = if (currentActivity.config.isCustomOrderSelected) {
                sortByCustomOrder(favorites, currentActivity)
            } else {
                favorites
            }

            activity?.runOnUiThread {
                gotContacts(allContacts)
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
        binding.apply {
            if (contacts.isEmpty()) {
                fragmentPlaceholder.beVisible()
                fragmentList.beGone()
            } else {
                fragmentPlaceholder.beGone()
                fragmentList.beVisible()
                updateListAdapter()
            }
        }
    }

    private fun updateListAdapter() {
        val currentActivity = activity ?: return
        val viewType = context.config.viewType
        setViewType(viewType)

        val currAdapter = binding.fragmentList.adapter as? ContactsAdapter
        if (currAdapter == null) {
            ContactsAdapter(
                activity = currentActivity,
                contacts = allContacts,
                recyclerView = binding.fragmentList,
                refreshItemsListener = this,
                viewType = viewType,
                showDeleteButton = false,
                enableDrag = true,
                itemClick = {
                    mtsGlobalAll.showNumberPickerDialog(activity, it as Contact, -2)
                },
                profileIconClick = {
                    val contactTemp = it as Contact
                    activity?.startContactDetailsIntentID(contactTemp.rawId.toLong(), contactTemp.source)
                }
            ).apply {
                binding.fragmentList.adapter = this

                onDragEndListener = {
                    val adapter = binding.fragmentList.adapter
                    if (adapter is ContactsAdapter) {
                        val items = adapter.contacts
                        saveCustomOrderToPrefs(items)
                        setupLetterFastScroller(items)
                    }
                }

                onSpanCountListener = { newSpanCount ->
                    context.config.contactsGridColumnCount = newSpanCount
                }
            }

            if (context.areSystemAnimationsEnabled) {
                binding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            currAdapter.viewType = viewType
            currAdapter.updateItems(allContacts)
        }
    }

    fun columnCountChanged() {
        (binding.fragmentList.layoutManager as? MyGridLayoutManager)?.spanCount =
            context.config.contactsGridColumnCount
        binding.fragmentList.adapter?.notifyItemRangeChanged(0, allContacts.size)
    }

    private fun sortByCustomOrder(
        favorites: List<Contact>,
        currentActivity: SimpleActivity
    ): ArrayList<Contact> {
        val favoritesOrder = currentActivity.config.favoritesContactsOrder

        if (favoritesOrder.isEmpty()) {
            return ArrayList(favorites)
        }

        val orderList = Converters().jsonToStringList(favoritesOrder)
        val map = orderList.withIndex().associate { it.value to it.index }
        val sorted = favorites.sortedBy { map[it.contactId.toString()] }

        return ArrayList(sorted)
    }

    private fun saveCustomOrderToPrefs(items: List<Contact>) {
        activity?.apply {
            val orderIds = items.map { it.contactId }
            config.favoritesContactsOrder = gson.toJson(orderIds)
            allContacts = ArrayList(items)
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
        sSearchText = text

        val fixedText = text
            .trim()
            .replace(WHITESPACE_REGEX, " ")

        val fixedTextNormalized = fixedText.normalizeString()

        val contacts = allContacts
            .map { contact -> contact to contact.getNameToDisplay().normalizeString() }
            .filter { (contact, normalizedName) ->
                normalizedName.contains(fixedTextNormalized, ignoreCase = true) ||
                    (fixedTextNormalized.toLongOrNull() != null &&
                        contact.doesContainPhoneNumber(fixedTextNormalized))
            }
            .sortedByDescending { (_, normalizedName) ->
                normalizedName.startsWith(fixedTextNormalized, ignoreCase = true)
            }
            .mapTo(ArrayList()) { it.first }

        binding.fragmentPlaceholder.beVisibleIf(contacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(contacts, fixedText)
        setupLetterFastScroller(contacts)
    }

    private fun setViewType(viewType: Int) {
        val spanCount = context.config.contactsGridColumnCount

        val layoutManager = if (viewType == VIEW_TYPE_GRID) {
            binding.letterFastscroller.beGone()
            MyGridLayoutManager(context, spanCount)
        } else {
            binding.letterFastscroller.beVisible()
            MyLinearLayoutManager(context)
        }
        binding.fragmentList.layoutManager = layoutManager
    }
}
