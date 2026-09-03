package cz.mts.phone.dialogs

import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.TAB_CALL_HISTORY
import cz.mts.base.helpers.TAB_CONTACTS
import cz.mts.base.helpers.TAB_FAVORITES
import cz.mts.base.views.MyAppCompatCheckbox
import cz.mts.phone.R
import cz.mts.phone.databinding.DialogManageVisibleTabsBinding
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.helpers.ALL_TABS_MASK

class ManageVisibleTabsDialog(
    private val activity: BaseSimpleActivity
) {
    private val binding by activity.viewBinding(DialogManageVisibleTabsBinding::inflate)

    private val tabs = linkedMapOf(
        TAB_CONTACTS     to R.id.manage_visible_tabs_contacts,
        TAB_FAVORITES    to R.id.manage_visible_tabs_favorites,
        TAB_CALL_HISTORY to R.id.manage_visible_tabs_call_history
    )

    init {
        val showTabs = activity.config.showTabs
        tabs.forEach { (key, viewId) ->
            binding.root.findViewById<MyAppCompatCheckbox>(viewId).isChecked = showTabs and key != 0
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }

    private fun dialogConfirmed() {
        var result = 0
        tabs.forEach { (key, viewId) ->
            if (binding.root.findViewById<MyAppCompatCheckbox>(viewId).isChecked) {
                result = result or key
            }
        }

        if (result == 0) {
            result = ALL_TABS_MASK
        }

        activity.config.showTabs = result
    }
}
