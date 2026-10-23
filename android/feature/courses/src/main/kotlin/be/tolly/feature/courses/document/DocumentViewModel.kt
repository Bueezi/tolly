package be.tolly.feature.courses.document

import android.webkit.WebResourceResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.ContentRepository
import be.tolly.core.data.DataError
import be.tolly.core.data.FileActions
import be.tolly.core.data.FileTarget
import be.tolly.core.data.HtmlResourceLoader
import be.tolly.core.model.ContentNode
import be.tolly.core.model.Download
import be.tolly.core.model.EmbeddedFile
import be.tolly.core.model.EmbeddedFiles
import be.tolly.core.ui.FileAction
import be.tolly.core.ui.FileOpenController
import be.tolly.core.ui.Refresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EmbeddedFileUi(val file: EmbeddedFile, val download: Download?)

/** An HTML file shown in place of a document that holds nothing else. */
sealed interface HtmlPreview {
    data object Loading : HtmlPreview
    data class Ready(val html: String) : HtmlPreview
    data object Failed : HtmlPreview
}

data class DocumentUiState(
    val node: ContentNode? = null,
    val bodyHtml: String? = null,
    val files: List<EmbeddedFileUi> = emptyList(),
    /** Id of the item holding the HTML (and thus the embedded files). */
    val ownerId: String? = null,
    /** The body has something to read besides its files; without, the files are the page. */
    val hasText: Boolean = true,
    /** For a page that only holds one HTML file: that file, shown in place. */
    val preview: HtmlPreview? = null,
    val loading: Boolean = true,
    val error: DataError? = null,
)

/**
 * An Ultra document. For page folders ([isPage]) the body comes from the folder's
 * `ultraDocumentBody` child; other children of the page are listed as extra items.
 */
@HiltViewModel(assistedFactory = DocumentViewModel.Factory::class)
class DocumentViewModel @AssistedInject constructor(
    @Assisted("courseId") val courseId: String,
    @Assisted("contentId") val contentId: String,
    @Assisted val isPage: Boolean,
    private val contentRepository: ContentRepository,
    private val resourceLoader: HtmlResourceLoader,
    private val fileActions: FileActions,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("courseId") courseId: String, @Assisted("contentId") contentId: String, isPage: Boolean): DocumentViewModel
    }

    val files = FileOpenController(viewModelScope, fileActions)

    private val refresher = Refresher(viewModelScope)
    private val preview = MutableStateFlow<HtmlPreview?>(null)
    private var previewing: String? = null

    /** The item whose HTML body we show: the document itself, or the page body child. */
    private val bodyNode: Flow<ContentNode?> =
        if (isPage) {
            contentRepository.observeChildren(courseId, contentId).map { children -> children.firstOrNull { it.isPageBody } }
        } else {
            contentRepository.observeNode(contentId)
        }

    val uiState: StateFlow<DocumentUiState> = combine(
        contentRepository.observeNode(contentId),
        bodyNode,
        fileActions.observeCourseDownloads(courseId),
        refresher.state,
        preview,
    ) { node, body, downloads, refresh, htmlPreview ->
        val html = body?.body
        val ownerId = body?.id ?: contentId
        val files = EmbeddedFiles.parse(html)
        val hasText = files.isEmpty() || EmbeddedFiles.hasOtherContent(html)
        val single = files.singleOrNull()?.takeIf { !hasText && EmbeddedFiles.isHtml(it) }
        if (single != null) startPreview(ownerId, single)
        DocumentUiState(
            node = node,
            bodyHtml = html,
            ownerId = ownerId,
            files = files.map { file ->
                EmbeddedFileUi(file, downloads[FileActions.keyFor(courseId, ownerId, file.displayName)])
            },
            hasText = hasText,
            preview = if (single != null) htmlPreview ?: HtmlPreview.Loading else null,
            loading = html == null && !refresh.loaded,
            error = refresh.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentUiState())

    /** Downloads the page's only file, an HTML page, once, and shows what's in it. */
    private fun startPreview(ownerId: String, file: EmbeddedFile) {
        val key = FileActions.keyFor(courseId, ownerId, file.displayName)
        if (previewing == key) return
        previewing = key
        viewModelScope.launch {
            val download = fileActions.fetch(FileTarget.Embedded(courseId, ownerId, file))
            val text = download?.localPath?.let { path ->
                withContext(Dispatchers.IO) {
                    runCatching { File(path).takeIf { it.length() <= MAX_PREVIEW_BYTES }?.readText() }.getOrNull()
                }
            }
            preview.value = text?.let { HtmlPreview.Ready(EmbeddedFiles.previewBody(it)) } ?: HtmlPreview.Failed
        }
    }

    /**
     * The pane opened (again: this view model lives as long as the tab). Always reloads, even with
     * the body cached, as embedded file links are signed and expire; a failed preview tries again.
     */
    fun onOpened() {
        if (preview.value == HtmlPreview.Failed) {
            previewing = null
            preview.value = null
        }
        refresh()
    }

    fun refresh() = refresher.refresh {
        if (isPage) contentRepository.refreshChildren(courseId, contentId) else contentRepository.refreshNode(courseId, contentId)
    }

    fun openFile(file: EmbeddedFile) = files.open(courseId, bodyOwnerId(), file)

    /** An action from a file's long-press menu. */
    fun act(file: EmbeddedFile, action: FileAction) = files.act(FileTarget.Embedded(courseId, bodyOwnerId(), file), action)

    fun onLink(url: String) = files.onLink(url, courseId, bodyOwnerId(), uiState.value.files.map { it.file })

    fun loadResource(url: String): WebResourceResponse? = resourceLoader.load(url)

    private fun bodyOwnerId(): String = uiState.value.ownerId ?: contentId

    private companion object {
        /** Bigger HTML files aren't a page someone wrote; they open in a browser instead. */
        const val MAX_PREVIEW_BYTES = 2L * 1024 * 1024
    }
}
