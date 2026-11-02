package cz.mts.phone.dialogs

import android.text.Editable
import android.view.LayoutInflater
import com.google.android.material.textfield.TextInputEditText
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.phone.R

object NameTypeDialog {

    private val NEWLINE_REGEX = Regex("[\\n\\r]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val INVALID_CHARS_REGEX = Regex("[^\\p{L}\\p{N} .,_-]")

    fun show(
        activity: BaseSimpleActivity,
        callback: NameTypeDialogCallback
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_name_type, null)
        val nameEdit = view.findViewById<TextInputEditText>(R.id.name_edit)
        val typeEdit = view.findViewById<TextInputEditText>(R.id.type_edit)

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ ->
                callback.onConfirmed(
                    name = normalize(nameEdit.text),
                    type = normalize(typeEdit.text)
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                callback.onCancelled()
            }

        activity.setupDialogStuff(view, builder, R.string.import_contacts) { dialog ->
            dialog.setOnCancelListener { callback.onCancelled() }
        }
    }

    private fun normalize(text: Editable?): String {
        return text
            ?.toString()
            ?.replace(NEWLINE_REGEX, " ")
            ?.replace(WHITESPACE_REGEX, " ")
            ?.replace(INVALID_CHARS_REGEX, "")
            ?.trim()
            ?: ""
    }

    interface NameTypeDialogCallback {
        fun onConfirmed(name: String, type: String)
        fun onCancelled() {}
    }
}
