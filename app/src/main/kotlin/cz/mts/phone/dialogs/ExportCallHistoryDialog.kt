package cz.mts.phone.dialogs

import androidx.appcompat.app.AlertDialog
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.databinding.DialogExportCallHistoryBinding
import cz.mts.base.extensions.getCurrentFormattedDateTime
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.isAValidFilename
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.extensions.toast
import cz.mts.base.extensions.value

class ExportCallHistoryDialog(
    private val activity: SimpleActivity,
    iType: Int = 0,
    callback: (filename: String) -> Unit
) {
    init {
        val sTitle = if (iType == 0) R.string.export_call_history
                     else            R.string.export_contacts

        val sFileName = if (iType == 0) "call_history_${activity.getCurrentFormattedDateTime()}"
                        else            "contacts_${activity.getCurrentFormattedDateTime()}"

        val binding = DialogExportCallHistoryBinding.inflate(activity.layoutInflater).apply {
            exportCallHistoryFilename.setText(sFileName)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, sTitle) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = binding.exportCallHistoryFilename.value
                        when {
                            filename.isEmpty()        -> activity.toast(R.string.empty_name)
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
