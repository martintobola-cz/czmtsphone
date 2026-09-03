package cz.mts.phone.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import cz.mts.base.adapters.MyRecyclerViewAdapter
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.getTextSize
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.adapters.ContactsAdapter
import cz.mts.phone.adapters.RecentCallsAdapter
import cz.mts.phone.databinding.FragmentLettersLayoutBinding
import cz.mts.phone.databinding.FragmentRecentsBinding

abstract class MyViewPagerFragment<BINDING : MyViewPagerFragment.InnerBinding>(
    context: Context,
    attributeSet: AttributeSet
) : RelativeLayout(context, attributeSet) {

    protected var activity: SimpleActivity? = null
    protected lateinit var innerBinding: BINDING

    fun setupFragment(activity: SimpleActivity) {
        if (this.activity != null) return

        this.activity = activity
        setupFragment()
        setupColors(
            activity.getProperTextColor(),
            activity.getProperPrimaryColor(),
            activity.getProperPrimaryColor()
        )
    }

    fun finishActMode() {
        (innerBinding.fragmentList?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
        (innerBinding.recentsList?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun fontSizeChanged() {
        val currentActivity = activity ?: return

        if (this is RecentsFragment) {
            (innerBinding.recentsList?.adapter as? RecentCallsAdapter)?.apply {
                fontSize = currentActivity.getTextSize()
                notifyDataSetChanged()
            }
        } else {
            (innerBinding.fragmentList?.adapter as? ContactsAdapter)?.apply {
                fontSize = currentActivity.getTextSize()
                notifyDataSetChanged()
            }
        }
    }

    abstract fun setupFragment()

    abstract fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int)

    abstract fun onSearchClosed()

    abstract fun onSearchQueryChanged(text: String)

    interface InnerBinding {
        val fragmentList: MyRecyclerView?
        val recentsList: MyRecyclerView?
    }

    class LettersInnerBinding(val binding: FragmentLettersLayoutBinding) : InnerBinding {
        override val fragmentList: MyRecyclerView = binding.fragmentList
        override val recentsList = null
    }

    class RecentsInnerBinding(val binding: FragmentRecentsBinding) : InnerBinding {
        override val fragmentList = null
        override val recentsList = binding.recentsList
    }
}
