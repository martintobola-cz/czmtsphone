package cz.mts.phone.helpers

import android.content.Context
import android.telecom.Call
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.phone.R
import cz.mts.phone.models.CallContact

/**
 * JEDINÉ místo, kde se rozhoduje, co se zobrazí o volajícím.
 * Používá fullscreen (CallActivity.updateOtherPersonsInfo), overlay (CallActivity.updateState)
 * i notifikace + hodinky (CallNotificationManagerMTs2.buildCollapsedView).
 *
 * Pravidla viz call_display_rules.txt.
 */

/** Hotový výsledek pro vykreslení. */
data class CallDisplay(
    /** Řádek 1 (fullscreen + overlay). */
    val name: String,
    /** Řádek 2 (fullscreen + overlay), může být prázdný. */
    val number: String,
    /** true = řádek 2 zobrazit (i když je text " " kvůli neposkakování tlačítek). */
    val numberVisible: Boolean,
    /** true = řádek 2 je klikatelný a otevře web s hodnocením čísla. */
    val numberClickable: Boolean,
    /** Číslo pro otevření webu (platné jen když numberClickable). */
    val webNumber: String,
    /** Jednořádkový titulek notifikace a hodinek: [emoji] jméno · číslo. */
    val notificationTitle: String,
    /** Skryté/neznámé číslo (číslo chybí): anonymní avatar, bez SMS tlačítka. */
    val isHidden: Boolean,
    /** Titulek bez emoji (vyplněno jen při aktivním spamchecku se známým číslem). */
    val titleBase: String = "",
    /** Hodnocení čísla (emoji) - vyplněno jen při aktivním spamchecku, když už výsledek přišel. */
    val rating: String = ""
) {
    /** Text pro seznam zmeškaných hovorů: stejný jako titulek, ale hodnocení je až NA KONCI. */
    val missedCallText: String
        get() = if (rating.isEmpty()) notificationTitle else "$titleBase $rating"
}

/** Vstupy bez závislosti na Androidu (kvůli testovatelnosti). */
data class CallDisplayInput(
    val isConference: Boolean,
    val isSaved: Boolean,          // kontakt nalezen v CacheContacts
    val contactName: String,
    val numberLabel: String,
    val hideNumberForSaved: Boolean,
    val number: String,            // zobrazované číslo; prázdné = číslo neznáme
    val cnap: String,              // caller ID od operátora (callerDisplayName)
    val spamCheck: Boolean,        // spamcheck je pro tento hovor aktivní (příchozí + bSpamChecking)
    val spamEmoji: String,         // text z checkNumberForRating; prázdný = výsledek zatím nepřišel
    val isSpam: Boolean,
    val unknownText: String,       // R.string.unknown_caller
    val spamText: String           // R.string.is_spam_call
)

fun computeCallDisplay(i: CallDisplayInput, normalize: (String) -> String): CallDisplay {
    val number = i.number.trim()

    // ── Konference ────────────────────────────────────────────────────────────
    if (i.isConference) {
        val n = i.contactName.ifBlank { i.unknownText }
        return CallDisplay(n, "", false, false, "", n, false)
    }

    // ── A. Uložený kontakt: nikdy spamcheck, CNAP se ignoruje ─────────────────
    if (i.isSaved) {
        val name = i.contactName.ifBlank { i.unknownText }
        val text: String
        val visible: Boolean
        when {
            number.isBlank() -> { text = ""; visible = false }
            i.numberLabel.isNotEmpty() -> {
                text = if (i.hideNumberForSaved) i.numberLabel else "$number - ${i.numberLabel}"
                visible = true
            }
            // pole nechceme schovávat, aby neodskakovala tlačítka pod ním, jen vyprázdníme text
            i.hideNumberForSaved -> { text = " "; visible = true }
            normalize(name) == normalize(number) -> { text = ""; visible = false }
            else -> { text = number; visible = true }
        }
        return CallDisplay(name, text, visible, false, "", name, false)
    }

    // ── Neuložený kontakt ─────────────────────────────────────────────────────
    // CNAP shodný s číslem se bere, jako by CNAP nebyl
    var cnap = i.cnap.trim()
    if (cnap.isNotEmpty() && number.isNotEmpty() && normalize(cnap) == normalize(number)) cnap = ""
    val hasCnap = cnap.isNotEmpty()

    // B. Číslo neznáme (prázdné číslo, handle jiný než tel:, ...)
    if (number.isEmpty()) {
        if (!hasCnap) {
            return CallDisplay(i.unknownText, "", false, false, "", i.unknownText, true)
        }
        val line2 = if (i.spamCheck) i.spamText else i.unknownText
        return CallDisplay(cnap, line2, true, false, "", cnap, true)
    }

    // Číslo známe
    val line1 = if (hasCnap) cnap else number
    val baseTitle = if (hasCnap) "$cnap · $number" else number

    // C. Bez spamchecku (vypnutý nebo odchozí hovor)
    if (!i.spamCheck) {
        return CallDisplay(line1, if (hasCnap) number else "", hasCnap, false, "", baseTitle, false)
    }

    // D. Spamcheck aktivní: prázdné emoji = čeká se na výsledek
    val emoji = i.spamEmoji.trim()
    val line2 = when {
        emoji.isEmpty() -> number
        i.isSpam        -> "${i.spamText} $emoji"
        else            -> "$number $emoji"       // nespam i chyba (💥😕, ∅😕, ≠😕)
    }
    val title = if (emoji.isEmpty()) baseTitle else "$emoji $baseTitle"
    return CallDisplay(line1, line2, true, true, number, title, false, titleBase = baseTitle, rating = emoji)
}

/** Android obálka: posbírá vstupy z hovoru, kontaktu a CallManageru. */
fun buildCallDisplay(
    context: Context,
    call: Call?,
    contact: CallContact,
    hideNumberForSavedContact: Boolean
): CallDisplay {
    val entry = CallManager.getEntryByCall(call)
    return buildCallDisplayFromData(
        context = context,
        contact = contact,
        isConference = CallManager.isConference(call),
        cnap = CallManager.getCnapByCall(call),
        spamCheck = entry?.spamCheck ?: false,
        spamEmoji = entry?.emoji.orEmpty(),
        isSpam = entry?.isSpam ?: false,
        hideNumberForSavedContact = hideNumberForSavedContact
    )
}

/** Totéž bez objektu Call (např. zmeškaný hovor, který už v CallManageru neexistuje). */
fun buildCallDisplayFromData(
    context: Context,
    contact: CallContact,
    isConference: Boolean = false,
    cnap: String = "",
    spamCheck: Boolean = false,
    spamEmoji: String = "",
    isSpam: Boolean = false,
    hideNumberForSavedContact: Boolean = false
): CallDisplay {
    val input = CallDisplayInput(
        isConference = isConference,
        isSaved = contact.id.toInt() != 0,
        contactName = contact.name,
        numberLabel = contact.numberLabel,
        hideNumberForSaved = hideNumberForSavedContact,
        number = contact.number,
        cnap = cnap,
        spamCheck = spamCheck,
        spamEmoji = spamEmoji,
        isSpam = isSpam,
        unknownText = context.getString(R.string.unknown_caller),
        spamText = context.getString(R.string.is_spam_call)
    )
    return computeCallDisplay(input) { normalizeDigitsOnly(it) }
}
