package cz.mts.phone.dialogs

import android.content.Context
import android.text.Editable
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import cz.mts.phone.R

object NameTypeDialog {

    private val NEWLINE_REGEX    = Regex("[\\n\\r]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val INVALID_CHARS_REGEX = Regex("[^\\p{L}\\p{N} .,_-]")

    fun show(
        context: Context,
        callback: NameTypeDialogCallback
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_name_type, null)
        val nameEdit = view.findViewById<TextInputEditText>(R.id.name_edit)
        val typeEdit = view.findViewById<TextInputEditText>(R.id.type_edit)

        AlertDialog.Builder(context)
            .setTitle(R.string.import_contacts)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                callback.onConfirmed(
                    name = normalize(nameEdit.text),
                    type = normalize(typeEdit.text)
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                callback.onCancelled()
            }
            .show()
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
