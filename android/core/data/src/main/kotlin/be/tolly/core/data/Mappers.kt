package be.tolly.core.data

import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.database.AnnouncementEntity
import be.tolly.core.database.AnnouncementWithCourse
import be.tolly.core.database.AttachmentEntity
import be.tolly.core.database.ContentEntity
import be.tolly.core.database.CourseEntity
import be.tolly.core.database.DownloadEntity
import be.tolly.core.model.Announcement
import be.tolly.core.model.Attachment
import be.tolly.core.model.ContentNode
import be.tolly.core.model.ContentType
import be.tolly.core.model.Course
import be.tolly.core.model.CourseKind
import be.tolly.core.model.CourseNaming
import be.tolly.core.model.Download
import be.tolly.core.model.DownloadStatus
import be.tolly.core.model.User
import be.tolly.core.network.UltraApi
import be.tolly.core.network.dto.AnnouncementDto
import be.tolly.core.network.dto.AttachmentDto
import be.tolly.core.network.dto.ContentDto
import be.tolly.core.network.dto.InternalAnnouncementDto
import be.tolly.core.network.dto.MembershipDto
import be.tolly.core.network.dto.UserDto
import java.time.Instant

internal fun parseInstant(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

internal fun UserDto.toModel(): User {
    val given = name?.preferred?.takeIf { it.isNotBlank() } ?: name?.given.orEmpty()
    val display = listOf(given, name?.family.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
    return User(
        id = id,
        userName = userName,
        displayName = display.ifBlank { userName },
        email = contact?.email,
        studentId = studentId?.takeIf { it.isNotBlank() },
    )
}

internal fun MembershipDto.toEntity(sortIndex: Int, unreadAnnouncements: Int): CourseEntity? {
    val c = course ?: return null
    val (name, code) = CourseNaming.splitName(c.name, c.courseId)
    val courseAvailable = !c.availability?.available.equals("No", ignoreCase = true)
    val membershipAvailable = !availability?.available.equals("No", ignoreCase = true)
    return CourseEntity(
        id = c.id,
        externalId = c.courseId,
        name = name,
        code = code,
        kind = if (c.organization) CourseKind.COMMUNITY else CourseKind.COURSE,
        available = courseAvailable && membershipAvailable,
        academicYear = CourseNaming.academicYear(c.courseId),
        webUrl = c.externalAccessUrl ?: UltraApi.courseWebUrl(c.id),
        lastAccessed = parseInstant(lastAccessed),
        unreadAnnouncements = unreadAnnouncements,
        sortIndex = sortIndex,
    )
}

internal fun CourseEntity.toModel() = Course(
    id = id,
    externalId = externalId,
    name = name,
    code = code,
    kind = kind,
    available = available,
    academicYear = academicYear,
    webUrl = webUrl,
    lastAccessed = lastAccessed,
    unreadAnnouncements = unreadAnnouncements,
)

internal fun contentTypeOf(handlerId: String?): ContentType {
    val id = handlerId.orEmpty()
    return when {
        id == "resource/x-bb-folder" -> ContentType.FOLDER
        id == "resource/x-bb-lesson" -> ContentType.LESSON
        id == "resource/x-bb-document" -> ContentType.DOCUMENT
        id == "resource/x-bb-file" -> ContentType.FILE
        id == "resource/x-bb-externallink" -> ContentType.LINK
        id.contains("lti", ignoreCase = true) -> ContentType.LTI
        id == "resource/x-bb-asmt-test-link" || id.contains("test", ignoreCase = true) -> ContentType.TEST
        id.contains("assignment", ignoreCase = true) -> ContentType.ASSIGNMENT
        id.contains("discussion", ignoreCase = true) || id.contains("forumlink", ignoreCase = true) -> ContentType.DISCUSSION
        else -> ContentType.OTHER
    }
}

/** [parentId] is the parent we asked for (null for the course root), since Ultra reports a hidden ROOT id. */
internal fun ContentDto.toEntity(courseId: String, parentId: String?): ContentEntity {
    val type = contentTypeOf(contentHandler?.id)
    val uiLink = links.firstOrNull { it.rel == "alternate" }?.href
    return ContentEntity(
        id = id,
        courseId = courseId,
        parentId = parentId,
        title = title.ifBlank { contentHandler?.file?.fileName.orEmpty() },
        description = description?.takeIf { it.isNotBlank() },
        type = type,
        isPage = contentHandler?.isBbPage == true,
        position = position,
        hasChildren = hasChildren ?: type.isContainer,
        body = body?.takeIf { it.isNotBlank() },
        url = contentHandler?.url,
        fileName = contentHandler?.file?.fileName,
        mimeType = contentHandler?.file?.mimeType,
        webUrl = uiLink?.let(UltraEndpoints::absolute) ?: UltraApi.courseWebUrl(courseId),
        modified = parseInstant(modified),
        available = !availability?.available.equals("No", ignoreCase = true),
    )
}

internal fun ContentEntity.toModel() = ContentNode(
    id = id,
    courseId = courseId,
    parentId = parentId,
    title = title,
    description = description,
    type = type,
    isPage = isPage,
    position = position,
    hasChildren = hasChildren,
    body = body,
    url = url,
    fileName = fileName,
    mimeType = mimeType,
    webUrl = webUrl,
    modified = modified,
    available = available,
)

internal fun AttachmentDto.toEntity(courseId: String, contentId: String) =
    AttachmentEntity(id = id, courseId = courseId, contentId = contentId, fileName = fileName, mimeType = mimeType)

internal fun AttachmentEntity.toModel() =
    Attachment(id = id, courseId = courseId, contentId = contentId, fileName = fileName, mimeType = mimeType)

internal fun InternalAnnouncementDto.toEntity(courseId: String, readLocal: Boolean) = AnnouncementEntity(
    id = id,
    courseId = courseId,
    title = title,
    bodyHtml = body?.displayText ?: body?.rawText.orEmpty(),
    created = parseInstant(startDateRestriction) ?: parseInstant(createdDate) ?: 0L,
    modified = parseInstant(modifiedDate),
    readRemote = readStatus?.isRead ?: false,
    readLocal = readLocal,
)

internal fun AnnouncementDto.toEntity(courseId: String, readLocal: Boolean) = AnnouncementEntity(
    id = id,
    courseId = courseId,
    title = title,
    bodyHtml = body.orEmpty(),
    created = parseInstant(created) ?: 0L,
    modified = parseInstant(modified),
    readRemote = false,
    readLocal = readLocal,
)

internal fun AnnouncementWithCourse.toModel() = with(announcement) {
    Announcement(
        id = id,
        courseId = courseId,
        courseName = courseName,
        title = title,
        bodyHtml = bodyHtml,
        created = created,
        modified = modified,
        isRead = !unreadLocal && (readRemote || readLocal),
    )
}

internal fun DownloadEntity.toModel() = Download(
    key = key,
    courseId = courseId,
    courseName = courseName,
    title = title,
    fileName = fileName,
    mimeType = mimeType,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    status = status,
    localPath = localPath,
    error = error,
    updatedAt = updatedAt,
    folderPath = folderPath,
)
