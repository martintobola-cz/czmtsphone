package cz.mts.phone.models

import android.telecom.PhoneAccountHandle

data class SIMAccount(
    val subscriptionId : Int,
    val indexid: Int,
    val handle: PhoneAccountHandle,
    val label: String,
    val phoneNumber: String,
    val color: Int,
    val type : Int,
    val countryISO : String,
)
