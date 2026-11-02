package cz.mts.phone.dialogs

import android.view.LayoutInflater
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.getPhoneNumberTypeText
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.models.contacts.Contact
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.R
import cz.mts.phone.adapters.SelectNumbersAdapter
import cz.mts.phone.models.PhonePickerItem
import cz.mts.phone.models.PhoneTypeUi

/**
 * Výběr jednoho čísla kontaktu (kontakt má víc čísel). Nahrazuje dvě shodné kopie
 * showNumberPickerDialog v ManageSpeedDialActivity a ContactsAdapter.
 * Vzhled (pozadí, barvy, tučnost tlačítek) řeší setupDialogStuff.
 */
class SelectPhoneNumberDialog(
    activity: BaseSimpleActivity,
    contact: Contact,
    private val onNumberSelected: (PhonePickerItem.Number) -> Unit
) {
    init {
        val items = buildPhonePickerItems(activity, contact)

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_select_numbers, null)
        view.findViewById<MyRecyclerView>(R.id.mtsRecyclerView).apply {
            setHasFixedSize(true)
            adapter = SelectNumbersAdapter(
                items,
                allowMultipleNumbersPerContact = false
            )
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ ->
                items.filterIsInstance<PhonePickerItem.Number>()
                    .firstOrNull { it.isSelected }
                    ?.let(onNumberSelected)
            }
            .setNegativeButton(R.string.cancel, null)

        activity.setupDialogStuff(view, builder)
    }

    companion object {
        /** Hlavička kontaktu + jeho čísla; předvybrané je první primární číslo. */
        fun buildPhonePickerItems(activity: BaseSimpleActivity, contact: Contact): List<PhonePickerItem> {
            val items = mutableListOf<PhonePickerItem>()

            items += PhonePickerItem.Header(
                contactId = contact.id,
                contactName = contact.getNameToDisplay()
            )

            var primaryAlreadyUsed = false
            contact.phoneNumbers.forEach { phone ->
                val isSelected = phone.isPrimary && !primaryAlreadyUsed
                if (isSelected) primaryAlreadyUsed = true

                items += PhonePickerItem.Number(
                    contactId = contact.id,
                    contactName = contact.getNameToDisplay(),
                    phone = phone,
                    type = PhoneTypeUi(
                        rawType = phone.type,
                        label = " - " + activity.getPhoneNumberTypeText(phone.type, phone.label)
                    ),
                    isSelected = isSelected
                )
            }
            return items
        }
    }
}
