package cz.mts.base.helpers

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object FastPhoneNumberFormatter {

    private val phoneUtil: PhoneNumberUtil by lazy(LazyThreadSafetyMode.NONE) {
        PhoneNumberUtil.getInstance()
    }

    @Volatile
    private var cachedFallbackRegion: String? = null

    fun invalidateRegionCache() {
        cachedFallbackRegion = null
    }

    fun format(
        raw: String?,
        enableFormatting: Boolean,
        regionOverride: String? = null
    ): String {
        if (raw == null) return ""

        val input = raw.trim()
        if (input.isEmpty()) return ""

        // Rychlé guardy – jeden průchod stringem
        var digitCount = 0
        for (c in input) {
            when {
                c == '*' || c == '#' -> return input
                c.isDigit() -> digitCount++
            }
        }

        // Krátká čísla (112, 158, 911, 1234…)
        if (digitCount in 1..4) {
            return input
        }

        // Formátování vypnuto
        if (!enableFormatting) {
            return normalizeFast(input)
        }

        val region = regionOverride ?: getCachedFallbackRegion()

        return formatWithLibPhoneNumber(input, region)
    }

    // ---------- internals ----------

    private fun getCachedFallbackRegion(): String {
        var region = cachedFallbackRegion
        if (region != null) return region

        region = Locale.getDefault().country
            .takeIf { it.isNotBlank() }
            ?: "CZ"

        cachedFallbackRegion = region
        return region
    }

    private fun normalizeFast(input: String): String {
        val sb = StringBuilder(input.length)
        val hasPlus = input.firstOrNull() == '+'

        for (c in input) {
            if (c.isDigit()) sb.append(c)
        }

        return if (hasPlus) "+$sb" else sb.toString()
    }

    private fun formatWithLibPhoneNumber(input: String, region: String): String {
        val isInternationalInput =
            input.startsWith("+") || input.startsWith("00")

        return try {
            val number = phoneUtil.parse(input, region)

            if (!phoneUtil.isValidNumber(number)) {
                input
            } else {
                val format = if (isInternationalInput) {
                    PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
                } else {
                    PhoneNumberUtil.PhoneNumberFormat.NATIONAL
                }

                phoneUtil.format(number, format)
            }
        } catch (_: Exception) {
            input
        }
    }

}
