package cz.mts.phone.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.adapters.ConferenceCallsAdapter
import cz.mts.phone.databinding.ActivityConferenceBinding
import cz.mts.phone.helpers.CallManager
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.getColoredDrawableWithColor
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.views.MyAppBarLayout
import cz.mts.phone.R

class ConferenceFragment : Fragment() {

    private var _binding: ActivityConferenceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityConferenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
    }

    private fun setupUi() {
        val simpleActivity = requireActivity() as SimpleActivity  // activity as SimpleActivity
        val topBarColor = requireContext().getProperBackgroundColor()
        val contrastColor = topBarColor.getContrastColor()

        with(binding) {
            // Nastavení top baru
            conferenceAppbar.apply {
                toolbar?.navigationIcon =
                    resources.getColoredDrawableWithColor(R.drawable.ic_arrow_left_vector, contrastColor)
                toolbar?.setNavigationContentDescription(NavigationIcon.Arrow.accessibilityResId)
                toolbar?.setNavigationOnClickListener { closeConference() }
                updateTopBarColors(this, topBarColor)
            }

            // itemClick:        nikdy se nevolá (allowSingleClick = false v adapteru), ale parametr existuje
            // onConferenceEnded: voláno když zbyde jen 1 účastník – zavře fragment
            conferenceList.adapter = ConferenceCallsAdapter(
                activity = simpleActivity,
                recyclerView = conferenceList,
                calls = ArrayList(CallManager.getConferenceCalls()),
                itemClick = {},
                onConferenceEnded = { closeConference() }
            )
        }
    }

    private fun closeConference() {
        activity?.onBackPressedDispatcher?.onBackPressed()
    }

    private fun updateTopBarColors(topAppBar: MyAppBarLayout, color: Int) {
        val contrastColor = color.getContrastColor()
        topAppBar.setBackgroundColor(color)
        topAppBar.toolbar?.apply {
            setBackgroundColor(color)
            setTitleTextColor(contrastColor)
            navigationIcon?.applyColorFilter(contrastColor)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
