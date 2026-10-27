package be.tolly.feature.settings

import be.tolly.core.ui.MailDraft
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.auth.AuthState
import be.tolly.core.data.AuthRepository
import be.tolly.core.data.LogExporter
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.download.DownloadRepository
import be.tolly.core.data.notifications.MailNotifier
import be.tolly.core.model.FileViewer
import be.tolly.core.model.ThemeMode
import be.tolly.core.model.User
import be.tolly.core.model.UserPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val preferences: UserPreferences = UserPreferences(),
    val downloadsSize: Long = 0,
    /** Where copies of downloads go, for showing. */
    val downloadsFolder: String = "",
    val signingOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val downloadRepository: DownloadRepository,
    private val logExporter: LogExporter,
    private val mailNotifier: MailNotifier,
) : ViewModel() {
    private val launches = Channel<Intent>(Channel.BUFFERED)

    /** Intents to start, e.g. the share sheet for exported logs. */
    val intents: Flow<Intent> = launches.receiveAsFlow()

    private val downloadsSize = MutableStateFlow(0L)
    private val signingOut = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.authState,
        preferencesRepository.preferences,
        downloadsSize.onStart { updateSize() },
        signingOut,
    ) { auth, prefs, size, isSigningOut ->
        SettingsUiState(
            user = (auth as? AuthState.SignedIn)?.user,
            preferences = prefs,
            downloadsSize = size,
            downloadsFolder = downloadRepository.folderLabel(prefs.downloadsFolder),
            signingOut = isSigningOut,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) = launch { preferencesRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { preferencesRepository.setDynamicColor(enabled) }
    fun setShowCommunities(show: Boolean) = launch { preferencesRepository.setShowCommunities(show) }

    fun setFileViewer(viewer: FileViewer) = launch { preferencesRepository.setFileViewer(viewer) }
    fun setShowPastCourses(show: Boolean) = launch { preferencesRepository.setShowPastCourses(show) }

    fun setCopyToDownloads(enabled: Boolean) = launch {
        preferencesRepository.setCopyToDownloads(enabled)
        // Files downloaded before the switch was turned on get their copy too.
        if (enabled) downloadRepository.exportAllToDownloads()
    }

    fun setNotifyAnnouncements(enabled: Boolean) = launch { preferencesRepository.setNotifyAnnouncements(enabled) }

    fun setNotifyMail(enabled: Boolean) = launch {
        preferencesRepository.setNotifyMail(enabled)
        if (!enabled) mailNotifier.clear()
    }

    fun setTabBadges(enabled: Boolean) = launch { preferencesRepository.setTabBadges(enabled) }

    fun setNewsCheckMinutes(minutes: Int) = launch { preferencesRepository.setNewsCheckMinutes(minutes) }

    /** A folder picked for copies (null: Downloads/Tolly); what was downloaded already is copied there too. */
    fun setDownloadsFolder(treeUri: Uri?) = launch {
        downloadRepository.setFolder(treeUri)
        if (preferencesRepository.preferences.first().copyToDownloads) downloadRepository.exportAllToDownloads()
    }

    /** Turns copies on into a newly picked folder (where Downloads/Tolly isn't available). */
    fun copyToFolder(treeUri: Uri) = launch {
        downloadRepository.setFolder(treeUri)
        preferencesRepository.setCopyToDownloads(true)
        downloadRepository.exportAllToDownloads()
    }

    fun setGlass(enabled: Boolean) = launch { preferencesRepository.setGlass(enabled) }

    fun setShakeToSwitchLook(enabled: Boolean) = launch { preferencesRepository.setShakeToSwitchLook(enabled) }

    fun setAmoled(enabled: Boolean) = launch { preferencesRepository.setAmoled(enabled) }

    fun setStudentNumber(value: String) = launch { preferencesRepository.setStudentNumber(value) }

    fun exportLogs() = launch { launches.send(logExporter.export()) }

    /** Opens the user's mail app with their feedback to the developer, with the log if they agreed. */
    fun sendFeedback(subject: String, message: String, attachLogs: Boolean) = launch {
        launches.send(logExporter.feedbackEmail(FEEDBACK_EMAIL, subject, message, attachLogs))
    }

    /** A feedback mail for Outlook on the web, handed to [onReady] (see [LogExporter.feedbackMail]). */
    fun feedbackMail(subject: String, message: String, attachLogs: Boolean, onReady: (MailDraft) -> Unit) = launch {
        val mail = logExporter.feedbackMail(message, attachLogs)
        onReady(MailDraft(FEEDBACK_EMAIL, subject, mail.text, mail.logName, mail.log))
    }

    /** Opens a new GitHub issue in the browser, prefilled; it is public, so no log goes with it. */
    fun openIssue(title: String, message: String, versionName: String) = launch {
        launches.send(Intent(Intent.ACTION_VIEW, githubIssueUri(title, message, versionName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun clearCache() = launch { authRepository.clearCache() }

    fun deleteDownloads() = launch {
        downloadRepository.deleteAll()
        updateSize()
    }

    fun signOut() = launch {
        signingOut.value = true
        try {
            authRepository.signOut()
        } finally {
            signingOut.value = false
        }
    }

    private fun updateSize() {
        viewModelScope.launch { downloadsSize.value = downloadRepository.storageUsed() }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
