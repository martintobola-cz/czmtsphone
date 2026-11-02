package cz.mts.base.dialogs

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
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
    @param:ColorInt private val color: Int,
    private val isPrimaryColorPicker: Boolean,
    @param:ArrayRes private val primaryColors: Int = R.array.md_primary_colors,
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

