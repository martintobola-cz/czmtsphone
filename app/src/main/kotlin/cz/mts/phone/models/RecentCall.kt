package cz.mts.phone.models
import cz.mts.base.extensions.toDayCode
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly

/**
 * Used at displaying recent calls.
 * For contacts with multiple numbers specify the number and type
 */
@kotlinx.serialization.Serializable
data class RecentCall(
    val id: Int,
    val phoneNumber: String,
    val name: String,
    val photoUri: String,
    val startTS: Long,
    val duration: Int,
    val type: Int,
    val simID: Int,
    val simColor: Int,
    val specificNumber: String,
    val specificType: String,
    val isUnknownNumber: Boolean,
    val groupedCalls: MutableList<RecentCall>? = null,
) : CallLogItem() {
    val dayCode = startTS.toDayCode("yyyy-MM-dd")

    fun doesContainPhoneNumber(text: String): Boolean {
     //   return if (text.toLongOrNull() != null) {
            val normalizedText = normalizeDigitsOnly(text)
            return if (normalizedText.isNotEmpty()) {
                normalizeDigitsOnly(phoneNumber).contains(normalizedText) ||
                phoneNumber.contains(text) ||
                phoneNumber.contains(normalizedText)
        } else {
            false
        }
    }
}
