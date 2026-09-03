package cz.mts.base.models.contacts

import android.graphics.Bitmap
import android.provider.ContactsContract
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import cz.mts.base.extensions.normalizeString
import cz.mts.base.helpers.PhoneNumberHelper
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.MTS_PHONE
import cz.mts.base.helpers.SORT_BY_FIRST_NAME
import cz.mts.base.helpers.SORT_BY_FULL_NAME
import cz.mts.base.helpers.SORT_BY_MIDDLE_NAME
import cz.mts.base.helpers.SORT_BY_SURNAME
import cz.mts.base.helpers.SORT_DESCENDING
import cz.mts.base.models.PhoneNumber
import java.util.Locale

@Serializable
data class Contact(
    var id: Int,
    var prefix: String = "",
    var firstName: String = "",
    var middleName: String = "",
    var surname: String = "",
    var suffix: String = "",
    var nickname: String = "",
    var photoUri: String = "",
    var phoneNumbers: ArrayList<PhoneNumber> = arrayListOf(),
    var emails: ArrayList<Email> = arrayListOf(),
    var addresses: ArrayList<Address> = arrayListOf(),
    var events: ArrayList<Event> = arrayListOf(),
    var source: String = "",
    var starred: Int = 0,
    var contactId: Int,
    var thumbnailUri: String = "",
    @Contextual
    var photo: Bitmap? = null,
    var notes: String = "",
    var groups: ArrayList<Group> = arrayListOf(),
    var organization: Organization = Organization("", ""),
    var websites: ArrayList<String> = arrayListOf(),
    var IMs: ArrayList<IM> = arrayListOf(),
    var mimetype: String = "",
    var ringtone: String? = ""
) : Comparable<Contact> {
    var rawId = id
    val name = getNameToDisplay()
    var birthdays = events.filter { it.type == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY }.map { it.value }.toMutableList() as ArrayList<String>
    var anniversaries =
        events.filter { it.type == ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY }.map { it.value }.toMutableList() as ArrayList<String>

    companion object {
        var sorting = 0
        var startWithSurname = false
    }

    override fun compareTo(other: Contact): Int {
        var result = when {
            sorting and SORT_BY_FIRST_NAME != 0 -> {
                val firstString = firstName.normalizeString()
                val secondString = other.firstName.normalizeString()
                compareUsingStrings(firstString, secondString, other)
            }

            sorting and SORT_BY_MIDDLE_NAME != 0 -> {
                val firstString = middleName.normalizeString()
                val secondString = other.middleName.normalizeString()
                compareUsingStrings(firstString, secondString, other)
            }

            sorting and SORT_BY_SURNAME != 0 -> {
                val firstString = surname.normalizeString()
                val secondString = other.surname.normalizeString()
                compareUsingStrings(firstString, secondString, other)
            }

            sorting and SORT_BY_FULL_NAME != 0 -> {
                val firstString = getNameToDisplay().normalizeString()
                val secondString = other.getNameToDisplay().normalizeString()
                compareUsingStrings(firstString, secondString, other)
            }

            else -> compareUsingIds(other)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }

    private fun compareUsingStrings(firstString: String, secondString: String, other: Contact): Int {
        var firstValue = firstString
        var secondValue = secondString

        if (firstValue.isEmpty() && firstName.isEmpty() && middleName.isEmpty() && surname.isEmpty()) {
            val fullCompany = getFullCompany()
            if (fullCompany.isNotEmpty()) {
                firstValue = fullCompany.normalizeString()
            } else if (emails.isNotEmpty()) {
                firstValue = emails.first().value
            }
        }

        if (secondValue.isEmpty() && other.firstName.isEmpty() && other.middleName.isEmpty() && other.surname.isEmpty()) {
            val otherFullCompany = other.getFullCompany()
            if (otherFullCompany.isNotEmpty()) {
                secondValue = otherFullCompany.normalizeString()
            } else if (other.emails.isNotEmpty()) {
                secondValue = other.emails.first().value
            }
        }

        return if (firstValue.firstOrNull()?.isLetter() == true && secondValue.firstOrNull()?.isLetter() == false) {
            -1
        } else if (firstValue.firstOrNull()?.isLetter() == false && secondValue.firstOrNull()?.isLetter() == true) {
            1
        } else {
            if (firstValue.isEmpty() && secondValue.isNotEmpty()) {
                1
            } else if (firstValue.isNotEmpty() && secondValue.isEmpty()) {
                -1
            } else {
                if (firstValue.equals(secondValue, ignoreCase = true)) {
                    getNameToDisplay().compareTo(other.getNameToDisplay(), true)
                } else {
                    firstValue.compareTo(secondValue, true)
                }
            }
        }
    }

    private fun compareUsingIds(other: Contact): Int {
        val firstId = id
        val secondId = other.id
        return firstId.compareTo(secondId)
    }

    fun getBubbleText(): String {
        return try {
            var name = when {
                isABusinessContact() -> getFullCompany()
                sorting and SORT_BY_SURNAME != 0 && surname.isNotEmpty() -> surname
                sorting and SORT_BY_MIDDLE_NAME != 0 && middleName.isNotEmpty() -> middleName
                sorting and SORT_BY_FIRST_NAME != 0 && firstName.isNotEmpty() -> firstName
                startWithSurname -> surname
                else -> firstName
            }

            if (name.isEmpty()) {
                name = getNameToDisplay()
            }

            name
        } catch (e: Exception) {
            ""
        }
    }

    fun getFirstLetter(): String {
        val bubbleText = getBubbleText()
        val character = if (bubbleText.isNotEmpty()) bubbleText.substring(0, 1) else ""
        return character.uppercase(Locale.getDefault()).normalizeString()
    }

    fun getNameToDisplay(): String {

        // --- FULL NAME ---
        if (
            prefix.isNotEmpty() ||
            firstName.isNotEmpty() ||
            middleName.isNotEmpty() ||
            surname.isNotEmpty() ||
            suffix.isNotEmpty()
        ) {
            val sb = StringBuilder(64)

            if (prefix.isNotEmpty()) {
                sb.append(prefix).append(' ')
            }

            if (startWithSurname) {
                if (surname.isNotEmpty()) {
                    sb.append(surname)
                    if (firstName.isNotEmpty() || middleName.isNotEmpty()) {
                        sb.append(", ")
                    } else {
                        sb.append(' ')
                    }
                }
                if (firstName.isNotEmpty()) {
                    sb.append(firstName).append(' ')
                }
                if (middleName.isNotEmpty()) {
                    sb.append(middleName).append(' ')
                }
            } else {
                if (firstName.isNotEmpty()) {
                    sb.append(firstName).append(' ')
                }
                if (middleName.isNotEmpty()) {
                    sb.append(middleName).append(' ')
                }
                if (surname.isNotEmpty()) {
                    sb.append(surname).append(' ')
                }
            }

            if (suffix.isNotEmpty()) {
                sb.append(", ").append(suffix)
            }

            if (sb.isNotBlank()) {
                return sb.toString().trimEnd()
            }
        }

        // jméno nic k zobrazení nepřineslo, takže email, company anebo prostě číslo
        val company = getFullCompany()
        if (company.isNotBlank()) return company

        val email = emails.firstOrNull()?.value
        if (!email.isNullOrBlank()) return email

        val phone = phoneNumbers.firstOrNull()?.value //
        if (!phone.isNullOrBlank()) return phone

        return "??? temporary"
    }



    fun getStringToCompare(): String {
        val photoToUse = if (isPrivate()) null else photo
        return copy(
            id = 0,
            prefix = "",
            firstName = getNameToDisplay().lowercase(Locale.getDefault()),
            middleName = "",
            surname = "",
            suffix = "",
            nickname = "",
            photoUri = "",
            phoneNumbers = ArrayList(),
            emails = ArrayList(),
            events = ArrayList(),
            source = "",
            addresses = ArrayList(),
            starred = 0,
            contactId = 0,
            thumbnailUri = "",
            photo = photoToUse,
            notes = "",
            groups = ArrayList(),
            websites = ArrayList(),
            organization = Organization("", ""),
            IMs = ArrayList(),
            ringtone = ""
        ).toString()
    }

    fun getHashToCompare() = getStringToCompare().hashCode()

    fun getFullCompany(): String {
        var fullOrganization = if (organization.company.isEmpty()) "" else "${organization.company}, "
        fullOrganization += organization.jobPosition
        return fullOrganization.trim().trimEnd(',')
    }

    fun isABusinessContact() =
        prefix.isEmpty() && firstName.isEmpty() && middleName.isEmpty() && surname.isEmpty() && suffix.isEmpty() && organization.isNotEmpty()

    fun doesContainPhoneNumber(text: String, convertLetters: Boolean = false): Boolean {
        return if (text.isNotEmpty()) {
            val normalizedText = if (convertLetters) normalizeDigitsOnly(text) else text
            phoneNumbers.any {
                PhoneNumberHelper.areSamePhoneNumber(it.normalizedNumber, normalizedText) ||
                    it.value.contains(text) ||
                    it.normalizedNumber.contains(normalizedText) ||
                    normalizeDigitsOnly(it.value).contains(normalizedText)
            }
        } else {
            false
        }
    }

    fun doesHavePhoneNumber(text: String): Boolean {
        return if (text.isNotEmpty())
        {
            val normalizedText = normalizeDigitsOnly(text)
            //po normalizaci nezbylo nic
            if (normalizedText.isEmpty()) {
                phoneNumbers.map { it.normalizedNumber }.any { phoneNumber ->
                    phoneNumber == text
                }
            } else {
                phoneNumbers.map { it.normalizedNumber }.any { phoneNumber ->
                    PhoneNumberHelper.areSamePhoneNumber(phoneNumber, normalizedText) ||
                        phoneNumber == text ||
                        phoneNumber == normalizedText
                }
            }
        } else {
            false
        }
    }

    fun isPrivate() = source == MTS_PHONE

    fun getSignatureKey() = photoUri.ifEmpty { hashCode() }

    fun getPrimaryNumber(): String? {
        val primaryNumber = phoneNumbers.firstOrNull { it.isPrimary }
        return primaryNumber?.normalizedNumber ?: phoneNumbers.firstOrNull()?.normalizedNumber
    }

}
