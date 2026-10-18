package cz.mts.base.compose.extensions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cz.mts.base.compose.system_ui_controller.rememberSystemUiController


fun Context.getActivity(): Activity {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> getActivity()
    }
}

@Composable
fun rememberMutableInteractionSource() = remember { MutableInteractionSource() }

@Composable
operator fun PaddingValues.plus(otherPaddingValues: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateLeftPadding(layoutDirection).plus(
            otherPaddingValues.calculateLeftPadding(
                layoutDirection
            )
        ),
        top = calculateTopPadding().plus(otherPaddingValues.calculateTopPadding()),
        end = calculateRightPadding(layoutDirection).plus(
            otherPaddingValues.calculateRightPadding(
                layoutDirection
            )
        ),
        bottom = calculateBottomPadding().plus(otherPaddingValues.calculateBottomPadding())
    )
}

@Composable
fun PaddingValues.plus(vararg otherPaddingValues: PaddingValues): PaddingValues {
    val thisArray = arrayOf(this)
    return PaddingValues(
        start = thisArray.plus(otherPaddingValues).sumOfDps(PaddingValues::calculateStartPadding),
        top = thisArray.plus(otherPaddingValues).sumOfDps(PaddingValues::calculateTopPadding),
        end = thisArray.plus(otherPaddingValues).sumOfDps(PaddingValues::calculateEndPadding),
        bottom = thisArray.plus(otherPaddingValues).sumOfDps(PaddingValues::calculateBottomPadding)
    )
}

@Composable
private fun Array<out PaddingValues>.sumOfDps(aggregator: (PaddingValues, LayoutDirection) -> Dp): Dp {
    val layoutDirection = LocalLayoutDirection.current
    return asSequence().map { paddingValues ->
        aggregator(paddingValues, layoutDirection)
    }.sumOfDps()
}

private fun Array<out PaddingValues>.sumOfDps(aggregator: PaddingValues.() -> Dp): Dp =
    asSequence().map { paddingValues ->
        paddingValues.aggregator()
    }.sumOfDps()

private fun Sequence<Dp>.sumOfDps(): Dp {
    var sum = 0.dp
    for (element in this) {
        sum += element
    }
    return sum
}


@Composable
internal fun TransparentSystemBars(darkIcons: Boolean = !isSystemInDarkTheme()) {
    val systemUiController = rememberSystemUiController()

    DisposableEffect(systemUiController, darkIcons) {
        systemUiController.setSystemBarsColor(
            color = Color.Transparent,
            darkIcons = darkIcons
        )
        onDispose { }
    }
}
