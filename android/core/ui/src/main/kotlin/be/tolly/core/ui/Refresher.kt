package be.tolly.core.ui

import be.tolly.core.data.DataError
import be.tolly.core.data.toDataError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a screen's refresh from Toledo stands. */
data class RefreshState(
    val refreshing: Boolean = false,
    /** Why the last refresh failed, cleared when the next one starts. */
    val error: DataError? = null,
    /** A refresh has finished once, so an empty cache really is empty. */
    val loaded: Boolean = false,
) {
    /** A list of [items] with this refresh's state; by default it loads until the first refresh ends. */
    fun <T> list(items: List<T>, loading: Boolean = items.isEmpty() && !loaded) =
        ListUiState(items, loading, refreshing, error)
}

/** A list read from Room while it refreshes from the network. */
data class ListUiState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: DataError? = null,
)

/**
 * Runs a ViewModel's network → Room refreshes one at a time and keeps their [RefreshState], so
 * each screen doesn't keep its own refreshing, error and loaded flags.
 */
class Refresher(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(RefreshState())
    val state: StateFlow<RefreshState> = _state.asStateFlow()

    /** Runs [load] unless a refresh is already running; a failure becomes the [RefreshState.error]. */
    fun refresh(load: suspend () -> Result<*>) {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        scope.launch {
            // A load that throws instead of returning a failure still ends the refresh.
            val failure = try {
                load().exceptionOrNull()
            } catch (e: CancellationException) {
                _state.update { it.copy(refreshing = false) }
                throw e
            } catch (e: Exception) {
                e
            }
            _state.update { it.copy(refreshing = false, loaded = true, error = failure?.toDataError() ?: it.error) }
        }
    }

    /** Shows the error of a load that runs beside the refresh, such as a learning module's items. */
    fun fail(error: Throwable) {
        _state.update { it.copy(error = error.toDataError()) }
    }
}
