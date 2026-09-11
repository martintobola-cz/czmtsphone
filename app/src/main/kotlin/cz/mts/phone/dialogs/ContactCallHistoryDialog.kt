package cz.mts.phone.dialogs

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.CallLog.Calls
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import cz.mts.base.extensions.adjustColor
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.extensions.getColoredDrawableWithColor
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.getTextSize
import cz.mts.base.helpers.FONT_SIZE_EXTRA_LARGE
import cz.mts.base.helpers.FONT_SIZE_LARGE
import cz.mts.base.helpers.FONT_SIZE_MEDIUM
import cz.mts.base.helpers.FONT_SIZE_SMALL
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.adapters.ContactCallHistoryAdapter
import cz.mts.phone.databinding.DialogContactCallHistoryBinding
import cz.mts.phone.fragments.RecentsFragment

/**
 * Zdrojem dat je běžící RecentsFragment: dialog si na startu vezme aktuální
 * (třeba jen částečně načtený) seznam a zaregistruje se na
 * [RecentsFragment.onKnownContactCallsChanged], takže se řádky doplňují postupně
 * i v případě, že recents na pozadí ještě dobíhá.
 */
class ContactCallHistoryDialog(
    private val activity: SimpleActivity,
    private val recentsFragment: RecentsFragment,
    private val contactName: String,
    private val sUri : String?
) {

    private val binding = DialogContactCallHistoryBinding.inflate(LayoutInflater.from(activity))
    private val adapter = ContactCallHistoryAdapter(activity)
    private var headerFilled = false

    init {
        binding.contactHistoryList.layoutManager = LinearLayoutManager(activity)
        binding.contactHistoryList.adapter = adapter

        binding.contactHistoryName.apply {
            text = contactName
            setTextColor(activity.getProperTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getTextSize() * 1.1f)
        }

        setupStatsIcons()
        refreshList()

        recentsFragment.onKnownContactCallsChanged = { activity.runOnUiThread { refreshList() } }

        val roundedBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 64f
            setColor(activity.getProperBackgroundColor().adjustColor(8, true))
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_close) { _, _ -> }
            .create()

        dialog.setOnDismissListener {
            // uvolníme napojení na fragment, ať dialog po zavření dál neposlouchá
            if (recentsFragment.onKnownContactCallsChanged != null) {
                recentsFragment.onKnownContactCallsChanged = null
            }
        }

        dialog.window?.setBackgroundDrawable(roundedBackground)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(activity.getProperPrimaryColor())
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
        }
    }

    private fun refreshList() {
        val calls = recentsFragment.getCallsForContact(contactName)
        adapter.submitList(calls)
        updateStats(calls)

        if (!headerFilled) {
            val photoUri = sUri?: calls.firstOrNull()?.photoUri.orEmpty()
            fillAvatar(photoUri)
            headerFilled = true
        }
    }

    private fun setupStatsIcons() {
        val res = activity.resources
        val theme = activity.theme
        binding.contactHistoryIncomingIcon.setImageDrawable(
            res.getColoredDrawableWithColor(R.drawable.ic_call_received_vector, res.getColor(R.color.color_incoming_call, theme))
        )
        binding.contactHistoryOutgoingIcon.setImageDrawable(
            res.getColoredDrawableWithColor(R.drawable.ic_call_made_vector, res.getColor(R.color.color_outgoing_call, theme))
        )
        binding.contactHistoryMissedIcon.setImageDrawable(
            res.getColoredDrawableWithColor(R.drawable.ic_call_missed_vector, res.getColor(R.color.color_missed_call, theme))
        )
    }

    // Počty se přepočítávají při každém refreshList(), takže se doplňují postupně s tím, jak recents dobíhá.
    private fun updateStats(calls: List<cz.mts.phone.models.RecentCall>) {
        var outgoing = 0
        var missed = 0
        var incoming = 0
        calls.forEach {
            when (it.type) {
                Calls.OUTGOING_TYPE -> outgoing++
                Calls.MISSED_TYPE -> missed++
                else -> incoming++
            }
        }
        val textColor = activity.getProperTextColor()
        binding.contactHistoryIncomingCount.text = incoming.toString()
        binding.contactHistoryIncomingCount.setTextColor(textColor)
        binding.contactHistoryOutgoingCount.text = outgoing.toString()
        binding.contactHistoryOutgoingCount.setTextColor(textColor)
        binding.contactHistoryMissedCount.text = missed.toString()
        binding.contactHistoryMissedCount.setTextColor(textColor)
    }

    private fun fillAvatar(photoUri: String) {
        val avatarSizePx = when (activity.config.fontSize) {
            FONT_SIZE_SMALL -> activity.resources.getDimensionPixelSize(cz.mts.base.R.dimen.s_small_icon_size)
            FONT_SIZE_MEDIUM -> activity.resources.getDimensionPixelSize(cz.mts.base.R.dimen.l_middle_icon_size)
            FONT_SIZE_LARGE -> activity.resources.getDimensionPixelSize(cz.mts.base.R.dimen.xl_big_icon_size)
            FONT_SIZE_EXTRA_LARGE -> activity.resources.getDimensionPixelSize(cz.mts.base.R.dimen.xxl_extrabig_icon_size)
            else -> activity.resources.getDimensionPixelSize(cz.mts.base.R.dimen.xl_big_icon_size)
        }

        binding.contactHistoryAvatar.layoutParams = binding.contactHistoryAvatar.layoutParams.apply {
            width = avatarSizePx
            height = avatarSizePx
        }

        // photoUri prázdné => loadContactImage sám spadne na fallback ikonu (viz SimpleContactsHelper)
        SimpleContactsHelper(activity).loadContactImage(photoUri, binding.contactHistoryAvatar, contactName, null, false
        )
    }
}
