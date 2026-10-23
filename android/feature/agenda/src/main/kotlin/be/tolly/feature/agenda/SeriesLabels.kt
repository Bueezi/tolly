package be.tolly.feature.agenda

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import be.tolly.core.data.TimetableGroups
import be.tolly.core.designsystem.R
import be.tolly.core.model.AgendaItem

/**
 * How reeksen are named: "Series 2" for `G0T43a_InfR2`, "Series 1 and 2" for two that share a
 * session; a group not numbered that way by its own name after the course code.
 */
internal fun seriesLabel(context: Context, groups: List<String>): String {
    val sorted = groups.distinct().sortedWith(TimetableGroups.order)
    val numbers = sorted.map(TimetableGroups::seriesNumber)
    if (sorted.isEmpty()) return ""
    if (numbers.any { it == null }) return sorted.joinToString(", ") { it.substringAfter('_') }
    val n = numbers.filterNotNull()
    return if (n.size == 1) {
        context.getString(R.string.agenda_series, n[0])
    } else {
        context.getString(R.string.agenda_series_many, n.dropLast(1).joinToString(", "), n.last())
    }
}

/** An agenda item's title, with the reeks for a session of another reeks the user also shows. */
@Composable
internal fun AgendaItem.shownTitle(): String {
    val series = extraSeries ?: return title
    return "$title · ${seriesLabel(LocalContext.current, series)}"
}
