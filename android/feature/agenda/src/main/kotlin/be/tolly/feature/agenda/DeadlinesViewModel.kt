package be.tolly.feature.agenda

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.AgendaRepository
import be.tolly.core.data.DataError
import be.tolly.core.model.AgendaItem
import be.tolly.core.model.AgendaItemKind
import be.tolly.core.ui.Refresher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** How soon a deadline is, which is how the Deadlines screen groups them. */
enum class DeadlineGroup { OVERDUE, TODAY, THIS_WEEK, LATER }

data class DeadlinesUiState(
    val groups: List<Pair<DeadlineGroup, List<AgendaItem>>> = emptyList(),
    val count: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: DataError? = null,
)

/**
 * What is due from [OVERDUE_DAYS] days ago to [WEEKS] weeks ahead, grouped by how soon: gradable
 * items, and course events still to come (see [isComing]).
 */
@HiltViewModel
class DeadlinesViewModel @Inject constructor(
    private val repository: AgendaRepository,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today = MutableStateFlow(LocalDate.now(zone))
    private val refresher = Refresher(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DeadlinesUiState> = today.flatMapLatest { day ->
        combine(repository.observe(from(day), until(day)), repository.observeLastSynced(), refresher.state) { items, synced, refresh ->
            val now = System.currentTimeMillis()
            val deadlines = items.filter { it.isDue(now) }.sortedBy { it.start }
            val tomorrow = day.plusDays(1).startMillis()
            val weekEnd = day.plusDays(7).startMillis()
            val groups = deadlines.groupBy {
                when {
                    it.start < now && it.kind == AgendaItemKind.DEADLINE -> DeadlineGroup.OVERDUE
                    it.start < tomorrow -> DeadlineGroup.TODAY
                    it.start < weekEnd -> DeadlineGroup.THIS_WEEK
                    else -> DeadlineGroup.LATER
                }
            }
            DeadlinesUiState(
                groups = DeadlineGroup.entries.mapNotNull { group -> groups[group]?.let { group to it } },
                count = deadlines.count { it.start >= now || it.kind == AgendaItemKind.EVENT },
                loading = items.isEmpty() && synced == null && !refresh.loaded,
                refreshing = refresh.refreshing,
                error = refresh.error,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeadlinesUiState())

    init {
        refresh()
    }

    fun refresh() {
        val day = today.value
        refresher.refresh { repository.refresh(from(day), until(day)) }
    }

    /** The screen is shown again: a new day moves the groups on. */
    fun onResume() {
        val now = LocalDate.now(zone)
        if (now != today.value) {
            today.value = now
            refresh()
        }
    }

    private fun from(day: LocalDate) = day.minusDays(OVERDUE_DAYS).startMillis()

    private fun until(day: LocalDate) = day.plusWeeks(WEEKS).startMillis()

    private fun LocalDate.startMillis() = atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        const val OVERDUE_DAYS = 14L
        const val WEEKS = 12L
    }
}

/** A gradable item, or a course event still to come: what the Deadlines page lists. */
internal fun AgendaItem.isDue(now: Long): Boolean = kind == AgendaItemKind.DEADLINE || isComing(now)

/**
 * A course event that hasn't ended and isn't a class. Toledo's activity stream lists course events
 * under "Upcoming" with the due items, and many courses only put their deadlines, tests and
 * presentations in the calendar as events. Some put their lectures there too, which the agenda
 * shows already and which aren't due.
 */
internal fun AgendaItem.isComing(now: Long): Boolean {
    if (kind != AgendaItemKind.EVENT || classSession.containsMatchIn(title)) return false
    val ends = end?.takeIf { it > start } ?: (start + if (isAllDay()) DateUtils.DAY_IN_MILLIS else 0)
    return ends >= now
}

private val classSession = Regex(
    """\b(hoorcollege|werkcollege|oefenzitting|oefenzittingen|practicum|labo|lecture|lectures)\b""",
    RegexOption.IGNORE_CASE,
)
