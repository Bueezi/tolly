package be.tolly

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import be.tolly.core.auth.AuthState
import be.tolly.core.data.notifications.MailNotifier
import be.tolly.core.data.notifications.NewsNotifier
import be.tolly.core.designsystem.theme.TollyTheme
import be.tolly.core.designsystem.theme.isDark
import be.tolly.ui.TollyApp

@AndroidEntryPoint
class MainActivity : TollyActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.uiState.value.auth == AuthState.Loading }
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SystemBarsFollowTheme(state.preferences.themeMode.isDark())
            TollyTheme(state.preferences) {
                TollyApp(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Notifications open the feed, and the announcement they are about, or the Mail tab. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(MailNotifier.EXTRA_OPEN_MAIL, false) == true) {
            viewModel.requestOpenMail()
            intent.removeExtra(MailNotifier.EXTRA_OPEN_MAIL)
            return
        }
        if (intent?.getBooleanExtra(NewsNotifier.EXTRA_OPEN_NEWS, false) != true) return
        viewModel.requestOpenNews(intent.getStringExtra(NewsNotifier.EXTRA_ANNOUNCEMENT_ID))
        intent.removeExtra(NewsNotifier.EXTRA_OPEN_NEWS)
    }
}
