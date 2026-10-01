package cz.mts.phone.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import cz.mts.base.helpers.TAB_CALL_HISTORY
import cz.mts.base.helpers.TAB_CONTACTS
import cz.mts.base.helpers.TAB_FAVORITES
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.fragments.MyViewPagerFragment
import cz.mts.base.extensions.baseConfig as config


class ViewPagerAdapter(
    private val activity: SimpleActivity
) : PagerAdapter() {

    // Snapshot pořadí při vytvoření adaptéru: getCount() a getFragment() se tak nikdy
    // nerozejdou, ani kdyby se config změnil za běhu. Změnu pořadí řeší restart v MainActivity.onResume().
    private val tabs: List<Int> = activity.config.getOrderedVisibleTabs()

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragment(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment<*>).setupFragment(activity)

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = tabs.size

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int): Int {
        // getOrNull + lastOrNull chrání před pádem na prázdném listu.
        val tabType = tabs.getOrNull(position)
            ?: tabs.lastOrNull()
            ?: error("No tabs available (showTabs=${activity.config.showTabs})")

        return when (tabType) {
            TAB_CONTACTS -> R.layout.fragment_contacts
            TAB_FAVORITES -> R.layout.fragment_favorites
            TAB_CALL_HISTORY -> R.layout.fragment_recents
            else -> error("Unknown tab type: $tabType")
        }
    }
}