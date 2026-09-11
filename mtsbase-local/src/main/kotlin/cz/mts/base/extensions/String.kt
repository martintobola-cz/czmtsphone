package cz.mts.base.extensions

import android.content.Context
import android.icu.text.BreakIterator
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import cz.mts.base.helpers.getDateFormats
import cz.mts.base.helpers.normalizeRegex
import cz.mts.base.helpers.photoExtensions
import cz.mts.base.helpers.videoExtensions
import java.io.File
import java.text.DateFormat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

fun String.getFilenameFromPath() = substring(lastIndexOf("/") + 1)

fun String.getBasePath(context: Context): String {
    return when {
        startsWith(context.internalStoragePath) -> context.internalStoragePath
        context.isPathOnSD(this) -> context.sdCardPath
        context.isPathOnOTG(this) -> context.otgPath
        else -> "/"
    }
}

fun String.getFirstParentDirName(context: Context, level: Int): String? {
    val basePath = getBasePath(context)
    val startIndex = basePath.length + 1
    return if (length > startIndex) {
        val pathWithoutBasePath = substring(startIndex)
        val pathSegments = pathWithoutBasePath.split("/")
        if (level < pathSegments.size) {
            pathSegments.slice(0..level).joinToString("/")
        } else {
            null
        }
    } else {
        null
    }
}

fun String.getFirstParentPath(context: Context, level: Int): String {
    val basePath = getBasePath(context)
    val startIndex = basePath.length + 1
    return if (length > startIndex) {
        val pathWithoutBasePath = substring(basePath.length + 1)
        val pathSegments = pathWithoutBasePath.split("/")
        val firstParentPath = if (level < pathSegments.size) {
            pathSegments.slice(0..level).joinToString("/")
        } else {
            pathWithoutBasePath
        }
        "$basePath/$firstParentPath"
    } else {
        basePath
    }
}

fun String.isAValidFilename(): Boolean {
    charArrayOf('/', '\n', '\r', '\t', '\u0000', '`', '?', '*', '\\', '<', '>', '|', '\"', ':')
        .forEach {
            if (contains(it))
                return false
        }

    return true
}

fun String.getOTGPublicPath(context: Context) =
    "${context.baseConfig.OTGTreeUri}/document/${context.baseConfig.OTGPartition}%3A${substring(context.baseConfig.OTGPath.length).replace("/", "%2F")}"

fun String.isGif() = endsWith(".gif", true)

fun String.isPortrait() = getFilenameFromPath().contains("portrait", true) && File(this).parentFile?.name?.startsWith("img_", true) == true

// fast extension checks, not guaranteed to be accurate
fun String.isVideoFast() = videoExtensions.any { endsWith(it, true) }

fun String.isImageFast() = photoExtensions.any { endsWith(it, true) }

fun String.getParentPath() = removeSuffix("/${getFilenameFromPath()}")

fun String.highlightTextPart(
    textToHighlight: String,
    color: Int,
    highlightAll: Boolean = false
): SpannableString {

    val spannable = SpannableString(this)
    if (textToHighlight.isBlank()) return spannable

    val normalizedTarget = textToHighlight.normalizeString()
    if (normalizedTarget.isEmpty()) return spannable

    // 1️⃣ Normalizujeme originál JEDNOU + mapujeme indexy
    val normalizedChars = ArrayList<Char>(length)
    val indexMap = ArrayList<Int>(length)

    for (i in indices) {
        val normalized = this[i].toString().normalizeString()
        if (normalized.isNotEmpty()) {
            normalizedChars.add(normalized[0])
            indexMap.add(i)
        }
    }

    val normalizedText = normalizedChars.joinToString("")
    var searchIndex = normalizedText.indexOf(normalizedTarget, 0, ignoreCase = true)

    while (searchIndex >= 0) {
        val startOriginal = indexMap[searchIndex]
        val endOriginal = if (searchIndex + normalizedTarget.length < indexMap.size) {
            indexMap[searchIndex + normalizedTarget.length]
        } else {
            length
        }

        spannable.setSpan(
            ForegroundColorSpan(color),
            startOriginal,
            endOriginal,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )

        if (!highlightAll) break

        searchIndex = normalizedText.indexOf(
            normalizedTarget,
            searchIndex + normalizedTarget.length,
            ignoreCase = true
        )
    }

    return spannable
}

// remove diacritics, for example č -> c
fun String.normalizeString() = Normalizer.normalize(this, Normalizer.Form.NFD).replace(normalizeRegex, "")

// get the contact names first letter at showing the placeholder without image
fun String.getNameLetter(): String {
    return firstUserVisibleGrapheme()
        ?.uppercase(Locale.getDefault())
        ?: "A"
}

fun String.firstUserVisibleGrapheme(): String? {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).also { it.setText(this) }
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val cluster = substring(start, end)
        if (isUserVisibleCluster(cluster)) {
            return cluster
        }

        start = end
        end = iterator.next()
    }
    return null
}

private fun isUserVisibleCluster(text: String): Boolean {
    if (text.isBlank()) return false
    var i = 0
    var substantive = false
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            Character.FORMAT.toInt(),
            Character.CONTROL.toInt(),
            Character.SPACE_SEPARATOR.toInt(),
            Character.LINE_SEPARATOR.toInt(),
            Character.PARAGRAPH_SEPARATOR.toInt() -> {
                // ignore these
            }

            else -> substantive = true
        }
        i += Character.charCount(codePoint)
    }
    return substantive
}

fun String.getDateTimeFromDateString(
    showYearsSince: Boolean,
    viewToUpdate: TextView? = null
): LocalDateTime {

    val dateFormats = getDateFormats()
    val now = LocalDateTime.now()
    var parsedDate: LocalDateTime = now

    for (format in dateFormats) {
        try {
            val formatter = DateTimeFormatter.ofPattern(format, Locale.getDefault())
            val hasYear = format.contains("y")

            parsedDate = if (hasYear) {
                LocalDate.parse(this, formatter).atStartOfDay()
            } else {
                // pokud není rok, přidáme aktuální rok
                val dateWithoutYear = LocalDate.parse(this, formatter)
                dateWithoutYear.withYear(now.year).atStartOfDay()
            }

            // vytvoříme lokální pattern podle nastavení systému (MEDIUM)
            val localizedDateFormatter = DateFormat
                .getDateInstance(DateFormat.MEDIUM, Locale.getDefault()) as SimpleDateFormat

            var pattern = localizedDateFormatter.toLocalizedPattern()
            val finalFormatter: DateTimeFormatter

            if (!format.contains("y")) {
                // odstranit rok z patternu
                pattern = pattern.replace("y", "")
                    .replace(",", "")
                    .trim()
            }

            finalFormatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

            // výsledek
            var finalString = parsedDate.format(finalFormatter)

            if (showYearsSince && format.contains("y")) {
                val years = Period.between(parsedDate.toLocalDate(), now.toLocalDate()).years
                finalString += " ($years)"
            }

            viewToUpdate?.text = finalString
            break

        } catch (_: Exception) {
            // zkusí další pattern
        }
    }

    return parsedDate
}

fun String.isBlockedNumberPattern() = contains("*")

fun String?.fromHtml(): Spanned =
    when {
        this == null -> SpannableString("")
        else -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    }
