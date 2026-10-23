package be.tolly.feature.courses.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.ContentRepository
import be.tolly.core.data.FileActions
import be.tolly.core.data.HighlightRepository
import be.tolly.core.data.HighlightTarget
import be.tolly.core.model.ContentNode
import be.tolly.core.model.ContentType
import be.tolly.core.model.Download
import be.tolly.core.model.Highlight
import be.tolly.core.ui.FileOpenController
import be.tolly.core.ui.ListUiState
import be.tolly.core.ui.Refresher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContentItemUi(
    val node: ContentNode,
    val download: Download?,
    /** The learning module this item is listed under, when that module is expanded inline. */
    val module: ContentNode? = null,
    /** For a learning module: its items are shown right below it. */
    val expanded: Boolean = false,
    /** For an expanded learning module: its items are still being fetched. */
    val loading: Boolean = false,
    /** The colour the user marked the item with by swiping it. */
    val highlight: Highlight? = null,
)

/** A row of a folder: an item, or the note under an expanded learning module that has no items. */
sealed interface FolderRow {
    val key: String

    data class Item(val item: ContentItemUi) : FolderRow {
        override val key: String get() = item.node.id
    }

    data class EmptyModule(val moduleId: String) : FolderRow {
        override val key: String get() = "empty:$moduleId"
    }
}

/** Learning modules expand in place (like in Ultra) instead of opening as a folder. */
val ContentItemUi.expandsInline: Boolean get() = node.type == ContentType.LESSON && !node.isPage && module == null

/**
 * One folder level of a course outline (`folderId == null` is the root). Learning modules in it
 * can be expanded, which lists their items below them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = FolderViewModel.Factory::class)
class FolderViewModel @AssistedInject constructor(
    @Assisted("courseId") private val courseId: String,
    @Assisted("folderId") private val folderId: String?,
    private val contentRepository: ContentRepository,
    private val highlights: HighlightRepository,
    fileActions: FileActions,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("courseId") courseId: String, @Assisted("folderId") folderId: String?): FolderViewModel
    }

    val files = FileOpenController(viewModelScope, fileActions)

    private val refresher = Refresher(viewModelScope)
    private val expanded = MutableStateFlow<Set<String>>(emptySet())
    private val loadingModules = MutableStateFlow<Set<String>>(emptySet())

    /** Cached items of every expanded module; a module is missing until its first emission. */
    private val moduleChildren: Flow<Map<String, List<ContentNode>>> = expanded.flatMapLatest { ids ->
        if (ids.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(ids.map { id -> contentRepository.observeChildren(courseId, id).map { id to it } }) { it.toMap() }
        }
    }

    val uiState: StateFlow<ListUiState<FolderRow>> = combine(
        contentRepository.observeChildren(courseId, folderId),
        fileActions.observeCourseDownloads(courseId),
        refresher.state,
        combine(expanded, moduleChildren, loadingModules, ::Triple),
        highlights.highlights,
    ) { nodes, downloads, refresh, (open, children, fetching), marks ->
        fun ui(node: ContentNode) = ContentItemUi(
            node = node,
            download = if (node.type == ContentType.FILE) downloads[FileActions.keyFor(node)] else null,
            highlight = marks[HighlightTarget.CONTENT, node.id],
        )
        val rows = buildList {
            for (node in nodes) {
                val item = ui(node)
                if (!item.expandsInline || node.id !in open) {
                    add(FolderRow.Item(item))
                    continue
                }
                val kids = children[node.id]
                val stillLoading = kids.isNullOrEmpty() && (kids == null || node.id in fetching)
                add(FolderRow.Item(item.copy(expanded = true, loading = stillLoading)))
                kids.orEmpty().forEach { add(FolderRow.Item(ui(it).copy(module = node))) }
                if (kids != null && kids.isEmpty() && !stillLoading) add(FolderRow.EmptyModule(node.id))
            }
        }
        refresh.list(rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    init {
        refresh()
    }

    fun refresh() {
        expanded.value.forEach(::refreshModule)
        refresher.refresh { contentRepository.refreshChildren(courseId, folderId) }
    }

    /** A swipe: marks [node] with [highlight], or takes that mark off again. */
    fun toggleHighlight(node: ContentNode, highlight: Highlight) {
        viewModelScope.launch { highlights.toggle(HighlightTarget.CONTENT, node.id, highlight) }
    }

    fun toggleModule(module: ContentNode) {
        if (module.id in expanded.value) {
            expanded.update { it - module.id }
        } else {
            expanded.update { it + module.id }
            refreshModule(module.id)
        }
    }

    private fun refreshModule(id: String) {
        if (id in loadingModules.value) return
        viewModelScope.launch {
            loadingModules.update { it + id }
            contentRepository.refreshChildren(courseId, id).onFailure(refresher::fail)
            loadingModules.update { it - id }
        }
    }
}
