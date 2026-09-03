package cz.mts.base.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cz.mts.base.R
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.isAutoTheme
import cz.mts.base.extensions.isSystemInDarkMode

abstract class BaseSplashActivity : AppCompatActivity() {

    abstract fun initActivity()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isAutoTheme()) {
            val isDarkMode = isSystemInDarkMode()
            baseConfig.textColor = getColor(
                if (isDarkMode) R.color.theme_dark_text_color
                else R.color.theme_light_text_color
            )
            baseConfig.backgroundColor = getColor(
                if (isDarkMode) R.color.theme_dark_background_color
                else R.color.theme_light_background_color
            )
        }

        initActivity()
    }
}
