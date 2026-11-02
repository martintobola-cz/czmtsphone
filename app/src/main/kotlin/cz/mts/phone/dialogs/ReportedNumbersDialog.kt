package cz.mts.phone.dialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.copyToClipboard
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.phone.R
import cz.mts.phone.databinding.DialogReportedNumbersBinding
import cz.mts.phone.databinding.ItemReportedNumberBinding
import cz.mts.phone.helpers.ReportedNumbers
import cz.mts.base.extensions.baseConfig as config

/**
 * Seznam čísel, která uživatel nahlásil jako spam (nejnovější nahoře).
 * ReportedNumbers čte při prvním použití soubor, proto se načítá mimo hlavní vlákno
 * a dialog se zobrazí až po načtení. Vzhled řeší setupDialogStuff.
 */
class ReportedNumbersDialog(private val activity: BaseSimpleActivity) {

    init {
        ensureBackgroundThread {
            val numbers = ReportedNumbers.getAll(activity)
            activity.runOnUiThread { showDialog(numbers) }
        }
    }

    private fun showDialog(numbers: List<String>) {
        if (activity.isFinishing || activity.isDestroyed) return

        val binding = DialogReportedNumbersBinding.inflate(LayoutInflater.from(activity))

        binding.reportedNumbersPlaceholder.beVisibleIf(numbers.isEmpty())
        binding.reportedNumbersList.beVisibleIf(numbers.isNotEmpty())
        binding.reportedNumbersList.layoutManager = LinearLayoutManager(activity)
        binding.reportedNumbersList.adapter = ReportedNumbersAdapter(
            items = numbers,
            textColor = activity.getProperTextColor(),
            format = activity.config.formatPhoneNumbers
        )

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.dialog_close, null)

        if (numbers.isNotEmpty()) {
            builder.setNeutralButton(R.string.copy) { _, _ ->
                activity.copyToClipboard(numbers.joinToString("\n") { numberForRecents(it, activity.config.formatPhoneNumbers) }, true)
            }
        }

        activity.setupDialogStuff(binding.root, builder, R.string.reported_spam_numbers)
    }

    private class ReportedNumbersAdapter(
        private val items: List<String>,
        private val textColor: Int,
        private val format: Boolean
    ) : RecyclerView.Adapter<ReportedNumbersAdapter.ViewHolder>() {

        class ViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemReportedNumberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding.root)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.text.text = numberForRecents(items[position], format)
            holder.text.setTextColor(textColor)
        }

        override fun getItemCount() = items.size
    }
}
