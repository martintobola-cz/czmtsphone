package cz.mts.base.extensions

import android.app.Application
import cz.mts.base.helpers.isNougatPlus
import java.util.Locale


fun Application.checkUseEnglish() {
    if (baseConfig.useEnglish && !isNougatPlus()) {
        val conf = resources.configuration
        conf.locale = Locale.ENGLISH
        resources.updateConfiguration(conf, resources.displayMetrics)
    }
}
