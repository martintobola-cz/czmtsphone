package cz.mts.phone.activities

import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cz.mts.base.helpers.PREFS_KEY
import cz.mts.base.helpers.PRIVACY_POLICY_ACCEPTED
import cz.mts.phone.R

class PrivacyPolicyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_ABOUT = "privacy_from_about"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val fromAbout = intent.getBooleanExtra(EXTRA_FROM_ABOUT, false)
        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)

        val webView = findViewById<WebView>(R.id.webViewPrivacy)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }
        }
        webView.loadUrl("file:///android_asset/privacy-policy.html")

        // Při prvním spuštění vždy nezaškrtnuto; z About odráží aktuální souhlas
        val checkbox = findViewById<CheckBox>(R.id.chkAcknowledge)
        val btnAgree = findViewById<Button>(R.id.btnAcknowledge)
        val btnDecline = findViewById<Button>(R.id.btnDecline)

// V About tlačítko odvolává dříve udělený souhlas, při prvním spuštění odmítá
        btnDecline.setText(if (fromAbout) R.string.revoke_consent else R.string.privacy_decline)
        // Při prvním spuštění vždy nezaškrtnuto
        checkbox.isChecked = fromAbout && prefs.getBoolean(PRIVACY_POLICY_ACCEPTED, false)
        btnAgree.isEnabled = checkbox.isChecked
        checkbox.setOnCheckedChangeListener { _, checked -> btnAgree.isEnabled = checked }

        btnAgree.setOnClickListener {
            prefs.edit().putBoolean(PRIVACY_POLICY_ACCEPTED, true).commit()
            if (fromAbout) finish()
            else {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        btnDecline.setOnClickListener {
            if (fromAbout) {
                // v About = odvolání souhlasu (pravidlo 7.6)
                prefs.edit().putBoolean(PRIVACY_POLICY_ACCEPTED, false).commit()
            }
            finishAffinity()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // z About = zpět beze změny souhlasu; při prvním spuštění ukončit appku
                if (fromAbout) finish() else finishAffinity()
            }
        })
    }
}
