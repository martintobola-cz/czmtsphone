package cz.mts.phone.models

import cz.mts.base.models.PhoneNumber

data class PhoneTypeUi(
    val rawType: Int,
    val label: String
)

sealed interface PhonePickerItem {

    data class Header(
        val contactId: Int,
        val contactName: String
    ) : PhonePickerItem

    data class Number(
        val contactId: Int,
        val contactName: String,
        val phone: PhoneNumber,
        val type: PhoneTypeUi,
        var isSelected: Boolean
    ) : PhonePickerItem

    data class Email(
        val contactId: Int,
        val contactName: String,
        val address: String,
        val typeLabel: String,
        var isSelected: Boolean
    ) : PhonePickerItem
}

/** Výsledek výběru v dialogu: buď čísla pro SMS, nebo jedna e-mailová adresa. */
sealed interface SendTarget {
    data class Numbers(val numbers: List<String>) : SendTarget
    data class EmailAddress(val address: String) : SendTarget
}
