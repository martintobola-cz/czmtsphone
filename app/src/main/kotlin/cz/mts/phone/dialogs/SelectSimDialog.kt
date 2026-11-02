package cz.mts.phone.dialogs

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.views.MyAppCompatCheckbox
import cz.mts.phone.R

data class SimChoice(val subId: Int, val title: String)

/**
 * Informační text + checkbox pro každou SIM + OK / Zrušit. Nic není předvybráno,
 * OK je neaktivní, dokud není vybraná aspoň jedna SIM. Dříve showSelectSimDialog v TransferActivity.
 * Checkboxy jsou MyAppCompatCheckbox, takže je obarví setupDialogStuff (updateTextColors).
 */
class SelectSimDialog(
    activity: BaseSimpleActivity,
    choices: List<SimChoice>,
    private val onConfirm: (List<Int>) -> Unit
) {
    private val selected = HashSet<Int>()
    private var dialog: AlertDialog? = null

    init {
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_select_sim, null)

        view.findViewById<TextView>(R.id.selectSimMessage).apply {
            setText(R.string.transfer_sim_import_message)
            setTextColor(activity.getProperTextColor())
        }

        val list = view.findViewById<LinearLayout>(R.id.selectSimList)
        choices.forEach { choice ->
            val checkBox = inflater.inflate(R.layout.item_select_sim_checkbox, list, false) as MyAppCompatCheckbox
            checkBox.text = choice.title
            checkBox.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(choice.subId) else selected.remove(choice.subId)
                updateOkButton()
            }
            list.addView(checkBox)
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ ->
                val subIds = choices.map { it.subId }.filter { it in selected }
                if (subIds.isNotEmpty()) onConfirm(subIds)
            }
            .setNegativeButton(R.string.cancel, null)

        activity.setupDialogStuff(view, builder) { alertDialog ->
            dialog = alertDialog
            updateOkButton()
        }
    }

    private fun updateOkButton() {
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.let {
            val enabled = selected.isNotEmpty()
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.4f
        }
    }
}
