package cz.mts.phone.helpers

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.extensions.baseConfig as config
import cz.mts.base.extensions.dpToPx
import cz.mts.base.helpers.FONT_SIZE_LARGE
import cz.mts.base.helpers.FONT_SIZE_MEDIUM
import cz.mts.base.helpers.FONT_SIZE_SMALL
import cz.mts.base.helpers.SMS_TEMPLATE_SEPARATOR
import cz.mts.phone.R
import kotlin.math.abs

data class OverlayColors(
    val windowBackground: Int    = Color.parseColor("#CC000000"),
    val activeRowBackground: Int = Color.parseColor("#33FFFFFF"),
    val textColor: Int           = Color.WHITE,
    val iconTint: Int            = Color.WHITE,
    val titleColor: Int          = Color.WHITE,
    val dividerColor: Int?       = null,
    val dimTextColor: Int?       = null
) {
    fun resolvedDimTextColor(): Int =
        dimTextColor ?: Color.argb(128, Color.red(textColor), Color.green(textColor), Color.blue(textColor))

    fun resolvedDividerColor(): Int =
        dividerColor ?: Color.argb(51, Color.red(titleColor), Color.green(titleColor), Color.blue(titleColor))
}

class SmsQuickReplyOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onDeclineWithSms(message: String)
        fun onDismissed()
    }

    enum class Mode { CALL, EDIT }

    var listener: Listener? = null
    var mode: Mode = Mode.CALL

    private val itemHeightPx: Int = context.dpToPx(56)
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnDeclineSms: ImageButton
    private lateinit var snapHelper: LinearSnapHelper
    private var pulseAnimator: ObjectAnimator? = null
    private var pendingScrollRunnable: Runnable? = null
    private var colors: OverlayColors = OverlayColors()

    private val templates: MutableList<String> by lazy {
        context.config.smsTemplates
            .split(SMS_TEMPLATE_SEPARATOR)
            .filter { it.isNotBlank() }
            .toMutableList()
    }

    private val ADD_NEW_SENTINEL = "__ADD_NEW__"

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_sms_quick_reply, this, true)
        // setupViews() se volá až v show() – mode musí být nastaven dřív
    }

    // ── Barvy ────────────────────────────────────────────────────────────────

    fun applyColors(c: OverlayColors = OverlayColors()) {
        colors = c
        findViewById<FrameLayout>(R.id.overlayRoot).setBackgroundColor(c.windowBackground)
        val highlight = findViewById<View?>(R.id.viewCenterHighlight)
        val hlBg = highlight?.background
        if (hlBg is GradientDrawable) hlBg.setColor(c.activeRowBackground)
        else highlight?.setBackgroundColor(c.activeRowBackground)
        findViewById<TextView?>(R.id.tvOverlayTitle)?.setTextColor(c.titleColor)
        findViewById<View?>(R.id.viewTitleDivider)?.setBackgroundColor(c.resolvedDividerColor())
        recyclerView.post { updateCenterHighlights() }  // ← bylo notifyDataSetChanged()
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun persistTemplates() {
        context.config.smsTemplates = templates.joinToString(SMS_TEMPLATE_SEPARATOR)
    }

    // ── Pomocná funkce: spolehlivá centrální pozice ───────────────────────────

    /**
     * Vrátí index položky, která je aktuálně uprostřed RecyclerView.
     * snapHelper.findSnapView() vrací null dokud snap ještě neproběhl
     * (např. při prvním onBindViewHolder) – v tom případě fallback
     * na první plně viditelnou položku, což je po postScroll(0) správně 0.
     */
    private fun currentCenterPosition(): Int {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return 0
        val snapView = snapHelper.findSnapView(lm)
        if (snapView != null) return lm.getPosition(snapView)
        // Fallback: první plně viditelná položka
        val first = lm.findFirstCompletelyVisibleItemPosition()
        return if (first != RecyclerView.NO_POSITION) first else 0
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private inner class SmsEditAdapter(
        private val items: MutableList<String>,
        private val textSizeSp: Float,
        private val onSendClick: (String) -> Unit,
//        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SmsEditAdapter.VH>() {

        var editingPosition: Int = -1
            private set
        private var editingOriginalText: String = ""

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvText: TextView    = view.findViewById(R.id.tvSmsTemplate)
            val etText: EditText    = view.findViewById(R.id.etSmsTemplate)
            val ivDelete: ImageView = view.findViewById(R.id.ivDeleteTemplate)
            val ivSave: ImageView   = view.findViewById(R.id.ivSaveTemplate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sms_template_edit, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item      = items[position]
            val isAddNew  = (item == ADD_NEW_SENTINEL)
            val isEditing = (position == editingPosition)
            // Centrální pozice vyhodnocená spolehlivě
            val isCenter  = (position == currentCenterPosition())

            // Barva ikon
            val iconTint = PorterDuffColorFilter(colors.iconTint, PorterDuff.Mode.SRC_IN)
            holder.ivDelete.colorFilter = iconTint
            holder.ivSave.colorFilter   = iconTint

            if (isEditing) {
                // ── Editační stav ─────────────────────────────────────────
                holder.tvText.visibility   = GONE
                holder.etText.visibility   = VISIBLE
                holder.ivDelete.visibility = GONE
                holder.ivSave.visibility   = VISIBLE

                holder.etText.apply {
                    maxLines     = 1
                    inputType    = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    isSingleLine = true
                    // ADD_NEW: začneme s prázdným polem; ostatní: původní text
                    setText(if (isAddNew || editingOriginalText == ADD_NEW_SENTINEL) "" else item)
                    setSelection(text.length)
                    requestFocus()
                    setTextColor(colors.textColor)
                }

                holder.etText.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        cancelEditing(holder); true
                    } else false
                }

                holder.ivSave.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos < 0) return@setOnClickListener
                    val newText = holder.etText.text.toString().trim()
                    if (newText.isNotEmpty()) {
                        if (editingOriginalText == ADD_NEW_SENTINEL) {
                            // Uložit jako novou šablonu – sentinel zůstane na posledním místě
                            templates.add(newText)
                            items[pos] = newText          // sentinel slot → nový text
                            items.add(ADD_NEW_SENTINEL)   // nový sentinel na konec
                            persistTemplates()
                            clearEditing()
                            notifyItemChanged(pos)
                            notifyItemInserted(items.size - 1)
                        } else {
                            items[pos] = newText
                            if (pos in templates.indices) templates[pos] = newText
                            persistTemplates()
                            clearEditing()
                            notifyItemChanged(pos)
                        }
                    } else {
                        // Prázdný vstup – zrušit editaci, obnovit původní
                        if (editingOriginalText == ADD_NEW_SENTINEL) items[pos] = ADD_NEW_SENTINEL
                        clearEditing()
                        notifyItemChanged(pos)
                    }
                    hideKeyboard(holder.etText)
                    recyclerView.post { updateCenterHighlights() }
                }

                holder.itemView.setOnClickListener(null)
                holder.itemView.setOnTouchListener(null)

            } else {
                // ── Normální stav ─────────────────────────────────────────
                holder.tvText.visibility = VISIBLE
                holder.tvText.text = if (isAddNew)
                    context.getString(R.string.sms_add_new_template)
                else item
                // Vždy 2 řádky v normálním stavu
                holder.tvText.maxLines = 2
                holder.tvText.setTextColor(
                    if (isCenter) colors.textColor else colors.resolvedDimTextColor()
                )
                holder.tvText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)

                holder.etText.visibility = GONE
                holder.ivSave.visibility = GONE

                // Koš: jen na centrální položce v EDIT módu, ne u ADD_NEW
                // MÁ BÝT:
                if (isCenter && mode == Mode.EDIT && !isAddNew) {
                    holder.ivDelete.visibility = VISIBLE
                } else {
                    holder.ivDelete.visibility = INVISIBLE
                    holder.ivDelete.setOnClickListener(null)
                }

                // Gesta – editaci/odeslání lze spustit jen pokud je položka uprostřed
                val gestureDetector = GestureDetector(context,
                    object : GestureDetector.SimpleOnGestureListener() {

                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            val pos = holder.bindingAdapterPosition
                            if (pos < 0 || editingPosition >= 0) return false
                            val centerNow = currentCenterPosition()
                            if (pos != centerNow) return false
                            when (mode) {
                                Mode.EDIT -> {
                                    startEditing(pos, items[pos])
                                    notifyItemChanged(pos)
                                }
                                Mode.CALL -> if (!isAddNew) onSendClick(item)
                            }
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            val pos = holder.bindingAdapterPosition
                            if (pos < 0 || editingPosition >= 0) return
                            val centerNow = currentCenterPosition()
                            if (pos != centerNow) return
                            if (mode == Mode.EDIT) {
                                startEditing(pos, items[pos])
                                notifyItemChanged(pos)
                            }
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            return true
                        }
                    })

                holder.itemView.setOnClickListener {}
                holder.itemView.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    gestureDetector.onTouchEvent(event)
                    true
                }
            }
        }

        // ── Editační stav ─────────────────────────────────────────────────

        private fun startEditing(pos: Int, originalText: String) {
            editingPosition     = pos
            editingOriginalText = originalText
            setRecyclerScrollEnabled(false)
            // Klávesnice se musí vyžádat až po notifyItemChanged → onBind → etText je VISIBLE
            recyclerView.postDelayed({
                val vh = recyclerView.findViewHolderForAdapterPosition(pos) as? VH ?: return@postDelayed
                vh.etText.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(vh.etText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        private fun clearEditing() {
            editingPosition     = -1
            editingOriginalText = ""
            setRecyclerScrollEnabled(true)
        }

        // 2. OPRAVA: barva po cancel – přidat post{} na konec cancelEditing()
        fun cancelEditing(holder: VH) {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) {
                items[pos] = if (editingOriginalText == ADD_NEW_SENTINEL) ADD_NEW_SENTINEL
                else editingOriginalText
            }
            hideKeyboard(holder.etText)
            clearEditing()
            if (pos >= 0) notifyItemChanged(pos)
            // Snap pozice se obnoví až po layout passu – proto post{}
            recyclerView.post { updateCenterHighlights() }
        }

        fun cancelEditingIfActive() {
            if (editingPosition < 0) return
            val vh = recyclerView.findViewHolderForAdapterPosition(editingPosition) as? VH
            if (vh != null) cancelEditing(vh) else clearEditing()
        }

        fun removeItem(pos: Int) {
            if (pos in 0 until items.size) {
                items.removeAt(pos)
                notifyItemRemoved(pos)
                notifyItemRangeChanged(maxOf(0, pos - 1), minOf(2, items.size))
            }
        }

        fun isEditing() = editingPosition >= 0

        private fun hideKeyboard(view: View) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    // ── LockableLayoutManager ─────────────────────────────────────────────────

    private class LockableLayoutManager(ctx: Context) : LinearLayoutManager(ctx) {
        var scrollEnabled: Boolean = true
        override fun canScrollVertically()   = scrollEnabled && super.canScrollVertically()
        override fun canScrollHorizontally() = scrollEnabled && super.canScrollHorizontally()
    }

    private lateinit var lockableLayoutManager: LockableLayoutManager

    private fun setRecyclerScrollEnabled(enabled: Boolean) {
        lockableLayoutManager.scrollEnabled = enabled
    }

    // ── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        lockableLayoutManager = LockableLayoutManager(context)
        snapHelper = LinearSnapHelper()

        recyclerView.layoutManager = lockableLayoutManager
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.adapter = buildAdapter()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateItemAppearances()
            }
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCenterHighlights()
                }
            }
        })

        postScroll()
    }

    private fun updateCenterHighlights() {
        val centerPos = currentCenterPosition()
        val adapter = recyclerView.adapter as? SmsEditAdapter ?: return
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val pos = recyclerView.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val isCenter = (pos == centerPos)
            val isEditing = (pos == adapter.editingPosition)
            val isAddNew = (pos == adapter.itemCount - 1 && mode == Mode.EDIT)

            // Barva textu
            val tv = child.findViewById<TextView?>(R.id.tvSmsTemplate)
            tv?.setTextColor(if (isCenter) colors.textColor else colors.resolvedDimTextColor())

            // Koš – jen v EDIT módu, jen centrální, jen ne editing a ne ADD_NEW
            val ivDelete = child.findViewById<ImageView?>(R.id.ivDeleteTemplate)
            if (ivDelete != null) {
                val showDelete = isCenter && mode == Mode.EDIT && !isEditing && !isAddNew
                ivDelete.visibility = if (showDelete) VISIBLE else INVISIBLE

                if (showDelete) {
                    val deletPos = pos  // zachytit aktuální pos
                    ivDelete.setOnClickListener {
                        if (deletPos in 0 until templates.size) {
                            templates.removeAt(deletPos)
                            persistTemplates()
                            (recyclerView.adapter as? SmsEditAdapter)?.removeItem(deletPos)
                            recyclerView.postDelayed({ updateCenterHighlights() }, 750)
                        }
                    }
                } else {
                    ivDelete.setOnClickListener(null)
                }
            }
        }
    }
    private fun buildDisplayList(): MutableList<String> =
        if (mode == Mode.EDIT) (templates + ADD_NEW_SENTINEL).toMutableList()
        else templates.toMutableList()

    private fun buildAdapter(): SmsEditAdapter {
        val isEdit = (mode == Mode.EDIT)
        return SmsEditAdapter(
            items = buildDisplayList(),
            textSizeSp = resolveTextSizeSp(),
            onSendClick = { message ->
                if (!isEdit) {
                    context.config.lastUsedSmsTemplate = message
                    stopPulseAnimation()
                    listener?.onDeclineWithSms(message)
                }
            }
        )
    }


    private fun resolveTextSizeSp(): Float = when (context.config.fontSize) {
        FONT_SIZE_SMALL  -> 12f
        FONT_SIZE_MEDIUM -> 14f
        FONT_SIZE_LARGE  -> 16f
        else             -> 18f
    }

    private fun postScroll() {
        pendingScrollRunnable?.let { recyclerView.removeCallbacks(it) }
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return

        val targetPos = if (mode == Mode.CALL) {
            val last = context.config.lastUsedSmsTemplate.trim()
            if (last.isNotBlank()) {
                templates.indexOfFirst { it.trim() == last }.takeIf { it >= 0 } ?: 0
            } else 0
        } else 0

        val runnable = Runnable {
            lm.scrollToPosition(targetPos)
            // Nechat RecyclerView layout proběhnout, pak snap na přesnou pozici
            recyclerView.post {
                val snapView = snapHelper.findSnapView(lm)
                if (snapView != null) {
                    val distance = snapHelper.calculateDistanceToFinalSnap(lm, snapView)
                    if (distance != null) recyclerView.scrollBy(distance[0], distance[1])
                }
                updateItemAppearances()
                updateCenterHighlights()
            }
        }
        pendingScrollRunnable = runnable
        recyclerView.post(runnable)
    }

    // ── Back button ───────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            val adapter = recyclerView.adapter as? SmsEditAdapter
            if (adapter != null && adapter.isEditing()) {
                adapter.cancelEditingIfActive()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ── Vzhled položek ────────────────────────────────────────────────────────

    private fun updateItemAppearances() {
        val centerY = recyclerView.height / 2f
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val childCenterY = (child.top + child.bottom) / 2f
            val distance = abs(childCenterY - centerY)
            val ratio = (1f - (distance / (itemHeightPx * 3.5f))).coerceIn(0f, 1f)
            child.alpha = 0.25f + 0.75f * ratio
            val scale = 0.82f + 0.18f * ratio
            child.scaleX = scale
            child.scaleY = scale
        }
    }

    private fun getCenteredTemplate(): String {
        val item = buildDisplayList().getOrNull(currentCenterPosition()) ?: ""
        return if (item == ADD_NEW_SENTINEL) templates.firstOrNull() ?: "" else item
    }

    // ── Views, tlačítka, dismiss ──────────────────────────────────────────────

    private fun setupViews() {
        btnDeclineSms = findViewById(R.id.btnDeclineSms)
        recyclerView  = findViewById(R.id.rvSmsTemplates)
        setupRecyclerView()
        findViewById<TextView?>(R.id.tvOverlayTitle)?.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_SP, resolveTextSizeSp()
        )
        setupButton()
        setupDismiss()
    }

    private fun setupButton() {
        btnDeclineSms.setOnClickListener {
            if (mode == Mode.CALL) {
                val message = getCenteredTemplate()
                stopPulseAnimation()
                listener?.onDeclineWithSms(message)
            }
        }
    }

    private fun setupDismiss() {
        findViewById<FrameLayout>(R.id.overlayRoot).setOnClickListener {
            val adapter = recyclerView.adapter as? SmsEditAdapter
            if (adapter != null && adapter.isEditing()) {
                adapter.cancelEditingIfActive()
            } else {
                listener?.onDismissed()
            }
        }
    }

    // ── Pulse animace ─────────────────────────────────────────────────────────

    private fun startPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            btnDeclineSms,
            PropertyValuesHolder.ofFloat(SCALE_X, 1f, 1.12f, 1f),
            PropertyValuesHolder.ofFloat(SCALE_Y, 1f, 1.12f, 1f)
        ).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        btnDeclineSms.scaleX = 1f
        btnDeclineSms.scaleY = 1f
    }

    // ── Zobrazení / skrytí ────────────────────────────────────────────────────

    fun show(parent: ViewGroup) {
        if (parent.findViewById<View>(R.id.overlayRoot) == null) {
            setupViews()   // mode je teď správně nastaven
            alpha = 0f
            parent.addView(this, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            animate().alpha(1f).setDuration(200).start()
            if (mode == Mode.CALL) startPulseAnimation()
        }
    }

    fun dismiss() {
        stopPulseAnimation()
        animate().alpha(0f).setDuration(180).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
        }.start()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pendingScrollRunnable?.let { recyclerView.removeCallbacks(it) }
        pendingScrollRunnable = null
        listener = null
    }


}
