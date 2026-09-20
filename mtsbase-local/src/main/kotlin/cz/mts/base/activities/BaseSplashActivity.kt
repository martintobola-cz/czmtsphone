package cz.mts.base.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.getDynamicBackgroundColors
import cz.mts.base.extensions.getDynamicTextColors
import cz.mts.base.extensions.isDynamicTheme

abstract class BaseSplashActivity : AppCompatActivity() {

    abstract fun initActivity()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //systémové téma (7), ale zrovna nejedou live "you" barvy (starší Android, nebo vypnutý dynamic theme přepínač)
        //=> textColor/backgroundColor v configu musí zůstat čerstvé podle aktuálního system dark/light módu
        if (baseConfig.themeIdSaved == 7 && !isDynamicTheme()) {
            baseConfig.textColor = getColor(getDynamicTextColors())
            baseConfig.backgroundColor = getColor(getDynamicBackgroundColors())
        }

        initActivity()
    }
}