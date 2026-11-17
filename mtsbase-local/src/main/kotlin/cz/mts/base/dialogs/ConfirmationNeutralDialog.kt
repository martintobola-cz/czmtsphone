package cz.mts.base.dialogs

import androidx.appcompat.app.AlertDialog
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.databinding.DialogMessageBinding
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff

/**
 * Podobný jako [ConfirmationAdvancedDialog], ale s třetím (neutral) tlačítkem.
 * Neutral tlačítko dialog NEZAVÍRÁ, jen zavolá [onNeutral] (např. otevření odkazu).
 * Positive, negative i zrušení dialogu (zpět, klepnutí mimo) ho zavřou.
 *
 * @param negative 0 = tlačítko se nezobrazí
 * @param cancelOnTouchOutside když je false, klepnutí mimo dialog nezavře,
 *        tlačítko zpět ho ale zruší a zavolá [onNegative]
 */
class ConfirmationNeutralDialog(
    activity: BaseSimpleActivity,
    message: String = "",
    messageId: Int = R.string.proceed_with_deletion,
    positive: Int = R.string.yes,
    neutral: Int,
    negative: Int = R.string.no,
    dialogTitle: String = "",
    val cancelOnTouchOutside: Boolean = false,
    val onNeutral: () -> Unit,
    val onNegative: () -> Unit = {},
    val onPositive: () -> Unit
) {
    private var dialog: AlertDialog? = null

    init {
        val view = DialogMessageBinding.inflate(activity.layoutInflater, null, false)
        view.message.text = message.ifEmpty { activity.resources.getString(messageId) }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(positive) { _, _ -> positivePressed() }
            .setNeutralButton(neutral, null) // skutečný listener se nastaví až po show()

        if (negative != 0) {
            builder.setNegativeButton(negative) { _, _ -> negativePressed() }
        }

        builder.setOnCancelListener { negativePressed() }

        builder.apply {
            activity.setupDialogStuff(
                view.root,
                this,
                titleText = dialogTitle,
                cancelOnTouchOutside = cancelOnTouchOutside
            ) { alertDialog ->
                dialog = alertDialog
                // Vlastní listener, aby neutral tlačítko dialog nezavřelo
                alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    onNeutral()
                }
            }
        }
    }

    private fun positivePressed() {
        dialog?.dismiss()
        onPositive()
    }

    private fun negativePressed() {
        dialog?.dismiss()
        onNegative()
    }
}