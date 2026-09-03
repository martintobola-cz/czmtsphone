package cz.mts.base.compose.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cz.mts.base.compose.extensions.FakeVersionCheck
import cz.mts.base.compose.extensions.TransparentSystemBars
import cz.mts.base.compose.theme.model.Theme
import cz.mts.base.compose.theme.model.Theme.Companion.systemDefaultMaterialYou

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

    val context = LocalContext.current
    val materialYouTheme = systemDefaultMaterialYou()
    var currentTheme: Theme by remember {
        mutableStateOf(
            if (view.isInEditMode) materialYouTheme else getTheme(
                context = context,
                materialYouTheme = materialYouTheme
            )
        )
    }
    LifecycleEventEffect(event = Lifecycle.Event.ON_START) {
        if (!view.isInEditMode) {
            currentTheme = getTheme(context = context, materialYouTheme = materialYouTheme)
        }
    }
    TransparentSystemBars()
    Theme(theme = currentTheme) {
        content()
        if (!view.isInEditMode) {
            OnContentDisplayed()
        }
    }
}

@Composable
fun AppThemeSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun OnContentDisplayed() {
    FakeVersionCheck()
}
