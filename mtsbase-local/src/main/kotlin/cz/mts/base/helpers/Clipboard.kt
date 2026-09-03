package cz.mts.base.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import cz.mts.base.activities.BaseSimpleActivity


object Clipboard {

fun copyTextToClipboard(activity: BaseSimpleActivity, label: String, text: String) {
        val clipboard: ClipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

}
