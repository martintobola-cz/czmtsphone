package cz.mts.base.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.materialswitch.MaterialSwitch
import cz.mts.base.R
import cz.mts.base.extensions.adjustAlpha
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.getContrastColor

open class MyMaterialSwitch : MaterialSwitch {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    private var showCheckmark = false
    private var iconApplied = false
    private var ready = false   // false během konstruktoru nadřídy

    init {
        ready = true
        setShowCheckmark(context.baseConfig.showCheckmarksOnSwitches)
    }

    fun setShowCheckmark(showCheckmark: Boolean) {
        this.showCheckmark = showCheckmark
        updateThumbIcon()
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        if (ready) updateThumbIcon()
    }

    private fun updateThumbIcon() {
        if (showCheckmark && isChecked) {
            thumbIconDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_check_vector)
            iconApplied = true
        } else if (iconApplied) {
            thumbIconDrawable = null
            iconApplied = false
        }
    }


    fun setColors(textColor: Int, accentColor: Int, backgroundColor: Int) {
        val onPrimary = accentColor.getContrastColor()
        val outlineColor = textColor.adjustAlpha(0.4f)
        val trackColor = textColor.adjustAlpha(0.2f)

        setTextColor(textColor)
        trackTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)
            ),
            intArrayOf(trackColor, accentColor)
        )

        thumbTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)
            ),
            intArrayOf(outlineColor, onPrimary)
        )

        thumbIconTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)
            ),
            intArrayOf(trackColor, accentColor)
        )

        trackDecorationTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_checked)
            ),
            intArrayOf(outlineColor, accentColor)
        )
    }
}
