package cz.mts.base.dialogs

import android.text.format.DateFormat
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.databinding.DialogChangeDateTimeFormatBinding
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.helpers.*
import java.util.Calendar
import java.util.Locale

class ChangeDateTimeFormatDialog(val activity: BaseSimpleActivity, val callback: () -> Unit) {
    private val view = DialogChangeDateTimeFormatBinding.inflate(activity.layoutInflater, null, false)

    init {
        view.apply {
            changeDateTimeDialogRadioOne.text = formatDateSample(DATE_FORMAT_ONE)
            changeDateTimeDialogRadioTwo.text = formatDateSample(DATE_FORMAT_TWO)
            changeDateTimeDialogRadioThree.text = formatDateSample(DATE_FORMAT_THREE)
            changeDateTimeDialogRadioFour.text = formatDateSample(DATE_FORMAT_FOUR)
            changeDateTimeDialogRadioFive.text = formatDateSample(DATE_FORMAT_FIVE)
            changeDateTimeDialogRadioSix.text = formatDateSample(DATE_FORMAT_SIX)
            changeDateTimeDialogRadioSeven.text = formatDateSample(DATE_FORMAT_SEVEN)
            changeDateTimeDialogRadioEight.text = formatDateSample(DATE_FORMAT_EIGHT)
            changeDateTimeDialogRadioMts.text = formatDateSample(DATE_FORMAT_MTS)
            changeDateTimeDialogRadioMtsLong.text = formatDateSample(DATE_FORMAT_MTSLONG)

            changeDateTimeDialog24Hour.isChecked = activity.baseConfig.use24HourFormat
            changeDateTimeDialogDayofweek.isChecked = activity.baseConfig.useDayOfWeekInTimeFormat

            val formatButton = when (activity.baseConfig.dateFormat) {
                DATE_FORMAT_ONE -> changeDateTimeDialogRadioOne
                DATE_FORMAT_TWO -> changeDateTimeDialogRadioTwo
                DATE_FORMAT_THREE -> changeDateTimeDialogRadioThree
                DATE_FORMAT_FOUR -> changeDateTimeDialogRadioFour
                DATE_FORMAT_FIVE -> changeDateTimeDialogRadioFive
                DATE_FORMAT_SIX -> changeDateTimeDialogRadioSix
                DATE_FORMAT_SEVEN -> changeDateTimeDialogRadioSeven
                DATE_FORMAT_EIGHT -> changeDateTimeDialogRadioEight
                DATE_FORMAT_MTSLONG -> changeDateTimeDialogRadioMtsLong
                else -> changeDateTimeDialogRadioMts
            }
            formatButton.isChecked = true
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.save) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view.root, this)
            }
    }

    private fun dialogConfirmed() {
        activity.baseConfig.dateFormat = when (view.changeDateTimeDialogRadioGroup.checkedRadioButtonId) {
            view.changeDateTimeDialogRadioOne.id -> DATE_FORMAT_ONE
            view.changeDateTimeDialogRadioTwo.id -> DATE_FORMAT_TWO
            view.changeDateTimeDialogRadioThree.id -> DATE_FORMAT_THREE
            view.changeDateTimeDialogRadioFour.id -> DATE_FORMAT_FOUR
            view.changeDateTimeDialogRadioFive.id -> DATE_FORMAT_FIVE
            view.changeDateTimeDialogRadioSix.id -> DATE_FORMAT_SIX
            view.changeDateTimeDialogRadioSeven.id -> DATE_FORMAT_SEVEN
            view.changeDateTimeDialogRadioEight.id -> DATE_FORMAT_EIGHT
            view.changeDateTimeDialogRadioMtsLong.id -> DATE_FORMAT_MTSLONG
            else -> DATE_FORMAT_MTS
        }

        activity.baseConfig.use24HourFormat = view.changeDateTimeDialog24Hour.isChecked
        activity.baseConfig.useDayOfWeekInTimeFormat = view.changeDateTimeDialogDayofweek.isChecked
        callback()
    }

    private fun formatDateSample(format: String): String {
        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.timeInMillis = timeSample
        return DateFormat.format(format, cal).toString()
    }
}

private val timeSample: Long
    get() = System.currentTimeMillis()
