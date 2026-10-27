package be.tolly.feature.courses.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.auth.GalleryEntry
import be.tolly.core.data.DataError
import be.tolly.core.data.LiveLink
import be.tolly.core.data.LiveStream
import be.tolly.core.data.VideoRepository
import be.tolly.core.data.WatchProgressRepository
import be.tolly.core.data.toDataError
import be.tolly.core.model.WatchProgress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** One list of the Video tab: still being looked up, found, or not to be had. */
sealed interface VideoList<out T> {
    data object Loading : VideoList<Nothing>
    /** [searching]: more may come, from the course's content that is still being gone through. */
    data class Loaded<T>(val items: List<T>, val searching: Boolean = false) : VideoList<T>
    data class Failed(val error: DataError) : VideoList<Nothing>
}

/** What tapping a livestream led to: its stream to play, or that it isn't live now. */
sealed interface LiveResult {
    data class Play(val link: LiveLink, val stream: LiveStream) : LiveResult
    data class NotLive(val link: LiveLink) : LiveResult
    data object Failed : LiveResult
}

/** A course's Video tab: the livestreams its content links to, and its lesson recordings. */
@HiltViewModel(assistedFactory = CourseVideoViewModel.Factory::class)
class CourseVideoViewModel @AssistedInject constructor(
    @Assisted val courseId: String,
    private val videos: VideoRepository,
    private val watchProgress: WatchProgressRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(courseId: String): CourseVideoViewModel
    }

    private val _live = MutableStateFlow<VideoList<LiveLink>>(VideoList.Loading)
    val live: StateFlow<VideoList<LiveLink>> = _live.asStateFlow()

    private val _recordings = MutableStateFlow<VideoList<GalleryEntry>>(VideoList.Loading)
    val recordings: StateFlow<VideoList<GalleryEntry>> = _recordings.asStateFlow()

    /** How far each recording was watched, by entry id. */
    val progress: StateFlow<Map<String, WatchProgress>> =
        watchProgress.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val liveResults = Channel<LiveResult>(Channel.BUFFERED)
    val liveResult: Flow<LiveResult> = liveResults.receiveAsFlow()

    private var loaded = false

    /** Looks both lists up, the first time the tab is shown (going through the folders takes a while). */
    fun load() {
        if (loaded) return
        refresh()
    }

    fun refresh() {
        loaded = true
        // What was found last time shows at once, while it is looked up again.
        val earlier = videos.cachedContentVideos(courseId)
        val earlierGallery = videos.cachedGalleryRecordings(courseId)
        _live.value = earlier?.let { VideoList.Loaded(it.live, searching = true) } ?: VideoList.Loading
        _recordings.value = if (earlier != null || earlierGallery != null) {
            VideoList.Loaded(merge(earlierGallery, earlier?.recordings), searching = true)
        } else {
            VideoList.Loading
        }
        viewModelScope.launch {
            // The gallery and the course's content are looked up side by side; a recording can be in
            // either (or both, then once), and either one failing still shows what the other found.
            // Either can take long (the gallery opens in a hidden web page and, for a course without
            // one, only gives up after its timeout; a big course has many folders), so whichever
            // answers first shows its recordings, and the other's join them later.
            val content = async { videos.contentVideos(courseId) }
            val gallery = async { videos.galleryRecordings(courseId) }
            launch {
                val fromContent = content.await()
                _live.value = fromContent.fold({ VideoList.Loaded(it.live) }, { VideoList.Failed(it.toDataError()) })
                if (!gallery.isCompleted && fromContent.isSuccess) {
                    _recordings.value = VideoList.Loaded(merge(earlierGallery, fromContent.getOrNull()?.recordings), searching = true)
                }
            }
            launch {
                val fromGallery = gallery.await()
                if (!content.isCompleted && fromGallery.isSuccess) {
                    _recordings.value = VideoList.Loaded(merge(fromGallery.getOrNull(), earlier?.recordings), searching = true)
                }
            }
            val fromContent = content.await()
            val fromGallery = gallery.await()
            val found = merge(fromGallery.getOrNull(), fromContent.getOrNull()?.recordings)
            val failure = fromGallery.exceptionOrNull()?.takeIf { fromContent.isFailure }
            _recordings.value = if (found.isEmpty() && failure != null) VideoList.Failed(failure.toDataError()) else VideoList.Loaded(found)
        }
    }

    /** A long press: [entry] counts as unwatched again. */
    fun clearProgress(entry: GalleryEntry) = watchProgress.clear(entry.entryId)

    /** Undoes [clearProgress]. */
    fun restoreProgress(entry: GalleryEntry, progress: WatchProgress) =
        watchProgress.save(entry.entryId, progress.position, progress.duration)

    private fun merge(gallery: List<GalleryEntry>?, content: List<GalleryEntry>?): List<GalleryEntry> =
        (gallery.orEmpty() + content.orEmpty()).distinctBy { it.entryId }

    /** Finds where [link] streams now, for the player; a stream that isn't on says so. */
    fun openLive(link: LiveLink) {
        viewModelScope.launch {
            val result = videos.resolveLive(link.pin)
            liveResults.send(
                result.fold(
                    { LiveResult.Play(link, it) },
                    { if (it.toDataError() == DataError.NOT_AVAILABLE) LiveResult.NotLive(link) else LiveResult.Failed },
                ),
            )
        }
    }
}
