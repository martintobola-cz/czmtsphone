package cz.mts.phone.helpers

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.widget.ImageView
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.onGlobalLayout
import cz.mts.base.extensions.performHapticFeedback
import cz.mts.phone.R
import cz.mts.phone.databinding.ActivityCallBinding
import kotlin.math.max
import kotlin.math.min

/**
 * Zodpovídá za UI příchozího hovoru:
 *   – swipe gesto na prostředním tlačítku
 *   – animace šipek
 *   – double-click fallback na tlačítkách odmítnout / přijmout (swipe režim)
 *   – single-click na odmítnout / přijmout (no-swipe režim)
 *
 * Třídu stačí v CallActivity vytvořit, zavolat [init] a implementovat [Host].
 * Do budoucna lze přidat další styly animace přidáním nové implementace [Host]
 * nebo parametrem AnimationStyle – CallActivity se nemusí vůbec měnit.
 */
class CallSwipeHandler(
    private val binding: ActivityCallBinding,
    private val host: Host,
) {

    // ── Host interface ────────────────────────────────────────────────────────
    interface Host {
        fun onAcceptCall()
        fun onDeclineCall()
        fun getHostProperTextColor(): Int
        fun getHostDrawable(resId: Int): Drawable?
        fun getHostColor(resId: Int): Int
        val isRTLLayout: Boolean
    }

    // ── Geometrie – plněno v onGlobalLayout ───────────────────────────────────
    private var minDragX = 0f
    private var maxDragX = 0f
    private var initialDraggableX = 0f
    private var initialLeftArrowX = 0f
    private var initialRightArrowX = 0f
    private var initialLeftArrowScaleX = 0f
    private var initialLeftArrowScaleY = 0f
    private var initialRightArrowScaleX = 0f
    private var initialRightArrowScaleY = 0f
    private var leftArrowTranslation = 0f
    private var rightArrowTranslation = 0f

    // ── Runtime stav ──────────────────────────────────────────────────────────
    /**
     * FIX CHYBA #1:
     * Původně se `stopAnimation` nastavovalo na `true` v ACTION_DOWN,
     * ale nikde se neresetovalo při uvolnění. Výsledek: `withEndAction` vidělo `true`
     * po prvním proběhu a smyčka se přerušila.
     * Řešení: `resetDraggable()` vždy nastaví `stopAnimation = false`
     * PŘED zavoláním `startArrowAnimation()`.
     */
    private var stopAnimation = false
    private var dragDownX = 0f
    private var lock = false

    /**
     * FIX CHYBA #2 – část A:
     * Původně byla sdílená `lastClickTime` pro obě tlačítka.
     * Klik na Přijmout + klik na Odmítnout do 500 ms = trigger → špatně.
     * Každé tlačítko má teď svůj vlastní čítač.
     */
    private var lastClickTimeAccept = 0L
    private var lastClickTimeDecline = 0L
    private val DOUBLE_CLICK_INTERVAL = 500L

    // ── Veřejné API ───────────────────────────────────────────────────────────

    /**
     * Inicializuje tlačítka příchozího hovoru.
     * Volej z CallActivity.initButtons
     *
     * DŮLEŽITÉ: Nenastavuj click listenery pro callAccept / callDecline
     * v CallActivity.initButtons() – tato třída je nastavuje sama.
     * (FIX CHYBA #2 – část B: původní kód je přepisoval po zavolání handleSwipe().)
     */
    fun init(disableSwipe: Boolean) = binding.apply {
        if (disableSwipe) {
            callDraggable.beGone()
            callDraggableBackground.beGone()
            callLeftArrow.beGone()
            callRightArrow.beGone()
            // V no-swipe režimu stačí single-click
            callDecline.setOnClickListener { host.onDeclineCall() }
            callAccept.setOnClickListener { host.onAcceptCall() }
        } else {
            setupSwipe()
        }
    }


    private fun resetDraggable() = binding.apply {

        stopAnimation = false
        dragDownX = 0f

        callDraggable.animate()
            .x(initialDraggableX)
            .withEndAction {
                callDraggableBackground.animate().alpha(0.2f)
                callDraggable.setImageDrawable(
                    host.getHostDrawable(R.drawable.ic_phone_down_vector)
                )
                callDraggable.drawable?.mutate()?.setTint(host.getHostProperTextColor())
            }

        callLeftArrow.animate().alpha(1f)
        callRightArrow.animate().alpha(1f)

        startArrowAnimation(
            callLeftArrow,
            initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY,
            leftArrowTranslation
        )
        startArrowAnimation(
            callRightArrow,
            initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY,
            rightArrowTranslation
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipe() = binding.apply {
        val isRtl = host.isRTLLayout

        callAccept.onGlobalLayout {
            minDragX = if (isRtl) callAccept.left.toFloat() else callDecline.left.toFloat()
            maxDragX = if (isRtl) callDecline.left.toFloat() else callAccept.left.toFloat()
            initialDraggableX = callDraggable.left.toFloat()

            initialLeftArrowX = callLeftArrow.x
            initialRightArrowX = callRightArrow.x
            initialLeftArrowScaleX = callLeftArrow.scaleX
            initialLeftArrowScaleY = callLeftArrow.scaleY
            initialRightArrowScaleX = callRightArrow.scaleX
            initialRightArrowScaleY = callRightArrow.scaleY

            leftArrowTranslation  = if (isRtl)  callAccept.x else -callDecline.x
            rightArrowTranslation = if (isRtl) -callAccept.x else  callDecline.x

            if (isRtl) {
                callLeftArrow.setImageResource(R.drawable.ic_chevron_right_vector)
                callRightArrow.setImageResource(R.drawable.ic_chevron_left_vector)
            }

            callLeftArrow.applyColorFilter(host.getHostColor(R.color.md_red_400))
            callRightArrow.applyColorFilter(host.getHostColor(R.color.md_green_400))

            startArrowAnimation(
                callLeftArrow,
                initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY,
                leftArrowTranslation
            )
            startArrowAnimation(
                callRightArrow,
                initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY,
                rightArrowTranslation
            )
        }

        callDraggable.drawable.mutate().setTint(host.getHostProperTextColor())
        callDraggableBackground.drawable.mutate().setTint(host.getHostProperTextColor())

        // ── Swipe gesto ───────────────────────────────────────────────────────
        callDraggable.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    callDraggableBackground.animate().alpha(0f)
                    stopAnimation = true
                    callLeftArrow.animate().alpha(0f)
                    callRightArrow.animate().alpha(0f)
                    lock = false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // reset vždy obnoví animaci
                    resetDraggable()
                }

                MotionEvent.ACTION_MOVE -> {
                    callDraggable.x = min(maxDragX, max(minDragX, event.rawX - dragDownX))
                    when {
                        callDraggable.x >= maxDragX - 50f && !lock -> {
                            lock = true
                            callDraggable.performHapticFeedback()
                            if (isRtl) host.onDeclineCall() else host.onAcceptCall()
                            resetDraggable()
                        }
                        callDraggable.x <= minDragX + 50f && !lock -> {
                            lock = true
                            callDraggable.performHapticFeedback()
                            if (isRtl) host.onAcceptCall() else host.onDeclineCall()
                            resetDraggable()
                        }
                        callDraggable.x > initialDraggableX -> {
                            val res = if (isRtl) R.drawable.ic_phone_down_red_vector
                            else R.drawable.ic_phone_green_vector
                            callDraggable.setImageDrawable(host.getHostDrawable(res))
                        }
                        callDraggable.x <= initialDraggableX -> {
                            val res = if (isRtl) R.drawable.ic_phone_green_vector
                            else R.drawable.ic_phone_down_red_vector
                            callDraggable.setImageDrawable(host.getHostDrawable(res))
                        }
                    }
                }
            }
            true
        }

        // ── Double-click fallback
        // Tyto listenery se NESMÍ nastavovat znovu v CallActivity.initButtons(),
        // protože by je přepsaly na single-click. Viz komentář v [init].
        callAccept.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTimeAccept <= DOUBLE_CLICK_INTERVAL) {
                host.onAcceptCall()
                lock = true
                resetDraggable()
                lastClickTimeAccept = 0L
            } else {
                lastClickTimeAccept = now
            }
        }

        callDecline.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTimeDecline <= DOUBLE_CLICK_INTERVAL) {
                host.onDeclineCall()
                lock = true
                resetDraggable()
                lastClickTimeDecline = 0L
            } else {
                lastClickTimeDecline = now
            }
        }
    }

    private fun startArrowAnimation(
        arrow: ImageView,
        initialX: Float,
        initialScaleX: Float,
        initialScaleY: Float,
        translation: Float,
    ) {
        arrow.apply {
            alpha = 1f
            x = initialX
            scaleX = initialScaleX
            scaleY = initialScaleY
            animate()
                .alpha(0f)
                .translationX(translation)
                .scaleXBy(-0.5f)
                .scaleYBy(-0.5f)
                .setDuration(1000)
                .withEndAction {
                    // stopAnimation je false pouze tehdy, když resetDraggable() proběhl –
                    // takže smyčka pokračuje po každém puštění prstu
                    if (!stopAnimation) {
                        startArrowAnimation(this, initialX, initialScaleX, initialScaleY, translation)
                    }
                }
        }
    }
}
