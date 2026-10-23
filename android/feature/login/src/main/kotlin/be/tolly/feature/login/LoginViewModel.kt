package be.tolly.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.AuthRepository
import be.tolly.core.data.DataError
import be.tolly.core.data.toDataError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginState {
    /** The KU Leuven login page is shown in the WebView. */
    data object Browsing : LoginState

    /** Back on Ultra; checking the captured session against the API. */
    data object Verifying : LoginState
    data object Done : LoginState
    data class Failed(val error: DataError?) : LoginState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Browsing)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /** Called on the main thread when the WebView navigated back to Ultra. */
    fun onUltraReached() {
        if (_state.value != LoginState.Browsing) return
        if (!authRepository.captureWebLogin()) {
            _state.value = LoginState.Failed(null)
            return
        }
        _state.value = LoginState.Verifying
        viewModelScope.launch {
            authRepository.verifyCapturedSession()
                .onSuccess { _state.value = LoginState.Done }
                .onFailure { _state.value = LoginState.Failed(it.toDataError()) }
        }
    }

    fun retry() {
        _state.value = LoginState.Browsing
    }
}
