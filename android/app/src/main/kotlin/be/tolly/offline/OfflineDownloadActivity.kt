package be.tolly.offline

import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import be.tolly.TollyActivity
import be.tolly.core.ui.LocalRequestSignIn
import be.tolly.feature.downloads.offline.OfflineScreen

/** Hosts the "download for offline" screen, which both the course view and Downloads open. */
@AndroidEntryPoint
class OfflineDownloadActivity : TollyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContent {
            // Signing in again happens in the main screen, which shows the session banner.
            CompositionLocalProvider(LocalRequestSignIn provides ::finish) {
                OfflineScreen(onClose = ::finish)
            }
        }
    }
}
