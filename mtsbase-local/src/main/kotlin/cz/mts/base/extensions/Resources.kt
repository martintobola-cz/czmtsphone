package cz.mts.base.extensions

import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat

fun Resources.getColoredDrawableWithColor(drawableId: Int, color: Int, alpha: Int = 255): Drawable {
    //TODO asi by bylo vhodné místo null předávat skutečné theme ctivity.theme / context.theme
    val drawable = ResourcesCompat.getDrawable(this, drawableId, null)!!.mutate()
    drawable.applyColorFilter(color)
    drawable.alpha = alpha
    return drawable
}
