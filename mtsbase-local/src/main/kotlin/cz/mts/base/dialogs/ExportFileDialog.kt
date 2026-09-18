package cz.mts.base.dialogs

import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.databinding.DialogExportBlockedNumbersBinding
import cz.mts.base.extensions.*

/**
 * Obecný dialog "zadej název souboru pro export" - dřív existoval zvlášť pro blokovaná čísla
 * (tenhle) a zvlášť ExportCallHistoryDialog v cz.mts.phone (historie volání / kontakty / záloha
 * nastavení, rozlišené parametrem iType). Sjednoceno do jednoho dialogu v base modulu, protože
 * dělají to samé - jen s jiným titulkem a jiným přednastaveným názvem souboru.
 *
 * Pro zpětnou kompatibilitu mají titleRes i defaultFilename výchozí hodnoty odpovídající
 * původnímu (pouze blokovaná čísla) chování, takže `ExportBlockedNumbersDialog(activity) { ... }`
 * funguje beze změny.
 *
 * @param titleRes         resource ID titulku dialogu - může být z libovolného modulu
 *                         (např. cz.mts.phone.R.string.export_call_history), string se
 *                         načítá až za běhu přes activity.getString().
 * @param defaultFilename  přednastavený název souboru (bez přípony); volající si ho sestaví
 *                         podle typu exportu (blokovaná čísla / historie volání / kontakty / ...).
 */
class ExportFileDialog(
    val activity: BaseSimpleActivity,
    @StringRes titleRes: Int = R.string.export_blocked_numbers,
    defaultFilename: String = "${activity.getString(R.string.blocked_numbers)}_${activity.getCurrentFormattedDateTime()}",
    callback: (filename: String) -> Unit,
) {
    init {
        val view = DialogExportBlockedNumbersBinding.inflate(activity.layoutInflater, null, false).apply {
            exportBlockedNumbersFilename.setText(defaultFilename)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view.root, this, titleRes) { alertDialog ->
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
