package cz.mts.base.dialogs

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.databinding.DialogColorPickerBinding
import cz.mts.base.extensions.*
import cz.mts.base.helpers.isQPlus
import java.util.LinkedList

private const val RECENT_COLORS_NUMBER = 5

@JvmInline
private value class Hsv(val value: FloatArray) {
    fun getColor() = Color.HSVToColor(value)
    fun getHue() = value[0]
    fun setHue(hue: Float) { value[0] = hue }
    fun getSat() = value[1]
    fun setSat(sat: Float) { value[1] = sat }
    fun getVal() = value[2]
    fun setVal(v: Float) { value[2] = v }
}

class ColorPickerDialog(
    val activity: BaseSimpleActivity,
    color: Int,
    val removeDimmedBackground: Boolean = false,
    val addDefaultColorButton: Boolean = false,
    val currentColorCallback: ((color: Int) -> Unit)? = null,
    val callback: (wasPositivePressed: Boolean, color: Int) -> Unit,
) {
    private val baseConfig = activity.baseConfig
    private val currentColorHsv = Hsv(FloatArray(3))
    private val backgroundColor = baseConfig.backgroundColor
    private var wasDimmedBackgroundRemoved = false
    private var dialog: AlertDialog? = null
    private val binding = DialogColorPickerBinding.inflate(activity.layoutInflater, null, false)

    init {
        Color.colorToHSV(color, currentColorHsv.value)

        binding.init(
            color = color,
            backgroundColor = backgroundColor,
            recentColors = baseConfig.colorPickerRecentColors,
            hsv = currentColorHsv,
            currentColorCallback = {
                if (removeDimmedBackground && !wasDimmedBackgroundRemoved) {
                    dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    wasDimmedBackgroundRemoved = true
                }
                currentColorCallback?.invoke(it)
            }
        )

        val textColor = activity.getProperTextColor()
        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> confirmNewColor() }
            .setNegativeButton(R.string.cancel) { _, _ -> dialogDismissed() }
            .setOnCancelListener { dialogDismissed() }
            .apply {
                if (addDefaultColorButton) {
                    setNeutralButton(R.string.default_color) { _, _ -> confirmDefaultColor() }
                }
            }

        builder.apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
                binding.colorPickerArrow.applyColorFilter(textColor)
                binding.colorPickerHexArrow.applyColorFilter(textColor)
                binding.colorPickerHueCursor.applyColorFilter(textColor)
            }
        }
    }

    private fun dialogDismissed() = callback(false, 0)

    private fun confirmDefaultColor() = callback(true, 0)

    private fun confirmNewColor() {
        val hexValue = binding.colorPickerNewHex.value
        val newColor = if (hexValue.length == 6) {
            Color.parseColor("#$hexValue")
        } else {
            currentColorHsv.getColor()
        }
        activity.addRecentColor(newColor)
        callback(true, newColor)
    }
}

// ─── DialogColorPickerBinding extensions ─────────────────────────────────────

