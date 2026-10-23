package be.tolly.feature.downloads.offline

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.data.CourseRepository
import be.tolly.core.data.DataError
import be.tolly.core.data.OfflineFile
import be.tolly.core.data.OfflineRepository
import be.tolly.core.data.OfflineScanProgress
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.toDataError
import be.tolly.core.data.isActive
import be.tolly.core.model.CourseKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OfflinePhase {
    data class Scanning(val progress: OfflineScanProgress?) : OfflinePhase
    data class Ready(val files: List<OfflineFile>) : OfflinePhase
    data class Failed(val error: DataError) : OfflinePhase

    /** The picked files are queued; the screen can close. */
    data class Queued(val count: Int) : OfflinePhase
}

data class OfflineUiState(
    val phase: OfflinePhase = OfflinePhase.Scanning(null),
    /** Keys of the files to download. */
    val selected: Set<String> = emptySet(),
    val hideDownloaded: Boolean = false,
    val copyToDownloads: Boolean = false,
    val freeBytes: Long = 0,
    /** Name of the single course being scanned, or null for several. */
    val courseName: String? = null,
    val courseCount: Int = 0,
) {
    val files: List<OfflineFile> get() = (phase as? OfflinePhase.Ready)?.files.orEmpty()
    val selectedFiles: List<OfflineFile> get() = files.filter { it.request.key in selected }
    val selectedBytes: Long get() = selectedFiles.sumOf { it.size ?: 0L }
}

/**
 * Scans the courses passed in [EXTRA_COURSE_IDS] (or all of this year's courses) and lets the user
 * pick which files to download, biggest first.
 */
@HiltViewModel
class OfflineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val offline: OfflineRepository,
    private val courses: CourseRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val requestedIds: List<String> = savedStateHandle.get<Array<String>>(EXTRA_COURSE_IDS)?.toList().orEmpty()
    private val state = MutableStateFlow(OfflineUiState())

    val uiState: StateFlow<OfflineUiState> =
        combine(state, preferences.preferences.map { it.copyToDownloads }) { current, copy -> current.copy(copyToDownloads = copy) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineUiState())

    init {
        scan()
    }

    fun scan() {
        viewModelScope.launch {
            val all = courses.observeCourses().first()
            val ids = requestedIds.ifEmpty { all.filter { it.kind == CourseKind.COURSE && it.isActive() && it.available }.map { it.id } }
            state.update {
                it.copy(
                    phase = OfflinePhase.Scanning(null),
                    courseName = if (ids.size == 1) all.firstOrNull { c -> c.id == ids.first() }?.displayName else null,
                    courseCount = ids.size,
                    freeBytes = freeBytes(),
                )
            }
            offline.scan(ids) { progress -> state.update { it.copy(phase = OfflinePhase.Scanning(progress)) } }
                .onSuccess { files ->
                    state.update { current ->
                        current.copy(
                            phase = OfflinePhase.Ready(files),
                            selected = files.filterNot { it.alreadyDownloaded }.map { it.request.key }.toSet(),
                        )
                    }
                }
                .onFailure { error -> state.update { it.copy(phase = OfflinePhase.Failed(error.toDataError())) } }
        }
    }

    fun toggle(file: OfflineFile) {
        if (file.alreadyDownloaded) return
        val key = file.request.key
        state.update { it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key) }
    }

    fun selectAll() = state.update { s -> s.copy(selected = s.files.filterNot { it.alreadyDownloaded }.map { it.request.key }.toSet()) }

    fun selectNone() = state.update { it.copy(selected = emptySet()) }

    fun setHideDownloaded(hide: Boolean) = state.update { it.copy(hideDownloaded = hide) }

    fun setCopyToDownloads(enabled: Boolean) {
        viewModelScope.launch { preferences.setCopyToDownloads(enabled) }
    }

    fun download() {
        val picked = state.value.selectedFiles
        viewModelScope.launch {
            offline.download(picked)
            state.update { it.copy(phase = OfflinePhase.Queued(picked.size)) }
        }
    }

    private fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)

    companion object {
        /** String array of course ids; empty or missing means all of this year's courses. */
        const val EXTRA_COURSE_IDS = "courseIds"
    }
}
