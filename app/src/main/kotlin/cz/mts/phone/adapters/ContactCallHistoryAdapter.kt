package cz.mts.phone.adapters

import android.graphics.PorterDuff
import android.provider.CallLog.Calls
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.extensions.adjustAlpha
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.formatDateOrTime
import cz.mts.base.extensions.formatSecondsToShortTimeString
import cz.mts.base.extensions.formatTime
import cz.mts.base.extensions.getColoredDrawableWithColor
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.getTextSize
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.databinding.ItemContactCallHistoryBinding
import cz.mts.phone.models.RecentCall

/**
 * Jednoduchý, needitovatelný adaptér pro zobrazení kompletní (negroupované) historie
 * hovorů jednoho konkrétního kontaktu uvnitř ContactCallHistoryDialog.
 * */
class ContactCallHistoryAdapter(
    private val activity: SimpleActivity
) : ListAdapter<RecentCall, ContactCallHistoryAdapter.ViewHolder>(DiffCallback()) {

    private val layoutInflater = LayoutInflater.from(activity)
    private val res = activity.resources
    private val cfg = activity.config
    private val fontSize: Float = activity.getTextSize()
    private val textColor = activity.getProperTextColor()
    private val secondaryTextColor = textColor.adjustAlpha(0.6f)
    private val missedCallColor = res.getColor(R.color.color_missed_call, activity.theme)

    private val simDrawable1 = AppCompatResources.getDrawable(activity, R.drawable.ic_sim1)?.mutate()
    private val simDrawable2 = AppCompatResources.getDrawable(activity, R.drawable.ic_sim2)?.mutate()

    private val iconOutgoing = res.getColoredDrawableWithColor(
        R.drawable.ic_call_made_vector, res.getColor(R.color.color_outgoing_call, activity.theme)
    )
    private val iconIncoming = res.getColoredDrawableWithColor(
        R.drawable.ic_call_received_vector, res.getColor(R.color.color_incoming_call, activity.theme)
    )
    private val iconMissed = res.getColoredDrawableWithColor(
        R.drawable.ic_call_missed_vector, missedCallColor
    )

    private val smallIconSizePx: Int = (fontSize * 1.2f).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemContactCallHistoryBinding.inflate(layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(val binding: ItemContactCallHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(call: RecentCall) {
            val smallTextSize = fontSize * 0.85f
            val smallTextSize2 = fontSize * 0.85f
            val iCallType = call.type

            binding.apply {

                // ---- Řádek 1: SIM, typ hovoru, datum ... doba trvání ----
                val hasSim = call.simID != -1
                historyRowSimHolder.beVisibleIf(hasSim)
                if (hasSim) {
                    historyRowSimImage.layoutParams = historyRowSimImage.layoutParams.apply {
                        width = smallIconSizePx
                        height = smallIconSizePx
                    }
                    val usableDrawable = when (call.simID) {
                        1 -> simDrawable1
                        2 -> simDrawable2
                        else -> null
                    }
                    if (usableDrawable != null) {
                        val fixedColor = call.simColor or 0xFF000000.toInt()
                        usableDrawable.mutate().setTintMode(PorterDuff.Mode.SRC_IN)
                        usableDrawable.setTint(fixedColor)
                        historyRowSimImage.setImageDrawable(usableDrawable)
                    } else {
                        historyRowSimImage.setImageResource(R.drawable.ic_sim_vector)
                        historyRowSimImage.applyColorFilter(call.simColor)
                    }
                    historyRowSimId.apply {
                        setTextColor(call.simColor.getContrastColor())
                        text = call.simID.toString()
                    }
                }

                historyRowTypeIcon.apply {
                    layoutParams = layoutParams.apply {
                        width = smallIconSizePx
                        height = smallIconSizePx
                    }
                    setImageDrawable(
                        when (iCallType) {
                            Calls.OUTGOING_TYPE -> iconOutgoing
                            Calls.MISSED_TYPE -> iconMissed
                            else -> iconIncoming
                        }
                    )
                }

                val dateOnly = call.startTS.formatDateOrTime(
                    context = activity,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false,
                    hideTodaysDate = false,
                    showDayIfUserWant = true
                )
                val timeOnly = call.startTS.formatTime(activity)

                historyRowDate.apply {
                    text = dateOnly
                    setTextColor(if (iCallType == Calls.MISSED_TYPE) missedCallColor else textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                }

                val iBlocked = (call.type == 6)
                val shouldShowDuration = ((iCallType != Calls.MISSED_TYPE &&
                    iCallType != Calls.REJECTED_TYPE &&
                    call.duration > 0) || (iBlocked))

                historyRowDuration.apply {
                    beVisibleIf(shouldShowDuration)

                    text = if (!iBlocked) "⏲ " + activity.formatSecondsToShortTimeString(call.duration)
                           else activity.getString(R.string.number_type3_mts)
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize2)
                }

                // ---- Řádek 2: čas • telefonní číslo • typ čísla ----
                historyRowTime.apply {
                    text = timeOnly
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                }

                val displayNumber = call.specificNumber.ifBlank { call.phoneNumber }
                val numberText = if (cfg.formatPhoneNumbers) {
                    numberForRecents(displayNumber, true) ?: displayNumber
                } else {
                    displayNumber
                }

                historyRowNumber.apply {
                    text = numberText
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                }

                val hasType = call.specificType.isNotBlank()
                historyRowType.apply {
                    beVisibleIf(hasType)
                    text = call.specificType
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                }

                // oddělovače viditelné jen když mají co oddělovat
                historyRowTimeNumberSeparator.apply {
                    beVisibleIf(timeOnly.isNotBlank() && numberText.isNotBlank())
                    text = " ⁝ "
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                }

                historyRowNumberTypeSeparator.apply {
                    beVisibleIf(hasType)
                    text = "•"
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<RecentCall>() {
        override fun areItemsTheSame(oldItem: RecentCall, newItem: RecentCall) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RecentCall, newItem: RecentCall) = oldItem == newItem
    }
}
