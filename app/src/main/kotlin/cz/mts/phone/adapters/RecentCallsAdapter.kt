package cz.mts.phone.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.PorterDuff
import android.provider.CallLog.Calls
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bumptech.glide.Glide
import cz.mts.base.adapters.MyRecyclerViewListAdapter
import cz.mts.base.dialogs.ConfirmationDialog
import cz.mts.base.extensions.addBlockedNumber
import cz.mts.base.extensions.addLockedLabelIfNeeded
import cz.mts.base.extensions.adjustAlpha
import cz.mts.base.extensions.adjustForContrast
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.copyToClipboard
import cz.mts.base.extensions.formatDateOrTime
import cz.mts.base.extensions.formatSecondsToShortTimeString
import cz.mts.base.extensions.formatTime
import cz.mts.base.extensions.getColoredDrawableWithColor
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getPopupMenuTheme
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.getTextSize
import cz.mts.base.extensions.highlightTextPart
import cz.mts.base.extensions.launchActivityIntent
import cz.mts.base.extensions.launchSendSMSIntent
import cz.mts.base.extensions.normalizeString
import cz.mts.base.extensions.setupViewBackground
import cz.mts.base.extensions.toDayCode
import cz.mts.base.extensions.toast
//import cz.mts.base.helpers.Clipboard.copyTextToClipboard
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.helpers.FONT_SIZE_EXTRA_LARGE
import cz.mts.base.helpers.FONT_SIZE_LARGE
import cz.mts.base.helpers.FONT_SIZE_MEDIUM
import cz.mts.base.helpers.FONT_SIZE_SMALL
import cz.mts.base.helpers.isNougatPlus
import cz.mts.base.helpers.KEY_PHONE
import cz.mts.base.helpers.PERMISSION_WRITE_CALL_LOG
import cz.mts.base.helpers.PhoneNumberHelper.getLocationSafeForUI
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.base.models.contacts.Contact
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.databinding.ItemRecentCallBinding
import cz.mts.phone.databinding.ItemRecentsDateBinding
import cz.mts.phone.dialogs.ShowGroupedCallsDialog
import cz.mts.phone.extensions.areMultipleSIMsAvailable
import cz.mts.phone.helpers.RecentsHelper
import cz.mts.phone.models.CallLogItem
import cz.mts.phone.models.RecentCall
import cz.mts.phone.R
import cz.mts.phone.activities.MainActivity
import cz.mts.phone.extensions.startContactDetailsIntentY
import cz.mts.phone.helpers.CacheContacts
import cz.mts.phone.helpers.RecentsQueryLimits
import cz.mts.phone.helpers.SentSmsRecord
import cz.mts.phone.helpers.SmsHistoryManager
import cz.mts.phone.helpers.getCallFilterInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


