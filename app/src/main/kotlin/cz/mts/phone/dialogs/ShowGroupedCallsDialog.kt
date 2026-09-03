package cz.mts.phone.dialogs

import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.viewBinding
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.adapters.RecentCallsAdapter
import cz.mts.phone.databinding.DialogShowGroupedCallsBinding
import cz.mts.phone.models.RecentCall
import cz.mts.base.extensions.toast

class ShowGroupedCallsDialog(
    private val activity: BaseSimpleActivity,
    recentCalls: List<RecentCall>
) {
    private val binding by activity.viewBinding(DialogShowGroupedCallsBinding::inflate)

    init {
        recentCalls.firstOrNull()?.let { headerCall ->
            val simpleActivity = activity as? SimpleActivity ?: return@let

            mtsGlobalAll.getRecentCallValues(activity, headerCall)

            var adapter: RecentCallsAdapter? = null
            adapter = RecentCallsAdapter(
                compactMode = true,
                activity = simpleActivity,
                recyclerView = binding.selectGroupedCallsList,
                showOverflowMenu = false,
                itemClick = { item ->
                    if (item is RecentCall) {
                        adapter?.getSmsRecord(item.id)?.let { sms ->
                            activity.toast(sms.message)
                        }
                    }
                },
                itemDelete = {},
                profileIconClick = {}
            ).apply {
                binding.selectGroupedCallsList.adapter = this
                updateItems(recentCalls)
            }

            mtsGlobalAll.mtsCallShowDialog(activity, bDoCall = false, bDialogType = true, viewX = binding.root, recent = headerCall)
        }
    }
}
