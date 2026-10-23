package be.tolly

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.designsystem.theme.TollyTheme
import be.tolly.core.designsystem.theme.isDark
import be.tolly.core.model.UserPreferences
import be.tolly.core.ui.AppLanguage
import javax.inject.Inject

/**
 * Base of the app's activities: they show the language picked in Settings, and
 * [setThemedContent] draws in the user's theme and look. Subclasses are `@AndroidEntryPoint`s,
 * which injects [preferencesRepository].
 */
abstract class TollyActivity : ComponentActivity() {
    @Inject lateinit var preferencesRepository: PreferencesRepository

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    /** [setContent] in a [TollyTheme] that follows the user's preferences, edge to edge. */
    protected fun setThemedContent(content: @Composable () -> Unit) = setContent {
        val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(UserPreferences())
        SystemBarsFollowTheme(prefs.themeMode.isDark())
        TollyTheme(prefs, content)
    }

    /**
     * Draws edge to edge with status and navigation bar icons in the app's theme, which can differ
     * from the system's: a light app on a dark phone would otherwise get white icons on white.
     */
    @Composable
    protected fun SystemBarsFollowTheme(dark: Boolean) {
        LaunchedEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
            )
        }
    }

    private companion object {
        // The scrims androidx.activity uses for three-button navigation.
        val LIGHT_SCRIM = AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
