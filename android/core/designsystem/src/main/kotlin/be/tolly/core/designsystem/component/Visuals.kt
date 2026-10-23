package be.tolly.core.designsystem.component

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.ContentType
import be.tolly.core.model.CourseColor
import be.tolly.core.model.CourseIcon
import be.tolly.core.model.EpochMillis

fun contentTypeIcon(type: ContentType, mimeType: String? = null, fileName: String? = null): ImageVector = when (type) {
    ContentType.FOLDER -> Icons.Outlined.Folder
    ContentType.LESSON -> Icons.Outlined.MenuBook
    ContentType.DOCUMENT -> Icons.AutoMirrored.Outlined.Article
    ContentType.FILE -> fileIcon(mimeType, fileName)
    ContentType.LINK -> Icons.Outlined.Link
    ContentType.LTI -> Icons.Outlined.Extension
    ContentType.ASSIGNMENT -> Icons.Outlined.Assignment
    ContentType.TEST -> Icons.Outlined.Quiz
    ContentType.DISCUSSION -> Icons.Outlined.Forum
    ContentType.OTHER -> Icons.Outlined.Description
}

fun fileIcon(mimeType: String?, fileName: String?): ImageVector {
    val mime = mimeType.orEmpty().lowercase()
    val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when {
        mime == "application/pdf" || ext == "pdf" -> Icons.Outlined.PictureAsPdf
        mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "gif", "svg", "webp") -> Icons.Outlined.Image
        mime.startsWith("video/") || ext in setOf("mp4", "mkv", "mov", "webm") -> Icons.Outlined.VideoFile
        mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "wav", "ogg") -> Icons.Outlined.AudioFile
        ext in setOf("ppt", "pptx", "odp", "key") || mime.contains("presentation") -> Icons.Outlined.Slideshow
        ext in setOf("xls", "xlsx", "ods", "csv") || mime.contains("spreadsheet") -> Icons.Outlined.TableChart
        ext in setOf("zip", "rar", "7z", "tar", "gz") || mime.contains("zip") -> Icons.Outlined.FolderZip
        ext in setOf("py", "java", "kt", "c", "cpp", "h", "js", "ts", "lean", "sql", "hs", "pl", "txt", "md", "json", "xml") ->
            Icons.Outlined.Code
        ext in setOf("doc", "docx", "odt", "rtf") || mime.contains("word") -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

