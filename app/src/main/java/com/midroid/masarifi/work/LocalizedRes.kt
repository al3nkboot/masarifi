package com.midroid.masarifi.work

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Returns a context whose resources use the app language (independent of the system locale). */
object LocalizedRes {
    fun of(context: Context, arabic: Boolean): Context = wrap(context, if (arabic) "ar" else "en")

    fun wrap(context: Context, language: String): Context {
        val locale = Locale.forLanguageTag(language)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
