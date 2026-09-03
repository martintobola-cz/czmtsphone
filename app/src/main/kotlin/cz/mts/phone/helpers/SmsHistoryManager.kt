package cz.mts.phone.helpers

import android.content.Context
import cz.mts.base.helpers.MAX_SMS_RECORDS
import cz.mts.base.extensions.baseConfig as config
import org.json.JSONArray
import org.json.JSONObject

data class SentSmsRecord(
    val phoneNumber: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SmsHistoryManager {


    fun saveSms(context: Context, phoneNumber: String, message: String) {
        val records = getAll(context).toMutableList()

        records.add(0, SentSmsRecord(phoneNumber, message)) // nejnovější první

        val trimmed = records.take(MAX_SMS_RECORDS) // max 30 záznamů

        val jsonArray = JSONArray().apply {
            trimmed.forEach { record ->
                put(JSONObject().apply {
                    put("phone", record.phoneNumber)
                    put("message", record.message)
                    put("timestamp", record.timestamp)
                })
            }
        }

        context.config.sentSmsList = jsonArray.toString()
    }

    fun getAll(context: Context): List<SentSmsRecord> {
        val json = context.config.sentSmsList
        if (json.isBlank()) return emptyList()

        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                SentSmsRecord(
                    phoneNumber = obj.getString("phone"),
                    message = obj.getString("message"),
                    timestamp = obj.getLong("timestamp")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
