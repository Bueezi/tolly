package be.tolly.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.model.DEFAULT_NEWS_CHECK_MINUTES
import be.tolly.core.model.NEWS_CHECK_INTERVALS
import be.tolly.core.model.ThemeMode
import be.tolly.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val preferences: Flow<UserPreferences> = context.preferencesStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.LIGHT,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            showCommunities = prefs[SHOW_COMMUNITIES] ?: false,
            showPastCourses = prefs[SHOW_PAST] ?: false,
            copyToDownloads = prefs[COPY_TO_DOWNLOADS] ?: false,
            downloadsFolder = prefs[DOWNLOADS_FOLDER],
            notifyAnnouncements = prefs[NOTIFY_ANNOUNCEMENTS] ?: false,
            notifyMail = prefs[NOTIFY_MAIL] ?: false,
            tabBadges = prefs[TAB_BADGES] ?: true,
            newsCheckMinutes = prefs[NEWS_CHECK_MINUTES]?.takeIf { it in NEWS_CHECK_INTERVALS } ?: DEFAULT_NEWS_CHECK_MINUTES,
            notificationPromptDone = prefs[NOTIFICATION_PROMPT_DONE] ?: false,
            // Earlier versions stored a variant (FROSTED, MIDNIGHT, ADAPTIVE): any of them means on.
            glass = prefs[GLASS_STYLE]?.let { it != GLASS_OFF } ?: (prefs[GLASS_UI] == true),
            shakeToSwitchLook = prefs[SHAKE_TO_SWITCH_LOOK] ?: true,
            extraSeries = prefs[EXTRA_SERIES].orEmpty(),
            setupDone = prefs[SETUP_DONE] ?: false,
            amoled = prefs[AMOLED] ?: false,
            studentNumber = prefs[STUDENT_NUMBER],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.preferencesStore.edit { it[THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.preferencesStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setShowCommunities(show: Boolean) {
        context.preferencesStore.edit { it[SHOW_COMMUNITIES] = show }
    }

    suspend fun setShowPastCourses(show: Boolean) {
        context.preferencesStore.edit { it[SHOW_PAST] = show }
    }

    suspend fun setCopyToDownloads(enabled: Boolean) {
        context.preferencesStore.edit { it[COPY_TO_DOWNLOADS] = enabled }
    }

    /** The folder (a tree URI the app holds a persisted permission for) for copies; null for `Download/Tolly`. */
    suspend fun setDownloadsFolder(treeUri: String?) {
        context.preferencesStore.edit { if (treeUri == null) it.remove(DOWNLOADS_FOLDER) else it[DOWNLOADS_FOLDER] = treeUri }
    }

    suspend fun setNewsCheckMinutes(minutes: Int) {
        context.preferencesStore.edit { it[NEWS_CHECK_MINUTES] = minutes }
    }

    suspend fun setSetupDone() {
        context.preferencesStore.edit { it[SETUP_DONE] = true }
    }

    suspend fun setNotifyAnnouncements(enabled: Boolean) {
        context.preferencesStore.edit {
            it[NOTIFY_ANNOUNCEMENTS] = enabled
            it[NOTIFICATION_PROMPT_DONE] = true
            // Only announcements posted from now on count as new; no flood of old ones.
            if (enabled) it[LAST_NOTIFIED] = System.currentTimeMillis()
        }
    }

    suspend fun setNotifyMail(enabled: Boolean) {
        context.preferencesStore.edit {
            it[NOTIFY_MAIL] = enabled
            // Mail that is unread now isn't new: only what arrives after this counts.
            it.remove(LAST_MAIL_UNREAD)
        }
    }

    suspend fun setTabBadges(enabled: Boolean) {
        context.preferencesStore.edit { it[TAB_BADGES] = enabled }
    }

    /** The unread mail count last seen (in the app or by the background check); null when not known. */
    suspend fun lastMailUnread(): Int? = context.preferencesStore.data.first()[LAST_MAIL_UNREAD]

    suspend fun setLastMailUnread(count: Int) {
        context.preferencesStore.edit { it[LAST_MAIL_UNREAD] = count }
    }

    /** The one-time notice seen last (see `Notices`); null on installs that never stored one. */
    suspend fun lastNotice(): Int? = context.preferencesStore.data.first()[LAST_NOTICE]

    suspend fun setLastNotice(id: Int) {
        context.preferencesStore.edit { it[LAST_NOTICE] = id }
    }

    suspend fun dismissNotificationPrompt() {
        context.preferencesStore.edit { it[NOTIFICATION_PROMPT_DONE] = true }
    }

    suspend fun setAmoled(enabled: Boolean) {
        context.preferencesStore.edit { it[AMOLED] = enabled }
    }

    suspend fun setGlass(enabled: Boolean) {
        context.preferencesStore.edit {
            it[GLASS_STYLE] = if (enabled) GLASS_ON else GLASS_OFF
            it.remove(GLASS_UI)
        }
    }

    /** Shows the reeksen [groups] in the agenda besides the user's own, or stops showing them. */
    suspend fun setExtraSeries(groups: Set<String>, shown: Boolean) {
        context.preferencesStore.edit {
            val current = it[EXTRA_SERIES].orEmpty()
            it[EXTRA_SERIES] = if (shown) current + groups else current - groups
        }
    }

    /** The agenda's view (list, day or week) as last picked, by name; null until one is picked. */
    suspend fun agendaMode(): String? = context.preferencesStore.data.first()[AGENDA_MODE]

    suspend fun setAgendaMode(mode: String) {
        context.preferencesStore.edit { it[AGENDA_MODE] = mode }
    }

    suspend fun setShakeToSwitchLook(enabled: Boolean) {
        context.preferencesStore.edit { it[SHAKE_TO_SWITCH_LOOK] = enabled }
    }

    /** The account that signed in last; kept on sign-out, to tell when another account signs in. */
    suspend fun lastUserId(): String? = context.preferencesStore.data.first()[LAST_USER_ID]

    suspend fun setLastUserId(id: String) {
        context.preferencesStore.edit { it[LAST_USER_ID] = id }
    }

    /** Creation time of the newest announcement a notification was posted for. */
    suspend fun lastNotified(): Long = context.preferencesStore.data.first()[LAST_NOTIFIED] ?: System.currentTimeMillis()

    suspend fun setLastNotified(value: Long) {
        context.preferencesStore.edit { it[LAST_NOTIFIED] = value }
    }

    /**
     * Announcements posted before this time count as read the first time the app sees them, so a
     * new sign-in doesn't start with a feed full of old news. Null until the first refresh sets it.
     */
    suspend fun newsBaseline(): Long? = context.preferencesStore.data.first()[NEWS_BASELINE]

    suspend fun setNewsBaseline(value: Long) {
        context.preferencesStore.edit { it[NEWS_BASELINE] = value }
    }

    /** The user's timetable feed (an iCalendar URL from KU Loket), once found. */
    suspend fun timetableFeed(): String? = context.preferencesStore.data.first()[TIMETABLE_FEED]

    suspend fun setTimetableFeed(url: String?) {
        context.preferencesStore.edit { if (url == null) it.remove(TIMETABLE_FEED) else it[TIMETABLE_FEED] = url }
    }

    /** The user's student number (`r0123456`) from KU Loket, once looked up. */
    suspend fun studentNumber(): String? = context.preferencesStore.data.first()[STUDENT_NUMBER]

    suspend fun setStudentNumber(value: String) {
        context.preferencesStore.edit { it[STUDENT_NUMBER] = value }
    }

    /** Forgets what belongs to the signed-in account; the app's own settings stay. */
    suspend fun clearAccountData() {
        context.preferencesStore.edit {
            it.remove(NEWS_BASELINE)
            it.remove(TIMETABLE_FEED)
            it.remove(STUDENT_NUMBER)
            // Another account's mailbox: its first check only notes the count again.
            it.remove(LAST_MAIL_UNREAD)
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SHOW_COMMUNITIES = booleanPreferencesKey("show_communities")
        val SHOW_PAST = booleanPreferencesKey("show_past_courses")
        val COPY_TO_DOWNLOADS = booleanPreferencesKey("copy_to_downloads")
        val NOTIFY_ANNOUNCEMENTS = booleanPreferencesKey("notify_announcements")
        val NOTIFICATION_PROMPT_DONE = booleanPreferencesKey("notification_prompt_done")
        /** The 0.3 on/off switch, read once to carry the choice over to [GLASS_STYLE]. */
        val GLASS_UI = booleanPreferencesKey("glass_ui")
        val GLASS_STYLE = stringPreferencesKey("glass_style")
        val SHAKE_TO_SWITCH_LOOK = booleanPreferencesKey("shake_to_switch_look")
        val NOTIFY_MAIL = booleanPreferencesKey("notify_mail")
        val TAB_BADGES = booleanPreferencesKey("tab_badges")
        val LAST_MAIL_UNREAD = intPreferencesKey("last_mail_unread")
        val LAST_NOTICE = intPreferencesKey("last_notice")
        val EXTRA_SERIES = stringSetPreferencesKey("extra_series")
        const val GLASS_ON = "LIQUID"
        const val GLASS_OFF = "OFF"
        val LAST_NOTIFIED = longPreferencesKey("last_notified")
        val LAST_USER_ID = stringPreferencesKey("last_user_id")
        val DOWNLOADS_FOLDER = stringPreferencesKey("downloads_folder")
        val NEWS_CHECK_MINUTES = intPreferencesKey("news_check_minutes")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val AMOLED = booleanPreferencesKey("amoled")
        val NEWS_BASELINE = longPreferencesKey("news_baseline")
        val TIMETABLE_FEED = stringPreferencesKey("timetable_feed")
        val STUDENT_NUMBER = stringPreferencesKey("student_number")
        val AGENDA_MODE = stringPreferencesKey("agenda_mode")
    }
}
