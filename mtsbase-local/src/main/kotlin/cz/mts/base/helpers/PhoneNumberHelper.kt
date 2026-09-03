package cz.mts.base.helpers

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import android.util.LruCache
import cz.mts.base.helpers.FastPhoneNumberFormatter.format
import java.util.Locale

object PhoneNumberHelper {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
    private val geocoder: PhoneNumberOfflineGeocoder = PhoneNumberOfflineGeocoder.getInstance()
    private val locale: Locale = Locale.getDefault()
    private val defaultRegion: String = locale.country
    private val parseCache = LruCache<String, Phonenumber.PhoneNumber>(200)
    private const val NONE = "\u0000"
    private val locationCache = LruCache<String, String>(200)
    private val phoneCache = LruCache<String, String>(100)


    private fun parseNumber(number: String?, region: String? = null): Phonenumber.PhoneNumber? {
        if (number.isNullOrBlank()) return null

        val normalized = normalizeDigitsOnly(number)
        if (normalized.isBlank() || normalized.length < 4) return null

        val reg = region?.takeIf { it.length == 2 } ?: defaultRegion
        val key = "$normalized|$reg"

        parseCache.get(key)?.let { return it }

        val parsed = try {
            phoneUtil.parse(normalized, reg)
        } catch (_: NumberParseException) {
            null
        }

        if (parsed != null) parseCache.put(key, parsed)
        return parsed
    }


    /** 1) Geolokace + vlajka */
    fun getCountryWithFlag(number: String?): String {
        val parsed = parseNumber(number) ?: return ""
        val region = phoneUtil.getRegionCodeForNumber(parsed) ?: return ""
        val description = geocoder.getDescriptionForNumber(parsed, locale) ?: ""
        return "${regionToFlag(region)} $region ($description)"
    }

    private fun regionToFlag(code: String): String {
        if (code.length != 2) return ""
        val c1 = Character.toChars(code[0].uppercaseChar() - 'A' + 0x1F1E6)
        val c2 = Character.toChars(code[1].uppercaseChar() - 'A' + 0x1F1E6)
        return String(c1) + String(c2)
    }

    // 2) Normalizace – pouze čísla a + na začátku pokud bylo
    fun normalizeDigitsOnly(number: String?, bNechejPlus : Boolean = true): String {
        if (number.isNullOrBlank()) return ""
        if (bNechejPlus) {
            if (number.any { it == '*' || it == '#' }) return number //pause/wait znaky
        }

        val sb = StringBuilder()
        var startsWithPlus = false
        for ((i, c) in number.withIndex()) {
            if (i == 0 && c == '+') { startsWithPlus = true; continue }
            if (c.isDigit()) sb.append(c)
        }
        return if ((startsWithPlus) && (bNechejPlus)) "+$sb" else sb.toString()
    }

    fun numberForRecents(
        sInput: String,
        format: Boolean = true
    ): String {
        if (!format) return sInput
        return format(sInput, true)
    }

    /** 3) E.164 s fallback region a volitelným formátováním */
      fun normalizeNumberE164(
        numberIN: String?,
        isoCountry: String? = null,
        format: Boolean = true
    ): String {
        val number = normalizeDigitsOnly(numberIN)
        if (number.isBlank()) return ""
        if (number.length < 4) return number

        val region = isoCountry?.takeIf { it.length == 2 } ?: defaultRegion
        val cacheKey = "$number|$region|$format"

        // Zkontrolovat cache
        phoneCache.get(cacheKey)?.let { return it }

        val result = try {
            val parsed = phoneUtil.parse(number, region)
            if (!phoneUtil.isValidNumber(parsed)) {
                normalizeDigitsOnly(number)
            } else {
                val e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
                if (!format) e164
                else {
                    // Formátované podle mezinárodního standardu (ISO zvyklosti)
                //    val countryRegion = phoneUtil.getRegionCodeForCountryCode(parsed.countryCode) ?: region
                    val formatType =  PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
                    phoneUtil.format(parsed, formatType)
                }
            }
        } catch (_: NumberParseException) {
            normalizeDigitsOnly(number)
        }

        // Uložit do cache
        phoneCache.put(cacheKey, result)
        return result
    }


    /** 4) Bezpečná geolokace pro UI */

    fun getLocationSafeForUI(number: String?, simIndex: Int): String {
        if (number.isNullOrBlank()) return ""

        val inputISO = MySIMcountryISO.getISO(simIndex)
        val inputRegion = inputISO.takeIf { it.length == 2 } ?: defaultRegion

        val normalized = normalizeDigitsOnly(number)
        if (normalized.isBlank() || normalized.length < 4) return ""

        val cacheKey = "$normalized|$inputRegion|${locale.toLanguageTag()}"
        locationCache.get(cacheKey)?.let { return if (it == NONE) "" else it }

        val parsed = parseNumber(normalized, inputRegion) ?: run {
            locationCache.put(cacheKey, NONE)
            return ""
        }

        val region = phoneUtil.getRegionCodeForNumber(parsed)
        if (region.isNullOrBlank() || region.equals(inputRegion, ignoreCase = true)) {
            locationCache.put(cacheKey, NONE)
            return ""
        }

        val description = geocoder.getDescriptionForNumber(parsed, locale)
        if (description.isNullOrBlank()) {
            locationCache.put(cacheKey, NONE)
            return ""
        }

        locationCache.put(cacheKey, description)
        return description
    }


    /** Rychlá shoda čísel (EXACT/NSN) */
    fun areSamePhoneNumber(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return try {
            when (phoneUtil.isNumberMatch(a, b)) {
                PhoneNumberUtil.MatchType.EXACT_MATCH,
                PhoneNumberUtil.MatchType.NSN_MATCH -> true
                else -> false
            }
        } catch (_: Exception) { false }
    }

    /** Text obsahuje číslo */
    fun containsNumber(text: String?, number: String?): Boolean {
        if (text.isNullOrBlank() || number.isNullOrBlank()) return false
        val t = normalizeDigitsOnly(text)
        val n = normalizeDigitsOnly(number)
        return t.contains(n)
    }

    fun isPhoneNumber(sString: String, isoCountry: String? = null): Boolean {
        if ((sString.isBlank()) || (sString.length < 4))  return false
        val region = isoCountry?.takeIf { it.length == 2 } ?: Locale.getDefault().country
        return try {
            val parsed = phoneUtil.parse(sString, region)
            phoneUtil.isValidNumber(parsed)
        } catch (_: NumberParseException) {
            false
        }
    }
}
