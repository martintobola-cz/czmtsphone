package cz.mts.base.extensions

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import cz.mts.base.R
import cz.mts.base.helpers.LOWER_ALPHA
import cz.mts.base.helpers.PopupMenuColorizer.blendColors
import cz.mts.base.helpers.appIconColorStrings
import cz.mts.base.helpers.isSPlus
import cz.mts.base.views.*


fun Context.isBlackAndWhiteTheme() = baseConfig.themeIdSaved == 4

fun Context.isDynamicTheme(useDynamicTheme: Boolean = baseConfig.useDynamicTheme) = isSPlus() && baseConfig.themeIdSaved == 7 && useDynamicTheme

fun Context.isWhiteTheme() = baseConfig.themeIdSaved == 6

//fun Context.isSystemInDarkModeOLD() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES != 0

fun Context.isSystemInDarkMode(): Boolean {
    val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
}

fun Context.getDynamicTextColors(useDynamicTheme: Boolean = baseConfig.useDynamicTheme): Int {
    return if (isSPlus() && useDynamicTheme) {
        R.color.you_neutral_text_color
    } else {
        val isDarkTheme = isSystemInDarkMode()
        if (isDarkTheme) R.color.theme_dark_text_color else R.color.theme_light_text_color
    }
}

fun Context.getDynamicBackgroundColors(useDynamicTheme: Boolean = baseConfig.useDynamicTheme): Int {
    return if (isSPlus() && useDynamicTheme) {
        R.color.you_background_color
    } else {
        val isDarkTheme = isSystemInDarkMode()
        if (isDarkTheme) R.color.theme_dark_background_color else R.color.theme_light_background_color
    }
}

fun Context.isAutoTheme() = !isSPlus() && baseConfig.themeIdSaved == 7

fun Context.getProperTextColor() = when {
    isDynamicTheme() -> resources.getColor(R.color.you_neutral_text_color, theme)
    else -> baseConfig.textColor
}

fun Context.getProperBackgroundColor() = when {
    isDynamicTheme() -> resources.getColor(R.color.you_background_color, theme)
    else -> baseConfig.backgroundColor
}

fun Context.getProperSelectedItemBackgroundColor(): Int {
    return blendColors(getProperBackgroundColor(), ContextCompat.getColor(this, R.color.activated_item_foreground))
}

fun Context.getProperMySearchBarColor(): Int {
  return blendColors(getProperBackgroundColor(), getProperPrimaryColor().adjustAlpha(LOWER_ALPHA))
}

fun Context.getProperPrimaryColor() = when {
    baseConfig.easterEggMode -> Color.parseColor("#347A2F")
    isDynamicTheme() -> resources.getColor(R.color.you_primary_color, theme)
    isWhiteTheme() || isBlackAndWhiteTheme() -> baseConfig.accentColor
    else -> baseConfig.primaryColor
}

fun Context.getProperStatusBarColor() = when {
    isDynamicTheme() -> resources.getColor(R.color.you_status_bar_color, theme)
    else -> getProperBackgroundColor()
}

// get the color of the status bar with material activity, if the layout is scrolled down a bit
fun Context.getColoredMaterialStatusBarColor(): Int {
    return getProperPrimaryColor()
}

//fun Context.getColoredMaterialStatusBarColorOLD(): Int {
//    return when {
//        isDynamicTheme() -> resources.getColor(R.color.you_status_bar_color, theme)
//        else -> getProperPrimaryColor()
//    }
//}

/** Barva kruhu za tlačítky na obrazovce hovoru. OFF = plně průhledná. */
fun Context.getCallButtonCircleColor(active: Boolean): Int {
    if (!baseConfig.highlightCallButtons) return Color.TRANSPARENT
    return if (active) getProperPrimaryColor().adjustAlpha(0.25f)
    else getProperTextColor().adjustAlpha(0.10f)
}

fun Context.updateTextColors(viewGroup: ViewGroup) {
    val textColor = getProperTextColor()
    val backgroundColor = getProperBackgroundColor()
    val accentColor = when {
        isWhiteTheme() || isBlackAndWhiteTheme() -> baseConfig.accentColor
        else -> getProperPrimaryColor()
    }

    val cnt = viewGroup.childCount
    (0 until cnt).map { viewGroup.getChildAt(it) }.forEach {
        when (it) {
            is MyTextView -> it.setColors(textColor, accentColor, backgroundColor)
            is MyAppCompatSpinner -> it.setColors(textColor, accentColor, backgroundColor)
            is MyCompatRadioButton -> it.setColors(textColor, accentColor, backgroundColor)
            is MyAppCompatCheckbox -> it.setColors(textColor, accentColor, backgroundColor)
            is MyMaterialSwitch -> it.setColors(textColor, accentColor, backgroundColor)
            is MyEditText -> it.setColors(textColor, accentColor, backgroundColor)
            is MyAutoCompleteTextView -> it.setColors(textColor, accentColor, backgroundColor)
            is MyFloatingActionButton -> it.setColors(textColor, accentColor, backgroundColor)
            is MySeekBar -> it.setColors(textColor, accentColor, backgroundColor)
            is MyButton -> it.setColors(textColor, accentColor, backgroundColor)
            is MyTextInputLayout -> it.setColors(textColor, accentColor, backgroundColor)
            is ViewGroup -> updateTextColors(it)
        }
    }
}

fun Context.getPopupMenuTheme(): Int {
    return if (isDynamicTheme()) R.style.AppTheme_YouPopupMenuStyle
           else if (isWhiteTheme()) R.style.AppTheme_PopupMenuLightStyle
           else R.style.AppTheme_PopupMenuDarkStyle
}


fun Context.density(): Float =
    (resources.displayMetrics.density)

fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()

fun Context.checkAppIconColor() {
    val appId = baseConfig.appId
    if (appId.isNotEmpty() && baseConfig.lastIconColor != baseConfig.appIconColor) {

        try {
            getAppIconColors().forEachIndexed { index, color ->
                toggleAppIconColor(appId, index, color, false)
            }
        } catch (e: Exception) {}

        var iDoit = false
        try {
            getAppIconColors().forEachIndexed { index, color ->
                if (baseConfig.appIconColor == color) {
                    toggleAppIconColor(appId, index, color, true)
                    iDoit = true
                }
            }
        } catch (e: Exception) {}

        //nic nedal, aplikace se nepodaří spustit a android bude ukazovat info aplikace
        //musíme tam něco dát, takže výchozí modrou
        if (!iDoit) {
            val className = "${appId.removeSuffix(".debug")}.activities.SplashActivity.Blue"
            val state =  PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            try {
                packageManager.setComponentEnabledSetting(ComponentName(appId, className), state, PackageManager.DONT_KILL_APP)
                baseConfig.lastIconColor = Color.parseColor("#2196F3") //md_blue500
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

    }
}

fun Context.toggleAppIconColor(appId: String, colorIndex: Int, color: Int, enable: Boolean) {
    val className = "${appId.removeSuffix(".debug")}.activities.SplashActivity${appIconColorStrings[colorIndex]}"
    val state = if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    try {
        packageManager.setComponentEnabledSetting(ComponentName(appId, className), state, PackageManager.DONT_KILL_APP)
        if (enable) {
            baseConfig.lastIconColor = color
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.getAppIconColors() = resources.getIntArray(R.array.md_app_icon_colors).toCollection(ArrayList())

fun Context.getDialogBackgroundColor(): Int {
    return when {
        isDynamicTheme() -> MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.TRANSPARENT
        )
        else -> getProperBackgroundColor()
    }
}
