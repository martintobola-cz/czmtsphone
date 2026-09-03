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
    }



