package cz.mts.phone.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import cz.mts.base.helpers.TAB_CALL_HISTORY
import cz.mts.base.helpers.TAB_CONTACTS
import cz.mts.base.helpers.TAB_FAVORITES
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.fragments.MyViewPagerFragment
import cz.mts.base.helpers.tabsList


class ViewPagerAdapter(
    private val activity: SimpleActivity
) : PagerAdapter() {

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

    override fun getCount() = tabsList.filter { it and activity.config.showTabs != 0 }.size

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int): Int {
        val showTabs = activity.config.showTabs
        val fragments = buildList {
            if (showTabs and TAB_CONTACTS > 0)      add(R.layout.fragment_contacts)
            if (showTabs and TAB_FAVORITES > 0)     add(R.layout.fragment_favorites)
            if (showTabs and TAB_CALL_HISTORY > 0)  add(R.layout.fragment_recents)
        }

        // FIX: getOrNull + lastOrNull chrání před pádem na prázdném listu.
        // Původní fragments.last() hodil NoSuchElementException pokud byly všechny taby vypnuté.
        return fragments.getOrNull(position)
            ?: fragments.lastOrNull()
            ?: error("No fragments available – all tabs are disabled (showTabs=$showTabs)")
    }
}
