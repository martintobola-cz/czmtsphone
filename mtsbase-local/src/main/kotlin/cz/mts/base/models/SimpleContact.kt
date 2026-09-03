package cz.mts.base.models

import cz.mts.base.extensions.normalizeString
import cz.mts.base.helpers.PhoneNumberHelper
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.SORT_BY_FULL_NAME
import cz.mts.base.helpers.SORT_DESCENDING

data class SimpleContact(
    val rawId: Int,
    val contactId: Int,
    var name: String,
    var photoUri: String,
    var phoneNumbers: ArrayList<PhoneNumber>,
    var birthdays: ArrayList<String>,
    var anniversaries: ArrayList<String>
) : Comparable<SimpleContact> {

    companion object {
        var sorting = -1
    }

    override fun compareTo(other: SimpleContact): Int {
        if (sorting == -1) {
            return compareByFullName(other)
        }

        var result = when {
            sorting and SORT_BY_FULL_NAME != 0 -> compareByFullName(other)
            else -> rawId.compareTo(other.rawId)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }

    private fun compareByFullName(other: SimpleContact): Int {
        val firstString = name.normalizeString()
        val secondString = other.name.normalizeString()

        return if (firstString.firstOrNull()?.isLetter() == true && secondString.firstOrNull()?.isLetter() == false) {
            -1
        } else if (firstString.firstOrNull()?.isLetter() == false && secondString.firstOrNull()?.isLetter() == true) {
            1
        } else {
            if (firstString.isEmpty() && secondString.isNotEmpty()) {
                1
            } else if (firstString.isNotEmpty() && secondString.isEmpty()) {
                -1
            } else {
                firstString.compareTo(secondString, true)
            }
        }
    }

    fun doesContainPhoneNumber(text: String): Boolean {
        return if (text.isNotEmpty()) {
            val normalizedText = normalizeDigitsOnly(text)
            if (normalizedText.isEmpty()) {
                phoneNumbers.map { it.normalizedNumber }.any { phoneNumber ->
                    phoneNumber.contains(text)
                }
            } else {
                phoneNumbers.map { it.normalizedNumber }.any { phoneNumber ->
                    PhoneNumberHelper.areSamePhoneNumber(normalizeDigitsOnly(phoneNumber), normalizedText) ||
                        phoneNumber.contains(text) ||
                        normalizeDigitsOnly(phoneNumber).contains(normalizedText) ||
                        phoneNumber.contains(normalizedText)
                }
            }
        } else {
            false
        }
    }

    fun doesHavePhoneNumber(text: String): Boolean {
        return if (text.isNotEmpty()) {
            val normalizedText = normalizeDigitsOnly(text)
            if (normalizedText.isEmpty()) {
                phoneNumbers.map { it.normalizedNumber }.any { phoneNumber ->
                    phoneNumber == text
                }
            } else {
                phoneNumbers.map { it.normalizedNumber }.any { phoneNumber ->
                    PhoneNumberHelper.areSamePhoneNumber(normalizeDigitsOnly(phoneNumber), normalizedText) ||
                        phoneNumber == text ||
                        normalizeDigitsOnly(phoneNumber) == normalizedText ||
                        phoneNumber == normalizedText
                }
            }
        } else {
            false
        }
    }
}
