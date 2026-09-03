package cz.mts.base.dialogs

import android.content.Context
import android.widget.FrameLayout
import androidx.annotation.ArrayRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.compose.alert_dialog.AlertDialogState
import cz.mts.base.compose.alert_dialog.DialogSurface
import cz.mts.base.compose.alert_dialog.dialogTextColor
import cz.mts.base.compose.alert_dialog.rememberAlertDialogState
import cz.mts.base.compose.extensions.MyDevices
import cz.mts.base.compose.theme.AppThemeSurface
import cz.mts.base.compose.theme.SimpleTheme
import cz.mts.base.databinding.DialogLineColorPickerBinding
import cz.mts.base.extensions.*
import cz.mts.base.interfaces.LineColorPickerListener
import cz.mts.base.views.MyAppBarLayout

private const val PRIMARY_COLORS_COUNT = 19
private const val DEFAULT_PRIMARY_COLOR_INDEX = 5
private const val DEFAULT_SECONDARY_COLOR_INDEX = 8


// ---------------------------------------------------------------------------
// View-based implementace
// ---------------------------------------------------------------------------
class LineColorPickerDialog(
    private val activity: BaseSimpleActivity,
    @ColorInt private val color: Int,
    private val isPrimaryColorPicker: Boolean,
    @ArrayRes private val primaryColors: Int = R.array.md_primary_colors,
    private val appIconIDs: ArrayList<Int>? = null,
    private val appBar: MyAppBarLayout? = null,
    private val callback: (wasPositivePressed: Boolean, color: Int) -> Unit,
) {

    private var dialog: AlertDialog? = null
    private val view = DialogLineColorPickerBinding.inflate(activity.layoutInflater, null, false)
    private val defaultColorValue = ContextCompat.getColor(activity, R.color.color_primary)

    init {
        setupView()
        buildDialog()
    }


    fun getSpecificColor(): Int = view.secondaryLineColorPicker.getCurrentColor()

    private fun setupView() {
        val indexes = getColorIndexes(color)
        val primaryIndex = indexes.first

        with(view) {
            hexCode.text = color.toHex()
            hexCode.setOnLongClickListener {
                activity.copyToClipboard(hexCode.value.substring(1))
                true
            }

            lineColorPickerIcon.beGoneIf(isPrimaryColorPicker)
            setupPickers(primaryIndex, secondaryIndex = indexes.second)
        }
    }

    private fun setupPickers(primaryIndex: Int, secondaryIndex: Int) {
        with(view) {
            primaryLineColorPicker.updateColors(
                activity.getColors(primaryColors),
                primaryIndex,
            )
            primaryLineColorPicker.listener = LineColorPickerListener { index, selectedColor ->
                val secondaryColors = activity.getColorsForIndex(index)
                secondaryLineColorPicker.updateColors(secondaryColors)

                val newColor = if (isPrimaryColorPicker) {
                    secondaryLineColorPicker.getCurrentColor()
                } else {
                    selectedColor
                }
                onColorUpdated(newColor)

                if (!isPrimaryColorPicker) {
                    updateIcon(index)
                }
            }

            secondaryLineColorPicker.beVisibleIf(isPrimaryColorPicker)
            secondaryLineColorPicker.updateColors(
                activity.getColorsForIndex(primaryIndex),
                secondaryIndex,
            )
            secondaryLineColorPicker.listener = LineColorPickerListener { _, selectedColor ->
                onColorUpdated(selectedColor)
            }

            updateIcon(primaryIndex)
        }
    }

    private fun buildDialog() {
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> onConfirmed() }
            .setNegativeButton(R.string.cancel) { _, _ -> onDismissed() }
            .setOnCancelListener { onDismissed() }
            .apply {
                activity.setupDialogStuff(view.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun onColorUpdated(@ColorInt newColor: Int) {
        view.hexCode.text = newColor.toHex()

        if (isPrimaryColorPicker && appBar != null) {
            activity.updateTopBarColors(appBar, newColor)
        }
    }

    private fun updateIcon(primaryIndex: Int) {
        view.lineColorPickerIcon.setImageResource(appIconIDs?.getOrNull(primaryIndex) ?: 0)
    }

    private fun onConfirmed() {
        val targetView = if (isPrimaryColorPicker) {
            view.secondaryLineColorPicker
        } else {
            view.primaryLineColorPicker
        }
        callback(true, targetView.getCurrentColor())
        destroy()
    }

    private fun onDismissed() {
        callback(false, 0)
        destroy()
    }

    private fun destroy() {
        dialog = null
    }

        private fun getColorIndexes(@ColorInt color: Int): Pair<Int, Int> {
        if (color == defaultColorValue) return getDefaultColorPair()

        for (i in 0 until PRIMARY_COLORS_COUNT) {
            val idx = activity.getColorsForIndex(i).indexOfFirst { it.rgbOnly() == color.rgbOnly() }
            if (idx != -1) return Pair(i, idx)
        }

        return getDefaultColorPair()
    }

    private fun getDefaultColorPair() = Pair(DEFAULT_PRIMARY_COLOR_INDEX, DEFAULT_SECONDARY_COLOR_INDEX)

    private fun Int.rgbOnly(): Int = this and 0x00FFFFFF
}

// ---------------------------------------------------------------------------
// Compose implementace
// ---------------------------------------------------------------------------

@Composable
fun LineColorPickerAlertDialog(
    alertDialogState: AlertDialogState,
    @ColorInt color: Int,
    isPrimaryColorPicker: Boolean,
    modifier: Modifier = Modifier,
    @ArrayRes primaryColors: Int = R.array.md_primary_colors,
    appIconIDs: ArrayList<Int>? = null,
    onActiveColorChange: (color: Int) -> Unit,
    onButtonPressed: (wasPositivePressed: Boolean, color: Int) -> Unit,
) {
    val context = LocalContext.current

    val defaultColor = remember {
        ContextCompat.getColor(context, R.color.color_primary)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = alertDialogState::hide,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogSurface {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(SimpleTheme.dimens.padding.extraLarge),
            ) {
                val dialogTextColor = dialogTextColor

                // Ref na binding uchovávaný v rámci composition; nullován při onRelease
                var binding by remember { mutableStateOf<DialogLineColorPickerBinding?>(null) }

                AndroidViewBinding(
                    factory = DialogLineColorPickerBinding::inflate,
                    onRelease = { binding = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                ) {
                    root.updateLayoutParams<FrameLayout.LayoutParams> {
                        height = FrameLayout.LayoutParams.WRAP_CONTENT
                    }
                    binding = this

                    hexCode.setTextColor(dialogTextColor.toArgb())
                    hexCode.text = color.toHex()
                    hexCode.setOnLongClickListener {
                        context.copyToClipboard(hexCode.value.substring(1))
                        true
                    }

                    lineColorPickerIcon.beGoneIf(isPrimaryColorPicker)

                    val indexes = context.getColorIndexes(color, defaultColor)
                    val primaryIndex = indexes.first

                    lineColorPickerIcon.setImageResource(appIconIDs?.getOrNull(primaryIndex) ?: 0)

                    primaryLineColorPicker.updateColors(context.getColors(primaryColors), primaryIndex)
                    primaryLineColorPicker.listener = LineColorPickerListener { index, selectedColor ->
                        val secondaryColors = context.getColorsForIndex(index)
                        secondaryLineColorPicker.updateColors(secondaryColors)

                        val newColor = if (isPrimaryColorPicker) {
                            secondaryLineColorPicker.getCurrentColor()
                        } else {
                            selectedColor
                        }
                        hexCode.text = newColor.toHex()
                        onActiveColorChange(newColor)

                        if (!isPrimaryColorPicker) {
                            lineColorPickerIcon.setImageResource(appIconIDs?.getOrNull(index) ?: 0)
                        }
                    }

                    secondaryLineColorPicker.beVisibleIf(isPrimaryColorPicker)
                    secondaryLineColorPicker.updateColors(
                        context.getColorsForIndex(primaryIndex),
                        indexes.second,
                    )
                    secondaryLineColorPicker.listener = LineColorPickerListener { _, selectedColor ->
                        hexCode.text = selectedColor.toHex()
                        onActiveColorChange(selectedColor)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        alertDialogState.hide()
                        onButtonPressed(false, 0)
                    }) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                    TextButton(onClick = {
                        val current = binding ?: return@TextButton
                        val targetView = if (isPrimaryColorPicker) {
                            current.secondaryLineColorPicker
                        } else {
                            current.primaryLineColorPicker
                        }
                        onButtonPressed(true, targetView.getCurrentColor())
                        alertDialogState.hide()
                    }) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Context extension funkce (package-private scope)
// ---------------------------------------------------------------------------

internal fun Context.getColorIndexes(@ColorInt color: Int, defaultColor: Int): Pair<Int, Int> {
    fun Int.rgbOnly(): Int = this and 0x00FFFFFF

    if (color == defaultColor) return getDefaultColorPair()

    for (i in 0 until PRIMARY_COLORS_COUNT) {
        val idx = getColorsForIndex(i).indexOfFirst { it.rgbOnly() == color.rgbOnly() }
        if (idx != -1) return Pair(i, idx)
    }

    return getDefaultColorPair()
}

internal fun Context.getColorsForIndex(index: Int): ArrayList<Int> = when (index) {
    0  -> getColors(R.array.md_reds)
    1  -> getColors(R.array.md_pinks)
    2  -> getColors(R.array.md_purples)
    3  -> getColors(R.array.md_deep_purples)
    4  -> getColors(R.array.md_indigos)
    5  -> getColors(R.array.md_blues)
    6  -> getColors(R.array.md_light_blues)
    7  -> getColors(R.array.md_cyans)
    8  -> getColors(R.array.md_teals)
    9  -> getColors(R.array.md_greens)
    10 -> getColors(R.array.md_light_greens)
    11 -> getColors(R.array.md_limes)
    12 -> getColors(R.array.md_yellows)
    13 -> getColors(R.array.md_ambers)
    14 -> getColors(R.array.md_oranges)
    15 -> getColors(R.array.md_deep_oranges)
    16 -> getColors(R.array.md_browns)
    17 -> getColors(R.array.md_blue_greys)
    18 -> getColors(R.array.md_greys)
    else -> throw IllegalArgumentException("Invalid color palette index: $index")
}

internal fun Context.getColors(@ArrayRes id: Int): ArrayList<Int> =
    resources.getIntArray(id).toCollection(ArrayList())

private fun getDefaultColorPair() = Pair(DEFAULT_PRIMARY_COLOR_INDEX, DEFAULT_SECONDARY_COLOR_INDEX)

// ---------------------------------------------------------------------------
// Compose preview
// ---------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview
@MyDevices
@Composable
private fun LineColorPickerAlertDialogPreview() {
    AppThemeSurface {
        LineColorPickerAlertDialog(
            alertDialogState = rememberAlertDialogState(),
            color = Color.Green.toInt(),
            isPrimaryColorPicker = true,
            onActiveColorChange = {},
            onButtonPressed = { _, _ -> },
        )
    }
}
