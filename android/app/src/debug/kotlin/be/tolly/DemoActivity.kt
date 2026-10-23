package be.tolly

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import be.tolly.core.data.AnnouncementRepository
import be.tolly.core.data.AuthRepository
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.notifications.NewsNotifier
import be.tolly.core.model.ThemeMode
import be.tolly.demo.DemoSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug-only shortcut to the demo account (the welcome screen's Demo button does the same).
 * Extra `notify` (boolean) also posts notifications for the unread demo announcements, and
 * `notice` (boolean) shows the newest one-time notice again, as after an update.
 *
 * For screenshots (brand/store/capture.mjs) the look can be set too: `theme` (LIGHT, DARK or
 * SYSTEM), `glass` and `dynamic` (booleans) and `agenda` (LIST, DAY or WEEK); `quiet` (boolean)
 * skips the notification prompt above the news. The language is set from outside, with
 * `adb shell cmd locale set-app-locales`.
 */
@AndroidEntryPoint
class DemoActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var seeder: DemoSeeder
    @Inject lateinit var announcementRepository: AnnouncementRepository
    @Inject lateinit var newsNotifier: NewsNotifier
    @Inject lateinit var preferences: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            applyLook()
            authRepository.startDemo(seeder::seed)
            if (intent.getBooleanExtra("notice", false)) preferences.setLastNotice(0)
            // `--ez notify true` posts the unread demo announcements, to try the notifications.
            if (intent.getBooleanExtra("notify", false)) {
                newsNotifier.notify(announcementRepository.observeAll().first().filterNot { it.isRead }.take(2))
            }
            startActivity(Intent(this@DemoActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }

    private suspend fun applyLook() {
        intent.getStringExtra("theme")?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name.uppercase() }?.let { preferences.setThemeMode(it) }
        }
        if (intent.hasExtra("glass")) preferences.setGlass(intent.getBooleanExtra("glass", false))
        if (intent.hasExtra("dynamic")) preferences.setDynamicColor(intent.getBooleanExtra("dynamic", true))
        intent.getStringExtra("agenda")?.let { preferences.setAgendaMode(it.uppercase()) }
        if (intent.getBooleanExtra("quiet", false)) preferences.dismissNotificationPrompt()
    }
}
