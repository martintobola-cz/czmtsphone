package cz.mts.phone.adapters

import android.telecom.Call
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import cz.mts.base.adapters.MyRecyclerViewAdapter
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.LOWER_ALPHA
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.databinding.ItemConferenceCallBinding
import cz.mts.phone.extensions.hasCapability
import cz.mts.phone.helpers.getCallContact
import java.util.WeakHashMap

/**
 * Adapter pro seznam účastníků konferenčního hovoru.
 */
class ConferenceCallsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    calls: ArrayList<Call>,
    itemClick: (Any) -> Unit,
    private val onConferenceEnded: () -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    // Privátní kopie – mutace zvenku nerozhodí adapter
    private val data: MutableList<Call> = calls.toMutableList()

    private val pendingRunnables = WeakHashMap<View, Runnable>()

    companion object {
        // Vlastní slot pro tag – nezkoliduje s root.tag který přepisuje bindViewHolder(holder)
      private val TAG_CALL = R.id.item_conference_call_name

    }

    // -------------------------------------------------------------------------
    // Povinné přepsání abstraktních metod
    // -------------------------------------------------------------------------

    override fun actionItemPressed(id: Int) {}
    override fun getActionMenuId(): Int = 0
    override fun getIsItemSelectable(position: Int): Boolean = false
    override fun getItemCount(): Int = data.size
    override fun getItemKeyPosition(key: Int): Int = -1
    override fun getItemSelectionKey(position: Int): Int? = null
    override fun getSelectableItemCount(): Int = 0
    override fun onActionModeCreated() {}
    override fun onActionModeDestroyed() {}
    override fun prepareActionMode(menu: Menu) {}

    // -------------------------------------------------------------------------
    // ViewHolder lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(
            ItemConferenceCallBinding.inflate(layoutInflater, parent, false).root
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = data[position]

        holder.bindView(call, allowSingleClick = false, allowLongClick = false) { itemView, _ ->
            ItemConferenceCallBinding.bind(itemView).apply {

                // Zruš případný runnable z předchozího bindování tohoto view
                cancelPendingRunnable(root)
                // Označ toto view aktuálním callem – guard v async callbacku níže.
                // POZOR: root.tag (bez id) přepisuje bindViewHolder(holder) → používáme vlastní slot
                root.setTag(TAG_CALL, call)

                loadCallContact(call, holder)
                setupSplitButton(call, holder)
                setupEndButton(call, holder)
            }
        }
        bindViewHolder(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        ItemConferenceCallBinding.bind(holder.itemView).apply {
            // DŮLEŽITÉ: musí být před setTag(null) —
            // runnable zkontroluje tag a zahodí se sám
            cancelPendingRunnable(root)
            root.setTag(TAG_CALL, null)

            if (!activity.isDestroyed && !activity.isFinishing) {
                Glide.with(activity).clear(itemConferenceCallImage)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Privátní pomocné metody
    // -------------------------------------------------------------------------

    /**
     * Spustí async načtení kontaktu a po dokončení aktualizuje UI.
     */
    private fun ItemConferenceCallBinding.loadCallContact(call: Call, holder: ViewHolder) {
        getCallContact(root.context, call) { callContact ->
            val runnable = Runnable {
                // View mohlo být mezitím recyklováno nebo rebindováno na jiný call
                if (root.getTag(TAG_CALL) != call) return@Runnable

                itemConferenceCallName.text = callContact.name.ifEmpty {
                    root.context.getString(R.string.unknown_caller)
                }
                itemConferenceCallName.setTextColor(textColor)

                val contactDrawable = activity.getDrawable(R.drawable.ic_person_vector)
                contactDrawable?.applyColorFilter(textColor)
                SimpleContactsHelper(activity).loadContactImage(
                    callContact.photoUri,
                    itemConferenceCallImage,
                    callContact.name,
                    contactDrawable
                )

                pendingRunnables.remove(root)
            }

            pendingRunnables[root] = runnable
            activity.runOnUiThread(runnable)
        }
    }

    /**
     * Nastaví tlačítko pro oddělení účastníka z konference.
     */
    private fun ItemConferenceCallBinding.setupSplitButton(call: Call, holder: ViewHolder) {
        val canSeparate = call.hasCapability(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)

        itemConferenceCallSplit.apply {
            isEnabled = canSeparate
            alpha = if (canSeparate) 1.0f else LOWER_ALPHA
            setColorFilter(textColor)

            setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                call.splitFromConference()
                removeItemAt(pos)
            }

            setOnLongClickListener {
                showTooltip(it)
                true
            }
        }
    }

    /**
     * Nastaví tlačítko pro ukončení hovoru konkrétního účastníka.
     */
    private fun ItemConferenceCallBinding.setupEndButton(call: Call, holder: ViewHolder) {
        val canDisconnect = call.hasCapability(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)

        itemConferenceCallEnd.apply {
            isEnabled = canDisconnect
            alpha = if (canDisconnect) 1.0f else LOWER_ALPHA
            setColorFilter(textColor)

            setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                call.disconnect()
                removeItemAt(pos)
            }

            setOnLongClickListener {
                showTooltip(it)
                true
            }
        }
    }

    /** Odebere položku ze seznamu a notifikuje adapter. Pokud zbyde jen 1, zavolá callback. */
    private fun removeItemAt(position: Int) {
        data.removeAt(position)
        notifyItemRemoved(position)
        if (data.size == 1) {
            onConferenceEnded()
        }
    }

    /** Zobrazí tooltip z contentDescription při long-clicku. */
    private fun showTooltip(view: View) {
        view.contentDescription?.toString()
            ?.takeIf { it.isNotEmpty() }
            ?.let { view.context.toast(it) }
    }

    /** Zruší pending runnable pro dané view a odstraní ho z mapy. */
    private fun cancelPendingRunnable(view: View) {
        pendingRunnables.remove(view)
    }
}
