package cz.mts.phone.dialogs

import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.beGoneIf
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.extensions.viewBinding
import cz.mts.phone.R
import cz.mts.phone.databinding.DialogChangeSortingBinding
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.helpers.SORT_BY_CUSTOM
import cz.mts.base.helpers.SORT_BY_DATE_CREATED
import cz.mts.base.helpers.SORT_BY_FIRST_NAME
import cz.mts.base.helpers.SORT_BY_FULL_NAME
import cz.mts.base.helpers.SORT_BY_MIDDLE_NAME
import cz.mts.base.helpers.SORT_BY_SURNAME
import cz.mts.base.helpers.SORT_DESCENDING

class ChangeSortingDialog(
    activity: BaseSimpleActivity,
    private val showCustomSorting: Boolean = false,
    private val callback: () -> Unit,
) {
    private val binding by activity.viewBinding(DialogChangeSortingBinding::inflate)

    // FIX: val + přímá inicializace – původní var currSorting = 0 byl okamžitě přepsán
    private val currSorting: Int

    private val activityConfig = activity.config

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.sort_by)
            }

        currSorting = if (showCustomSorting && activityConfig.isCustomOrderSelected) {
            SORT_BY_CUSTOM
        } else {
            activityConfig.sorting
        }

        setupSortRadio()
        setupOrderRadio()
    }

    private fun setupSortRadio() {
        binding.apply {
            sortingDialogRadioSorting.setOnCheckedChangeListener { _, checkedId ->
                val isCustomSorting = checkedId == sortingDialogRadioCustom.id
                sortingDialogRadioOrder.beGoneIf(isCustomSorting)
            }

            val sortBtn = when {
                showCustomSorting && activityConfig.isCustomOrderSelected -> sortingDialogRadioCustom
                currSorting and SORT_BY_FIRST_NAME != 0  -> sortingDialogRadioFirstName
                currSorting and SORT_BY_MIDDLE_NAME != 0 -> sortingDialogRadioMiddleName
                currSorting and SORT_BY_SURNAME != 0     -> sortingDialogRadioSurname
                currSorting and SORT_BY_FULL_NAME != 0   -> sortingDialogRadioFullName
                currSorting and SORT_BY_CUSTOM != 0      -> sortingDialogRadioCustom
                else                                     -> sortingDialogRadioDateCreated
            }
            sortBtn.isChecked = true

            sortingDialogRadioCustom.beGoneIf(!showCustomSorting)
        }
    }

    private fun setupOrderRadio() {
        val orderBtn = if (currSorting and SORT_DESCENDING != 0)
            binding.sortingDialogRadioDescending
        else
            binding.sortingDialogRadioAscending

        orderBtn.isChecked = true
    }

    private fun dialogConfirmed() {
        var sorting = when (binding.sortingDialogRadioSorting.checkedRadioButtonId) {
            R.id.sorting_dialog_radio_first_name  -> SORT_BY_FIRST_NAME
            R.id.sorting_dialog_radio_middle_name -> SORT_BY_MIDDLE_NAME
            R.id.sorting_dialog_radio_surname      -> SORT_BY_SURNAME
            R.id.sorting_dialog_radio_full_name    -> SORT_BY_FULL_NAME
            R.id.sorting_dialog_radio_custom       -> SORT_BY_CUSTOM
            else                                   -> SORT_BY_DATE_CREATED
        }

        if (sorting != SORT_BY_CUSTOM &&
            binding.sortingDialogRadioOrder.checkedRadioButtonId == R.id.sorting_dialog_radio_descending
        ) {
            sorting = sorting or SORT_DESCENDING
        }

        // FIX: jedno přiřazení výrazem místo dvou duplicitních if/else větví
        activityConfig.isCustomOrderSelected = showCustomSorting && sorting == SORT_BY_CUSTOM

        if (!activityConfig.isCustomOrderSelected) {
            activityConfig.sorting = sorting
        }

        callback()
    }
}
