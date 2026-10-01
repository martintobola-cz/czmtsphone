package cz.mts.phone.activities

import android.content.Intent
import cz.mts.base.activities.BaseSplashActivity
import cz.mts.base.helpers.EASTER_EGG_MODE
import cz.mts.base.helpers.PREFS_KEY
import cz.mts.base.helpers.PRIVACY_POLICY_ACCEPTED
import cz.mts.base.helpers.SHOWNEWS_MTS

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
        val debug = prefs.getBoolean(EASTER_EGG_MODE, false)
        val iFirstRun = prefs.getInt(SHOWNEWS_MTS, 0)

        val needsPolicy = if (prefs.contains(PRIVACY_POLICY_ACCEPTED)) {
            !prefs.getBoolean(PRIVACY_POLICY_ACCEPTED, false)   // souhlas explicitně odebrán / nedán
        } else {
            iFirstRun == 0
        }

        val targetActivity = if (debug || needsPolicy) PrivacyPolicyActivity::class.java
        else MainActivity::class.java

        startActivity(Intent(this, targetActivity))
        finish()
    }
}
