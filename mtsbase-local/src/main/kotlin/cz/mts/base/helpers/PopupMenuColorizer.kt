package cz.mts.base.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.widget.ListPopupWindow
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import cz.mts.base.extensions.adjustColor
import cz.mts.base.extensions.copyToClipboard
import cz.mts.base.extensions.toast
import kotlin.math.roundToInt

object PopupMenuColorizer {

    private fun debugReport(context: Context, message: String, debug: Boolean): String {
        if (debug) {
            context.toast(message)
        }
        return message
    }

    fun MenuItem.setOneTitleColor(text: CharSequence, color: Int?) {
        if (color == null) {
            title = text
            return
        }
        val spannable = SpannableString(text)
        spannable.setSpan(ForegroundColorSpan(color), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        title = spannable
    }

    fun colorizeTitles(menu: Menu, textColor: Int, textSizePx: Float? = null) {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val title = item.title?.toString() ?: continue
            val spannable = SpannableString(title)
            spannable.setSpan(ForegroundColorSpan(textColor), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (textSizePx != null) {
                spannable.setSpan(AbsoluteSizeSpan(textSizePx.toInt()), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            item.title = spannable
            item.subMenu?.let { colorizeTitles(it, textColor, textSizePx) }
        }
    }

    /** Pozadí pro standalone androidx.appcompat.widget.PopupMenu – volat AŽ PO .show()! */
    fun applyBackground(debug: Boolean, popupMenu: PopupMenu, context: Context, backgroundColor: Int, cornerRadiusDp: Float = 8f): String {
        val result = try {
            val bg = buildBackgroundDrawable(context, backgroundColor.adjustColor(), cornerRadiusDp)

            val mPopupField = PopupMenu::class.java.getDeclaredField("mPopup")
            mPopupField.isAccessible = true
            val menuPopupHelper = mPopupField.get(popupMenu) ?: return debugReport(context, "applyBackground: no mPopup", debug)

            val getPopupMethod = menuPopupHelper.javaClass.getMethod("getPopup")
            getPopupMethod.isAccessible = true
            val menuPopup = getPopupMethod.invoke(menuPopupHelper)
                ?: return debugReport(context, "applyBackground: no menuPopup (not shown yet?)", debug)

            applyBackgroundToMenuPopup(menuPopup, bg)
        } catch (e: Exception) {
            "exception: ${e.javaClass.simpleName}: ${e.message}"
        }
        return debugReport(context, "applyBackground: $result", debug)
    }


    private fun findOverflowButton(root: ViewGroup): View? {
        val overflowDescription = root.context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description)
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.contentDescription == overflowDescription) return child
            if (child is ViewGroup) findOverflowButton(child)?.let { return it }
        }
        return null
    }

