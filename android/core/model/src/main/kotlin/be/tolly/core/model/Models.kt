package be.tolly.core.model

/** Timestamps are epoch milliseconds (UTC) throughout the app. */
typealias EpochMillis = Long

data class User(
    val id: String,
    /** Toledo's user name, at KU Leuven a q- or u-number rather than the student number. */
    val userName: String,
    val displayName: String,
    val email: String?,
    /** Toledo's student id field, when it has one. */
    val studentId: String? = null,
)

enum class CourseKind { COURSE, COMMUNITY }

data class Course(
    /** Blackboard primary key, e.g. `_167831_1`. */
    val id: String,
    /** Blackboard course id, e.g. `ULTRA-B-KUL-G0T42a-2627`. */
    val externalId: String,
    /** Display name without the trailing `[code]`. */
    val name: String,
    /** Short course code such as `G0T42a`, when present. */
    val code: String?,
    val kind: CourseKind,
    val available: Boolean,
    /** Academic year suffix such as `2627`, or null for evergreen communities. */
    val academicYear: String?,
    val webUrl: String,
    val lastAccessed: EpochMillis?,
    val unreadAnnouncements: Int,
    /** Name the user gave the course in Tolly, if any. */
    val nickname: String? = null,
    /** Avatar icon: the user's pick, or one guessed from the name. */
    val icon: CourseIcon = CourseIcon.INITIALS,
    /** Avatar colour; null derives one from the course id. */
    val color: CourseColor? = null,
    /** Place in the user's own order, or null to sort by name. */
    val position: Int? = null,
    /** The colour the user marked the course with by swiping it, if any. */
    val highlight: Highlight? = null,
) {
    val displayName: String get() = nickname ?: name
}

/**
 * A colour the user marks a course, content item or announcement with by swiping its row right.
 * It lives in the app only, like the read state of announcements. (A green mark from swiping
 * left is gone; such stored marks are skipped.)
 */
enum class Highlight { ORANGE }

enum class ContentType {
    FOLDER,
    LESSON,
    DOCUMENT,
    FILE,
    LINK,
    LTI,
    ASSIGNMENT,
    TEST,
    DISCUSSION,
    OTHER,
    ;

    val isContainer: Boolean get() = this == FOLDER || this == LESSON
}

data class ContentNode(
    val id: String,
    val courseId: String,
    /** Null for items at the root of the course outline. */
    val parentId: String?,
    val title: String,
    val description: String?,
    val type: ContentType,
    /** Ultra "page" folders hold a single [ULTRA_DOCUMENT_BODY] document with the page body. */
    val isPage: Boolean,
    val position: Int,
    val hasChildren: Boolean,
    /** HTML body for documents (and some other items). */
    val body: String?,
    /** Target URL for external links. */
    val url: String?,
    val fileName: String?,
    val mimeType: String?,
    /** Link to this item in the Ultra web UI. */
    val webUrl: String,
    val modified: EpochMillis?,
    val available: Boolean,
) {
    val isPageBody: Boolean get() = type == ContentType.DOCUMENT && title == ULTRA_DOCUMENT_BODY

    companion object {
        const val ULTRA_DOCUMENT_BODY = "ultraDocumentBody"
    }
}

data class Attachment(
    val id: String,
    val courseId: String,
    val contentId: String,
    val fileName: String,
    val mimeType: String?,
)

/** A file referenced inside a document body (`data-bbfile`). */
data class EmbeddedFile(
    val displayName: String,
    val mimeType: String?,
    val url: String,
)

data class Announcement(
    val id: String,
    val courseId: String,
    val courseName: String?,
    val title: String,
    val bodyHtml: String,
    val created: EpochMillis,
    val modified: EpochMillis?,
    val isRead: Boolean,
    val courseIcon: CourseIcon = CourseIcon.INITIALS,
    val courseColor: CourseColor? = null,
    val highlight: Highlight? = null,
)

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED }

data class Download(
    /** Stable key for the file, see [DownloadRequest.key]. */
    val key: String,
    val courseId: String,
    val courseName: String?,
    val title: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val localPath: String?,
    val error: String?,
    val updatedAt: EpochMillis,
    /** Folders between the course and the file, joined with '/'. */
    val folderPath: String = "",
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

/** What to download. [sourceUrl] is an absolute Ultra URL (API download endpoint or bbcswebdav link). */
data class DownloadRequest(
    val courseId: String,
    val courseName: String?,
    val contentId: String?,
    val title: String,
    val fileName: String,
    val mimeType: String?,
    val sourceUrl: String,
    /** Folders between the course and the file, joined with '/', for the copy in public Downloads. */
    val folderPath: String = "",
) {
    val key: String get() = key(courseId, contentId, fileName)

    companion object {
        /** The key of the download of [fileName] from [contentId], so a file can be looked up before it's requested. */
        fun key(courseId: String, contentId: String?, fileName: String): String = "$courseId|${contentId.orEmpty()}|$fileName"
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val dynamicColor: Boolean = true,
    val showCommunities: Boolean = false,
    val showPastCourses: Boolean = false,
    /** Also copy finished downloads to [downloadsFolder], or else the public `Download/Tolly/<course>` folder. */
    val copyToDownloads: Boolean = false,
    /** A folder the user picked for those copies (a Storage Access Framework tree URI); null for `Download/Tolly`. */
    val downloadsFolder: String? = null,
    /** Post a notification for every new announcement. */
    val notifyAnnouncements: Boolean = false,
    /** Post a notification when new KU Leuven mail arrives (the portal's unread count goes up). */
    val notifyMail: Boolean = false,
    /** Unread counts on the News and Mail tabs. */
    val tabBadges: Boolean = true,
    /** How often the background check for new announcements (and mail) runs, one of [NEWS_CHECK_INTERVALS]. */
    val newsCheckMinutes: Int = DEFAULT_NEWS_CHECK_MINUTES,
    /** The user answered the "get notified" card in the feed (either way). */
    val notificationPromptDone: Boolean = false,
    /** The glass look: iOS styling with Liquid Glass bars, instead of Material 3 Expressive. */
    val glass: Boolean = false,
    /** Shaking the phone switches between the two looks. */
    val shakeToSwitchLook: Boolean = true,
    /** Other reeksen shown in the agenda besides the user's own, as timetable groups (`G0T43a_InfR1`). */
    val extraSeries: Set<String> = emptySet(),
    /** The quick setup shown on a new install has been gone through (or skipped). */
    val setupDone: Boolean = false,
    /** Pure black screens in dark mode, for OLED displays. */
    val amoled: Boolean = false,
    /** The user's student number (`r0123456`), from KU Loket; Toledo's user name is another id. */
    val studentNumber: String? = null,
)

/** The choices for [UserPreferences.newsCheckMinutes], in minutes; 15 is WorkManager's shortest period. */
val NEWS_CHECK_INTERVALS = listOf(15, 30, 60, 120, 180, 360)

const val DEFAULT_NEWS_CHECK_MINUTES = 60
