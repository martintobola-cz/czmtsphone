package cz.mts.phone.adapters

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import cz.mts.base.adapters.MyRecyclerViewAdapter
import cz.mts.base.databinding.ItemContactWithoutNumberBinding
import cz.mts.base.databinding.ItemContactWithoutNumberGridBinding
import cz.mts.base.dialogs.ConfirmationDialog
import cz.mts.base.extensions.addLockedLabelIfNeeded
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.blockContact
import cz.mts.base.extensions.contactsDB
import cz.mts.base.extensions.getPhoneNumberTypeText
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getTextSize
import cz.mts.base.extensions.highlightTextPart
import cz.mts.base.extensions.isContactBlocked
import cz.mts.base.extensions.launchSendSMSIntent
import cz.mts.base.extensions.normalizeString
import cz.mts.base.extensions.setupViewBackground
import cz.mts.base.extensions.shortcutManager
import cz.mts.base.extensions.toast
import cz.mts.base.extensions.unblockContact
import cz.mts.base.helpers.CONTACTS_GRID_MAX_COLUMNS_COUNT
import cz.mts.base.helpers.FONT_SIZE_EXTRA_LARGE
import cz.mts.base.helpers.FONT_SIZE_LARGE
import cz.mts.base.helpers.FONT_SIZE_MEDIUM
import cz.mts.base.helpers.FONT_SIZE_SMALL
import cz.mts.base.helpers.LocalContactPhotoStorage
import cz.mts.base.helpers.MTS_PHONE
import cz.mts.base.helpers.isNougatPlus
import cz.mts.base.helpers.isOreoPlus
import cz.mts.base.helpers.PERMISSION_CALL_PHONE
import cz.mts.base.helpers.PERMISSION_WRITE_CONTACTS
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.base.helpers.VIEW_TYPE_GRID
import cz.mts.base.helpers.VIEW_TYPE_LIST
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.interfaces.ItemMoveCallback
import cz.mts.base.interfaces.ItemTouchHelperContract
import cz.mts.base.interfaces.StartReorderDragListener
import cz.mts.base.models.contacts.Contact
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.extensions.areMultipleSIMsAvailable
import cz.mts.phone.interfaces.RefreshItemsListener
import cz.mts.phone.R
import cz.mts.phone.activities.MainActivity
import cz.mts.phone.extensions.startContactDetailsIntentID
import cz.mts.phone.helpers.CacheContacts
import cz.mts.phone.helpers.RecentsQueryLimits
import cz.mts.phone.models.PhonePickerItem
import cz.mts.phone.models.PhoneTypeUi
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ContactsAdapter(
    activity: SimpleActivity,
    var contacts: MutableList<Contact>,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    private var refreshItemsListener: RefreshItemsListener? = null,
    var viewType: Int = VIEW_TYPE_LIST,
    private val showDeleteButton: Boolean = true,
    private val enableDrag: Boolean = false,
    private val allowLongClick: Boolean = true,
    itemClick: (Any) -> Unit,
    val profileIconClick: ((Any) -> Unit)? = null
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick),
    ItemTouchHelperContract, MyRecyclerView.MyZoomListener {

    private var textToHighlight = highlightText
    var fontSize: Float = activity.getTextSize()
    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null
    var onDragEndListener: (() -> Unit)? = null
    var onSpanCountListener: (Int) -> Unit = {}

    init {
        setupDragListener(true)

        if (recyclerView.layoutManager is GridLayoutManager) {
            setupZoomListener(this)
        }

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this))
            touchHelper!!.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_contacts

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val isOneItemSelected = isOneItemSelected()
        val selectedNumber = getSelectedPhoneNumber().orEmpty()

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = false //isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""
            findItem(R.id.cab_delete).isVisible = showDeleteButton
            findItem(R.id.cab_create_shortcut).title = activity.addLockedLabelIfNeeded(R.string.create_shortcut)
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected && isOreoPlus()
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected
            findItem(R.id.cab_view_recents).isVisible = isOneItemSelected
            findItem(R.id.cab_block_unblock_contact).isVisible = isOneItemSelected && isNougatPlus()
            getCabBlockContactTitle { title ->
                findItem(R.id.cab_block_unblock_contact).title = title
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_block_unblock_contact -> tryBlockingUnblocking()
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
          //  R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_view_details -> viewContactDetails()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_view_recents -> viewContactRecentCalls()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun allowHorizontalDragReorder() = viewType == VIEW_TYPE_GRID

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = Binding.getByItemViewType(viewType).inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun getItemViewType(position: Int): Int {
        return viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, true, allowLongClick) { itemView, _ ->
            val viewType = getItemViewType(position)
            setupView(Binding.getByItemViewType(viewType).bind(itemView), contact, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    private fun getCabBlockContactTitle(callback: (String) -> Unit) {
        val contact = getSelectedItems().firstOrNull() ?: return callback("")

        activity.isContactBlocked(contact) { blocked ->
            val cabItemTitleRes = if (blocked) {
                R.string.unblock_contact
            } else {
                R.string.block_contact
            }

            callback(activity.addLockedLabelIfNeeded(cabItemTitleRes))
        }
    }

    private fun tryBlockingUnblocking() {
        val contact = getSelectedItems().firstOrNull() ?: return

        activity.isContactBlocked(contact) { blocked ->
            if (blocked) {
                tryUnblocking(contact)
            } else {
                tryBlocking(contact)
            }
        }
    }

    private fun tryBlocking(contact: Contact) {
        askConfirmBlock(contact) { contactBlocked ->
            val resultMsg = if (contactBlocked) {
                R.string.block_contact_success
            } else {
                R.string.block_contact_fail
            }

            activity.toast(resultMsg)
            finishActMode()
        }
    }

    private fun tryUnblocking(contact: Contact) {
        val contactUnblocked = activity.unblockContact(contact)
        val resultMsg = if (contactUnblocked) {
            R.string.unblock_contact_success
        } else {
            R.string.unblock_contact_fail
        }

        activity.toast(resultMsg)
        finishActMode()
    }

    private fun askConfirmBlock(contact: Contact, callback: (Boolean) -> Unit) {
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), contact.getNameToDisplay())

        ConfirmationDialog(activity, question) {
            val contactBlocked = activity.blockContact(contact)
            callback(contactBlocked)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<Contact>, highlightText: String = "") {
        // FIX: hashCode() → equals() (List.equals porovnává element po elementu, hashCode může kolizovat)
        //původně if (newItems.hashCode() != contacts.hashCode())  // hash collision může přeskočit update
        if (newItems != contacts) {
            contacts = ArrayList(newItems)
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    @SuppressLint("MissingPermission")
    private fun callContact(useSimOne: Boolean) {
        val selContact = getSelectedContact() ?: return
        mtsGlobalAll.showNumberPickerDialog(activity, selContact, useSimOne)
    }

//    private fun removeDefaultSIM() {
//        val phoneNumber = getSelectedPhoneNumber() ?: return
//        activity.config.removeCustomSIM(phoneNumber)
//        finishActMode()
//    }

    private fun sendSMS() {
        resolveSmsRecipients(
            contacts = getSelectedItems(),
            showPicker = { items, onConfirm -> showSelectNumbersDialog(items, onConfirm) },
            onResolved = { numbers -> activity.launchSendSMSIntent(TextUtils.join(";", numbers)) }
        )
    }

    private fun showSelectNumbersDialog(
        items: List<PhonePickerItem>,
        onConfirm: (List<String>) -> Unit
    ) {
        val numberItems = items.filterIsInstance<PhonePickerItem.Number>()

        val hasAnyContactWithMultipleNumbers =
            numberItems
                .groupBy { it.contactId }
                .any { it.value.size > 1 }

        if (!hasAnyContactWithMultipleNumbers) {
            val numbers = numberItems
                .mapNotNull { it.phone.normalizedNumber }
                .distinct()

            if (numbers.isNotEmpty()) {
                onConfirm(numbers)
            }
            return
        }

        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_select_numbers, null)

        val recyclerView = view.findViewById<MyRecyclerView>(R.id.mtsRecyclerView)
        recyclerView.setHasFixedSize(true)

        val adapter = SelectNumbersAdapter(items, true)
        recyclerView.adapter = adapter

        val roundedBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 64f
            setColor(activity.getProperBackgroundColor())
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setNeutralButton(R.string.send_sms) { _, _ ->
                val selectedNumbers = items
                    .filterIsInstance<PhonePickerItem.Number>()
                    .filter { it.isSelected }
                    .mapNotNull { it.phone.normalizedNumber }
                    .distinct()

                if (selectedNumbers.isNotEmpty()) {
                    onConfirm(selectedNumbers)
                }
            }
            .setPositiveButton(android.R.string.cancel, null)
            .create()

        dialog.window?.setBackgroundDrawable(roundedBackground)
        dialog.show()

        // getButton() je dostupné až po show()
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        positiveButton?.setTextColor(activity.getProperPrimaryColor())
        neutralButton?.setTextColor(activity.getProperPrimaryColor())
        positiveButton?.isAllCaps = false
        neutralButton?.isAllCaps = false
        positiveButton?.setTypeface(positiveButton.typeface, Typeface.BOLD)
        neutralButton?.setTypeface(neutralButton.typeface, Typeface.BOLD)
    }

    fun resolveSmsRecipients(
        contacts: List<Contact>,
        showPicker: (List<PhonePickerItem>, (List<String>) -> Unit) -> Unit,
        onResolved: (List<String>) -> Unit
    ) {
        if (contacts.isEmpty()) return

        if (contacts.all { it.phoneNumbers.size == 1 }) {
            val numbers = contacts
                .mapNotNull { it.phoneNumbers.first().normalizedNumber }
                .distinct()

            if (numbers.isNotEmpty()) {
                onResolved(numbers)
            }
            return
        }

        val pickerItems = mutableListOf<PhonePickerItem>()

        contacts.forEach { contact ->
            pickerItems += PhonePickerItem.Header(
                contactId = contact.id,
                contactName = contact.getNameToDisplay()
            )

            val numbers = contact.phoneNumbers
            var primaryAlreadyUsed = false

            numbers.forEach { phone ->
                val isSelected = when {
                    numbers.size == 1 -> true
                    phone.isPrimary && !primaryAlreadyUsed -> {
                        primaryAlreadyUsed = true
                        true
                    }
                    else -> false
                }

                pickerItems += PhonePickerItem.Number(
                    contactId = contact.id,
                    contactName = contact.getNameToDisplay(),
                    phone = phone,
                    type = PhoneTypeUi(
                        rawType = phone.type,
                        label = activity.getPhoneNumberTypeText(phone.type, " ")
                    ),
                    isSelected = isSelected
                )
            }
        }

        showPicker(pickerItems) { selected ->
            if (selected.isNotEmpty()) {
                onResolved(selected.distinct())
            }
        }
    }

    private fun viewContactDetails() {
        val contact = getSelectedItems().firstOrNull() ?: return
        activity.startContactDetailsIntentID(contact.rawId.toLong(), contact.source)
    }

    private fun viewContactRecentCalls() {
        val contact = getSelectedItems().firstOrNull() ?: return
        val mainActivity = activity as? MainActivity ?: return
        mainActivity.showContactCallHistory(contact.getNameToDisplay())
        finishActMode()

    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.getNameToDisplay()}\""
        } else {
            resources.getQuantityString(R.plurals.delete_contacts, itemsCnt, itemsCnt)
        }

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
                deleteContacts()
            }
        }
    }

    private fun deleteContacts() {
        if (selectedKeys.isEmpty()) return

        val progress = activity.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(
            R.id.progress_indicator_main
        )

        activity.runOnUiThread {
            CacheContacts.bAnimateRunnig = true
            progress?.beVisible()
        }

        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()

        val idsToRemove = contactsToRemove
            .filter { it.source != MTS_PHONE }
            .mapNotNull { it.rawId }
            .toCollection(ArrayList())

        val photoUrisToRemove = contactsToRemove
            .filter { it.source == MTS_PHONE }
            .map { it.photoUri }
            .filter { it.isNotEmpty() }

        val idsToRemoveLocal = contactsToRemove
            .filter { it.source == MTS_PHONE }
            .mapNotNull { it.id }
            .toCollection(ArrayList())

        // FIX: AtomicInteger místo plain Int – přistupuje se z více vláken (background + callbacky)
        val pendingOps = AtomicInteger(0)

        fun onOperationFinished() {
            if (pendingOps.decrementAndGet() == 0) {
                activity.runOnUiThread {
                    contacts.removeAll(contactsToRemove.toSet())
                    removeSelectedItems(positions)
                    finishActMode()
                    CacheContacts.bAnimateRunnig = false
                    progress?.beGone()
                    CacheContacts.bInvalidateCache = true
                    RecentsQueryLimits.setRefreshState(true)
                    refreshItemsListener?.refreshItems() { refreshItemsListener?.refreshSearch() }
                }
            }
        }

        ensureBackgroundThread {
            if (idsToRemove.isNotEmpty()) {
                pendingOps.incrementAndGet()
                SimpleContactsHelper(activity).deleteContactRawIDs(idsToRemove) {
                    onOperationFinished()
                }
            }

            if (idsToRemoveLocal.isNotEmpty()) {
                pendingOps.incrementAndGet()
                idsToRemoveLocal.filter { it > 0 }.distinct()
                    .forEach { id -> activity.contactsDB.deleteContactId(id) }
                photoUrisToRemove.forEach { uri -> LocalContactPhotoStorage.delete(activity, uri) }
                onOperationFinished()
            }

            // obě prázdné → přímé ukončení bez čítače
            if (pendingOps.get() == 0) {
                activity.runOnUiThread {
                    CacheContacts.bAnimateRunnig = false
                    progress?.beGone()
                    contacts.removeAll(contactsToRemove.toSet())
                    removeSelectedItems(positions)
                    finishActMode()
                }
            }
        }
    }

    private fun getSelectedItems(): ArrayList<Contact> =
        contacts.filterTo(ArrayList()) { selectedKeys.contains(it.rawId) }

    private fun getSelectedPhoneNumber(): String? =
        getSelectedItems().firstOrNull()?.getPrimaryNumber()

    private fun getSelectedContact(): Contact? =
        getSelectedItems().firstOrNull()

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val contact = contacts.firstOrNull { selectedKeys.contains(it.rawId) } ?: return
        val manager = activity.shortcutManager
        if (manager.isRequestPinShortcutSupported) {
            if (contact.phoneNumbers.size > 1) {
                showNumberPickerDialog(contact) { selectedNumber ->
                    proceedWithShortcutCreation(contact, selectedNumber)
                }
            } else {
                val number = contact.phoneNumbers.firstOrNull()?.value ?: return
                proceedWithShortcutCreation(contact, number)
            }
        }
    }

    private fun proceedWithShortcutCreation(contact: Contact, phoneNumber: String) {
        val manager = activity.shortcutManager
        SimpleContactsHelper(activity).getShortcutImage(contact.photoUri, contact.getNameToDisplay()) { image ->
            activity.runOnUiThread {
                activity.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                    val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                    val intent = Intent(action).apply {
                        data = Uri.fromParts("tel", phoneNumber, null)
                    }

                    val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                        .setShortLabel(contact.getNameToDisplay())
                        .setIcon(Icon.createWithBitmap(image))
                        .setIntent(intent)
                        .build()

                    manager.requestPinShortcut(shortcut, null)
                }
            }
        }
    }

    private fun showNumberPickerDialog(contact: Contact, onNumberSelected: (String) -> Unit) {
        val items = buildPhonePickerItems(contact)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_select_numbers, null)
        view.findViewById<MyRecyclerView>(R.id.mtsRecyclerView).apply {
            setHasFixedSize(true)
            adapter = SelectNumbersAdapter(
                items,
                allowMultipleNumbersPerContact = false
            )
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selected = items
                    .filterIsInstance<PhonePickerItem.Number>()
                    .firstOrNull { it.isSelected }
                selected?.let { numberItem ->
                    onNumberSelected(numberItem.phone.value)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 64f
                setColor(activity.getProperBackgroundColor())
            }
        )
        dialog.show()

        val primaryColor = activity.getProperPrimaryColor()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(primaryColor)
            setTypeface(typeface, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(primaryColor)
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    /**
     * Sestaví seznam položek pro dialog výběru čísla.
     * Poznámka: tato metoda se volá jen při size > 1,
     */
    private fun buildPhonePickerItems(contact: Contact): List<PhonePickerItem> {
        val items = mutableListOf<PhonePickerItem>()

        items += PhonePickerItem.Header(
            contactId = contact.id,
            contactName = contact.getNameToDisplay()
        )

        var primaryAlreadyUsed = false

        contact.phoneNumbers.forEach { phone ->
            val isSelected = phone.isPrimary && !primaryAlreadyUsed
            if (isSelected) primaryAlreadyUsed = true

            items += PhonePickerItem.Number(
                contactId = contact.id,
                contactName = contact.getNameToDisplay(),
                phone = phone,
                type = PhoneTypeUi(
                    rawType = phone.type,
                    label = activity.getPhoneNumberTypeText(phone.type, " ")
                ),
                isSelected = isSelected
            )
        }

        return items
    }


    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Binding.getByItemViewType(holder.itemViewType).bind(holder.itemView).apply {
                Glide.with(activity).clear(itemContactImage)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(binding: ItemViewBinding, contact: Contact, holder: ViewHolder) {
        binding.apply {
            root.setupViewBackground(activity)
            itemContactFrame.isSelected = selectedKeys.contains(contact.rawId)

            itemContactImage.apply {
                val iconSizePx = when (activity.config.fontSize) {
                    FONT_SIZE_SMALL ->
                        resources.getDimensionPixelSize(cz.mts.base.R.dimen.s_small_icon_size)
                    FONT_SIZE_MEDIUM ->
                        resources.getDimensionPixelSize(cz.mts.base.R.dimen.l_middle_icon_size)
                    FONT_SIZE_LARGE ->
                        resources.getDimensionPixelSize(cz.mts.base.R.dimen.xl_big_icon_size)
                    FONT_SIZE_EXTRA_LARGE ->
                        resources.getDimensionPixelSize(cz.mts.base.R.dimen.xxl_extrabig_icon_size)
                    else ->
                        resources.getDimensionPixelSize(cz.mts.base.R.dimen.xl_big_icon_size)
                }

                layoutParams = layoutParams.apply {
                    width = iconSizePx
                    height = iconSizePx
                }
                requestLayout()

                if (profileIconClick != null && viewType != VIEW_TYPE_GRID) {
                    setBackgroundResource(R.drawable.selector_clickable_circle)

                    setOnClickListener {
                        if (!actModeCallback.isSelectable) {
                            profileIconClick.invoke(contact)
                        } else {
                            holder.viewClicked(contact)
                        }
                    }
                    setOnLongClickListener {
                        holder.viewLongClicked()
                        true
                    }
                }
            }

            itemContactName.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                val name = contact.getNameToDisplay()
                text = if (textToHighlight.isEmpty()) {
                    name
                } else {
                    val normalizedName = name.normalizeString()
                    val normalizedSearchText = textToHighlight.normalizeString()
                    if (normalizedName.contains(normalizedSearchText, true)) {
                        name.highlightTextPart(textToHighlight, properPrimaryColor)
                    } else {
                        name
                    }
                }
            }

            if (enableDrag && textToHighlight.isEmpty()) {
                dragHandleIcon.apply {
                    beVisibleIf(selectedKeys.isNotEmpty())
                    applyColorFilter(textColor)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            startReorderDragListener?.requestDrag(holder)
                        }
                        false
                    }
                }
            } else {
                dragHandleIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }
            }

            if (!activity.isDestroyed) {
                SimpleContactsHelper(root.context).loadContactImage(
                    contact.photoUri,
                    itemContactImage,
                    contact.getNameToDisplay()
                )
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        activity.config.isCustomOrderSelected = true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contacts, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contacts, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        onDragEndListener?.invoke()
    }

    override fun zoomIn() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpanCount = layoutManager.spanCount
            val newSpanCount = (currentSpanCount - 1).coerceIn(1, CONTACTS_GRID_MAX_COLUMNS_COUNT)
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()
            onSpanCountListener(newSpanCount)
        }
    }

    override fun zoomOut() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpanCount = layoutManager.spanCount
            val newSpanCount = (currentSpanCount + 1).coerceIn(1, CONTACTS_GRID_MAX_COLUMNS_COUNT)
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()
            onSpanCountListener(newSpanCount)
        }
    }

    private sealed interface Binding {
        companion object {
            fun getByItemViewType(viewType: Int): Binding {
                return when (viewType) {
                    VIEW_TYPE_GRID -> ItemContactGrid
                    else -> ItemContact
                }
            }
        }

        fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding
        fun bind(view: View): ItemViewBinding

        data object ItemContactGrid : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactGridBindingAdapter(ItemContactWithoutNumberGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactGridBindingAdapter(ItemContactWithoutNumberGridBinding.bind(view))
            }
        }

        data object ItemContact : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactBindingAdapter(ItemContactWithoutNumberBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactBindingAdapter(ItemContactWithoutNumberBinding.bind(view))
            }
        }
    }

    private interface ItemViewBinding : ViewBinding {
        val itemContactName: TextView
        val itemContactImage: ImageView
        val itemContactFrame: ConstraintLayout
        val dragHandleIcon: ImageView
    }

    private class ItemContactGridBindingAdapter(val binding: ItemContactWithoutNumberGridBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override fun getRoot(): View = binding.root
    }

    private class ItemContactBindingAdapter(val binding: ItemContactWithoutNumberBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override fun getRoot(): View = binding.root
    }
}
