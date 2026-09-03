package cz.mts.phone.helpers

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cz.mts.phone.R

/**
 * Jednoduchý adapter pro CALL mód (bez editace).
 * Používá layout item_sms_template.xml (tvTemplate).
 *
 * Pozn.: V EDIT módu se místo tohoto adapteru používá vnitřní
 * SmsEditAdapter uvnitř SmsQuickReplyOverlay.
 */
class SmsTemplateAdapter(
    private val templates: List<String>,
    private val textSizeSp: Float,
    private val onSendClick: (String) -> Unit
) : RecyclerView.Adapter<SmsTemplateAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        // item_sms_template.xml používá id tvTemplate (stará verze)
        val tvTemplate: TextView = view.findViewById(R.id.tvTemplate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sms_template, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tvTemplate.text = templates[position]
        holder.tvTemplate.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        var lastClickTime = 0L
        holder.tvTemplate.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime <= 400L) {
                onSendClick(templates[position])
                lastClickTime = 0L
            } else {
                lastClickTime = now
            }
        }
    }

    override fun getItemCount() = templates.size
}
