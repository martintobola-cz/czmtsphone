package cz.mts.base.helpers

import android.util.LruCache
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import cz.mts.base.helpers.FastPhoneNumberFormatter.format
import java.util.Locale

object PhoneNumberHelper {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
    private val geocoder: PhoneNumberOfflineGeocoder = PhoneNumberOfflineGeocoder.getInstance()
    private val locale: Locale = Locale.getDefault()

    // Jazyk telefonu je jen POSLEDNÍ záchrana pro region (nemusí odpovídat zemi SIM, může být i prázdný).
    // Čte se vždy znovu, ne jen jednou při startu procesu.
    private val defaultRegion: String get() = Locale.getDefault().country
    private val parseCache = LruCache<String, Phonenumber.PhoneNumber>(200)
    private const val NONE = "\u0000"
    private val locationCache = LruCache<String, String>(200)
    private val phoneCache = LruCache<String, String>(100)

    private fun parseNumber(number: String?, region: String? = null): Phonenumber.PhoneNumber? {
        if (number.isNullOrBlank()) return null

        val normalized = normalizeDigitsOnly(number)
        if (normalized.isBlank() || normalized.length < 4) return null

        // region může přijít malými písmeny ("cz" z TelephonyManageru); neznámý se ignoruje a použije se jazyk telefonu
        val reg = normalizeRegionOrNull(region) ?: defaultRegion
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

    /**
     * 1) Geolokace + vlajka
     *
     * @param region země SIM, přes kterou hovor jde (viz Context.getCallRegion()). Určuje, jak se vyloží číslo
     *               v národním formátu (602123456, 0905123456) nebo s mezinárodní předvolbou 00. U čísla s "+"
     *               nehraje roli. null = region z jazyka telefonu (nespolehlivé, jako dosud).
     */
    fun getCountryWithFlag(number: String?, region: String? = null): String {
        val parsed = parseNumber(number, region) ?: return ""
        val numberRegion = phoneUtil.getRegionCodeForNumber(parsed) ?: return ""
        val description = geocoder.getDescriptionForNumber(parsed, locale) ?: ""
        return "${regionToFlag(numberRegion)} $numberRegion ($description)"
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

    /**
     * Dvoupísmenný ISO kód země VELKÝMI písmeny, jinak null.
     * libphonenumber vyžaduje velká písmena a zná jen své regiony, kdežto TelephonyManager
     * (simCountryIso / networkCountryIso) vrací malá ("cz").
     */
    fun normalizeRegionOrNull(iso: String?): String? =
        iso?.trim()
            ?.takeIf { it.length == 2 }
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it in phoneUtil.supportedRegions }

    /**
     * První použitelný region z kandidátů v pořadí, jak je volající předá (typicky země SIM přijímající hovor,
     * pak země sítě). Když žádný není použitelný, vrátí se region z jazyka telefonu – ten může být prázdný
     * (např. jazyk "en" bez země); parsování národních čísel pak selže a vrátí se null / "nejde převést".
     */
    fun resolveRegion(vararg candidates: String?): String {
        for (candidate in candidates) {
            normalizeRegionOrNull(candidate)?.let { return it }
        }
        return defaultRegion
    }

    /**
     * E.164 (+číslice) POUZE pokud je číslo platné pro daný region (číslo s +, resp. s mezinárodní
     * předvolbou země, nepotřebuje region vůbec). Jinak null = "nejde převést", nic se nedomýšlí:
     * krátká / servisní / neplatná čísla (i neplatné číslo začínající +) vrací null.
     *
     * Na rozdíl od normalizeNumberE164() tedy nikdy nevrací "číslice jako fallback".
     */
    fun toE164OrNull(number: String?, region: String? = null): String? {
        val raw = number?.trim()
        if (raw.isNullOrEmpty()) return null
        // * a # jsou servisní kódy, ne telefonní číslo (normalizeDigitsOnly by je vrátilo beze změny)
        if (raw.any { it == '*' || it == '#' }) return null

        val normalized = normalizeDigitsOnly(raw)
        if (normalized.length < 4) return null

        val reg = normalizeRegionOrNull(region) ?: defaultRegion
        val cacheKey = "e164?|$normalized|$reg"
        phoneCache.get(cacheKey)?.let { return if (it == NONE) null else it }

        val result = try {
            val parsed = phoneUtil.parse(normalized, reg)
            if (phoneUtil.isValidNumber(parsed)) {
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                null
            }
        } catch (_: NumberParseException) {
            null
        }

        phoneCache.put(cacheKey, result ?: NONE)
        return result
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

        val region = normalizeRegionOrNull(isoCountry) ?: defaultRegion
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
        val region = normalizeRegionOrNull(isoCountry) ?: defaultRegion
        return try {
            val parsed = phoneUtil.parse(sString, region)
            phoneUtil.isValidNumber(parsed)
        } catch (_: NumberParseException) {
            false
        }
    }
}