package cz.mts.phone.adapters

import android.view.Menu
import android.view.ViewGroup
import cz.mts.base.adapters.MyRecyclerViewAdapter
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.databinding.ItemSpeedDialBinding
import cz.mts.phone.interfaces.RemoveSpeedDialListener
import cz.mts.base.models.SpeedDial

class SpeedDialAdapter(
    activity: SimpleActivity,
    var speedDialValues: List<SpeedDial>,
    private val removeListener: RemoveSpeedDialListener,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_delete_only

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) return

        when (id) {
            R.id.cab_delete -> deleteSpeedDial()
        }
    }

    override fun getSelectableItemCount() = speedDialValues.size

    override fun getIsItemSelectable(position: Int) = speedDialValues[position].isValid()

    override fun getItemSelectionKey(position: Int) = speedDialValues.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = speedDialValues.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemSpeedDialBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val speedDial = speedDialValues[position]
        holder.bindView(speedDial, true, true) { itemView, _ ->
            setupView(ItemSpeedDialBinding.bind(itemView), speedDial)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = speedDialValues.size

    private fun getSelectedItems(): ArrayList<SpeedDial> =
        speedDialValues.filterTo(ArrayList()) { selectedKeys.contains(it.id) }

    private fun deleteSpeedDial() {
        val ids = getSelectedItems().mapTo(ArrayList()) { it.id }
        removeListener.removeSpeedDial(ids)
        finishActMode()
    }

    private fun setupView(binding: ItemSpeedDialBinding, speedDial: SpeedDial) {
        binding.apply {
            val displayName = "${speedDial.id}. ${if (speedDial.isValid()) speedDial.getName(activity) else ""}"

            speedDialLabel.apply {
                text = displayName
                isSelected = selectedKeys.contains(speedDial.id)
                setTextColor(textColor)
            }
        }
    }
}
