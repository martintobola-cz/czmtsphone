package cz.mts.phone.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import cz.mts.base.dialogs.RadioGroupDialog
import cz.mts.base.models.RadioItem
import cz.mts.phone.R
import cz.mts.phone.helpers.openSpamNumberWeb
import java.util.Locale

class FakeActivity : SimpleActivity() {

    companion object {
        private const val EXTRA_NUMBER = "extra_number"
        private const val EXTRA_URL = "extra_url"

        fun start(context: Context, phoneNumber: String, iUrl: Int = -1) {
            if (phoneNumber.isBlank()) return
            val intent = Intent(context, FakeActivity::class.java).apply {
                putExtra(EXTRA_NUMBER, phoneNumber)
                putExtra(EXTRA_URL, iUrl)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    // BaseSimpleActivity volá setTheme() v onCreate i onResume s vlastním tématem,
    // takže ho tady vždy nahradíme průhledným.
    override fun setTheme(resId: Int) {
        super.setTheme(R.style.Theme_Transparent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val iUrl = intent.getIntExtra(EXTRA_URL, -1)

        if (number.isBlank()) {
            finish()
            return
        }

        if (iUrl == -1 && Locale.getDefault().language == "cs") {
            val items = arrayListOf(
                RadioItem(0, "Callfilter.app"),
                RadioItem(1, "Muzutozvednout.cz")
            )
            RadioGroupDialog(
                activity = this,
                items = items,
                titleId = R.string.url_emoji,
                cancelCallback = { finish() }
            ) { selected ->
                openSpamNumberWeb(this, selected as Int, number)
                finish()
            }
        } else {
            openSpamNumberWeb(this, iUrl, number)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // BaseSimpleActivity tady nastavila neprůhlednou barvu pozadí okna
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
    }
}
