package cz.mts.phone.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.net.toUri
import cz.mts.base.extensions.*
import cz.mts.base.helpers.Clipboard.copyTextToClipboard
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.base.helpers.NavigationIcon
import cz.mts.phone.databinding.ActivityAboutBinding
import cz.mts.phone.extensions.appVersionCode
import cz.mts.phone.extensions.appVersionName

private const val URL_SCHEME = "https://"
private const val URL_CHANGELOG = "${URL_SCHEME}mts.speccy.cz/mtsphone-news.htm#v"
private const val URL_PRIVACY = "${URL_SCHEME}mts.speccy.cz/privacy-policy(czmtsphone).html"
private const val TEXT_PRIVACY = "mts.speccy.cz/privacy-policy"
private const val URL_SOURCE = "${URL_SCHEME}github.com/martintobola-cz/czmtsphone"
private const val URL_HOME = "${URL_SCHEME}mts.speccy.cz/mtsphone.htm"
private const val URL_DONATE = "${URL_SCHEME}buymeacoffee.com/cz.mts.phone"

private const val URL_THANKS_1 = "${URL_SCHEME}github.com/SimpleMobileTools/Simple-Dialer"
private const val URL_THANKS_2 = "${URL_SCHEME}github.com/FossifyOrg/Phone"
private const val URL_THANKS_3 = "${URL_SCHEME}github.com/kitsumed/ShizuCallRecorder"
private const val URL_THANKS_4 = "${URL_SCHEME}github.com/thedjchi/Shizuku"
private const val URL_THANKS_5 = "${URL_SCHEME}github.com/Genymobile/scrcpy"

private const val AUTHOR_EMAIL = "martin.tobola@gmail.com"
private const val BTC_ADDRESS = "14b8S8D98xBx4G5DCkt4XYsU3X4QQ7nivj"

private const val DEBUG_CLICKS_REQUIRED = 10
private const val DEBUG_CLICK_WINDOW_MS = 1500L

private val String.withoutScheme get() = removePrefix(URL_SCHEME)

class AboutActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null

    private val binding by viewBinding(ActivityAboutBinding::inflate)

    private val changelogUrl by lazy { URL_CHANGELOG + appVersionCode.toInt() }

    // 10x klik na History = toggle Debug 0/1 (Debug = 2 řeší jen mtsGlobalAll)
    private var historyClickCount = 0
    private var historyLastClickMs = 0L

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
        // barva pozadí je uživatelsky nastavitelná → přepočítat barvu ikon nav baru
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.aboutHolder)
    }

    private fun setupTexts() = binding.apply {
        aboutVersionLabel.text = "cz.mts.phone $appVersionName"
        aboutVersionValue.text = changelogUrl.withoutScheme

        aboutSourceLabel.text = "Source code"
        aboutSourceValue.text = URL_SOURCE.withoutScheme

        aboutHomeLabel.text = "Home"
        aboutHomeValue.text = URL_HOME.withoutScheme

        aboutLicenseLabel.text = "License"
        aboutLicenseValue.text = "GNU/GPL3, Apache 2.0, MIT, BSD"

        aboutPrivacyLabel.text = "Privacy policy"
        aboutPrivacyValue.text = TEXT_PRIVACY

        aboutDonateLabel.text = "Donate"
        aboutDonateValue.text = "If you enjoy using this app and would like to see it continue to improve, " +
            "please consider supporting its development. Every contribution, no matter how small, " +
            "makes a real difference and helps keep the project alive and moving forward.\n\n" +
            "☕ - ${URL_DONATE.withoutScheme}\n" +
            "\uD83E\uDE99 - $BTC_ADDRESS"

        aboutHistoryLabel.text = "History"
        aboutHistoryValue.text = "The original app was created by Slovak developer Tibor Kaputa \uD83C\uDDF8\uD83C\uDDF0 (Simple Mobile Tools). " +
            "Unfortunately, he sold it, and the new Israeli company immediately introduced an overpriced subscription model and ads. " +
            "Naturally, this led to the creation of a new fork(s), best-known is Indian \uD83C\uDDEE\uD83C\uDDF3 fork Fossify (Naveen Singh). " +
            "Now I made a new fork, with love, from the \uD83C\uDDE8\uD83C\uDDFF Czech Republic. " +
            "The original code underwent a major overhaul and refactoring. I completely rewrote it \uD83D\uDE01"

        aboutThanksLabel.text = "Special thanks"
        thanksLinks().forEach { (view, url) -> view.text = url.withoutScheme }

        aboutEmailLabel.text = "Send email to author"
        aboutEmailValue.text = AUTHOR_EMAIL + "\n\n"
    }

    private fun thanksLinks() = binding.run {
        listOf(
            aboutThanksLink1 to URL_THANKS_1,
            aboutThanksLink2 to URL_THANKS_2,
            aboutThanksLink3 to URL_THANKS_3,
            aboutThanksLink4 to URL_THANKS_4,
            aboutThanksLink5 to URL_THANKS_5,
        )
    }

    private fun setupClickListeners() = binding.apply {
        aboutVersionHolder.setOnClickListener { openUrl(changelogUrl) }
        aboutSourceHolder.setOnClickListener { openUrl(URL_SOURCE) }
        aboutHomeHolder.setOnClickListener { openUrl(URL_HOME) }
        aboutDonateHolder.setOnClickListener { onDonateClick() }
        aboutEmailHolder.setOnClickListener { onEmailClick() }
        aboutHistoryHolder.setOnClickListener { onHistoryClick() }
        thanksLinks().forEach { (view, url) -> view.setOnClickListener { openUrl(url) } }

        aboutPrivacyHolder.setOnClickListener {
            startActivity(
                Intent(this@AboutActivity, PrivacyPolicyActivity::class.java)
                    .putExtra(PrivacyPolicyActivity.EXTRA_FROM_ABOUT, true)
            )
        }
    }

    private fun onDonateClick() {
        copyTextToClipboard(this, "BTC address", BTC_ADDRESS)
        openUrl(URL_DONATE)
    }

    private fun onEmailClick() {
        val subject = "cz.mts.phone $appVersionName (API-${Build.VERSION.SDK_INT})"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf(AUTHOR_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        startActivitySafely(intent)
    }

    private fun onHistoryClick() {
        val now = SystemClock.elapsedRealtime()
        if (now - historyLastClickMs > DEBUG_CLICK_WINDOW_MS) historyClickCount = 0
        historyLastClickMs = now

        if (++historyClickCount >= DEBUG_CLICKS_REQUIRED) {
            historyClickCount = 0
            iSaveDebugMode = if (iSaveDebugMode != 0) 0 else 1
            toast("Debug mode $iSaveDebugMode")
        }
    }

    private fun openUrl(url: String) =
        startActivitySafely(Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun startActivitySafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No app found to handle this action")
        }
    }
}
