package cz.mts.base.adapters

import android.graphics.drawable.ColorDrawable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.*
import cz.mts.base.interfaces.MyActionModeCallback
import cz.mts.base.models.RecyclerSelectionPayload
import cz.mts.base.views.MyRecyclerView
import kotlin.math.max
import kotlin.math.min

abstract class MyRecyclerViewListAdapter<T>(
    val activity: BaseSimpleActivity,
    val recyclerView: MyRecyclerView,
    diffUtil: DiffUtil.ItemCallback<T>,
    val itemClick: (T) -> Unit,
    val onRefresh: () -> Unit = {},
) : ListAdapter<T, MyRecyclerViewListAdapter<T>.ViewHolder>(diffUtil) {
    protected val baseConfig = activity.baseConfig
    protected val resources = activity.resources!!
    protected val layoutInflater = activity.layoutInflater
    protected var textColor = activity.getProperTextColor()
    protected var backgroundColor = activity.getProperBackgroundColor()
    protected var properPrimaryColor = activity.getProperPrimaryColor()
    protected var contrastColor = properPrimaryColor.getContrastColor()
    protected var actModeCallback: MyActionModeCallback
    protected var selectedKeys = LinkedHashSet<Int>()
    protected var positionOffset = 0
    protected var actMode: ActionMode? = null

    private var actBarTextView: TextView? = null
    private var lastLongPressedItem = -1

    // API 35+ is edge-to-edge, so statusBarColor cannot be used to restore the
    // status bar. We only preserve the status bar icon appearance.
    private var lightStatusBarBeforeActionMode: Boolean? = null

    abstract fun getActionMenuId(): Int

    abstract fun prepareActionMode(menu: Menu)

    abstract fun actionItemPressed(id: Int)

    abstract fun getSelectableItemCount(): Int

    abstract fun getIsItemSelectable(position: Int): Boolean

    abstract fun getItemSelectionKey(position: Int): Int?

    abstract fun getItemKeyPosition(key: Int): Int

    abstract fun onActionModeCreated()

    abstract fun onActionModeDestroyed()

    protected fun isOneItemSelected() = selectedKeys.size == 1

    init {
        actModeCallback = object : MyActionModeCallback() {
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                actionItemPressed(item.itemId)
                return true
            }

            override fun onCreateActionMode(actionMode: ActionMode, menu: Menu?): Boolean {
                if (getActionMenuId() == 0) {
                    return true
                }

                isSelectable = true
                actMode = actionMode
                actBarTextView = layoutInflater.inflate(R.layout.actionbar_title, null) as TextView
                actBarTextView!!.layoutParams = ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                actMode!!.customView = actBarTextView
                actBarTextView!!.setOnClickListener {
                    if (getSelectableItemCount() == selectedKeys.size) {
                        finishActMode()
                    } else {
                        selectAll()
                    }
                }

                activity.menuInflater.inflate(getActionMenuId(), menu)
                val bgColor = if (activity.isDynamicTheme()) {
                    ResourcesCompat.getColor(resources, R.color.you_contextual_status_bar_color, activity.theme)
                } else {
                    resources.getColor(R.color.dark_grey, activity.theme)
                }

                actBarTextView!!.setTextColor(bgColor.getContrastColor())
                activity.updateMenuItemColors(menu, baseColor = bgColor)
                onActionModeCreated()

                activity.onSelectionModeChanged(true)

                if (activity.isDynamicTheme()) {
                    actBarTextView?.onGlobalLayout {
                        val backArrow = activity.findViewById<ImageView>(androidx.appcompat.R.id.action_mode_close_button)
                        backArrow?.applyColorFilter(bgColor.getContrastColor())
                    }
                }

                /*
                 * Android 15+ (target SDK 35+) enforces edge-to-edge. AppCompat's
                 * ActionMode still creates its old "status guard" view, which is
                 * normally black or white. That view is what causes the status-bar
                 * area to suddenly become black/white.
                 *
                 * Do not use Window.statusBarColor here. On Android 15+ that API
                 * is disabled. Instead, make AppCompat's guard transparent so the
                 * view that was already drawing underneath the status bar remains
                 * visible.
                 */
                makeActionModeStatusGuardTransparent()

                // ActionMode may also change the status-bar icon appearance.
                // Restore the state that existed before ActionMode was started.
                lightStatusBarBeforeActionMode?.let {
                    WindowCompat.getInsetsController(
                        activity.window,
                        activity.window.decorView
                    ).isAppearanceLightStatusBars = it
                }

                return true
            }

            override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean {
                prepareActionMode(menu)
                return true
            }

            override fun onDestroyActionMode(actionMode: ActionMode) {
                isSelectable = false
                selectedKeys.toHashSet().forEach { key ->
                    val position = getItemKeyPosition(key)
                    if (position != -1) {
                        toggleItemSelection(false, position, false)
                    }
                }

                updateTitle()
                selectedKeys.clear()
                actBarTextView?.text = ""
                actMode = null
                lastLongPressedItem = -1
                onActionModeDestroyed()

                activity.onSelectionModeChanged(false)

                // Restore only the icon appearance. The status-bar background is
                // supplied by the app's edge-to-edge content, not Window.statusBarColor.
                lightStatusBarBeforeActionMode?.let {
                    WindowCompat.getInsetsController(
                        activity.window,
                        activity.window.decorView
                    ).isAppearanceLightStatusBars = it
                }

                lightStatusBarBeforeActionMode = null
            }
        }
    }

    /**
     * AppCompat's ActionMode uses an internal statusGuard view. On Android 15+
     * that guard is still inserted even though statusBarColor is no longer
     * effective. Its default black/white background therefore becomes visible
     * over the app's edge-to-edge content.
     *
     * Make only that guard transparent. The actual status-bar area then shows
     * whatever View in the app was already drawing underneath it.
     *
     * This is intentionally kept in the adapter because this adapter is the
     * place where ActionMode is created.
     */
    private fun makeActionModeStatusGuardTransparent() {
        val decorView = activity.window.decorView

        // AppCompat may add the guard during the same traversal in which the
        // ActionMode is created, so wait until the decor hierarchy has settled.
        decorView.post {
            val statusBarHeight = getStatusBarInsetHeight()

            if (statusBarHeight > 0) {
                findAndClearStatusGuard(
                    decorView as ViewGroup,
                    statusBarHeight
                )
            }
        }
    }

    private fun getStatusBarInsetHeight(): Int {
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
        return insets?.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.statusBars()
        )?.top ?: 0
    }

    private fun findAndClearStatusGuard(parent: ViewGroup, statusBarHeight: Int): Boolean {
        val darkGuardColor = try {
            ContextCompat.getColor(activity, androidx.appcompat.R.color.abc_decor_view_status_guard)
        } catch (_: Exception) {
            null
        }

        val lightGuardColor = try {
            ContextCompat.getColor(activity, androidx.appcompat.R.color.abc_decor_view_status_guard_light)
        } catch (_: Exception) {
            null
        }

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)

            if (isStatusGuard(child, statusBarHeight, darkGuardColor, lightGuardColor)) {
                child.background = ColorDrawable(android.graphics.Color.TRANSPARENT)
                return true
            }

            if (child is ViewGroup && findAndClearStatusGuard(child, statusBarHeight)) {
                return true
            }
        }

        return false
    }

    private fun isStatusGuard(
        view: View,
        statusBarHeight: Int,
        darkGuardColor: Int?,
        lightGuardColor: Int?
    ): Boolean {
        if (view.height != statusBarHeight || view.width <= 0) {
            return false
        }

        val background = view.background as? ColorDrawable ?: return false
        val color = background.color

        // These are the exact colors AppCompat uses for its statusGuard.
        if (color == darkGuardColor || color == lightGuardColor) {
            return true
        }

        return false
    }

    protected fun toggleItemSelection(select: Boolean, pos: Int, updateTitle: Boolean = true) {
        if (select && !getIsItemSelectable(pos)) {
            return
        }

        val itemKey = getItemSelectionKey(pos) ?: return
        if ((select && selectedKeys.contains(itemKey)) || (!select && !selectedKeys.contains(itemKey))) {
            return
        }

        if (select) {
            selectedKeys.add(itemKey)
        } else {
            selectedKeys.remove(itemKey)
        }

        notifyItemChanged(pos + positionOffset, RecyclerSelectionPayload(select))

        if (updateTitle) {
            updateTitle()
        }

        if (selectedKeys.isEmpty()) {
            finishActMode()
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val any = payloads.firstOrNull()
        if (any is RecyclerSelectionPayload) {
            holder.itemView.isSelected = any.selected
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private fun updateTitle() {
        val selectableItemCount = getSelectableItemCount()
        val selectedCount = min(selectedKeys.size, selectableItemCount)
        val oldTitle = actBarTextView?.text
        val newTitle = "$selectedCount / $selectableItemCount"
        if (oldTitle != newTitle) {
            actBarTextView?.text = newTitle
            actMode?.invalidate()
        }
    }

    fun itemLongClicked(position: Int) {
        recyclerView.setDragSelectActive(position)
        lastLongPressedItem = if (lastLongPressedItem == -1) {
            position
        } else {
            val min = min(lastLongPressedItem, position)
            val max = max(lastLongPressedItem, position)
            for (i in min..max) {
                toggleItemSelection(true, i, false)
            }
            updateTitle()
            position
        }
    }

    protected fun getSelectedItemPositions(sortDescending: Boolean = true): ArrayList<Int> {
        val positions = ArrayList<Int>()
        val keys = selectedKeys.toList()
        keys.forEach {
            val position = getItemKeyPosition(it)
            if (position != -1) {
                positions.add(position)
            }
        }

        if (sortDescending) {
            positions.sortDescending()
        }
        return positions
    }

    protected fun selectAll() {
        val cnt = itemCount - positionOffset
        for (i in 0 until cnt) {
            toggleItemSelection(true, i, false)
        }
        lastLongPressedItem = -1
        updateTitle()
    }

    protected fun setupDragListener(enable: Boolean) {
        if (enable) {
            recyclerView.setupDragListener(object : MyRecyclerView.MyDragListener {
                override fun selectItem(position: Int) {
                    toggleItemSelection(true, position, true)
                }

                override fun selectRange(initialSelection: Int, lastDraggedIndex: Int, minReached: Int, maxReached: Int) {
                    selectItemRange(
                        initialSelection,
                        max(0, lastDraggedIndex - positionOffset),
                        max(0, minReached - positionOffset),
                        maxReached - positionOffset
                    )
                    if (minReached != maxReached) {
                        lastLongPressedItem = -1
                    }
                }
            })
        } else {
            recyclerView.setupDragListener(null)
        }
    }

    protected fun selectItemRange(from: Int, to: Int, min: Int, max: Int) {
        if (from == to) {
            (min..max).filter { it != from }.forEach { toggleItemSelection(false, it, true) }
            return
        }

        if (to < from) {
            for (i in to..from) {
                toggleItemSelection(true, i, true)
            }

            if (min > -1 && min < to) {
                (min until to).filter { it != from }.forEach { toggleItemSelection(false, it, true) }
            }

            if (max > -1) {
                for (i in from + 1..max) {
                    toggleItemSelection(false, i, true)
                }
            }
        } else {
            for (i in from..to) {
                toggleItemSelection(true, i, true)
            }

            if (max > -1 && max > to) {
                (to + 1..max).filter { it != from }.forEach { toggleItemSelection(false, it, true) }
            }

            if (min > -1) {
                for (i in min until from) {
                    toggleItemSelection(false, i, true)
                }
            }
        }
    }

    fun setupZoomListener(zoomListener: MyRecyclerView.MyZoomListener?) {
        recyclerView.setupZoomListener(zoomListener)
    }


    fun finishActMode() {
        actMode?.finish()
    }

    fun updateTextColor(textColor: Int) {
        this.textColor = textColor
        onRefresh.invoke()
    }

    fun updatePrimaryColor() {
        properPrimaryColor = activity.getProperPrimaryColor()
        contrastColor = properPrimaryColor.getContrastColor()
    }

    fun updateBackgroundColor(backgroundColor: Int) {
        this.backgroundColor = backgroundColor
    }

    protected fun createViewHolder(layoutType: Int, parent: ViewGroup?): ViewHolder {
        val view = layoutInflater.inflate(layoutType, parent, false)
        return ViewHolder(view)
    }

    protected fun createViewHolder(view: View): ViewHolder {
        return ViewHolder(view)
    }

    protected fun bindViewHolder(holder: ViewHolder) {
        holder.itemView.tag = holder
    }

    protected fun removeSelectedItems(positions: ArrayList<Int>) {
        positions.forEach {
            notifyItemRemoved(it)
        }
        finishActMode()
    }

    open inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(item: T, allowSingleClick: Boolean, allowLongClick: Boolean, callback: (itemView: View, adapterPosition: Int) -> Unit): View {
            return itemView.apply {
                callback(this, bindingAdapterPosition)

                if (allowSingleClick) {
                    setOnClickListener { viewClicked(item) }
                    setOnLongClickListener { if (allowLongClick) viewLongClicked() else viewClicked(item); true }
                } else {
                    setOnClickListener(null)
                    setOnLongClickListener(null)
                }
            }
        }

        fun viewClicked(any: T) {
            if (actModeCallback.isSelectable) {
                val currentPosition = bindingAdapterPosition - positionOffset
                val isSelected = selectedKeys.contains(getItemSelectionKey(currentPosition))
                toggleItemSelection(!isSelected, currentPosition, true)
            } else {
                itemClick.invoke(any)
            }
            lastLongPressedItem = -1
        }

        fun viewLongClicked() {
            val currentPosition = bindingAdapterPosition - positionOffset
            if (!actModeCallback.isSelectable) {
                val insetsController = WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView
                )
                lightStatusBarBeforeActionMode = insetsController.isAppearanceLightStatusBars

                (activity as AppCompatActivity).startSupportActionMode(actModeCallback)
            }

            toggleItemSelection(true, currentPosition, true)
            itemLongClicked(currentPosition)
        }
    }
}