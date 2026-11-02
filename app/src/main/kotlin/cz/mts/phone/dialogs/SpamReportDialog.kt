package cz.mts.phone.dialogs

import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.views.MyAppCompatCheckbox
import cz.mts.base.views.MyEditText
import cz.mts.base.views.MyTextView
import cz.mts.phone.R

object SpamReportDialog {

    private const val DEFAULT_SUBJECT = "Other"
    private const val MAX_COMMENT_LENGTH = 200

    /**
     * Zatím lze vybrat jen jednu kategorii. Pro více kategorií stačí přepnout na true
     * (a rozšířit callback, viz [SpamReportDialogCallback]).
     */
    private const val ALLOW_MULTIPLE = false

    private val NEWLINE_REGEX = Regex("[\\n\\r]")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /** id checkboxu -> hodnota jako "subject" posílaná do API */
    private val CATEGORIES = listOf(
        R.id.spam_category_scam to "Scam",
        R.id.spam_category_telemarketing to "Telemarketing",
        R.id.spam_category_silent_call to "Silent_call",
        R.id.spam_category_financial_services to "Financial_services",
        R.id.spam_category_debt_collection to "Debt_collection",
        R.id.spam_category_business to "Business",
        R.id.spam_category_retail to "Retail",
        R.id.spam_category_other to "Other"
    )

    fun show(
        activity: BaseSimpleActivity,
        phoneNumber: String,
        callback: SpamReportDialogCallback
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_spam_report, null)
        val questionText = view.findViewById<MyTextView>(R.id.spam_question)
        val commentEdit = view.findViewById<MyEditText>(R.id.spam_comment_edit)
        val question = "\n" + activity.getString(R.string.spam_report_question, phoneNumber)
        val spannable = SpannableString(question)

        val start = question.indexOf(phoneNumber)
        if (phoneNumber.isNotEmpty() && start >= 0) {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                start + phoneNumber.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        questionText.text = spannable

        val checkboxes = CATEGORIES.map { (id, subject) ->
            view.findViewById<MyAppCompatCheckbox>(id).apply { tag = subject }
        }

        if (!ALLOW_MULTIPLE) {
            // Jen jedna volba: zaškrtnutí jedné odškrtne ostatní
            checkboxes.forEach { selected ->
                selected.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        checkboxes.forEach { other ->
                            if (other !== selected && other.isChecked) other.isChecked = false
                        }
                    }
                }
            }
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.yes) { _, _ ->
                val subject = checkboxes.firstOrNull { it.isChecked }?.tag as? String
                    ?: DEFAULT_SUBJECT
                callback.onConfirmed(
                    subject = subject,
                    comment = normalize(commentEdit.text)
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                callback.onCancelled()
            }

        activity.setupDialogStuff(view, builder) { alertDialog ->
            alertDialog.setOnCancelListener { callback.onCancelled() }
        }
    }

    private fun normalize(text: Editable?): String {
        return text
            ?.toString()
            ?.replace(NEWLINE_REGEX, " ")
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.take(MAX_COMMENT_LENGTH)
            ?: ""
    }

    interface SpamReportDialogCallback {
        /** Při ALLOW_MULTIPLE = true by se sem místo jednoho subjectu předával seznam. */
        fun onConfirmed(subject: String, comment: String)
        fun onCancelled() {}
    }
}
