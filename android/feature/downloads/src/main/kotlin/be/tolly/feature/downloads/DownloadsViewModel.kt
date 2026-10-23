package be.tolly.feature.downloads

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.auth.ApplicationScope
import be.tolly.core.data.CourseRepository
import be.tolly.core.data.FileActions
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.download.DownloadRepository
import be.tolly.core.model.Course
import be.tolly.core.model.Download
import be.tolly.core.model.DownloadStatus
import be.tolly.core.ui.FileAction
import be.tolly.core.ui.FileEvent
import be.tolly.core.ui.FileOpenController
import be.tolly.core.ui.canSaveCopies
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Downloads of one course; [course] (with the user's name, icon and colour) is null if it's no longer cached. */
data class DownloadGroup(val courseId: String, val courseName: String?, val course: Course?, val items: List<Download>)

data class DownloadsUiState(
    val loading: Boolean = true,
    val groups: List<DownloadGroup> = emptyList(),
    val storageUsed: Long = 0,
    /** Copies can be saved straight into a folder (Android 10+ or a picked folder); else the user picks a file. */
    val canSaveToFolder: Boolean = canSaveCopies(null),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    courseRepository: CourseRepository,
    preferences: PreferencesRepository,
    fileActions: FileActions,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val files = FileOpenController(viewModelScope, fileActions)
    private val copies = Channel<FileEvent>(Channel.BUFFERED)
    private val deletedChannel = Channel<Download>(Channel.BUFFERED)

    /** Opening, sharing and copying files, for [HandleFileEvents][be.tolly.core.ui.HandleFileEvents]. */
    val events: Flow<FileEvent> = merge(files.events, copies.receiveAsFlow())

    /** Downloads the user just deleted, for an Undo snackbar. */
    val deleted: Flow<Download> = deletedChannel.receiveAsFlow()

    /** Deleted by the user but still undoable: hidden now, removed from disk when the job runs. */
    private val pendingDeletes = MutableStateFlow<Map<String, Job>>(emptyMap())

    /**
     * Space the downloads take, measured again only when a download finishes or goes: a running
     * download updates its row several times a second, and measuring walks every file.
     */
    private val storageUsed = repository.observeAll()
        .map { all -> all.filter { it.status == DownloadStatus.DONE }.map { it.key }.toSet() }
        .distinctUntilChanged()
        .map { repository.storageUsed() }

    val uiState: StateFlow<DownloadsUiState> = combine(
        repository.observeAll(),
        pendingDeletes,
        courseRepository.observeCourses(),
        preferences.preferences,
        storageUsed,
    ) { all, pending, courses, prefs, used -> DownloadsInput(all.filterNot { it.key in pending }, courses.associateBy { it.id }, prefs.downloadsFolder, used) }
        .map { (downloads, courses, folder, used) ->
            DownloadsUiState(
                loading = false,
                groups = downloads
                    .groupBy { it.courseId }
                    .map { (courseId, items) ->
                        val course = courses[courseId]
                        val name = course?.displayName ?: items.firstNotNullOfOrNull { it.courseName }
                        DownloadGroup(courseId, name, course, items.sortedBy { it.title.lowercase() })
                    }
                    .sortedBy { it.courseName?.lowercase() },
                storageUsed = used,
                canSaveToFolder = canSaveCopies(folder),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun open(download: Download) = files.act(download, FileAction.OPEN)

    fun share(download: Download) = files.act(download, FileAction.SHARE)

    fun retry(download: Download) {
        viewModelScope.launch { repository.retry(download.key) }
    }

    fun delete(download: Download) {
        // The application scope keeps the deletion going if the user leaves the screen meanwhile.
        val job = appScope.launch {
            delay(UNDO_WINDOW_MS)
            repository.delete(download.key)
            pendingDeletes.update { it - download.key }
        }
        pendingDeletes.update { it + (download.key to job) }
        deletedChannel.trySend(download)
    }

    fun undoDelete(download: Download) {
        pendingDeletes.value[download.key]?.cancel()
        pendingDeletes.update { it - download.key }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }

    /** Copies into the folder for copies: the one the user picked, or Downloads/Tolly (Android 10+). */
    fun exportToDownloads(download: Download) = files.act(download, FileAction.SAVE_COPY)

    /** Android 8–9: copy to a document the user picked with the system file picker. */
    fun exportTo(download: Download, target: Uri) {
        viewModelScope.launch {
            val result = runCatching { repository.copyTo(download, target) }
            copies.send(if (result.isSuccess) FileEvent.Saved(null) else FileEvent.SaveFailed)
        }
    }

    private companion object {
        /** A little longer than a short snackbar, so Undo works for as long as it's shown. */
        const val UNDO_WINDOW_MS = 5_000L
    }
}

private data class DownloadsInput(
    val downloads: List<Download>,
    val courses: Map<String, Course>,
    val folder: String?,
    val storageUsed: Long,
)
