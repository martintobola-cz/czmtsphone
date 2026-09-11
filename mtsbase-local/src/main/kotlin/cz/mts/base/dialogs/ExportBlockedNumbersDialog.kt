package cz.mts.base.dialogs

import androidx.appcompat.app.AlertDialog
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.databinding.DialogExportBlockedNumbersBinding
import cz.mts.base.extensions.*

class ExportBlockedNumbersDialog(
    val activity: BaseSimpleActivity,
    callback: (filename: String) -> Unit,
) {
    init {
        val view = DialogExportBlockedNumbersBinding.inflate(activity.layoutInflater, null, false).apply {
            exportBlockedNumbersFilename.setText("${activity.getString(R.string.blocked_numbers)}_${activity.getCurrentFormattedDateTime()}")
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view.root, this, R.string.export_blocked_numbers) { alertDialog ->
                    alertDialog.showKeyboard(view.exportBlockedNumbersFilename)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = view.exportBlockedNumbersFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(R.string.empty_name)
                            filename.isAValidFilename() -> {
                                callback(filename)
                                alertDialog.dismiss()
                            }

                            else -> activity.toast(R.string.invalid_name)
                        }
                    }
                }
            }
    }
}
