package cz.mts.phone.adapters

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.helpers.MTS_PHONE
import cz.mts.base.models.contacts.ContactSource
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.databinding.ItemFilterContactSourceBinding

class FilterContactSourcesAdapter(
    private val activity: SimpleActivity,
    private val contactSources: List<ContactSource>,
    private val displayContactSources: List<String>
) : RecyclerView.Adapter<FilterContactSourcesAdapter.ViewHolder>() {

    private val selectedKeys = HashSet<String>()

    init {
        contactSources.forEach { contactSource ->
            if (displayContactSources.contains(contactSource.name)) {
                selectedKeys.add(contactSource.selectionKey())
            }
            // Pokud source NAME i TYPE == MTS_PHONE, první podmínka to zachytí.
            // Tato větev pokrývá případ, kdy type == MTS_PHONE, ale name se liší.
            if (contactSource.type == MTS_PHONE && displayContactSources.contains(MTS_PHONE)) {
                selectedKeys.add(contactSource.selectionKey())
            }
        }
    }

    private fun toggleItemSelection(select: Boolean, contactSource: ContactSource, position: Int) {
        if (select) {
            selectedKeys.add(contactSource.selectionKey())
        } else {
            selectedKeys.remove(contactSource.selectionKey())
        }
        notifyItemChanged(position)
    }

    fun getSelectedContactSources() =
        contactSources.filter { selectedKeys.contains(it.selectionKey()) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilterContactSourceBinding.inflate(activity.layoutInflater, parent, false)
        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(contactSources[position])
    }

    override fun getItemCount() = contactSources.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        fun bindView(contactSource: ContactSource) {
            ItemFilterContactSourceBinding.bind(itemView).apply {
                val isSelected = selectedKeys.contains(contactSource.selectionKey())
                filterContactSourceCheckbox.isChecked = isSelected
                filterContactSourceCheckbox.setColors(
                    activity.getProperTextColor(),
                    activity.getProperPrimaryColor(),
                    activity.getProperBackgroundColor()
                )

                val countText = if (contactSource.count >= 0) " (${contactSource.count})" else ""
                filterContactSourceCheckbox.text = "${contactSource.publicName}$countText"

                filterContactSourceHolder.setOnClickListener {
                    viewClicked(contactSource)
                }
            }
        }

        private fun viewClicked(contactSource: ContactSource) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val currentlySelected = selectedKeys.contains(contactSource.selectionKey())
                toggleItemSelection(!currentlySelected, contactSource, position)
            }
        }
    }

    /**
     * Unikátní klíč pro ContactSource nezávislý na hashCode().
     * Kombinuje name a type, které dohromady identifikují zdroj kontaktů.
     */
    private fun ContactSource.selectionKey() = "$name/$type"
}
