package cz.mts.base

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import cz.mts.base.extensions.appLockManager
import cz.mts.base.extensions.checkUseEnglish

open class MTsApp : Application() {

    open val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()

       // EmojiCompat.init(BundledEmojiCompatConfig(this))
        checkUseEnglish()
        setupAppLockManager()
    }

    private fun setupAppLockManager() {
        if (isAppLockFeatureAvailable) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
        }
    }
}