/** Theme-aware container/content colours for a course: its [color], or a stable hue derived from [seed]. */
@Composable
fun courseColors(seed: String, color: CourseColor? = null): Pair<Color, Color> {
    // Light text means a dark theme (the surface itself may be see-through in the glass look).
    val dark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    return remember(seed, color, dark) {
        val hue = color?.hue ?: ((seed.hashCode().toLong() and 0xFFFFFFFFL) % 360).toFloat()
        val saturation = color?.saturation ?: 1f
        if (dark) {
            Color.hsl(hue, 0.35f * saturation, 0.30f) to Color.hsl(hue, 0.70f * saturation, 0.88f)
        } else {
            Color.hsl(hue, 0.55f * saturation, 0.86f) to Color.hsl(hue, 0.60f * saturation, 0.22f)
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** The vector for a subject icon, or null for [CourseIcon.INITIALS]. */
fun courseIconVector(icon: CourseIcon): ImageVector? = when (icon) {
    CourseIcon.INITIALS -> null
    CourseIcon.MATH -> Icons.Outlined.Functions
    CourseIcon.STATISTICS -> Icons.Outlined.QueryStats
    CourseIcon.CODE -> Icons.Outlined.Code
    CourseIcon.TOOLS -> Icons.Outlined.Terminal
    CourseIcon.DATABASE -> Icons.Outlined.Storage
    CourseIcon.LOGIC -> Icons.Outlined.AccountTree
    CourseIcon.AI -> Icons.Outlined.SmartToy
    CourseIcon.GRAPHICS -> Icons.Outlined.ViewInAr
    CourseIcon.HCI -> Icons.Outlined.TouchApp
    CourseIcon.WEB -> Icons.Outlined.Web
    CourseIcon.NETWORK -> Icons.Outlined.Hub
    CourseIcon.CLOUD -> Icons.Outlined.Cloud
    CourseIcon.SECURITY -> Icons.Outlined.Security
    CourseIcon.ELECTRONICS -> Icons.Outlined.Memory
    CourseIcon.ROBOTICS -> Icons.Outlined.PrecisionManufacturing
    CourseIcon.ENERGY -> Icons.Outlined.Bolt
    CourseIcon.PHYSICS -> Icons.Outlined.RocketLaunch
    CourseIcon.ASTRONOMY -> Icons.Outlined.Nightlight
    CourseIcon.CHEMISTRY -> Icons.Outlined.Science
    CourseIcon.BIOLOGY -> Icons.Outlined.Biotech
    CourseIcon.ENVIRONMENT -> Icons.Outlined.Eco
    CourseIcon.AGRICULTURE -> Icons.Outlined.Agriculture
    CourseIcon.MEDICINE -> Icons.Outlined.MedicalServices
    CourseIcon.PHARMACY -> Icons.Outlined.Medication
    CourseIcon.ECONOMICS -> Icons.Outlined.Savings
    CourseIcon.LAW -> Icons.Outlined.Gavel
    CourseIcon.POLITICS -> Icons.Outlined.AccountBalance
    CourseIcon.SOCIOLOGY -> Icons.Outlined.Diversity3
    CourseIcon.EDUCATION -> Icons.Outlined.School
    CourseIcon.LANGUAGE -> Icons.Outlined.Translate
    CourseIcon.MEDIA -> Icons.Outlined.Campaign
    CourseIcon.LITERATURE -> Icons.AutoMirrored.Outlined.MenuBook
    CourseIcon.HISTORY -> Icons.Outlined.HistoryEdu
    CourseIcon.PHILOSOPHY -> Icons.Outlined.Lightbulb
    CourseIcon.THEOLOGY -> Icons.Outlined.Church
    CourseIcon.PSYCHOLOGY -> Icons.Outlined.Psychology
    CourseIcon.ENGINEERING -> Icons.Outlined.Engineering
    CourseIcon.ARCHITECTURE -> Icons.Outlined.Architecture
    CourseIcon.GEOGRAPHY -> Icons.Outlined.Public
    CourseIcon.ART -> Icons.Outlined.Palette
    CourseIcon.PHOTOGRAPHY -> Icons.Outlined.PhotoCamera
    CourseIcon.MUSIC -> Icons.Outlined.MusicNote
    CourseIcon.SPORTS -> Icons.Outlined.SportsSoccer
    CourseIcon.LIBRARY -> Icons.Outlined.LocalLibrary
    CourseIcon.PROJECT -> Icons.Outlined.Construction
    CourseIcon.THESIS -> Icons.Outlined.EditNote
    CourseIcon.INTERNSHIP -> Icons.Outlined.Work
    CourseIcon.SEMINAR -> Icons.Outlined.RecordVoiceOver
    CourseIcon.LAB -> Icons.Outlined.Handyman
    CourseIcon.CALCULATOR -> Icons.Outlined.Calculate
    CourseIcon.COMMUNITY -> Icons.Outlined.Groups
    CourseIcon.BOOK -> Icons.Outlined.AutoStories
}

/** Rounded square with the course's subject icon (or initials), in the course's colour. */
@Composable
fun CourseAvatar(
    name: String,
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    icon: CourseIcon = CourseIcon.INITIALS,
    color: CourseColor? = null,
) {
    val (container, content) = courseColors(seed, color)
    val vector = courseIconVector(icon)
    // An app-icon squircle with the glass look.
    val shape = if (Tolly.glass) RoundedRectangle(size * 0.28f) else MaterialTheme.shapes.medium
    Surface(shape = shape, color = container, contentColor = content, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (vector != null) {
                Icon(vector, contentDescription = null, modifier = Modifier.size(size * 0.55f))
            } else {
                val initials = remember(name) { initialsOf(name) }
                Text(
                    initials,
                    style = if (size < 40.dp) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun relativeTime(epochMillis: EpochMillis): String {
    val now = System.currentTimeMillis()
    return remember(epochMillis, now / 60_000) {
        DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }
}

/** The time of day, like "16:00" (or "4:00 PM", following the system's setting). */
@Composable
fun timeOfDay(epochMillis: EpochMillis): String {
    val context = LocalContext.current
    return remember(epochMillis) { DateUtils.formatDateTime(context, epochMillis, DateUtils.FORMAT_SHOW_TIME) }
}

@Composable
fun dateTime(epochMillis: EpochMillis): String {
    val context = LocalContext.current
    return remember(epochMillis) {
        DateUtils.formatDateTime(
            context,
            epochMillis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
        )
    }
}

@Composable
fun fileSize(bytes: Long): String {
    val context = LocalContext.current
    return remember(bytes) { Formatter.formatShortFileSize(context, bytes) }
}

private val stopWords = setOf(
    "voor", "van", "en", "de", "het", "een", "in", "op", "met", "tot", "aan",
    "the", "of", "and", "for", "to", "a", "an", "on", "with",
)

internal fun initialsOf(name: String): String {
    val words = name.split(Regex("""[\s\-&@:/()\[\],.]+""")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    val significant = words.filter { it.lowercase() !in stopWords }.ifEmpty { words }
    // A one-word name gets its first two letters, so "Gegevensbanken" reads "GE" rather than a lone "G".
    val single = significant.singleOrNull()?.filter { it.isLetterOrDigit() }
    if (single != null && single.length >= 2) return single.take(2).uppercase()
    return significant.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}
