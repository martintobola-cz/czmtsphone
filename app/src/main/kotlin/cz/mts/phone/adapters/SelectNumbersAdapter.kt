package cz.mts.phone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.base.views.MyTextView
import cz.mts.phone.R
import cz.mts.phone.models.PhonePickerItem
import cz.mts.base.extensions.baseConfig as config

class SelectNumbersAdapter(
    private val items: List<PhonePickerItem>,
    private val allowMultipleNumbersPerContact: Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_NUMBER = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PhonePickerItem.Header -> VIEW_TYPE_HEADER
            is PhonePickerItem.Number -> VIEW_TYPE_NUMBER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(
                    R.layout.item_phone_picker_header,
                    parent,
                    false
                )
                HeaderViewHolder(view)
            }

            VIEW_TYPE_NUMBER -> {
                val view = inflater.inflate(
                    R.layout.item_phone_picker_number,
                    parent,
                    false
                )
                NumberViewHolder(view)
            }

            else -> error("Unknown viewType $viewType")
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        val textColor = context.getProperTextColor()
        val format = context.config.formatPhoneNumbers

        when (val item = items[position]) {

            is PhonePickerItem.Header -> {
                (holder as HeaderViewHolder).bind(item, textColor)
            }

            is PhonePickerItem.Number -> {
                (holder as NumberViewHolder).bind(item, textColor, format)
            }
        }
    }



    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.contactName)

        fun bind(item: PhonePickerItem.Header, textColor: Int) {
            name.text = item.contactName
            name.setTextColor(textColor)
        }

    }

    private inner class NumberViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val checkBox: CheckBox = view.findViewById(R.id.checkbox)
        val phoneType: TextView = view.findViewById(R.id.phoneType)
        val phoneNumber: TextView = view.findViewById(R.id.phoneNumber)
        val phoneSeparator: MyTextView = view.findViewById(R.id.phoneSeparator)

        fun bind(item: PhonePickerItem.Number, textColor: Int, format : Boolean) {

            phoneType.text = item.type.label
            phoneNumber.text = numberForRecents(item.phone.value, format)
            phoneType.setTextColor(textColor)
            phoneNumber.setTextColor(textColor)
            phoneSeparator.setTextColor(textColor)

            // nutné kvůli recyclingu
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.isSelected

            checkBox.setOnCheckedChangeListener { _, checked ->
                if (item.isSelected == checked) return@setOnCheckedChangeListener

                item.isSelected = checked

                if (checked && !allowMultipleNumbersPerContact) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        findHeaderPositionFor(pos)?.let {
                            uncheckOtherNumbers(it, item)
                        }
                    }
                }
            }



            // klik na celý řádek
            itemView.setOnClickListener {
                checkBox.toggle()
            }
        }
    }

    private fun findHeaderPositionFor(position: Int): Int? {
        for (i in position downTo 0) {
            if (items[i] is PhonePickerItem.Header) return i
        }
        return null
    }

    private fun uncheckOtherNumbers(
        headerPos: Int,
        exceptItem: PhonePickerItem.Number
    ) {
        var i = headerPos + 1
        while (i < items.size && items[i] is PhonePickerItem.Number) {
            val numberItem = items[i] as PhonePickerItem.Number
            if (numberItem !== exceptItem && numberItem.isSelected) {
                numberItem.isSelected = false
                notifyItemChanged(i)
            }
            i++
        }
    }

}
