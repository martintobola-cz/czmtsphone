package cz.mts.phone.dialogs

import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.ALL_TABS_MASK
import cz.mts.base.helpers.TAB_CONTACTS
import cz.mts.base.helpers.TAB_FAVORITES
import cz.mts.phone.R
import cz.mts.phone.databinding.DialogManageVisibleTabsBinding
import cz.mts.phone.databinding.ItemManageTabBinding
import cz.mts.base.extensions.baseConfig as config

class ManageVisibleTabsDialog(
    private val activity: BaseSimpleActivity
) {
    private val binding by activity.viewBinding(DialogManageVisibleTabsBinding::inflate)

    private class Row(val type: Int, val binding: ItemManageTabBinding)
    private val rows = mutableListOf<Row>()

    init {
        val showTabs = activity.config.showTabs

        activity.config.getAllTabsOrdered().forEach { type ->
            val item = ItemManageTabBinding.inflate(activity.layoutInflater, binding.manageVisibleTabsList, false)
            item.manageTabCheckbox.apply {
                setText(labelFor(type))
                isChecked = showTabs and type != 0
            }

            val row = Row(type, item)
            item.manageTabUp.setOnClickListener { move(row, -1) }
            item.manageTabDown.setOnClickListener { move(row, +1) }

            rows.add(row)
            binding.manageVisibleTabsList.addView(item.root)
        }
        updateArrows()

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply { activity.setupDialogStuff(binding.root, this) }
    }

    private fun labelFor(type: Int) = when (type) {
        TAB_CONTACTS -> R.string.contacts_tab
        TAB_FAVORITES -> R.string.favorites_tab
        else -> R.string.call_history_tab
    }

    private fun move(row: Row, direction: Int) {
        val from = rows.indexOf(row)
        val to = from + direction
        if (from < 0 || to !in rows.indices) return

        rows.removeAt(from)
        rows.add(to, row)

        // přesouváme existující view, aby zůstalo obarvené přes setupDialogStuff
        binding.manageVisibleTabsList.removeView(row.binding.root)
        binding.manageVisibleTabsList.addView(row.binding.root, to)
        updateArrows()
    }

    private fun updateArrows() {
        rows.forEachIndexed { i, row ->
            row.binding.manageTabUp.apply { isEnabled = i > 0; alpha = if (isEnabled) 1f else 0.3f }
            row.binding.manageTabDown.apply { isEnabled = i < rows.lastIndex; alpha = if (isEnabled) 1f else 0.3f }
        }
    }

    private fun dialogConfirmed() {
        var mask = 0
        rows.forEach { if (it.binding.manageTabCheckbox.isChecked) mask = mask or it.type }
        if (mask == 0) mask = ALL_TABS_MASK

        activity.config.showTabs = mask
        activity.config.tabsOrder = rows.joinToString(",") { it.type.toString() }
    }
}