class RecentCallsAdapter(
    private val compactMode: Boolean = false,
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val showOverflowMenu: Boolean,
    itemClick: (Any) -> Unit,
    private val itemDelete: (List<CallLogItem>) -> Unit,
    val profileIconClick: ((Any) -> Unit)? = null,


) : MyRecyclerViewListAdapter<CallLogItem>(activity, recyclerView, RecentCallsDiffCallback(), itemClick) {

    companion object {
        private const val VIEW_TYPE_DATE = 0
        private const val VIEW_TYPE_CALL = 1
        private const val PAYLOAD_DAY_CHANGE = "PAYLOAD_DAY_CHANGE"
        private const val PAYLOAD_HIGHLIGHT = "PAYLOAD_HIGHLIGHT"

    }
    fun getSmsRecord(callId: Int): SentSmsRecord? = smsRecordByCallId[callId]
    // Na úrovni adaptéru – načti jednou a spotřebovávej
    private var availableSmsRecords = SmsHistoryManager.getAll(activity).toMutableList()
    private var smsRecordByCallId = HashMap<Int, SentSmsRecord>()
    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private var missedCallColor = resources.getColor(R.color.color_missed_call)
    private var secondaryTextColor = textColor.adjustAlpha(0.6f)
    private var textToHighlight = ""
    private var durationPadding = resources.getDimension(R.dimen.normal_margin).toInt()
    private val cachedSimColors = HashMap<Pair<Int,Int>, Int>()
    private var todayCode: String = LocalDate.now().toDayCode()
    private var yesterdayCode: String = LocalDate.now().minusDays(1).toDayCode()




    init {
        initDrawables()
        setupDragListener(true)
        setHasStableIds(true)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val selectedItems = getSelectedItems()
        val isOneItemSelected = selectedItems.size == 1
        val contact = if (isOneItemSelected) CacheContacts.findContactByCall(selectedItems.first())
                      else null
      //  val selectedNumber = "tel:${getSelectedPhoneNumber()}"

        menu.apply {
            findItem(R.id.cab_show_call_details).isVisible = isOneItemSelected
            findItem(R.id.cab_view_recents).isVisible = true
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = false //isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""
            findItem(R.id.cab_block_number).title = activity.addLockedLabelIfNeeded(R.string.block_number)
            findItem(R.id.cab_block_number).isVisible = isNougatPlus()
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected && contact == null
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected && contact != null
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_show_call_details -> showCallDetails()
            R.id.cab_view_recents -> viewContactRecentCalls()
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
      //MTSXXX      R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_block_number -> tryBlocking()
            R.id.cab_add_number -> addNumberToContact()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_copy_number -> copyNumber()
            R.id.cab_remove -> askConfirmRemove()
            R.id.cab_select_all -> selectAll()
            R.id.cab_view_details -> launchContactDetailsIntent(getSelectedItems().first())
        }
    }

    private fun viewContactRecentCalls() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        val contact = CacheContacts.findContactByCall(recentCall)
        viewContactRecentCalls(contact, recentCall)
        finishActMode()
    }

    private fun viewContactRecentCalls(contact: Contact?, recentCall : RecentCall) {
        val sNameOrNumber = if (recentCall.isUnknownNumber) recentCall.name
                            else contact?.getNameToDisplay() ?: recentCall.phoneNumber
        val mainActivity = activity as? MainActivity ?: return
        if (contact != null) mainActivity.showContactCallHistory(sNameOrNumber) //jméno
        else mainActivity.viewContactInRecents(sNameOrNumber) //číslo
    }

    private fun checkForSpam() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        val sNumber = recentCall.phoneNumber
        val start = System.nanoTime()
        getCallFilterInfo(activity.applicationContext, sNumber) { result ->
                if (result != null) {
                    val end = System.nanoTime()
                    val durationMs = (end - start) / 1_000_000
                    var sResult = result.toString().replaceFirst("CallFilterResult(", "")
                    sResult = sResult.replace(", ", "\n")
                    sResult = sResult.replace(")", "")
                    mtsGlobalAll.showMyAlertDialog(
                        activity,
                        "IN:\n" + sNumber + "\n\nOUT:\n" + sResult + "\n\nDuration:\n" + durationMs.toString() + " ms",
                        result.source,
                        true,
                        0
                    )

                }
        }
    }

    override fun getItemId(position: Int): Long {
        return currentList.getOrNull(position)?.getItemId()?.toLong() ?: RecyclerView.NO_ID
    }

    override fun getItemViewType(position: Int): Int {
        return when (currentList.getOrNull(position)) {
            is CallLogItem.Date -> VIEW_TYPE_DATE
            is RecentCall -> VIEW_TYPE_CALL
            else -> VIEW_TYPE_CALL // fallback
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<RecentCall>().size

    override fun getIsItemSelectable(position: Int) = currentList.getOrNull(position) is RecentCall

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.getItemId()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.getItemId() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}




    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = when (viewType) {
            VIEW_TYPE_DATE -> RecentCallDateViewHolder(
                ItemRecentsDateBinding.inflate(layoutInflater, parent, false)
            )

            VIEW_TYPE_CALL -> RecentCallViewHolder(
                ItemRecentCallBinding.inflate(layoutInflater, parent, false)
            )


            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }

        return viewHolder
    }


    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            when (payloads.firstOrNull()) {

                PAYLOAD_DAY_CHANGE -> {
                    if (holder is RecentCallDateViewHolder) {
                        val item = currentList[position] as? CallLogItem.Date ?: return
                        holder.bind(item)
                    }
                    return
                }

                PAYLOAD_HIGHLIGHT -> {
                    if (holder is RecentCallViewHolder) {
                        holder.updateHighlight(textToHighlight)
                    }
                    return
                }
            }
        }

        onBindViewHolder(holder, position)
    }




    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]

        when (holder) {
            is RecentCallDateViewHolder ->
                holder.bind(item as CallLogItem.Date)

            is RecentCallViewHolder -> {
                holder.bind(item as RecentCall)

                if (compactMode) {
                    holder.binding.itemRecentsName.isVisible = false
                    holder.binding.itemRecentsImage.isVisible = false
                    holder.binding.itemRecentsLocation.isVisible = false
                }
            }
        }

        bindViewHolder(holder)
    }



    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            if (holder is RecentCallViewHolder) {
                Glide.with(activity).clear(holder.binding.itemRecentsImage)
            }
        }
    }

    fun initDrawables() {
        val theme = activity.theme
        missedCallColor = resources.getColor(R.color.color_missed_call, theme)

        val outgoingCallColor = resources.getColor(R.color.color_outgoing_call, theme)
        val incomingCallColor = resources.getColor(R.color.color_incoming_call, theme)
        outgoingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_made_vector, outgoingCallColor)
        incomingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_received_vector, incomingCallColor)
        incomingMissedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_missed_vector, missedCallColor)
    }

    private fun callContact(useSimOne: Boolean) {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        val name = getSelectedName() ?: return
        activity.let { act ->
            mtsGlobalAll.mtsCallRecentCall(act, getSelectedItems().first(), useSimOne)
        }
}

    fun updateTodayCode(now: LocalDate = LocalDate.now()) {
        val newToday = now.toDayCode()
        val newYesterday = now.minusDays(1).toDayCode()

        if (todayCode != newToday || yesterdayCode != newYesterday) {
            todayCode = newToday
            yesterdayCode = newYesterday
            notifyItemRangeChanged(0, itemCount, PAYLOAD_DAY_CHANGE)
        }
    }

    private fun callContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        val name = getSelectedName() ?: return
       // Toast.makeText(activity, "DEBUG2",android.widget.Toast.LENGTH_LONG)
        activity.let { act ->
            mtsGlobalAll.mtsCallRecentCall(act, getSelectedItems().first(), -2, false)
        }
    }


    private fun tryBlocking() {
            val phoneNumber = getSelectedPhoneNumber() ?: return
            askConfirmBlock()
    }

    private fun askConfirmBlock() {
        val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbers)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return

        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

            activity.launchActivityIntent(this)
        }
    }


    private fun sendSMS() {
        val numbers = getSelectedItems().map { normalizeDigitsOnly(it.phoneNumber) }
        val recipient = TextUtils.join(";", numbers)
        if (numbers.toString().isNotBlank()) activity.launchSendSMSIntent(recipient)
    }


    private fun showCallDetails() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        showCallDetails2(recentCall)
    }

    private fun showCallDetails2(call: RecentCall) {
        val grouped = call.groupedCalls
        val callOne: MutableList<RecentCall> = mutableListOf(call)

        if (grouped == null || grouped.size <= 1) {
            ShowGroupedCallsDialog(activity,callOne)
        } else {
            ShowGroupedCallsDialog(activity, grouped)
        }
    }


    private fun copyNumber() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(recentCall.phoneNumber)
        finishActMode()
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        val idsToRemove = HashSet<Int>()

        callsToRemove.forEach { call ->
            idsToRemove.add(call.id)
            call.groupedCalls?.forEach { grouped ->
                idsToRemove.add(grouped.id)
            }
        }

        val newList = currentList.filterNot { item ->
            when (item) {
                is RecentCall -> {
                    idsToRemove.contains(item.id)
                }
                else -> false
            }
        }

        finishActMode()

        RecentsQueryLimits.recentcount -= idsToRemove.size //číselník načtených recents v UI

        RecentsHelper(activity).removeRecentCalls(idsToRemove.toList()) {
            activity.runOnUiThread {
                submitList(newList)
                itemDelete(callsToRemove)
            }
        }
    }

    private fun launchContactDetailsIntent(recent: RecentCall?) {
        if (recent != null) {
            activity.startContactDetailsIntentY(recent)
        }
    }
    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<CallLogItem>, highlightText: String? = null, bForce : Boolean = false) {

        val newHighlight = highlightText ?: this.textToHighlight

        val highlightChanged = if (bForce) true
                               else newHighlight != this.textToHighlight
        this.textToHighlight = newHighlight

        availableSmsRecords = SmsHistoryManager.getAll(activity).toMutableList()
//        if (mtsGlobalAll.iSaveDebugMode == 1) copyTextToClipboard(activity, "",availableSmsRecords.toString())

        rebuildSmsRecordMap(newItems)

        // Vždy provedeme submitList – DiffUtil si ohlídá změny
        submitList(newItems) {
            // Pokud se změnil highlight, musíme rebindnout položky
            if (highlightChanged) {
                recyclerView.post {
                    // jistota, že všechny položky znovu zavolají onBindViewHolder
                    notifyItemRangeChanged(0, itemCount, PAYLOAD_HIGHLIGHT)
                }
            }
        }
        // přesuneš ven, protože jinak to zasahuje do updateItems logiky
        finishActMode()
    }


    private fun getSelectedItems() = currentList.filterIsInstance<RecentCall>()
        .filter { selectedKeys.contains(it.getItemId()) }

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

    private fun getSelectedName() = getSelectedItems().firstOrNull()?.name

    private fun showPopupMenu(view: View, call: RecentCall) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)
        val contact = CacheContacts.findContactByCall(call)
      //  val selectedNumber = "tel:${call.phoneNumber}"

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(R.menu.menu_recent_item_options)
            menu.apply {
                val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
                findItem(R.id.cab_call).isVisible = !areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_show_call_details).isVisible
                findItem(R.id.cab_view_recents).isVisible = true
                findItem(R.id.cab_call_sim_1).isVisible = areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_call_sim_2).isVisible = areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_send_sms).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_view_details).isVisible = contact != null && !call.isUnknownNumber
                findItem(R.id.cab_add_number).isVisible = contact == null && !call.isUnknownNumber //getRecentCallValues(activity, call, true)
                findItem(R.id.cab_copy_number).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_block_number).title = activity.addLockedLabelIfNeeded(R.string.block_number)
                findItem(R.id.cab_block_number).isVisible = isNougatPlus() && !call.isUnknownNumber
                findItem(R.id.cab_check_spam).isVisible = mtsGlobalAll.iSaveDebugMode != 0
                findItem(R.id.cab_remove_default_sim).isVisible = false //(activity.config.getCustomSIM(selectedNumber) ?: "") != "" && !call.isUnknownNumber
            }

            setOnMenuItemClickListener { item ->
                val callId = call.id
                when (item.itemId) {
                    R.id.cab_call -> {
                        executeItemMenuOperation(callId) {
                            callContact()
                        }
                    }

                    R.id.cab_call_sim_1 -> {
                        executeItemMenuOperation(callId) {
                            callContact(true)
                        }
                    }

                    R.id.cab_call_sim_2 -> {
                        executeItemMenuOperation(callId) {
                            callContact(false)
                        }
                    }

                    R.id.cab_send_sms -> {
                        executeItemMenuOperation(callId) {
                            sendSMS()
                        }
                    }

                    R.id.cab_view_details -> {
                        executeItemMenuOperation(callId) {
                            launchContactDetailsIntent(call)
                        }
                    }

                    R.id.cab_view_recents -> {
                        executeItemMenuOperation(callId) {
                            viewContactRecentCalls(contact, call)
                        }
                    }

                    R.id.cab_add_number -> {
                        executeItemMenuOperation(callId) {
                            addNumberToContact()
                        }
                    }

                    R.id.cab_show_call_details -> {
                        executeItemMenuOperation(callId) {
                            showCallDetails()
                        }
                    }

                    R.id.cab_block_number -> {
                        selectedKeys.add(callId)
                        tryBlocking()
                    }

                    R.id.cab_remove -> {
                        selectedKeys.add(callId)
                        askConfirmRemove()
                    }

                    R.id.cab_copy_number -> {
                        executeItemMenuOperation(callId) {
                            copyNumber()
                        }
                    }

                    R.id.cab_check_spam -> {
                        executeItemMenuOperation(callId) {
                            checkForSpam()
                        }
                    }

             //MTSXX       R.id.cab_remove_default_sim -> {
             //           executeItemMenuOperation(callId) {
             //               removeDefaultSIM()
             //           }
             //       }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(callId: Int, callback: () -> Unit) {
        selectedKeys.add(callId)
        callback()
        selectedKeys.remove(callId)
    }


    private inner class RecentCallViewHolder(
        val binding: ItemRecentCallBinding
    ) : ViewHolder(binding.root) {

        private val ctx = binding.root.context
        private val res = ctx.resources
        private val cfg = activity.config
        // Pomocné
        private val contactsHelper = SimpleContactsHelper(ctx)


        // SIM Caching
        private val simDrawable1 =
            AppCompatResources.getDrawable(ctx, R.drawable.ic_sim1)?.mutate()
        private val simDrawable2 =
            AppCompatResources.getDrawable(ctx, R.drawable.ic_sim2)?.mutate()

        private var lastSimId = Int.MIN_VALUE
        private var lastSimColor = Int.MIN_VALUE

        private val iconIncoming = incomingCallIcon.mutate()
        private val iconOutgoing = outgoingCallIcon.mutate()
        private val iconMissed = incomingMissedCallIcon.mutate()


        private fun buildHighlightedName(
            call: RecentCall,
            highlight: String,
            primaryColor: Int
        ): CharSequence {

            //ve vyhledávacím poli je číslo ?
            val isNumberInHighlight = normalizeDigitsOnly(highlight).isNotBlank()
            val rawName = call.name
            //tento call nemá kontakt, takže v call.name je jen číslo
            val isNoContactCall = rawName == call.phoneNumber || rawName == call.specificNumber

            var baseText = if (isNumberInHighlight && isNoContactCall) normalizeDigitsOnly(rawName) //neřešíme formátovaní a zachováváme pouze čísla aby se nám správně highlightoval text
                           else if (isNoContactCall && activity.config.formatPhoneNumbers) numberForRecents(rawName, format = true)
                           else rawName

            // specificType JE součást baseTextu → patří do highlightu, ale stejně se v něm nevyhledává... viz applysearch() ve fragmentu
            if (call.specificType.isNotBlank()) {
                baseText += " - ${call.specificType}"
            }

            val builder = SpannableStringBuilder()

            val normalizedBase = baseText.normalizeString()
            val normalizedHighlight = highlight.normalizeString()

            if (normalizedHighlight.isNotEmpty() &&
                normalizedBase.contains(normalizedHighlight, ignoreCase = true)
            ) {
                builder.append(baseText.highlightTextPart(highlight, primaryColor)
                )
            } else {
                builder.append(baseText)
            }

            // suffix AŽ PO highlightu
            call.groupedCalls?.let {
                builder.append(" (${it.size})")
            }

            return builder
        }


        fun updateHighlight(highlight: String) {
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return

            val call = currentList[pos] as? RecentCall ?: return

            binding.itemRecentsName.text = buildHighlightedName(
                call = call,
                highlight = highlight,
                primaryColor = properPrimaryColor
            )
        }

        fun bind(call: RecentCall) = bindView(
            item = call,
            allowSingleClick = true,
            allowLongClick = !compactMode && !call.isUnknownNumber
        ) { _, _ ->

            val currentFontSize = fontSize
            val smallTextSize = currentFontSize * 0.8f
            val primaryColor = properPrimaryColor
            val missedColor = missedCallColor
            val textColorInt = textColor
            val secondaryColorInt = secondaryTextColor

            val iconSizePx: Int = when (cfg.fontSize) {
                FONT_SIZE_SMALL ->
                    res.getDimensionPixelSize(cz.mts.base.R.dimen.s_small_icon_size)
                FONT_SIZE_MEDIUM ->
                    res.getDimensionPixelSize(cz.mts.base.R.dimen.l_middle_icon_size)
                FONT_SIZE_LARGE ->
                    res.getDimensionPixelSize(cz.mts.base.R.dimen.xl_big_icon_size)
                FONT_SIZE_EXTRA_LARGE ->
                    res.getDimensionPixelSize(cz.mts.base.R.dimen.xxl_extrabig_icon_size)
                else ->
                    res.getDimensionPixelSize(cz.mts.base.R.dimen.xl_big_icon_size)
            }
            binding.apply {
                root.setupViewBackground(activity)

                val isSelected = selectedKeys.contains(call.id)
                itemRecentsHolder.isSelected = isSelected

                val iCallType = call.type

                itemRecentsName.apply {
                    text = buildHighlightedName(
                        call = call,
                        highlight = textToHighlight,
                        primaryColor = primaryColor
                    )
                    setTextColor(textColorInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize)
                }


                itemRecentsDateTime.apply {
                    text = call.startTS.formatTime(activity)

                    setTextColor(
                        if (iCallType == Calls.MISSED_TYPE) missedColor
                        else secondaryColorInt
                    )
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                }

                val shouldShowDuration =
                    iCallType != Calls.MISSED_TYPE &&
                        iCallType != Calls.REJECTED_TYPE &&
                        call.duration > 0

                itemRecentsDateTimeDurationSeparator.apply {
                    beVisibleIf(shouldShowDuration)
                    text = "•"
                    setTextColor(textColorInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                }

                itemRecentsDuration.apply {
                    beVisibleIf(shouldShowDuration)
                    text = context.formatSecondsToShortTimeString(call.duration)
                    setTextColor(textColorInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                    if (!showOverflowMenu) {
                        setPadding(0, 0, durationPadding, 0)
                    }
                }

                if (iCallType == 6) {
                    itemRecentsDateTimeDurationSeparator.apply {
                        beVisibleIf(true)
                        text = "• " + activity.getString(R.string.number_type3_mts)
                        setTextColor(textColorInt)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                    }
                }
                else
                {
                itemRecentsLocation.apply {
                    val location = getLocationSafeForUI(call.phoneNumber, call.simID)
                    text = location
                    beVisibleIf(location.isNotBlank())
                    setTextColor(textColorInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                }
                }

                val hasSim = areMultipleSIMsAvailable && call.simID != -1
                itemRecentsSimImage.beVisibleIf(hasSim)
                itemRecentsSimId.beVisibleIf(hasSim)

                if (hasSim) {
                    if (call.simID != lastSimId || call.simColor != lastSimColor) {
                        updateSimUI(call)
                        lastSimId = call.simID
                        lastSimColor = call.simColor
                    }
                }

                //musíme ho nastavit a poslat už tady, protože je průhledný a první vykreslení by měl jako getContactLetterIcon pozadí
                val placeholderImage =
                    if (mtsGlobalAll.iSaveDebugMode == 2) ContextCompat.getDrawable(activity.baseContext,  R.drawable.karlavatar)
                    else if (call.isUnknownNumber) ContextCompat.getDrawable(activity.baseContext,  R.drawable.anonymousavatar)
                    else null

                contactsHelper.loadContactImage(call.photoUri, itemRecentsImage, call.name, placeholderImage, call.isUnknownNumber)

                if (profileIconClick != null) {
                    itemRecentsImage.setBackgroundResource(R.drawable.selector_clickable_circle)
                    itemRecentsImage.setOnClickListener {
                        if (!actModeCallback.isSelectable) {
                            showCallDetails2(call)
                        } else viewClicked(call)
                    }
                    itemRecentsImage.setOnLongClickListener {
                        viewLongClicked()
                        true
                    }
                }

                itemRecentsImage.layoutParams = itemRecentsImage.layoutParams.apply {
                    width = iconSizePx
                    height = iconSizePx

                    itemRecentsImage.requestLayout()
                }

                val icon = when (iCallType) {
                    Calls.OUTGOING_TYPE -> iconOutgoing
                    Calls.MISSED_TYPE -> iconMissed
                    else -> iconIncoming
                }
                itemRecentsType.setImageDrawable(icon)

                overflowMenuIcon.beVisibleIf(showOverflowMenu)
                overflowMenuIcon.drawable.mutate().setTint(activity.getProperTextColor())
                overflowMenuIcon.setOnClickListener {
                    showPopupMenu(overflowMenuAnchor, call)
                }
                // SMS ikonka
                val smsRecord = smsRecordByCallId[call.id]

                itemRecentsSmsIcon.apply {
                    if (smsRecord != null) {
                        isVisible = true
                        setOnClickListener { context.toast(smsRecord.message) }
                    } else {
                        isVisible = false
                        setOnClickListener(null)
                    }
                }


                itemRecentsSmsIcon.apply {
                    if (smsRecord != null) {
                        isVisible = true
                      //  applyColorFilter(secondaryColorInt)
                        setOnClickListener { context.toast(smsRecord.message)}
                    } else {
                        isVisible = false
                        setOnClickListener(null)
                    }
                }

            }

        }

        private fun updateSimUI(call: RecentCall) {
            val usableDrawable = when (call.simID) {
                1 -> simDrawable1
                2 -> simDrawable2
                else -> null
            }

            if (usableDrawable != null) {
                val fixedColor = call.simColor or 0xFF000000.toInt()
                usableDrawable.mutate().setTintMode(PorterDuff.Mode.SRC_IN)
                usableDrawable.setTint(fixedColor)

                binding.itemRecentsSimImage.alpha = 1f
                binding.itemRecentsSimImage.setImageDrawable(usableDrawable)
                binding.itemRecentsSimId.apply {
                    setTextColor(call.simColor.getContrastColor())
                    text = call.simID.toString()
                }
            } else {
                // fallback
                binding.itemRecentsSimImage.alpha = 1f
                binding.itemRecentsSimImage.applyColorFilter(call.simColor)
                binding.itemRecentsSimId.apply {
                    setTextColor(call.simColor.getContrastColor())
                    text = call.simID.toString()
                }
            }
        }
    }


    private fun getAdjustedSimColor(simColor: Int): Int {
        return cachedSimColors.getOrPut(simColor to backgroundColor) {
            simColor.adjustForContrast(backgroundColor)
        }
    }

    private fun rebuildSmsRecordMap(items: List<CallLogItem>) {
        val available = SmsHistoryManager.getAll(activity).toMutableList()
        val result = HashMap<Int, SentSmsRecord>()

        items.filterIsInstance<RecentCall>()
            .filter { it.type == Calls.INCOMING_TYPE || it.type == Calls.REJECTED_TYPE }
            .forEach { call ->
                val match = available.firstOrNull { sms ->
                    normalizeDigitsOnly(sms.phoneNumber) == normalizeDigitsOnly(call.phoneNumber) &&
                        Math.abs(sms.timestamp - call.startTS) <= 30000L
                }
                if (match != null) {
                    result[call.id] = match
                    available.remove(match) // každý sms záznam jen jednou
                }
            }

        smsRecordByCallId = result
    }

    private inner class RecentCallDateViewHolder(
        val binding: ItemRecentsDateBinding
    ) : ViewHolder(binding.root) {

        fun bind(date: CallLogItem.Date) {
            binding.dateTextView.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.76f)

                val itemDay = Instant.ofEpochMilli(date.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toDayCode()

                    text = when (itemDay) {
                    todayCode -> activity.getString(R.string.today)
                    yesterdayCode -> activity.getString(R.string.yesterday)

                    else ->
                        date.timestamp.formatDateOrTime(
                            context = activity,
                            hideTimeOnOtherDays = true,
                            showCurrentYear = false,
                            hideTodaysDate = true,
                            showDayIfUserWant = true
                        )
                }
            }
        }
    }
}

class RecentCallsDiffCallback : DiffUtil.ItemCallback<CallLogItem>() {

    override fun areItemsTheSame(oldItem: CallLogItem, newItem: CallLogItem) = oldItem.getItemId() == newItem.getItemId()

    override fun areContentsTheSame(oldItem: CallLogItem, newItem: CallLogItem): Boolean {
        return when {
            RecentsQueryLimits.getPreviewState() -> false
            oldItem is CallLogItem.Date && newItem is CallLogItem.Date -> oldItem.timestamp == newItem.timestamp && oldItem.dayCode == newItem.dayCode
            oldItem is RecentCall && newItem is RecentCall -> {
                oldItem.phoneNumber == newItem.phoneNumber &&
                        oldItem.name == newItem.name &&
                        oldItem.photoUri == newItem.photoUri &&
                        oldItem.startTS == newItem.startTS &&
                        oldItem.duration == newItem.duration &&
                        oldItem.type == newItem.type &&
                        oldItem.simID == newItem.simID &&
                        oldItem.simColor == newItem.simColor &&
                        oldItem.specificNumber == newItem.specificNumber &&
                        oldItem.specificType == newItem.specificType &&
                        oldItem.isUnknownNumber == newItem.isUnknownNumber &&
                        oldItem.groupedCalls?.size == newItem.groupedCalls?.size
            }
            else -> false
        }
    }
}
