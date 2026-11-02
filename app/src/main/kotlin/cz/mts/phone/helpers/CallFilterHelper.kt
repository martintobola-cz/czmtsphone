package cz.mts.phone.helpers

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import cz.mts.base.extensions.isAppInstalled


data class CallFilterResult(
    val ok: Boolean,
    val label: String,
    val status: String,
    val normalizedNumber: String,
    val iResultPositive : Int,
    val iResultNegative : Int,
    val iResultNeutral : Int,
    val source: String,
    val apiVersion: Int,
    val error: String? = null
)

fun getCallFilterInfo(context: Context, phoneNumber: String, callback: (CallFilterResult?) -> Unit) {
    if (phoneNumber.isBlank()) {
        callback(null)
        return
    }
    val sUri = if (context.isAppInstalled( "cz.mts.callfilter")) "content://cz.mts.callfilter"
               else if (context.isAppInstalled( "callfilter.app")) "content://app.callfilter.api"
               else ""


    if (sUri.isBlank()) {
        callback(null)
        return
    }

    Thread {
        val result = try {
            val bundle = context.contentResolver.call(
                Uri.parse(sUri),
                "lookup_number",
                null,
                Bundle().apply { putString("phone_number", phoneNumber) }
            )
            if (bundle != null) {
                CallFilterResult(
                    ok = bundle.getBoolean("ok"),
                    label = bundle.getString("label").orEmpty(),
                    status = bundle.getString("status").orEmpty(),
                    normalizedNumber = bundle.getString("normalized_number").orEmpty(),
                    iResultPositive = bundle.getInt("positive_rating_count", 0),
                    iResultNegative = bundle.getInt("negative_rating_count", 0),
                    iResultNeutral = bundle.getInt("neutral_rating_count", 0),
                    source = bundle.getString("source").orEmpty(),
                    apiVersion = bundle.getInt("api_version", 0),
                    error = bundle.getString("error"),
                )
            } else null
        } catch (_: Exception) {
            null
        }

        Handler(Looper.getMainLooper()).post {
            callback(result)
        }
    }.start()
}

data class SpamReportResult(
    val ok: Boolean,
    val httpCode: Int,
    val inserted: Int,
    val failed: Int,
    val error: String,
    val alreadyReported: Boolean = false   // true = číslo už bylo dříve nahlášeno, na server se nic neposílalo
)

/**
 * Zavolá report_spam v cz.mts.callfilter (hlášení umí jen tato aplikace, ne callfilter.app).
 * Číslo, které už bylo úspěšně nahlášeno (viz ReportedNumbers), se znovu neposílá.
 * Úspěšně nahlášené číslo se uloží do ReportedNumbers.
 * Síť i soubor běží na vlastním vlákně, callback se volá na hlavním (UI) vlákně.
 */
fun reportSpamNumber(
    context: Context,
    number: String,
    subject: String = "Other",      // nebo "Telemarketing"
    robocall: Boolean = false,      // true = robocall
    comment: String = "",
    violationTimeMs: Long = 0L,     // čas hovoru (epoch ms), např. z historie hovorů; 0 = aktuální čas
    consumerState: String = "",     // region/stát, může být prázdné
    callback: (SpamReportResult) -> Unit
) {
    if (number.isBlank()) {
        callback(SpamReportResult(false, 0, 0, 0, "Number is blank"))
        return
    }

    if (!context.isAppInstalled("cz.mts.callfilter")) {
        callback(SpamReportResult(false, 0, 0, 0, "cz.mts.callfilter is not installed"))
        return
    }

    Thread {
        val result = try {
            if (ReportedNumbers.contains(context, number)) {
                SpamReportResult(false, 0, 0, 0, "Number was already reported", alreadyReported = true)
            } else {
                val extras = Bundle().apply {
                    putString("phone_number", number)
                    putString("subject", subject)
                    putBoolean("robocall", robocall)
                    putString("comment", comment)
                    putLong("violation_time_ms", violationTimeMs)
                    putString("consumer_state", consumerState)
                }
                val r = context.contentResolver.call(
                    Uri.parse("content://cz.mts.callfilter"),
                    "report_spam",
                    null,
                    extras
                )
                if (r == null) {
                    SpamReportResult(false, 0, 0, 0, "No response from cz.mts.callfilter")
                } else {
                    val res = SpamReportResult(
                        ok = r.getBoolean("ok"),
                        httpCode = r.getInt("http_code", 0),
                        inserted = r.getInt("inserted", 0),
                        failed = r.getInt("failed", 0),
                        error = r.getString("error").orEmpty()
                    )
                    // uložíme jen to, co server opravdu přijal
                    if (res.ok) ReportedNumbers.add(context, number)
                    res
                }
            }
        } catch (e: Exception) {
            SpamReportResult(false, 0, 0, 0, e.message ?: "Exception")
        }

        Handler(Looper.getMainLooper()).post {
            callback(result)
        }
    }.start()
}