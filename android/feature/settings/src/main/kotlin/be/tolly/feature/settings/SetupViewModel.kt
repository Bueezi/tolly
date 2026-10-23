package be.tolly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.model.ThemeMode
import be.tolly.core.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(val preferences: UserPreferences = UserPreferences())

/** The quick setup on a new install: the same settings as in Settings, a few at a time. */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<SetupUiState> = preferences.preferences
        .map { SetupUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    fun setGlass(enabled: Boolean) = launch { preferences.setGlass(enabled) }
    fun setThemeMode(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { preferences.setDynamicColor(enabled) }
    fun setShakeToSwitchLook(enabled: Boolean) = launch { preferences.setShakeToSwitchLook(enabled) }
    /** The user set the mail switch themselves, so announcements no longer change it. */
    private var mailChosen = false

    // Someone who wants to hear about announcements wants to hear about mail too; its own switch is right below.
    fun setNotifyAnnouncements(enabled: Boolean) = launch {
        preferences.setNotifyAnnouncements(enabled)
        if (enabled && !mailChosen) preferences.setNotifyMail(true)
    }
    fun setNotifyMail(enabled: Boolean) = launch {
        mailChosen = true
        preferences.setNotifyMail(enabled)
    }
    fun setNewsCheckMinutes(minutes: Int) = launch { preferences.setNewsCheckMinutes(minutes) }


    fun finish() = launch { preferences.setSetupDone() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
