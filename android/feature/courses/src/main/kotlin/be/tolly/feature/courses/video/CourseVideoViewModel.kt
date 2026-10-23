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
import be.tolly.core.data.toDataError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** One list of the Video tab: still being looked up, found, or not to be had. */
sealed interface VideoList<out T> {
    data object Loading : VideoList<Nothing>
    data class Loaded<T>(val items: List<T>) : VideoList<T>
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
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(courseId: String): CourseVideoViewModel
    }

    private val _live = MutableStateFlow<VideoList<LiveLink>>(VideoList.Loading)
    val live: StateFlow<VideoList<LiveLink>> = _live.asStateFlow()

    private val _recordings = MutableStateFlow<VideoList<GalleryEntry>>(VideoList.Loading)
    val recordings: StateFlow<VideoList<GalleryEntry>> = _recordings.asStateFlow()

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
        _live.value = VideoList.Loading
        _recordings.value = VideoList.Loading
        viewModelScope.launch {
            // The gallery and the course's content are looked up side by side; a recording can be in
            // either (or both, then once), and either one failing still shows what the other found.
            val gallery = async { videos.galleryRecordings(courseId) }
            val content = videos.contentVideos(courseId)
            _live.value = content.fold({ VideoList.Loaded(it.live) }, { VideoList.Failed(it.toDataError()) })
            val fromGallery = gallery.await()
            val found = (fromGallery.getOrNull().orEmpty() + content.getOrNull()?.recordings.orEmpty()).distinctBy { it.entryId }
            val failure = fromGallery.exceptionOrNull()?.takeIf { content.isFailure }
            _recordings.value = if (found.isEmpty() && failure != null) VideoList.Failed(failure.toDataError()) else VideoList.Loaded(found)
        }
    }

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
