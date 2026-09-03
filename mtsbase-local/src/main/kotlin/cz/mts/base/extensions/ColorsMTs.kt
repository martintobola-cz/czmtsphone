package cz.mts.base.extensions


import android.graphics.Color
import kotlin.math.pow

fun getContrastingColor(color: Int): Int {
    // Vytažení RGB složek
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF

    // Přepočet na jas (luminance) podle WCAG
    val brightness = (0.299 * r + 0.587 * g + 0.114 * b)

    // Pokud je barva spíš světlá → vrať černou
    // Pokud je tmavá → vrať bílou
    return if (brightness > 186) Color.BLACK else Color.WHITE
}


data class RGB(val r: Int, val g: Int, val b: Int)

private fun luminance(color: RGB): Double {
    fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92
        else ((c + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * channel(color.r) +
        0.7152 * channel(color.g) +
        0.0722 * channel(color.b)
}

fun contrast(c1: RGB, c2: RGB): Double {
    val L1 = luminance(c1)
    val L2 = luminance(c2)
    return (maxOf(L1, L2) + 0.05) / (minOf(L1, L2) + 0.05)
}

fun areColorsTooSimilar(c1: RGB, c2: RGB, threshold: Double = 2.0): Boolean {
    return contrast(c1, c2) < threshold
}

fun colorToRGB(color: Int): RGB {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return RGB(r, g, b)
}

fun areColorsTooSimilarInt(color1: Int, color2: Int, threshold: Double = 2.0): Boolean {
    return areColorsTooSimilar(colorToRGB(color1), colorToRGB(color2), threshold)
}

/**
 * Vrací true pokud je podklad světlý (→ použij tmavé ikony).
 */
fun shouldUseLightIcons(backgroundColor: Int): Boolean {
    val r = Color.red(backgroundColor) / 255.0
    val g = Color.green(backgroundColor) / 255.0
    val b = Color.blue(backgroundColor) / 255.0

    fun linearize(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

    val luminance = 0.2126 * linearize(r) +
        0.7152 * linearize(g) +
        0.0722 * linearize(b)

    return luminance > 0.5
}