@Suppress("ClickableViewAccessibility")
private fun DialogColorPickerBinding.init(
    color: Int,
    backgroundColor: Int,
    recentColors: List<Int>,
    hsv: Hsv,
    currentColorCallback: (color: Int) -> Unit,
) {
    var isHueBeingDragged = false

    if (isQPlus()) {
        root.isForceDarkAllowed = false
    }

    colorPickerSquare.setHue(hsv.getHue())
    colorPickerNewColor.setFillWithStroke(color, backgroundColor)
    colorPickerOldColor.setFillWithStroke(color, backgroundColor)

    val hexCode = getHexCode(color)
    colorPickerOldHex.text = "#$hexCode"
    colorPickerOldHex.setOnLongClickListener {
        root.context.copyToClipboard(hexCode)
        true
    }
    colorPickerNewHex.setText(hexCode)
    setupRecentColors(backgroundColor, recentColors)

    colorPickerHue.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> isHueBeingDragged = true
            MotionEvent.ACTION_UP -> isHueBeingDragged = false
        }
        if (event.action == MotionEvent.ACTION_MOVE ||
            event.action == MotionEvent.ACTION_DOWN ||
            event.action == MotionEvent.ACTION_UP
        ) {
            val y = event.y.coerceIn(0f, colorPickerHue.measuredHeight - 0.001f)
            var hue = 360f - 360f / colorPickerHue.measuredHeight * y
            if (hue == 360f) hue = 0f

            hsv.setHue(hue)
            updateHue(hsv, backgroundColor, currentColorCallback)
            colorPickerNewHex.setText(getHexCode(hsv.getColor()))
            true
        } else {
            false
        }
    }

    colorPickerSquare.setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_MOVE ||
            event.action == MotionEvent.ACTION_DOWN ||
            event.action == MotionEvent.ACTION_UP
        ) {
            val x = event.x.coerceIn(0f, colorPickerSquare.measuredWidth.toFloat())
            val y = event.y.coerceIn(0f, colorPickerSquare.measuredHeight.toFloat())

            hsv.setSat(1f / colorPickerSquare.measuredWidth * x)
            hsv.setVal(1f - 1f / colorPickerSquare.measuredHeight * y)

            moveColorPicker(hsv)
            colorPickerNewColor.setFillWithStroke(hsv.getColor(), backgroundColor)
            colorPickerNewHex.setText(getHexCode(hsv.getColor()))
            true
        } else {
            false
        }
    }

    colorPickerNewHex.onTextChangeListener { hex ->
        if (hex.length == 6 && !isHueBeingDragged) {
            runCatching {
                val newColor = Color.parseColor("#$hex")
                Color.colorToHSV(newColor, hsv.value)
                updateHue(hsv, backgroundColor, currentColorCallback)
                moveColorPicker(hsv)
            }
            // neplatný hex – tiše ignorujeme, stejně jako původní try/catch
        }
    }

    root.onGlobalLayout {
        moveHuePicker(hsv)
        moveColorPicker(hsv)
    }
}

private fun DialogColorPickerBinding.setupRecentColors(
    backgroundColor: Int,
    recentColors: List<Int>,
) {
    if (recentColors.isEmpty()) return

    this.recentColors.beVisible()

    this.recentColors.children
        .filterIsInstance<ImageView>()
        .toList()
        .forEach { view ->
            this.recentColors.removeView(view)
            recentColorsFlow.removeView(view)
        }

    val squareSize = root.context.resources
        .getDimensionPixelSize(R.dimen.colorpicker_hue_width)

    recentColors.take(RECENT_COLORS_NUMBER).forEach { recentColor ->
        val recentColorView = ImageView(root.context).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(squareSize, squareSize)
            setFillWithStroke(recentColor, backgroundColor)
            setOnClickListener {
                colorPickerNewHex.setText(getHexCode(recentColor))
            }
        }
        this.recentColors.addView(recentColorView)
        recentColorsFlow.addView(recentColorView)
    }
}

private fun DialogColorPickerBinding.updateHue(
    hsv: Hsv,
    backgroundColor: Int,
    currentColorCallback: (color: Int) -> Unit,
) {
    colorPickerSquare.setHue(hsv.getHue())
    moveHuePicker(hsv)
    colorPickerNewColor.setFillWithStroke(hsv.getColor(), backgroundColor)
    currentColorCallback(hsv.getColor())
}

private fun DialogColorPickerBinding.moveHuePicker(hsv: Hsv) {
    var y = colorPickerHue.measuredHeight - hsv.getHue() * colorPickerHue.measuredHeight / 360f
    if (y == colorPickerHue.measuredHeight.toFloat()) y = 0f
    colorPickerHueCursor.x = (colorPickerHue.left - colorPickerHueCursor.width).toFloat()
    colorPickerHueCursor.y = colorPickerHue.top + y - colorPickerHueCursor.height / 2
}

private fun DialogColorPickerBinding.moveColorPicker(hsv: Hsv) {
    val x = hsv.getSat() * colorPickerSquare.measuredWidth
    val y = (1f - hsv.getVal()) * colorPickerSquare.measuredHeight
    colorPickerCursor.x = colorPickerSquare.left + x - colorPickerCursor.width / 2
    colorPickerCursor.y = colorPickerSquare.top + y - colorPickerCursor.height / 2
}

private fun getHexCode(color: Int) = color.toHex().substring(1)

fun Context.addRecentColor(color: Int) {
    var recentColors = baseConfig.colorPickerRecentColors
    recentColors.remove(color)
    if (recentColors.size >= RECENT_COLORS_NUMBER) {
        val toDrop = recentColors.size - RECENT_COLORS_NUMBER + 1
        recentColors = LinkedList(recentColors.dropLast(toDrop))
    }
    recentColors.addFirst(color)
    baseConfig.colorPickerRecentColors = recentColors
}

