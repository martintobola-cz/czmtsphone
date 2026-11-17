package cz.mts.base.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import java.util.Locale


fun Int.getContrastColor(): Int {
    val luminance = ColorUtils.calculateLuminance(this)
    return if (luminance > 0.5) Color.BLACK else Color.WHITE
}

fun Int.toHex() = String.format("#%06X", 0xFFFFFF and this).uppercase(Locale.getDefault())

fun Int.adjustAlpha(factor: Float): Int {
    val alpha = Math.round(Color.alpha(this) * factor)
    val red = Color.red(this)
    val green = Color.green(this)
    val blue = Color.blue(this)
    return Color.argb(alpha, red, green, blue)
}

fun Int.getFormattedDuration(forceShowHours: Boolean = false): String {
    val sb = StringBuilder(8)
    val hours = this / 3600
    val minutes = this % 3600 / 60
    val seconds = this % 60

    if (this >= 3600) {
        sb.append(String.format(Locale.getDefault(), "%02d", hours)).append(":")
    } else if (forceShowHours) {
        sb.append("0:")
    }

    sb.append(String.format(Locale.getDefault(), "%02d", minutes))
    sb.append(":").append(String.format(Locale.getDefault(), "%02d", seconds))
    return sb.toString()
}


@Deprecated(
    message = "Broken due to the Year 2038 problem. Use Long.formatDate() instead (but note that it uses milliseconds, not seconds).",
    replaceWith = ReplaceWith("(this * 1000L).formatDate(context, dateFormat, timeFormat)")
)
fun Int.formatDate(context: Context, dateFormat: String? = null, timeFormat: String? = null): String {
    return (this * 1000L).formatDate(context, dateFormat, timeFormat)
}

@Deprecated(
    message = "Broken due to the Year 2038 problem. Use Long.formatDateOrTime() instead (but note that it uses milliseconds, not seconds).",
    replaceWith = ReplaceWith("(this * 1000L).formatDateOrTime(context, hideTimeAtOtherDays, showYearEvenIfCurrent)")
)
fun Int.formatDateOrTime(context: Context, hideTimeAtOtherDays: Boolean, showYearEvenIfCurrent: Boolean): String {
    return (this * 1000L).formatDateOrTime(context, hideTimeAtOtherDays, showYearEvenIfCurrent)
}


fun Int.isNearBlackBlack(): Boolean {
    val r = Color.red(this)
    val g = Color.green(this)
    val b = Color.blue(this)
    // perceptuální luminance
    val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
    // tmavost 0–100
    val darkness = 100 - (luminance / 255.0 * 100)
    val isVeryDark = darkness >= 88
    return isVeryDark
}

fun Int.adjustColor(factor: Int = 8, dialog: Boolean = false): Int {

    val iColor = this
    //korekce, protože jinak to vrátí sytě červenou !!!!
    if (iColor.isNearBlackBlack()) return Color.parseColor("#1D1D1D")
    if (dialog) return iColor
    if (iColor == Color.WHITE) return Color.parseColor("#F5F5F5")

    var hsv = FloatArray(3)
    Color.colorToHSV(iColor, hsv)
    val hsl = hsv2hsl(hsv)

    val delta = factor / 100f

    when {
        // Hodně tmavá → zesvětli
        hsl[2] <= 0.25f -> {
            hsl[2] = (hsl[2] + delta).coerceAtMost(1f)
        }

        // Hodně světlá → ztmav
        hsl[2] >= 0.75f -> {
            hsl[2] = (hsl[2] - delta).coerceAtLeast(0f)
        }

        else -> {
            hsl[2] = (hsl[2] - delta).coerceAtLeast(0f)
        }
    }

    hsv = hsl2hsv(hsl)
    return Color.HSVToColor(hsv)
}


private fun hsl2hsv(hsl: FloatArray): FloatArray {
    val hue = hsl[0]
    var sat = hsl[1]
    val light = hsl[2]
    sat *= if (light < .5) light else 1 - light
    return floatArrayOf(hue, 2f * sat / (light + sat), light + sat)
}

private fun hsv2hsl(hsv: FloatArray): FloatArray {
    val hue = hsv[0]
    val sat = hsv[1]
    val value = hsv[2]

    val newHue = (2f - sat) * value
    var newSat = sat * value / if (newHue < 1f) newHue else 2f - newHue
    if (newSat > 1f)
        newSat = 1f

    return floatArrayOf(hue, newSat, newHue / 2f)
}

fun Int.getColorStateList(): ColorStateList {
    val states = arrayOf(
        intArrayOf(android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_checked),
        intArrayOf(android.R.attr.state_pressed)
    )
    val colors = intArrayOf(this, this, this, this)
    return ColorStateList(states, colors)
}

