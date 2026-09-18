package cz.mts.phone.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import cz.mts.base.extensions.*
import cz.mts.base.helpers.Clipboard.copyTextToClipboard
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.helpers.NavigationIcon
import cz.mts.phone.databinding.ActivityAboutBinding
import cz.mts.phone.extensions.appVersionCode
import cz.mts.phone.extensions.appVersionName

class AboutActivity : SimpleActivity() {

    // stejný mechanismus jako v SettingsActivity – barva ikon nav baru
    // se přepočítává podle aktuální (uživatelem nastavitelné) barvy pozadí
    override var customNavBarLightIcons: Boolean? = null

    private val binding by viewBinding(ActivityAboutBinding::inflate)

    // ── 10x klik na History = toggle Debug 0/1 (Debug = 2 řeší jen mtsGlobalAll) ──
    private var historyClickCount = 0
    private var historyLastClickTimeMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(aboutNestedScrollview))
            setupMaterialScrollListener(aboutNestedScrollview, aboutAppbar)
        }

        setupTexts()
        setupClickListeners()
    }

    override fun onResume() {
        // stejná logika jako v SettingsActivity: přepočítat barvu ikon nav baru
        // podle aktuální barvy pozadí, protože ta je uživatelsky nastavitelná
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        // NavigationIcon.Arrow zajišťuje zavření aktivity při kliknutí na šipku
        // zpět stejně jako v SettingsActivity – řeší to setupTopAppBar sama
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.aboutHolder)
    }

    private fun changelogUrl() = "mts.speccy.cz/mtsphone-news.htm#v" + appVersionCode.toInt().toString()

    // ── texty definované přímo v aktivitě, ne v XML ─────────────────────────

    private fun setupTexts() {
        binding.apply {
            aboutVersionLabel.text = "cz.mts.phone " + appVersionName
            aboutVersionValue.text = changelogUrl()

            aboutSourceLabel.text = "Source code"
            aboutSourceValue.text = "github.com/martintobola-cz/czmtsphone"

            aboutHomeLabel.text = "Home"
            aboutHomeValue.text = "mts.speccy.cz/mtsphone.htm"

            aboutLicenseLabel.text = "License"
            aboutLicenseValue.text = "GNU/GPL3, Apache 2.0, MIT, BSD"

            aboutPrivacyLabel.text = "Privacy policy"
            aboutPrivacyValue.text = "mts.speccy.cz/privacy-policy(czmtsphone).html"

            aboutDonateLabel.text = "Donate"
            aboutDonateValue.text = "If you enjoy using this app and would like to see it continue to improve, " +
                "please consider supporting its development. Every contribution, no matter how small, " +
                "makes a real difference and helps keep the project alive and moving forward.\n" +
                "BTC \uD83E\uDE99: 14b8S8D98xBx4G5DCkt4XYsU3X4QQ7nivj"

            aboutHistoryLabel.text = "History"
            aboutHistoryValue.text = "The original app was created by Slovak developer Tibor Kaputa \uD83C\uDDF8\uD83C\uDDF0 (Simple Mobile Tools). " +
                "Unfortunately, he sold it, and the new Israeli company immediately introduced an overpriced subscription model and ads. " +
                "Naturally, this led to the creation of a new fork(s), best-known is Indian \uD83C\uDDEE\uD83C\uDDF3 fork Fossify (Naveen Singh). " +
                "Now I made a new fork, with love, from the \uD83C\uDDE8\uD83C\uDDFF Czech Republic. " +
                "The original code underwent a major overhaul and refactoring. I completely rewrote it."

            aboutEmailLabel.text = "Send email to author"
            aboutEmailValue.text = "martin.tobola@gmail.com\n\n\n\n"
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            aboutVersionHolder.setOnClickListener { onVersionClick() }
            aboutSourceHolder.setOnClickListener { onSourceClick() }
            aboutHomeHolder.setOnClickListener { onHomeClick() }
            aboutLicenseHolder.setOnClickListener { onLicenseClick() }
            aboutPrivacyHolder.setOnClickListener { onPrivacyClick() }
            aboutDonateHolder.setOnClickListener { onDonateClick() }
            aboutEmailHolder.setOnClickListener { onEmailClick() }
            aboutHistoryHolder.setOnClickListener { onHistoryClick() }
        }
    }

    // ── řádky s odkazem ─────────────────────────────────────────────────────

    private fun onVersionClick() {
        goWWW(changelogUrl())
    }

    private fun onSourceClick() {
        goWWW("github.com/martintobola-cz/czmtsphone")
    }

    private fun onHomeClick() {
        goWWW("mts.speccy.cz/mtsphone.htm")
    }

    private fun onPrivacyClick() {
        //startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        goWWW("https://mts.speccy.cz/privacy-policy(czmtsphone).html")
    }

    private fun onEmailClick() {
        goEmail("martin.tobola@gmail.com", "cz.mts.phone " + appVersionName + " (API-" + android.os.Build.VERSION.SDK_INT + ")")
    }

    // ── řádky bez odkazu ─────────────────────────────────────────────────────

    private fun onLicenseClick() {
    }

    private fun onDonateClick() {
        goWWW("drive.google.com/file/d/1IUaYSi05fpQy34Elc2ykdvoIM4jw6U6e/view?usp=drive_link")
        copyTextToClipboard(this, "BTC address", "14b8S8D98xBx4G5DCkt4XYsU3X4QQ7nivj")
    }

    // 10x klik během 1,5s okna přepne pouze Debug 0 <-> 1.
    private fun onHistoryClick() {
        val now = System.currentTimeMillis()
        if (now - historyLastClickTimeMs > 1500) {
            historyClickCount = 0
        }
        historyClickCount++
        historyLastClickTimeMs = now

        if (historyClickCount >= 10) {
            historyClickCount = 0
            iSaveDebugMode = if (iSaveDebugMode != 0) 0 else 1
            toast("Debug mode " + iSaveDebugMode.toString())
        }
    }

    private fun goWWW(sWWW: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://" + sWWW))
            this.startActivity(browserIntent)
        } catch (e: Exception) {
            this.toast(e.message.toString())
        }
    }

    private fun goEmail(sAddress: String, sSubject: String) {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(sAddress))
                putExtra(Intent.EXTRA_SUBJECT, sSubject)
            }
            this.startActivity(emailIntent)
        } catch (e: Exception) {
            this.toast(e.message.toString())
        }
    }
}
