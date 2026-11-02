package cz.mts.phone.dialogs

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.extensions.viewBinding
import cz.mts.phone.R
import cz.mts.phone.databinding.DialogEditShakeTextBinding
import cz.mts.base.extensions.baseConfig as config

class EditShakeCallTextDialog(
    private val activity: BaseSimpleActivity,
    private val callback: (String) -> Unit = {}
) {
    private val binding by activity.viewBinding(DialogEditShakeTextBinding::inflate)

    init {
        val bShakeOn = activity.config.shakeEffectConfirmingCall
        val callNowText = activity.config.callNowMtsText
        val callNowTextDefault = if (callNowText.isBlank() && bShakeOn) activity.getString(R.string.call_now_mts_a) + "\n" + activity.getString(R.string.call_now_mts_b) + "\n\u260E"
                                 else if (callNowText.isBlank() && !bShakeOn) activity.getString(R.string.call_now_mts_a) + " " + activity.getString(R.string.call_now_mts_b) + " \u260E "
                                 else callNowText

        binding.editShakeTextEdittext.setText(callNowTextDefault)

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)      // listener nastavíme níž, aby dialog nezavřel sám
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.default_reset, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { dialog ->
                    setupDialog(dialog)
                }
            }
    }

    private fun setupDialog(dialog: AlertDialog) {
        binding.editShakeTextEdittext.apply {
            requestFocus()
            setSelection(text?.length ?: 0)
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = binding.editShakeTextEdittext.text?.toString().orEmpty()
            activity.config.callNowMtsText = value
            callback(value)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            binding.editShakeTextEdittext.setText("")
            binding.editShakeTextEdittext.setSelection("".length)
        }
    }
}
