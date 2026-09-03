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
