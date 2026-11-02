package cz.mts.phone.dialogs

import android.view.LayoutInflater
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.R
import cz.mts.phone.adapters.SelectNumbersAdapter
import cz.mts.phone.models.PhonePickerItem
import cz.mts.phone.models.SendTarget

/**
 * Výběr příjemců pro SMS / zprávu (čísla, nebo jeden e-mail).
 * Pokud žádný kontakt nemá víc než jednu možnost, dialog se nezobrazí
 * a rovnou se zavolá onConfirm.
 */
class SelectSendTargetDialog(
    activity: BaseSimpleActivity,
    items: List<PhonePickerItem>,
    private val onConfirm: (SendTarget) -> Unit
) {
    private val numberItems = items.filterIsInstance<PhonePickerItem.Number>()
    private val emailItems = items.filterIsInstance<PhonePickerItem.Email>()

    init {
        val hasEmails = emailItems.isNotEmpty()

        // dialog je potřeba jen pokud má některý kontakt víc než jednu možnost (číslo/e-mail)
        val needsChoice = (numberItems.map { it.contactId } + emailItems.map { it.contactId })
            .groupingBy { it }
            .eachCount()
            .any { it.value > 1 }

        if (!needsChoice) {
            targetFromSelection()?.let(onConfirm)
        } else {
            val view = LayoutInflater.from(activity).inflate(R.layout.dialog_select_numbers, null)
            view.findViewById<MyRecyclerView>(R.id.mtsRecyclerView).apply {
                setHasFixedSize(true)
                // s e-maily (= jeden kontakt) smí být vybraná právě jedna možnost
                adapter = SelectNumbersAdapter(
                    items,
                    allowMultipleNumbersPerContact = !hasEmails
                )
            }

            val sendLabel = if (hasEmails) R.string.send_message else R.string.send_sms

            val builder = activity.getAlertDialogBuilder()
                .setNeutralButton(sendLabel) { _, _ -> targetFromSelection()?.let(onConfirm) }
                .setPositiveButton(R.string.cancel, null)

            activity.setupDialogStuff(view, builder)
        }
    }

    private fun targetFromSelection(): SendTarget? {
        // e-mail a číslo se nikdy nemíchají, e-mail je jen u jednoho kontaktu
        emailItems.firstOrNull { it.isSelected }?.let {
            return SendTarget.EmailAddress(it.address)
        }
        val numbers = numberItems
            .filter { it.isSelected }
            .mapNotNull { it.phone.normalizedNumber }
            .distinct()
        return if (numbers.isNotEmpty()) SendTarget.Numbers(numbers) else null
    }
}
