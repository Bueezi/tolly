package be.tolly.feature.announcements

import android.webkit.WebResourceResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.AnnouncementRepository
import be.tolly.core.data.FileActions
import be.tolly.core.data.HtmlResourceLoader
import be.tolly.core.model.Announcement
import be.tolly.core.model.EmbeddedFiles
import be.tolly.core.ui.FileOpenController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = AnnouncementViewModel.Factory::class)
class AnnouncementViewModel @AssistedInject constructor(
    @Assisted private val announcementId: String,
    private val repository: AnnouncementRepository,
    private val resourceLoader: HtmlResourceLoader,
    fileActions: FileActions,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(announcementId: String): AnnouncementViewModel
    }

    val files = FileOpenController(viewModelScope, fileActions)

    /** `loading` until the database answered; then [announcement] is null only if it no longer exists. */
    data class State(val loading: Boolean = true, val announcement: Announcement? = null)

    val state: StateFlow<State> = repository.observe(announcementId)
        .map { State(loading = false, announcement = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** Opening marks the announcement read inside the app (Toledo itself is not told). */
    fun markRead() {
        viewModelScope.launch {
            repository.markReadLocally(repository.observe(announcementId).filterNotNull().first())
        }
    }

    /** Marks the announcement unread again (opening it marked it read), or read. */
    fun toggleRead() {
        val current = state.value.announcement ?: return
        viewModelScope.launch { repository.setRead(current, !current.isRead) }
    }

    fun onLink(url: String) {
        val current = state.value.announcement ?: return
        files.onLink(url, current.courseId, current.id, EmbeddedFiles.parse(current.bodyHtml))
    }

    fun loadResource(url: String): WebResourceResponse? = resourceLoader.load(url)
}
