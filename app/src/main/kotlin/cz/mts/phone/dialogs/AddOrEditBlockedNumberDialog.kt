package cz.mts.phone.dialogs

import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.isValidNegativeBlockedNumberRule
import cz.mts.base.extensions.toast
import cz.mts.base.models.BlockedNumber
import cz.mts.phone.R
import cz.mts.phone.databinding.DialogAddBlockedNumberBinding

/**
 * Necompose náhrada za dřívější Compose dialog AddOrEditBlockedNumberAlertDialog.
 * Pokud v projektu existuje jiný, propracovanější (Compose) dialog, který chcete zachovat,
 * lze místo tohoto zavolat ten - tady jde jen o minimální plně View-based variantu,
 * aby byla ManageBlockedNumbersActivity kompletně necompose.
 *
 * blockedNumber == null -> přidání nového čísla
 * blockedNumber != null -> úprava / možnost odblokovat (tlačítko unblock)
 *
 * onDelete/onSave pracují rovnou s číslem jako String, protože Context.addBlockedNumber(number: String)
 * a Context.deleteBlockedNumber(number: String) v cz.mts.base.extensions berou String, ne BlockedNumber.
 */
class AddOrEditBlockedNumberDialog(
    private val activity: BaseSimpleActivity,
    private val blockedNumber: BlockedNumber?,
    private val onDelete: (String) -> Unit,
    private val onSave: (String) -> Unit,
) {

    init {
        val binding = DialogAddBlockedNumberBinding.inflate(LayoutInflater.from(activity))
        binding.addBlockedNumberEdittext.setText(blockedNumber?.number.orEmpty())

        val properTextColor = activity.getProperTextColor()
        binding.addBlockedNumberEdittext.setTextColor(properTextColor)
        binding.addBlockedNumberHelperText.setTextColor(properTextColor)

        val builder = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)

        if (blockedNumber != null) {
            builder.setNeutralButton(R.string.unblock, null)
        }

        val dialog = builder.create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 64f
                setColor(activity.getProperBackgroundColor())
            }
        )

        dialog.show()

        val primaryColor = activity.getProperPrimaryColor()
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            dialog.getButton(which)?.apply {
                setTextColor(primaryColor)
                setTypeface(typeface, Typeface.BOLD)
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            // plná klávesnice umožní zadat cokoliv; povolené jsou jen číslice, wildcardy * ?,
            // "+" na začátku a "!" na začátku (negativní pravidlo, např. !+420* = zablokuj vše, co nezačíná +420)
            val number = sanitizeBlockedNumberInput(binding.addBlockedNumberEdittext.text.toString())
            if (!isValidBlockedNumberInput(number)) {
                activity.toast(R.string.block_error_string_value)
                return@setOnClickListener
            }

            onSave(number)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            blockedNumber?.number?.let(onDelete)
            dialog.dismiss()
        }
    }
}

/**
 * Nechá jen ASCII číslice, wildcardy * ?, "!" (jen jako první znak) a "+" (jen na začátku,
 * případně hned za "!"). Mezery, pomlčky a ostatní znaky zahodí.
 */
private fun sanitizeBlockedNumberInput(raw: String): String {
    val sb = StringBuilder()
    for (c in raw.trim()) {
        when {
            c in '0'..'9' || c == '*' || c == '?' -> sb.append(c)
            c == '!' && sb.isEmpty() -> sb.append(c)
            c == '+' && (sb.isEmpty() || sb.toString() == "!") -> sb.append(c)
        }
    }
    return sb.toString()
}

/**
 * Negativní pravidlo musí obsahovat aspoň jednu číslici a aspoň jeden wildcard * nebo ?
 * (viz isValidNegativeBlockedNumberRule), ostatní vstupy aspoň číslici nebo wildcard
 * (samotné "+" se nikdy neuloží).
 */
private fun isValidBlockedNumberInput(value: String): Boolean {
    if (value.startsWith("!")) return value.isValidNegativeBlockedNumberRule()
    return value.any { it in '0'..'9' || it == '*' || it == '?' }
}
