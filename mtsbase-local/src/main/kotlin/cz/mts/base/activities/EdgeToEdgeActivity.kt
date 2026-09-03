package cz.mts.base.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ScrollingView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.R
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.getColoredDrawableWithColor
import cz.mts.base.extensions.getColoredMaterialStatusBarColor
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.onApplyWindowInsets
import cz.mts.base.extensions.setSystemBarsAppearance
import cz.mts.base.extensions.updateMarginWithBase
import cz.mts.base.extensions.updatePaddingWithBase
import cz.mts.base.views.MyAppBarLayout
import cz.mts.base.extensions.ensureBasePadding
import cz.mts.base.extensions.ensureBaseMargin

abstract class EdgeToEdgeActivity : AppCompatActivity() {

    open var isSearchBarEnabled = false
    open var customNavBarLightIcons: Boolean? = null

    open val padCutout: Boolean
        get() = true

    private var topAppBar: MyAppBarLayout? = null
    private var scrollingView: ScrollingView? = null
    private var materialScrollColorAnimation: ValueAnimator? = null
    private var currentScrollY = 0

    private val contentRoot by lazy { findViewById<View>(android.R.id.content) }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
    }

    override fun onResume() {
        super.onResume()
        window.setSystemBarsAppearance(getProperBackgroundColor(), customNavBarLightIcons)
    }

    override fun onDestroy() {
        materialScrollColorAnimation?.end()
        materialScrollColorAnimation = null
        topAppBar = null
        scrollingView = null
        super.onDestroy()
    }

    fun applyNavigationBar(color: Int, lightIcons: Boolean) {
      //  if (Build.VERSION.SDK_INT >= 35) {
            // Na API 35+ barvu nelze přepsat – nastavíme jen appearance ikon
            window.setSystemBarsAppearance(getProperBackgroundColor(), customNavBarLightIcons)
            //WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = lightIcons
      //  } else {
      //      window.navigationBarColor = color
      //      WindowInsetsControllerCompat(window, window.decorView)
      //          .isAppearanceLightNavigationBars = lightIcons
      //  }
    }

    fun setupEdgeToEdge(
        padTopSystem: List<View> = emptyList(),
        padBottomSystem: List<View> = emptyList(),
        padBottomImeAndSystem: List<View> = emptyList(),
        moveBottomSystem: List<View> = emptyList(),
        animateIme: Boolean = false,
    ) {
        val paddingViews = (padTopSystem + padBottomSystem + padBottomImeAndSystem)
            .distinct()

        paddingViews.forEach { it.ensureBasePadding() }

        moveBottomSystem
            .distinct()
            .forEach { it.ensureBaseMargin() }

        if (padCutout) {
            contentRoot.ensureBasePadding()
        }

        onApplyWindowInsets { insets ->
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
            val imeAndSystem = insets.getInsets(Type.ime() or Type.systemBars())

            paddingViews.forEach { view ->
                val top = if (view in padTopSystem) system.top else 0

                val bottom = when {
                    view in padBottomImeAndSystem -> imeAndSystem.bottom
                    view in padBottomSystem -> system.bottom
                    else -> 0
                }

                view.updatePaddingWithBase(
                    top = top,
                    bottom = bottom
                )
            }

            moveBottomSystem.forEach {
                it.updateMarginWithBase(bottom = system.bottom)
            }

            if (padCutout) {
                val cutout = insets.getInsets(Type.displayCutout())
                val sideLeft = maxOf(system.left, cutout.left)
                val sideRight = maxOf(system.right, cutout.right)

                contentRoot.updatePaddingWithBase(
                    left = sideLeft,
                    right = sideRight
                )
            }

            if (animateIme) {
                ViewCompat.setWindowInsetsAnimationCallback(
                    contentRoot,
                    object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                        override fun onProgress(
                            insets: WindowInsetsCompat,
                            runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                        ): WindowInsetsCompat {
                            val bottom = insets.getInsets(Type.systemBars() or Type.ime()).bottom

                            padBottomImeAndSystem.forEach {
                                it.updatePaddingWithBase(bottom = bottom)
                            }

                            return insets
                        }
                    }
                )
            }
        }
    }

    fun setupMaterialScrollListener(scrollingView: ScrollingView?, topAppBar: MyAppBarLayout) {
        this.scrollingView = scrollingView
        this.topAppBar = topAppBar

        when (scrollingView) {
            is RecyclerView -> scrollingView.setOnScrollChangeListener { _, _, _, _, _ ->
                val newScrollY = scrollingView.computeVerticalScrollOffset()
                scrollingChanged(newScrollY, currentScrollY)
                currentScrollY = newScrollY
            }
            is NestedScrollView -> scrollingView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                scrollingChanged(scrollY, oldScrollY)
            }
        }
    }

    private fun scrollingChanged(newScrollY: Int, oldScrollY: Int) {
        when {
            newScrollY > 0 && oldScrollY == 0 ->
                animateTopBarColors(getProperBackgroundColor(), getColoredMaterialStatusBarColor())
            newScrollY == 0 && oldScrollY > 0 ->
                animateTopBarColors(getColoredMaterialStatusBarColor(), getRequiredTopBarColor())
        }
    }

    fun animateTopBarColors(colorFrom: Int, colorTo: Int) {
        if (topAppBar == null) return

        materialScrollColorAnimation?.end()
        materialScrollColorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        materialScrollColorAnimation?.addUpdateListener { animator ->
            // Znovu načteme referenci – bar mohl být mezitím odpojen
            val currentBar = topAppBar ?: return@addUpdateListener
            updateTopBarColors(currentBar, animator.animatedValue as Int)
        }
        materialScrollColorAnimation?.start()
    }

    fun getRequiredTopBarColor(): Int {
        val isAtTop = scrollingView?.computeVerticalScrollOffset() == 0
        return if (
            (scrollingView is RecyclerView || scrollingView is NestedScrollView) && isAtTop
        ) {
            getProperBackgroundColor()
        } else {
            getColoredMaterialStatusBarColor()
        }
    }

    fun updateTopBarColors(topAppBar: MyAppBarLayout, color: Int) {
        val contrastColor = if (isSearchBarEnabled) {
            getProperBackgroundColor().getContrastColor()
        } else {
            color.getContrastColor()
        }

        window.setSystemBarsAppearance(color, customNavBarLightIcons)

        if (!isSearchBarEnabled) {
            topAppBar.setBackgroundColor(color)
            topAppBar.toolbar?.setBackgroundColor(color)
            topAppBar.toolbar?.setTitleTextColor(contrastColor)
            topAppBar.toolbar?.navigationIcon?.applyColorFilter(contrastColor)
            topAppBar.toolbar?.collapseIcon = resources.getColoredDrawableWithColor(
                drawableId = R.drawable.ic_arrow_left_vector,
                color = contrastColor,
            )
        }

        topAppBar.toolbar?.overflowIcon =
            resources.getColoredDrawableWithColor(R.drawable.ic_three_dots_vector, contrastColor)

        val menu = topAppBar.toolbar?.menu ?: return
        for (i in 0 until menu.size) {
            try {
                menu[i].icon?.setTint(contrastColor)
            } catch (_: Exception) {
            }
        }
    }
}
