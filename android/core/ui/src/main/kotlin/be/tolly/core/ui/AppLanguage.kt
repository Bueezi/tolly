package be.tolly.core.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language the app shows itself in, whatever the phone's language is. Android 13 and up
 * keep it themselves (it also shows in the system's per-app language settings); older versions
 * get it from a small preference that every activity applies in `attachBaseContext` ([wrap]).
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    DUTCH("nl"),
    ;

    companion object {
        fun current(context: Context): AppLanguage {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
            } else {
                prefs(context).getString(KEY, null)
            }
            return entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
        }

        /** Switches to [language]; the activity is recreated in it. */
        fun set(activity: Activity, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.getSystemService(LocaleManager::class.java).applicationLocales =
                    language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
            } else {
                prefs(activity).edit().putString(KEY, language.tag).commit()
                activity.recreate()
            }
        }

        /** Before Android 13: [base] in the chosen language, for an activity's `attachBaseContext`. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = prefs(base).getString(KEY, null) ?: return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
            return base.createConfigurationContext(config)
        }

        private fun prefs(context: Context) = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)

        private const val KEY = "language"
    }
}
