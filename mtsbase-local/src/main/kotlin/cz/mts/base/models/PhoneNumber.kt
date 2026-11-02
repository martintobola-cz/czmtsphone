package cz.mts.base.models

import kotlinx.serialization.Serializable

@Serializable
data class PhoneNumber(
    var value: String,
    var type: Int,
    var label: String,
    var normalizedNumber: String,
    var isPrimary: Boolean = false,
    var dataId: Long = 0L   // _ID řádku v tabulce Data, vyšší = později vloženo
)
