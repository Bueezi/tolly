package be.tolly.feature.courses.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grading
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import be.tolly.core.data.DataError
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.dateTime
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.groupShape
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.Grade
import be.tolly.core.ui.ListUiState
import be.tolly.core.ui.RefreshableList
import kotlin.math.roundToInt

/** The Grades tab: the user's results per gradebook item, with an overall average when scores allow. */
@Composable
internal fun CourseGrades(state: ListUiState<Grade>, onRefresh: () -> Unit, onOpenInToledo: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    RefreshableList(
        state = state,
        onRefresh = onRefresh,
        emptyIcon = Icons.Outlined.Grading,
        emptyTitle = stringResource(R.string.grades_empty),
        // Some courses keep their gradebook to themselves; Toledo can still show it.
        emptyContent = if (state.error == DataError.NOT_AVAILABLE) {
            { GradesNotAvailable(onOpenInToledo) }
        } else {
            null
        },
    ) {
        item(key = "summary") { GradeSummary(state.items, onOpenInToledo) }
        itemsIndexed(state.items, key = { _, it -> it.columnId }) { index, grade ->
            GradeRow(
                grade = grade,
                position = ItemPosition(index, state.items.size),
                expanded = expanded == grade.columnId,
                onClick = { expanded = if (expanded == grade.columnId) null else grade.columnId },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun GradesNotAvailable(onOpenInToledo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.grades_not_available),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onOpenInToledo, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.action_open_in_toledo))
        }
    }
}

/** Average percentage over the graded items that have a maximum score. */
internal fun average(grades: List<Grade>): Pair<Int, Int>? {
    val scored = grades.filter { it.score != null && (it.possible ?: 0.0) > 0.0 }
    if (scored.isEmpty()) return null
    val total = scored.sumOf { it.score!! }
    val possible = scored.sumOf { it.possible!! }
    return (total / possible * 100).roundToInt() to scored.size
}

@Composable
private fun GradeSummary(grades: List<Grade>, onOpenInToledo: () -> Unit) {
    val average = average(grades)
    val glass = Tolly.glass
    Surface(
        color = if (glass) Tolly.colors.group else MaterialTheme.colorScheme.secondaryContainer,
        shape = groupShape(ItemPosition.Single),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (average != null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${average.first}%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        pluralStringResource(R.plurals.grades_average, average.second, average.second),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                LinearProgressIndicator(progress = { average.first / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.grades_no_average), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(if (average != null) R.string.grades_disclaimer else R.string.grades_no_average_body),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
            OutlinedButton(onClick = onOpenInToledo) { Text(stringResource(R.string.action_open_in_toledo)) }
        }
    }
}

@Composable
internal fun GradeRow(grade: Grade, position: ItemPosition, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val feedback = grade.feedback?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
    TollyListItem(
        modifier = modifier.groupItem(position, onClick = onClick, enabled = !feedback.isNullOrBlank()),
        headline = { Text(grade.name.ifBlank { stringResource(R.string.grades_item) }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supporting = {
            Column {
                val status = when {
                    grade.displayGrade != null -> null
                    grade.needsGrading -> stringResource(R.string.grades_needs_grading)
                    else -> stringResource(R.string.grades_not_graded)
                }
                val due = grade.dueDate?.let { stringResource(R.string.grades_due, dateTime(it)) }
                val line = listOfNotNull(status, due).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!feedback.isNullOrBlank()) {
                    AnimatedVisibility(visible = expanded) {
                        Text(feedback, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (!expanded) Text(stringResource(R.string.grades_has_feedback), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailing = {
            val text = grade.displayGrade ?: "–"
            Surface(
                color = if (grade.displayGrade != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        },
    )
}
