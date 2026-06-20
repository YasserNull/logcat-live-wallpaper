package com.yn.logcatlivewallpaper.utils

import android.content.Context
import android.content.res.Configuration
import com.yn.logcatlivewallpaper.core.Preferences
import java.util.Locale

object LocaleHelper {
  fun wrap(context: Context): Context {
    val language = Preferences.getSettings(context).language
    val locale = Locale.forLanguageTag(language)
    Locale.setDefault(locale)

    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    return context.createConfigurationContext(config)
  }
}
