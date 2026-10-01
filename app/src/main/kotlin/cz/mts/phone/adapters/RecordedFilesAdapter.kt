package cz.mts.phone.adapters

import android.text.format.Formatter
import android.util.TypedValue
import android.view.Menu
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.adapters.MyRecyclerViewListAdapter
import cz.mts.base.dialogs.ConfirmationDialog
import cz.mts.base.extensions.*
import cz.mts.base.helpers.*
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.databinding.ItemRecordedFileBinding
import cz.mts.phone.helpers.*

data class RecordedFile(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long
)

/**
 * Adaptér seznamu nahrávek. Vzor: BlockedNumbersAdapter (výběr, CAB, potvrzení smazání)
 * + RecentCallsAdapter (velikost a barvy písma).
 *
 * - CAB: Přehrát, Smazat, Exportovat, Označit vše (cab_recorded_files.xml).
 *   Přehrát a Exportovat jsou vidět jen při jednom označeném záznamu.
 * - Barvy CAB řeší rovnou základní MyRecyclerViewListAdapter (podle configu), tady nic není.
 * - Řazení: vždy nejnovější nahoře (podle lastModified), natvrdo v submitList().
 * - Vlastní mazání ([onDelete]) a export ([onExport]) dělá aktivita (přehrávač, launcher pro výběr cíle).
 *
 * POZOR: selectedKeys je v základní třídě Int, RecordedFile má klíč String (path).
 * Jako klíč se proto používá path.hashCode(). Pro pár desítek souborů je kolize
 * prakticky vyloučená, ale je to hash, ne unikátní id.
 */
class RecordedFilesAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (RecordedFile) -> Unit,
    private val onDelete: (List<RecordedFile>) -> Unit,
    private val onExport: (RecordedFile) -> Unit,
) : MyRecyclerViewListAdapter<RecordedFile>(activity, recyclerView, RecordedFileDiffCallback(), itemClick) {

    // stejně jako RecentCallsAdapter - velikost písma se bere z configu, ne natvrdo
    var fontSize: Float = activity.getTextSize()

    // nejnovější první; při shodě času podle názvu (ten obsahuje YYYY_MM_DD_HH_MM_SS)
    private val newestFirst = compareByDescending<RecordedFile> { it.modified }
        .thenByDescending { it.name }

    init {
        setHasStableIds(true)
    }

    private fun RecordedFile.key(): Int = path.hashCode()

    // ─── Řazení ────────────────────────────────────────────────────────────

    override fun submitList(list: List<RecordedFile>?) {
        super.submitList(list?.sortedWith(newestFirst))
    }

    override fun submitList(list: List<RecordedFile>?, commitCallback: Runnable?) {
        super.submitList(list?.sortedWith(newestFirst), commitCallback)
    }

    // ─── CAB (horní menu při výběru) ───────────────────────────────────────

    override fun getActionMenuId() = R.menu.cab_recorded_files

    override fun prepareActionMode(menu: Menu) {
        val isOneItemSelected = selectedKeys.size == 1
        menu.apply {
            findItem(R.id.cab_play).isVisible = isOneItemSelected
            findItem(R.id.cab_remove).isVisible = true
            findItem(R.id.cab_export).isVisible = isOneItemSelected
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
            R.id.cab_play -> playSelected()
            R.id.cab_remove -> askConfirmRemove()
            R.id.cab_export -> exportSelected()
        }
    }

    private fun getSelectedItems() = currentList.filter { selectedKeys.contains(it.key()) }

    private fun playSelected() {
        val item = getSelectedItems().singleOrNull() ?: return
        itemClick.invoke(item)
    }

    private fun exportSelected() {
        val item = getSelectedItems().singleOrNull() ?: return
        finishActMode()
        onExport(item)
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            removeSelected()
        }
    }

    private fun removeSelected() {
        val toRemove = getSelectedItems()
        if (toRemove.isEmpty()) {
            return
        }
        finishActMode()
        onDelete(toRemove)
    }

    override fun getItemId(position: Int): Long {
        return currentList.getOrNull(position)?.key()?.toLong() ?: RecyclerView.NO_ID
    }

    override fun getSelectableItemCount() = currentList.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.key()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.key() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    // ─── Aktualizace dat ──────────────────────────────────────────────────

    fun updateItems(newItems: List<RecordedFile>) {
        if (actModeCallback.isSelectable && selectedKeys.isNotEmpty()) {
            val newKeys = newItems.mapTo(HashSet()) { it.key() }
            if (selectedKeys.any { it !in newKeys }) {
                finishActMode()
            }
        }
        submitList(newItems)
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return RecordedFileViewHolder(ItemRecordedFileBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]
        (holder as RecordedFileViewHolder).bind(item)
        bindViewHolder(holder)
    }

    /**
     * Datum a čas jdou přes stejné funkce jako v RecentCallsAdapter (respektují config uživatele).
     * Kdyby výsledek nevypadal dle očekávání, ladí se jen tady.
     */
    private fun formatFileDateTime(timestamp: Long): String {
        val date = timestamp.formatDateOrTime(
            context = activity,
            hideTimeOnOtherDays = true,
            showCurrentYear = true,
            hideTodaysDate = false,
            showDayIfUserWant = true
        )
        return "$date ${timestamp.formatTime(activity)}"
    }

    private inner class RecordedFileViewHolder(
        val binding: ItemRecordedFileBinding,
    ) : ViewHolder(binding.root) {

        fun bind(file: RecordedFile) = bindView(
            item = file,
            allowSingleClick = true,
            allowLongClick = true,
        ) { _, _ ->
            val primaryTextSize = fontSize
            val secondaryTextSize = fontSize * 0.8f          // stejně jako smallTextSize v RecentCallsAdapter
            val secondaryColor = textColor.adjustAlpha(0.6f) // stejně jako secondaryTextColor v RecentCallsAdapter

            binding.apply {
                root.setupViewBackground(activity)
                root.isSelected = selectedKeys.contains(file.key())

                recordedFileName.apply {
                    text = file.name
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, primaryTextSize)
                }

                recordedFileInfo.apply {
                    val size = Formatter.formatShortFileSize(context, file.size)
                    text = "${formatFileDateTime(file.modified)} • $size"
                    setTextColor(secondaryColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, secondaryTextSize)
                }
            }
        }
    }
}

private class RecordedFileDiffCallback : DiffUtil.ItemCallback<RecordedFile>() {
    override fun areItemsTheSame(oldItem: RecordedFile, newItem: RecordedFile) = oldItem.path == newItem.path
    override fun areContentsTheSame(oldItem: RecordedFile, newItem: RecordedFile) = oldItem == newItem
}