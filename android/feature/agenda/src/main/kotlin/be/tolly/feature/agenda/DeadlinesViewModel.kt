package be.tolly.feature.agenda

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

/** The gradable items due from [OVERDUE_DAYS] days ago to [WEEKS] weeks ahead, grouped by how soon. */
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
            val deadlines = items.filter { it.kind == AgendaItemKind.DEADLINE }.sortedBy { it.start }
            val tomorrow = day.plusDays(1).startMillis()
            val weekEnd = day.plusDays(7).startMillis()
            val groups = deadlines.groupBy {
                when {
                    it.start < now -> DeadlineGroup.OVERDUE
                    it.start < tomorrow -> DeadlineGroup.TODAY
                    it.start < weekEnd -> DeadlineGroup.THIS_WEEK
                    else -> DeadlineGroup.LATER
                }
            }
            DeadlinesUiState(
                groups = DeadlineGroup.entries.mapNotNull { group -> groups[group]?.let { group to it } },
                count = deadlines.count { it.start >= now },
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
