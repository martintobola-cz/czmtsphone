package cz.mts.base.extensions

import android.view.View
import android.view.Window
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Window.insetsController(view: View? = null): WindowInsetsControllerCompat {
    return WindowInsetsControllerCompat(this, view ?: decorView)
}

fun Window.setCustomNavigationBar(
    navBarColor: Int,
    navBarLightIcons: Boolean
) {
    // nastavíme barvu navigačního panelu
    navigationBarColor = navBarColor
    // nastavíme barvu ikon (true = tmavé ikony, false = světlé)
    insetsController().isAppearanceLightNavigationBars = navBarLightIcons
}

//nastaví horní statusbar a dolní navigační bar
fun Window.setSystemBarsAppearance(backgroundColorStatus: Int, customNavBarLightIcons : Boolean?) {
    val isLightBackground = shouldUseLightIcons(backgroundColorStatus)
    val isLightBackgroundNavigation = customNavBarLightIcons ?: isLightBackground
    insetsController().apply {
        isAppearanceLightStatusBars = isLightBackground
        isAppearanceLightNavigationBars = isLightBackgroundNavigation
    }
}

fun Window.showBars() = insetsController().apply {
    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    show(WindowInsetsCompat.Type.systemBars())
}

fun Window.hideBars(transient: Boolean = true) = insetsController().apply {
    systemBarsBehavior = if (transient) {
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    hide(WindowInsetsCompat.Type.systemBars())
}