    fun attachCabOverflowColorHook(
        actionModeBar: ViewGroup,
        context: Context,
        getBackgroundColor: () -> Int,
        isColoringEnabled: () -> Boolean,
        isDebugEnabled: () -> Boolean
    ) {
        actionModeBar.post {
            try {
                val overflowButton = findOverflowButton(actionModeBar) ?: return@post
                val actionMenuView = overflowButton.parent as? androidx.appcompat.widget.ActionMenuView ?: return@post

                overflowButton.setOnClickListener {
                    if (actionMenuView.isOverflowMenuShowing) {
                        actionMenuView.hideOverflowMenu()
                    } else {
                        actionMenuView.showOverflowMenu()
                        if (isColoringEnabled()) {
                            actionMenuView.post {
                                colorizeCabOverflowBackground(actionMenuView, context, getBackgroundColor().adjustColor(), isDebugEnabled())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                debugReport(context, "attachCabOverflowColorHook exception: ${e.javaClass.simpleName}: ${e.message}", isDebugEnabled())
            }
        }
    }
    /** Navěsí barvení na Toolbar overflow tlačítko "...". Zavolejte JEDNOU po requireToolbar().inflateMenu(...). */
    fun attachOverflowColorHook(
        toolbar: Toolbar,
        context: Context,
        getTextColor: () -> Int,
        getBackgroundColor: () -> Int,
        isColoringEnabled: () -> Boolean,
        isDebugEnabled: () -> Boolean
    ) {
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val overflowButton = findOverflowButton(toolbar) ?: return
                toolbar.viewTreeObserver.removeOnGlobalLayoutListener(this)

                overflowButton.setOnClickListener {
                    if (toolbar.isOverflowMenuShowing) {
                        toolbar.hideOverflowMenu()
                    } else {
                        if (isColoringEnabled()) {
                            colorizeTitles(toolbar.menu, getTextColor())
                        }
                        toolbar.showOverflowMenu()
                        toolbar.post {
                            if (isColoringEnabled()) {
                                colorizeToolbarOverflowBackground(isDebugEnabled(), toolbar, context, getBackgroundColor().adjustColor())
                            }
                        }
                    }
                }
            }
        }
        toolbar.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    fun colorizeToolbarOverflowBackground(debug: Boolean, toolbar: Toolbar, context: Context, backgroundColor: Int): String {
        val result = try {
            val menuViewField = Toolbar::class.java.getDeclaredField("mMenuView")
            menuViewField.isAccessible = true
            val actionMenuView = menuViewField.get(toolbar) ?: return debugReport(context, "toolbarBg: no mMenuView", debug)

            val menuPopup = resolveOverflowMenuPopup(actionMenuView, context)
            val bg = buildBackgroundDrawable(context, backgroundColor, 8f)
            applyBackgroundToMenuPopup(menuPopup, bg)
        } catch (e: Exception) {
            "exception: ${e.javaClass.simpleName}: ${e.message}"
        }
        return debugReport(context, "toolbarBg: $result", debug)
    }

    private fun colorizeCabOverflowBackground(actionMenuView: ViewGroup, context: Context, backgroundColor: Int, debug: Boolean): String {
        val result = try {
            val menuPopup = resolveOverflowMenuPopup(actionMenuView, context)
            val bg = buildBackgroundDrawable(context, backgroundColor, 8f)
            applyBackgroundToMenuPopup(menuPopup, bg)
        } catch (e: Exception) {
            "exception: ${e.javaClass.simpleName}: ${e.message}"
        }
        return debugReport(context, "cabBg: $result", debug)
    }


    private fun buildBackgroundDrawable(context: Context, color: Int, cornerRadiusDp: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp * context.resources.displayMetrics.density
            setColor(color)
        }

    private fun findFieldInHierarchy(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredField(name).apply { isAccessible = true }
            } catch (e: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    private fun getRealPopupWindow(listPopupWindowLike: Any): android.widget.PopupWindow? {
        val field = findFieldInHierarchy(listPopupWindowLike.javaClass, "mPopup") ?: return null
        return field.get(listPopupWindowLike) as? android.widget.PopupWindow
    }

    private fun dismissAndReshowWithoutNotifying(listPopupWindowLike: Any, bg: GradientDrawable): String {
        val clazz = listPopupWindowLike.javaClass
        val realPopupWindow = getRealPopupWindow(listPopupWindowLike)
            ?: return "no real PopupWindow (mPopup) found"

        val listenerField = findFieldInHierarchy(realPopupWindow.javaClass, "mOnDismissListener")
        val originalListener = listenerField?.get(realPopupWindow)

        return try {
            listenerField?.set(realPopupWindow, null)

            val setBgMethod = clazz.getMethod("setBackgroundDrawable", android.graphics.drawable.Drawable::class.java)
            val dismissMethod = clazz.getMethod("dismiss")
            val showMethod = clazz.getMethod("show")

            setBgMethod.invoke(listPopupWindowLike, bg)
            dismissMethod.invoke(listPopupWindowLike)
            showMethod.invoke(listPopupWindowLike)
            "ok"
        } catch (e: Exception) {
            "exception: ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            listenerField?.set(realPopupWindow, originalListener)
        }
    }

    private fun applyBackgroundToMenuPopup(menuPopup: Any, bg: GradientDrawable): String {
        val className = menuPopup.javaClass.name
        return when {
            className.contains("Standard") -> {
                val listPopupField = menuPopup.javaClass.getDeclaredField("mPopup")
                listPopupField.isAccessible = true
                val listPopupWindow = listPopupField.get(menuPopup) as? ListPopupWindow
                    ?: return "cast failed, actual=${listPopupField.get(menuPopup)?.javaClass?.name}"
                "standard: " + dismissAndReshowWithoutNotifying(listPopupWindow, bg)
            }
            className.contains("Cascading") -> {
                val presentersField = menuPopup.javaClass.getDeclaredField("mPresenters")
                presentersField.isAccessible = true
                val presenters = presentersField.get(menuPopup) as? List<*>
                presenters?.forEach { info ->
                    val windowField = info!!.javaClass.getDeclaredField("window")
                    windowField.isAccessible = true
                    val window = windowField.get(info) ?: return@forEach
                    val thisBg = bg.constantState?.newDrawable()?.mutate() as? GradientDrawable ?: bg
                    dismissAndReshowWithoutNotifying(window, thisBg)
                }
                "ok cascading"
            }
            else -> "unmatched class: $className"
        }
    }

    // ---------------------------------------------------------------------
    // Sdílené: z ActionMenuView vytáhne aktuálně zobrazený MenuPopup
    // ---------------------------------------------------------------------

    private fun resolveOverflowMenuPopup(actionMenuView: Any, context: Context): Any {
        val presenterField = actionMenuView.javaClass.getDeclaredField("mPresenter")
        presenterField.isAccessible = true
        val presenter = presenterField.get(actionMenuView)
            ?: throw IllegalStateException("no mPresenter")

        //dumpFields(context, "presenter", presenter, true)   // ← DOČASNĚ

        val overflowPopupField = presenter.javaClass.getDeclaredField("mOverflowPopup")
        overflowPopupField.isAccessible = true
        val overflowPopupHelper = overflowPopupField.get(presenter)
            ?: throw IllegalStateException("no mOverflowPopup (not shown yet?)")

        val getPopupMethod = overflowPopupHelper.javaClass.getMethod("getPopup")
        getPopupMethod.isAccessible = true
        return getPopupMethod.invoke(overflowPopupHelper)
            ?: throw IllegalStateException("no menuPopup")
    }

    private fun pollForCabOverflowPopup(
        actionMenuView: ViewGroup,
        context: Context,
        backgroundColor: Int,
        debug: Boolean,
        attempt: Int = 0
    ) {
        //if (attempt == 0) {
         //   dumpFields(context, "actionMenuView", actionMenuView, true)
        //}

        val maxAttempts = 15
        try {
            val menuPopup = resolveOverflowMenuPopup(actionMenuView, context)
            val bg = buildBackgroundDrawable(context, backgroundColor, 8f)
            val result = applyBackgroundToMenuPopup(menuPopup, bg)
            debugReport(context, "cabBg: $result (attempt $attempt)", debug)
        } catch (e: Exception) {
            if (attempt < maxAttempts) {
                actionMenuView.postDelayed({
                    pollForCabOverflowPopup(actionMenuView, context, backgroundColor, debug, attempt + 1)
                }, 20)
            } else {
                debugReport(context, "cabBg giving up after $attempt attempts: ${e.javaClass.simpleName}: ${e.message}", debug)
            }
        }
    }

    private fun dumpFields(context: Context, label: String, obj: Any, debug: Boolean) {
        if (!debug) return
        var c: Class<*>? = obj.javaClass
        val sb = StringBuilder("$label = ${obj.javaClass.name}\n")
        while (c != null) {
            val names = c.declaredFields.joinToString { it.name }
            sb.append("  [${c.simpleName}] $names\n")
            c = c.superclass
        }
        context.copyToClipboard(sb.toString())
    }

    fun blendColors(
        backgroundColor: Int,
        foregroundColor: Int,
        alphaFactor: Float = 1f
    ): Int {
        val alpha = (Color.alpha(foregroundColor) * alphaFactor)
            .roundToInt()
            .coerceIn(0, 255)

        val red = (
            Color.red(foregroundColor) * alpha +
                Color.red(backgroundColor) * (255 - alpha)
            ) / 255

        val green = (
            Color.green(foregroundColor) * alpha +
                Color.green(backgroundColor) * (255 - alpha)
            ) / 255

        val blue = (
            Color.blue(foregroundColor) * alpha +
                Color.blue(backgroundColor) * (255 - alpha)
            ) / 255

        return Color.rgb(red, green, blue)
    }
}
