package be.tolly.core.data

import be.tolly.core.database.ContentDao
import be.tolly.core.database.SyncStateDao
import be.tolly.core.database.SyncStateEntity
import be.tolly.core.model.Attachment
import be.tolly.core.model.ContentNode
import be.tolly.core.model.DownloadRequest
import be.tolly.core.model.EmbeddedFile
import be.tolly.core.network.UltraApi
import be.tolly.core.network.collectAllPages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: UltraApi,
    private val contentDao: ContentDao,
    private val syncStateDao: SyncStateDao,
) {
    /** Children of [parentId], or the course outline when [parentId] is null. */
    fun observeChildren(courseId: String, parentId: String?): Flow<List<ContentNode>> {
        val source = if (parentId == null) contentDao.observeRoot(courseId) else contentDao.observeChildren(courseId, parentId)
        return source.map { list -> list.map { it.toModel() } }
    }

    fun observeNode(id: String): Flow<ContentNode?> = contentDao.observe(id).map { it?.toModel() }

    /**
     * Titles of the folders from the course outline down to [contentId] (inclusive when
     * [includeSelf]), joined with '/'. Uses the cache only; unknown ancestors are skipped.
     */
    suspend fun folderPath(contentId: String?, includeSelf: Boolean): String {
        val titles = ArrayDeque<String>()
        var id = contentId
        var first = true
        var guard = 0
        while (id != null && guard++ < MAX_DEPTH) {
            val node = contentDao.get(id)?.toModel() ?: break
            if ((includeSelf || !first) && !node.isPageBody) titles.addFirst(node.title)
            first = false
            id = node.parentId
        }
        return titles.joinToString("/")
    }

    /** The download of content item [node]'s [attachment], under the item's own file name. */
    suspend fun downloadRequest(node: ContentNode, attachment: Attachment, courseName: String?): DownloadRequest = DownloadRequest(
        courseId = node.courseId,
        courseName = courseName,
        contentId = node.id,
        title = node.title,
        fileName = node.downloadFileName,
        mimeType = attachment.mimeType ?: node.mimeType,
        sourceUrl = UltraApi.attachmentDownloadUrl(node.courseId, node.id, attachment.id),
        folderPath = folderPath(node.id, includeSelf = false),
    )

    /** The download of [file], embedded in the body of [ownerId] (a document or announcement). */
    suspend fun downloadRequest(courseId: String, ownerId: String?, file: EmbeddedFile, courseName: String?): DownloadRequest = DownloadRequest(
        courseId = courseId,
        courseName = courseName,
        contentId = ownerId,
        title = file.displayName,
        fileName = file.displayName,
        mimeType = file.mimeType,
        sourceUrl = file.url,
        folderPath = folderPath(ownerId, includeSelf = true),
    )

    fun observeAttachments(courseId: String, contentId: String): Flow<List<Attachment>> =
        contentDao.observeAttachments(courseId, contentId).map { list -> list.map { it.toModel() } }

    fun observeLastSynced(courseId: String, parentId: String?): Flow<Long?> =
        syncStateDao.observe(syncKey(courseId, parentId))

    suspend fun refreshChildren(courseId: String, parentId: String?): Result<Unit> = refreshCall {
        val first = if (parentId == null) api.rootContents(courseId) else api.children(courseId, parentId)
        val items = collectAllPages(first) { api.contentsPage(it) }
        contentDao.replaceChildren(courseId, parentId, items.map { it.toEntity(courseId, parentId) })
        syncStateDao.upsert(SyncStateEntity(syncKey(courseId, parentId), System.currentTimeMillis()))
    }

    /** Reloads a single item (e.g. to get a fresh document body with newly signed file links). */
    suspend fun refreshNode(courseId: String, contentId: String): Result<Unit> = refreshCall {
        val dto = api.content(courseId, contentId)
        val parentId = contentDao.get(contentId)?.parentId
        contentDao.upsert(dto.toEntity(courseId, parentId))
    }

    suspend fun refreshAttachments(courseId: String, contentId: String): Result<List<Attachment>> = dataCall(inDemo = { emptyList() }) {
        val entities = api.attachments(courseId, contentId).results.map { it.toEntity(courseId, contentId) }
        contentDao.replaceAttachments(courseId, contentId, entities)
        entities.map { it.toModel() }
    }

    /** Cached attachments, fetching them when nothing is cached yet. */
    suspend fun attachments(courseId: String, contentId: String): Result<List<Attachment>> {
        val cached = contentDao.getAttachments(courseId, contentId)
        return if (cached.isNotEmpty()) Result.success(cached.map { it.toModel() }) else refreshAttachments(courseId, contentId)
    }

    private companion object {
        const val MAX_DEPTH = 32
    }

    private fun syncKey(courseId: String, parentId: String?) = "content:$courseId:${parentId ?: "root"}"
}
