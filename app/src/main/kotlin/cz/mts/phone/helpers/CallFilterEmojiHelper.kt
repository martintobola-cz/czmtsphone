package cz.mts.phone.helpers

import android.content.Intent
import android.net.Uri
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun categoryToEmoji(sLabel: String, sStatus: String, bRetBlankString: Boolean = false): String {
        val statusEmoji = when (sStatus.trim().lowercase()) {
            "spam" -> "⛔"
            "safe" -> "🟢"
            else -> ""
        }
        val labelEmoji = when (sLabel.trim().lowercase()) {
            "scam" -> "🎣"
            "telemarketing", "surveys" -> "🗣️"
            "silent_call" -> "🤖"
            "financial_services" -> "💰"
            "debt_collection" -> "🥊"
            "business" -> "🏢"
            "retail" -> "🛒"
            "personal_blacklist" -> "📓"
            //"private_number" -> "🕶️"
            else -> ""
        }

        val result = statusEmoji + labelEmoji
            //listOf(statusEmoji, labelEmoji)
           // .filter { it.isNotBlank() }
           // .joinToString(",")

        return if (!bRetBlankString) result.ifBlank { "❔" } else result
    }


data class RatingResult(val text: String, val isSpam: Boolean)

    fun checkNumberForRating(result: CallFilterResult, number: String, bRecent: Boolean): RatingResult {

        //error výsledek
        if (!result.ok) return RatingResult("💥😕", false)

        val sRetNumber = normalizeDigitsOnly(result.normalizedNumber, false)
        val sVstupniNumber = normalizeDigitsOnly(number, false)
        val bSpamCallfilterApp = result.status.trim().lowercase() == "spam"

        //vstupní anebo vrácené číslo je prázdné
        if (sVstupniNumber.isBlank() || sRetNumber.isBlank()) {
            return RatingResult("∅😕", false)
        }

        //vstupní a vrácené číslo si nejsou podobné
        if (!sRetNumber.contains(sVstupniNumber) && !sVstupniNumber.contains(sRetNumber)) {
            return RatingResult("≠😕", false)
        }

        // čísla si odpovídají takže vyhodnotíme
        val callFilterEmoji = categoryToEmoji(result.label, result.status, true)
        val pos = result.iResultPositive
        val neg = result.iResultNegative
        val neu = result.iResultNeutral
        val total = pos + neg + neu

        var bSpam = false
        val sRetText: String

        when {
            //1. je to spam a známe labelEmoji
            bSpamCallfilterApp && callFilterEmoji.isNotBlank() -> {
                sRetText = callFilterEmoji
                bSpam = true
            }
            //2. alespoň 2 hodnocení a pozitivní převažuje nad negativním+neutrálním
            (total > 1) && (pos > (neg + neu)) -> {
                sRetText = " 🟢 ${pos}x"
            }
            //3. alespoň 2 hodnocení a negativní převažuje nad pozitivním+neutrálním
            (total > 1) && (neg > (pos + neu)) -> {
                sRetText = " ⛔ ${neg}x"
                bSpam = true
            }
            //4. nedá se jednoznačně určit
            //(total > 1) && ((neg + pos) > 0) -> {
            //    sRetText = " ⛔ ${neg}x, 🟢 ${pos}x"
            //}
            //5. dáme hodnocení Callfilter.app
            else -> {
                sRetText = categoryToEmoji(result.label, result.status)
            }
        }


        return if (!bRecent) RatingResult(sRetText, bSpamCallfilterApp || bSpam)
        else when {
            bSpamCallfilterApp -> RatingResult(categoryToEmoji(result.label, result.status), true)
            bSpam -> RatingResult("⛔", true)
            else -> RatingResult("", false)
        }
    }

    fun openSpamNumberWeb (activity : BaseSimpleActivity, sPhoneNumber : String)
    {
        val url = "https://www.muzutozvednout.cz/search?q=" + URLEncoder.encode(sPhoneNumber.filter { it.isDigit() || it == '+' }, StandardCharsets.UTF_8.toString())
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
        } catch (e: Exception) {
            activity.toast(e.message.toString())
        }
    }
