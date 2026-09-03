package cz.mts.base.extensions

import android.content.Context
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.text.DecimalFormat
import java.time.format.TextStyle

private val zone get() = ZoneId.systemDefault()

private fun Long.toZoned(): ZonedDateTime =
    Instant.ofEpochMilli(this).atZone(zone)

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

private fun Context.timeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern(getTimeFormat(), Locale.getDefault())

fun Long.formatSize(): String {
    // https://stackoverflow.com/a/5599842
    if (this <= 0) {
        return "0 B"
    }

    val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB")
    val digitGroups = (Math.log10(toDouble()) / Math.log10(1024.0)).toInt()
    return "${DecimalFormat("#,##0.#").format(this / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

fun Long.formatDate(
    context: Context,
    dateFormat: String? = null,
    timeFormat: String? = null
): String {
    val dt = toLocalDateTime()

    val df = DateTimeFormatter.ofPattern(
        dateFormat ?: context.baseConfig.dateFormat, Locale.getDefault()
    )
    val tf = DateTimeFormatter.ofPattern(
        timeFormat ?: context.getTimeFormat(), Locale.getDefault()
    )

    return "${dt.format(df)}, ${dt.format(tf)}"
}

fun Long.formatTime(context: Context): String {
    return toLocalDateTime().format(context.timeFormatter())
}

fun Long.isThisYear(): Boolean {
    return toLocalDate().year == LocalDate.now().year
}

fun LocalDate.toDayCode(format: String = "ddMMyy"): String {
    val formatter = DateTimeFormatter.ofPattern(format, Locale.getDefault())
    return this.format(formatter)
}

fun Long.toDayCode(format: String = "ddMMyy"): String {
    val formatter = DateTimeFormatter.ofPattern(format, Locale.getDefault())
    return toLocalDate().format(formatter)
}

fun Long.dayShort(locale: Locale = Locale.getDefault()): String {
    val dayOfWeek = Instant
        .ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .dayOfWeek
    return dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
}

fun Long.formatDateOrTime(
    context: Context,
    hideTimeOnOtherDays: Boolean,
    showCurrentYear: Boolean,
    hideTodaysDate: Boolean = true,
    showDayIfUserWant: Boolean = false,
): String {
    val dt = toLocalDateTime()
    val date = dt.toLocalDate()
    val today = LocalDate.now()

    // Stejné jako DateUtils.isToday()
    if (hideTodaysDate && date == today) {
        return dt.format(context.timeFormatter())
    }

    // Použij uživatelský pattern
    var pattern = context.baseConfig.dateFormat

    // Pokud nemáš ukazovat rok → odstraň "y" přesně jako tvoje verze
    if (!showCurrentYear && date.year == today.year) {
        pattern = pattern
            .replace(Regex("y+"), "")
            .replace(Regex("[/ .-]+$"), "") // přesně jako v původní logice: trim(), trim(-.), trim(/)
            .trim()
    }

    if (!hideTimeOnOtherDays) {
        pattern += ", ${context.getTimeFormat()}"
    }

    val showDayOfWeek = if (showDayIfUserWant) context.baseConfig.useDayOfWeekInTimeFormat
                        else false

    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

    return if (showDayOfWeek) dayShort() + ", " +  dt.format(formatter)
    else dt.format(formatter)
}

