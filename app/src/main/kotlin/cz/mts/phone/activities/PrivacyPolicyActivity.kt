package cz.mts.phone.activities

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cz.mts.base.helpers.PREFS_KEY
import cz.mts.phone.R

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val webView = findViewById<WebView>(R.id.webViewPrivacy)
        webView.settings.javaScriptEnabled = true // potřeba kvůli scrollování na jazyk
        webView.webViewClient = object : WebViewClient() {
            // i kdyby v HTML náhodou byl externí odkaz, necháme ho otevřít uvnitř WebView
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }
        }
        webView.loadUrl("file:///android_asset/privacy-policy.html")

        findViewById<android.widget.Button>(R.id.btnAcknowledge).setOnClickListener {
            getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
                .edit()
                .putBoolean("privacy_policy_accepted", true)
                .commit()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Zabrání obejití policy stiskem "zpět" — místo návratu na Splash rovnou ukončí appku
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })
    }
}
