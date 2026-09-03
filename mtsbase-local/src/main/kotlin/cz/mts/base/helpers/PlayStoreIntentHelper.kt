package cz.mts.base.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri

object PlayStoreIntentHelper {

    fun createOpenStoreIntent(context: Context, packageName: String): Intent {
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.android.vending")
        }

        val webIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (playIntent.resolveActivity(context.packageManager) != null) {
            playIntent
        } else {
            webIntent
        }
    }
}
