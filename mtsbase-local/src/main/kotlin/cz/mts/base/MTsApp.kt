package cz.mts.base

import android.app.Application
import cz.mts.base.extensions.checkUseEnglish

open class MTsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
    }
}
