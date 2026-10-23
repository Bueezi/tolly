package be.tolly.feature.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.groupShape
import be.tolly.core.designsystem.component.timeOfDay
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.ClassNow
import be.tolly.core.model.SessionKind

/** "Class now · until 16:00", "Exercise session in 10 minutes", "Next practical in 3 h 15 min". */
@Composable
internal fun classNowTime(classNow: ClassNow): String {
    val (now, nextIn, nextAt) = when (classNow.kind) {
        SessionKind.CLASS -> Triple(R.string.courses_class_now, R.plurals.courses_class_next_in, R.string.courses_class_next_in_long)
        SessionKind.EXERCISES -> Triple(R.string.courses_exercises_now, R.plurals.courses_exercises_next_in, R.string.courses_exercises_next_in_long)
        SessionKind.PRACTICAL -> Triple(R.string.courses_practical_now, R.plurals.courses_practical_next_in, R.string.courses_practical_next_in_long)
    }
    return when {
        classNow.status == ClassNow.Status.ONGOING -> stringResource(now, timeOfDay(classNow.end))
        classNow.minutesUntil < 60 -> pluralStringResource(nextIn, classNow.minutesUntil, classNow.minutesUntil)
        else -> stringResource(nextAt, durationText(classNow.minutesUntil))
    }
}

/** "2 h 15 min", "3 h" or, from a day on, "1 d 2 h": how long until a class that is an hour or more away. */
@Composable
private fun durationText(minutes: Int): String {
    val days = minutes / MINUTES_PER_DAY
    val hours = minutes % MINUTES_PER_DAY / 60
    val rest = minutes % 60
    return when {
        days > 0 && hours > 0 -> stringResource(R.string.duration_days_hours, days, hours)
        days > 0 -> stringResource(R.string.duration_days, days)
        rest > 0 -> stringResource(R.string.duration_hours_minutes, hours, rest)
        else -> stringResource(R.string.duration_hours, hours)
    }
}

private const val MINUTES_PER_DAY = 24 * 60

/**
 * When the class is and, on a line of its own, where: a room like `200C 01.05 (Aula …)` is too
 * long to share a line with the time, and it's what the user looks for on the way there.
 */
@Composable
internal fun ClassNowLines(classNow: ClassNow, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier) {
        Text(
            classNowTime(classNow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = color,
            fontWeight = if (classNow.prominent) FontWeight.Medium else null,
        )
        classNow.location?.let { location ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = stringResource(R.string.courses_class_room),
                    modifier = Modifier.size(16.dp),
                    tint = if (color.isSpecified) color else LocalContentColor.current,
                )
                Text(location, maxLines = 2, overflow = TextOverflow.Ellipsis, color = color)
            }
        }
    }
}

/**
 * The course's class now (or next today) at the top of the course, as its row in the course list
 * says it: tinted while it's going on or about to start.
 */
@Composable
internal fun ClassNowBanner(classNow: ClassNow, modifier: Modifier = Modifier) {
    val tinted = classNow.prominent && !Tolly.glass
    Surface(
        color = if (tinted) MaterialTheme.colorScheme.primaryContainer else Tolly.colors.group,
        contentColor = if (tinted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = groupShape(ItemPosition.Single),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            ClassNowLines(classNow, color = if (tinted) Color.Unspecified else MaterialTheme.colorScheme.primary)
        }
    }
}
