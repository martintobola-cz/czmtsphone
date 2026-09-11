package cz.mts.base.helpers

import android.content.Context
import android.content.ContextWrapper
import java.util.Locale

class MyContextWrapper(context: Context) : ContextWrapper(context) {

    fun wrap(context: Context, language: String): ContextWrapper {
        var newContext = context
        val config = newContext.resources.configuration
        val sysLocale = config.locales.get(0)

        if (language != "" && sysLocale.language != language) {
            val locale = Locale.Builder().setLanguage(language).build()
            Locale.setDefault(locale)
            config.setLocale(locale)
        }

        newContext = newContext.createConfigurationContext(config)
        return MyContextWrapper(newContext)
    }
}
