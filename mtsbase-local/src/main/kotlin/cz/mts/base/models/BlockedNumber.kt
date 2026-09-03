package cz.mts.base.models

import androidx.compose.runtime.Immutable

@Immutable
data class BlockedNumber(
    val id: Long,
    val number: String,                //BlockedNumbers.COLUMN_ORIGINAL_NUMBER
    val normalizedNumber: String,      //BlockedNumbers.COLUMN_E164_NUMBER
    val numberToCompare: String,       //normalizeDigitsOnly(BlockedNumbers.COLUMN_E164_NUMBER)
    val contactName: String? = null
)
//Context.getBlockedNumbers() tam se to plní
