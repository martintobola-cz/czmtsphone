package cz.mts.base.helpers

import cz.mts.base.models.BlockedNumber
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

object BlockedNumbersJson {
    const val MIME_TYPE = "application/json"
    const val FILE_EXTENSION = "json"

    private const val FORMAT_ID = "cz.mts.blocked-numbers"
    private const val FORMAT_VERSION = 1

    enum class EntryType(val jsonValue: String) {
        EXACT("exact"),
        PATTERN("pattern");

        companion object {
            fun fromJson(value: String): EntryType? = entries.firstOrNull { it.jsonValue == value }
        }
    }

    data class Entry(
        val type: EntryType,
        val value: String,
    )

    class InvalidFormatException(message: String, cause: Throwable? = null) :
        IllegalArgumentException(message, cause)

    fun isPatternValue(value: String): Boolean =
        value.indexOf('*') >= 0 || value.indexOf('?') >= 0

    fun write(blockedNumbers: Collection<BlockedNumber>, outputStream: OutputStream) {
        val items = JSONArray()

        blockedNumbers.forEach { blockedNumber ->
            val value = blockedNumber.number.trim()
            if (value.isNotEmpty()) {
                val type = if (isPatternValue(value)) EntryType.PATTERN else EntryType.EXACT
                items.put(
                    JSONObject()
                        .put("type", type.jsonValue)
                        .put("value", value)
                )
            }
        }

        val root = JSONObject()
            .put("format", FORMAT_ID)
            .put("version", FORMAT_VERSION)
            .put("items", items)

        val writer = outputStream.bufferedWriter(StandardCharsets.UTF_8)
        writer.write(root.toString(2))
        writer.newLine()
        writer.flush()
    }

    fun read(inputStream: InputStream): List<Entry> {
        val text = inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        if (text.isBlank()) {
            throw InvalidFormatException("Empty JSON file")
        }

        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw InvalidFormatException("Invalid JSON", e)
        }

        if (root.optString("format") != FORMAT_ID) {
            throw InvalidFormatException("Unknown blocked-number file format")
        }

        if (root.optInt("version", -1) != FORMAT_VERSION) {
            throw InvalidFormatException("Unsupported blocked-number file version")
        }

        val items = root.optJSONArray("items")
            ?: throw InvalidFormatException("Missing items array")

        val result = ArrayList<Entry>(items.length())
        val seen = HashSet<String>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i)
                ?: throw InvalidFormatException("Item $i is not an object")

            val typeText = item.optString("type")
            val type = EntryType.fromJson(typeText)
                ?: throw InvalidFormatException("Item $i has unknown type")

            val value = item.optString("value").trim()
            if (value.isEmpty()) {
                throw InvalidFormatException("Item $i has an empty value")
            }

            val containsWildcard = isPatternValue(value)
            if (type == EntryType.PATTERN && !containsWildcard) {
                throw InvalidFormatException("Item $i is marked as pattern but has no wildcard")
            }
            if (type == EntryType.EXACT && containsWildcard) {
                throw InvalidFormatException("Item $i is marked as exact but contains a wildcard")
            }
            if (type == EntryType.EXACT && value.none { it.isDigit() }) {
                throw InvalidFormatException("Item $i is not a phone number")
            }

            val key = "${type.jsonValue}:$value"
            if (seen.add(key)) {
                result.add(Entry(type, value))
            }
        }

        return result
    }
}
