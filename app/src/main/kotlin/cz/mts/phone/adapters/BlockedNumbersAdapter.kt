package cz.mts.phone.adapters

import android.annotation.SuppressLint
import android.view.Menu
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.adapters.MyRecyclerViewListAdapter
import cz.mts.base.dialogs.ConfirmationDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.models.BlockedNumber
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.R
import cz.mts.phone.databinding.ItemBlockedNumberBinding

/**
 * Necompose adaptér pro seznam blokovaných čísel.
 * Výběr / horní CAB menu (cab_blocked_numbers.xml) + PopupMenu na jednotlivém řádku
 * (menu_blocked_number_item_options.xml) je sladěný se stejným vzorem, jaký používá
 * RecentCallsAdapter (getActionMenuId / prepareActionMode / actionItemPressed + showPopupMenu).
 *
 * POZOR: BlockedNumber.id je Long, ale selectedKeys / getItemSelectionKey v MyRecyclerViewListAdapter
 * pracuje s Int (stejně jako u RecentCall.id v RecentCallsAdapter) - proto se zde id ořezává přes
 * .toInt(). Pro lokální tabulku blokovaných čísel by to reálně nemělo být omezující, ale stojí to
 * za kontrolu, pokud by id v budoucnu mohlo přesáhnout Int.MAX_VALUE.
 */
class BlockedNumbersAdapter(
    activity: BaseSimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    private val itemDelete: (List<BlockedNumber>) -> Unit,
) : MyRecyclerViewListAdapter<BlockedNumber>(activity, recyclerView, BlockedNumberDiffCallback(), itemClick) {

    // stejně jako RecentCallsAdapter.fontSize - velikost písma NENÍ natvrdo, bere se z configu/stylů
    var fontSize: Float = activity.getTextSize()

    init {
        setHasStableIds(true)
    }

    // ─── CAB (horní menu při výběru) ───────────────────────────────────────────

    override fun getActionMenuId() = R.menu.cab_blocked_numbers

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_copy_number).isVisible = true
            findItem(R.id.cab_unblock).isVisible = true
            findItem(R.id.cab_select_all).isVisible = true
        }
    }

    override fun actionItemPressed(id: Int) {

        if (id == R.id.cab_select_all) {
            toggleSelectAll()
            return
        }

        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_number -> copyNumber()
            R.id.cab_unblock -> askConfirmUnblock()
            R.id.cab_select_all -> toggleSelectAll()
        }
    }

    override fun getItemId(position: Int): Long {
        return currentList.getOrNull(position)?.id ?: RecyclerView.NO_ID
    }

    override fun getSelectableItemCount() = currentList.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.id.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun getSelectedItems() = currentList.filter { selectedKeys.contains(it.id.toInt()) }

    private fun copyNumber() {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) {
            return
        }
        val numbers = selectedItems.joinToString("\n") { it.number }
        activity.copyToClipboard(numbers)
        //finishActMode()
    }

    private fun askConfirmUnblock() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            unblockSelected()
        }
    }

    private fun unblockSelected() {
        val toRemove = getSelectedItems()
        if (toRemove.isEmpty()) {
            return
        }

        val newList = currentList.filterNot { blockedNumber -> toRemove.any { it.id == blockedNumber.id } }
        finishActMode()

        ensureBackgroundThread {
            toRemove.forEach { activity.deleteBlockedNumber(it.number) }
            activity.runOnUiThread {
                submitList(newList)
                itemDelete(toRemove)
            }
        }
    }

    // ─── Aktualizace dat (analogické RecentCallsAdapter.updateItems) ──────────

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<BlockedNumber>) {
        if (actModeCallback.isSelectable && selectedKeys.isNotEmpty()) {
            val newIds = newItems.mapNotNullTo(HashSet()) { it.id.toInt() }
            val anySelectedItemMissing = selectedKeys.any { it !in newIds }
            if (anySelectedItemMissing) {
                finishActMode()
            }
        }
        submitList(newItems)
    }



    // ─── ViewHolder ─────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return BlockedNumberViewHolder(ItemBlockedNumberBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val blockedNumber = currentList[position]
        (holder as BlockedNumberViewHolder).bind(blockedNumber)
        bindViewHolder(holder)
    }

    private inner class BlockedNumberViewHolder(
        val binding: ItemBlockedNumberBinding,
    ) : ViewHolder(binding.root) {

        fun bind(blockedNumber: BlockedNumber) = bindView(
            item = blockedNumber,
            allowSingleClick = true,
            allowLongClick = true,
        ) { _, _ ->
            binding.apply {
                root.setupViewBackground(activity)
                itemBlockedNumberHolder.isSelected = selectedKeys.contains(blockedNumber.id.toInt())

                val hasContactName = blockedNumber.contactName != null
                val textColor = activity.getProperTextColor()

                itemBlockedNumberTitle.apply {
                    text = if (hasContactName) blockedNumber.contactName else blockedNumber.number
                    setTextColor(textColor)
                    //setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                }

                itemBlockedNumberSubtitle.apply {
                    beVisibleIf(hasContactName)
                    text = blockedNumber.number
                    setTextColor(textColor.adjustAlpha(0.7f))
                    //setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
                }

            }
        }
    }
}

private class BlockedNumberDiffCallback : DiffUtil.ItemCallback<BlockedNumber>() {
    override fun areItemsTheSame(oldItem: BlockedNumber, newItem: BlockedNumber) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: BlockedNumber, newItem: BlockedNumber) = oldItem == newItem
}
