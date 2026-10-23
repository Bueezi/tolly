package be.tolly.feature.courses

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Something opened from a course into the third (extra) pane. */
sealed interface OpenItem : Parcelable {
    /** An Ultra document, or an Ultra page folder when [isPage] (its body lives in a child item). */
    @Parcelize
    data class Document(val contentId: String, val isPage: Boolean, val title: String) : OpenItem

    @Parcelize
    data class Announcement(val id: String) : OpenItem
}

/** Navigator key for the Courses destination: the course in the detail pane plus an optional item. */
@Parcelize
data class CoursePaneKey(val courseId: String, val item: OpenItem? = null) : Parcelable
