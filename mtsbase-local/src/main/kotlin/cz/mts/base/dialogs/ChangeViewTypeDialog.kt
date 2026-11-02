package cz.mts.base.dialogs

import androidx.appcompat.app.AlertDialog
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.databinding.DialogChangeViewTypeBinding
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.helpers.VIEW_TYPE_GRID
import cz.mts.base.helpers.VIEW_TYPE_LIST

class ChangeViewTypeDialog(val activity: BaseSimpleActivity, val callback: () -> Unit) {
    private var view: DialogChangeViewTypeBinding
    private var config = activity.baseConfig
    private var dialog: AlertDialog? = null

    init {
        view = DialogChangeViewTypeBinding.inflate(activity.layoutInflater, null, false).apply {
            val viewToCheck = when (config.viewType) {
                VIEW_TYPE_GRID -> changeViewTypeDialogRadioGrid.id
                else -> changeViewTypeDialogRadioList.id
            }

            changeViewTypeDialogRadio.check(viewToCheck)

            changeViewTypeDialogRadio.setOnCheckedChangeListener { _, checkedId ->
                config.viewType = if (checkedId == changeViewTypeDialogRadioGrid.id) {
                    VIEW_TYPE_GRID
                } else {
                    VIEW_TYPE_LIST
                }
                callback()
                dialog?.dismiss()
            }
        }

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(view.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }
}
