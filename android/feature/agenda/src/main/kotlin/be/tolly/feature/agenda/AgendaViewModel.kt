package be.tolly.feature.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.AgendaRepository
import be.tolly.core.data.DataError
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.TimeEditRepository
import be.tolly.core.data.TimetableGroups
import be.tolly.core.model.AgendaItem
import be.tolly.core.model.GroupSession
import be.tolly.core.ui.RefreshState
import be.tolly.core.ui.Refresher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class AgendaDay(val date: LocalDate, val items: List<AgendaItem>)

/** How the agenda is drawn: grouped per day, one day on a time grid, or a week on one. */
enum class AgendaMode { LIST, DAY, WEEK }

/** A course's reeksen in the agenda's menu: its [groups], of which [own] are the user's. */
data class SeriesChoice(val title: String, val groups: List<String>, val own: Set<String>)

/** The other groups' (reeksen') sessions of the lesson whose sheet is open. */
sealed interface OtherGroups {
    data object Loading : OtherGroups
    data class Loaded(val sessions: List<GroupSession>) : OtherGroups
    data object Failed : OtherGroups
}

data class AgendaUiState(
    /** The coming weeks, for the list. */
    val days: List<AgendaDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val mode: AgendaMode = AgendaMode.LIST,
    /** The day of the Day view, and the week (its Monday on) of the Week view. */
    val selectedDate: LocalDate = LocalDate.now(),
    /** Items per day around the selected week, for the grids. */
    val gridDays: Map<LocalDate, List<AgendaItem>> = emptyMap(),
    /** Deadlines in the next seven days, for the summary line. */
    val deadlinesThisWeek: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: DataError? = null,
)

/**
 * The Toledo calendar. The list shows [WEEKS] weeks from today; the Day and Week views show any
 * day or week, past ones too, loading each week the first time it is visited.
 */
@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: AgendaRepository,
    private val timeEdit: TimeEditRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Today; [onResume] moves it on, so a tab kept in memory overnight starts at the new day. */
    private val today = MutableStateFlow(LocalDate.now(zone))
    private val mode = MutableStateFlow(AgendaMode.LIST)
    private val selectedDate = MutableStateFlow(today.value)

    private val refresher = Refresher(viewModelScope)

    /** The Mondays of the weeks fetched since the app started; the list's weeks count too. */
    private val loadedWeeks = mutableSetOf<LocalDate>()

    private val listItems = today.flatMapLatest { day ->
        repository.observe(day.startMillis(), day.plusWeeks(WEEKS).startMillis())
    }

    /** The selected week and the one on each side, so swiping between days never meets a gap. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val gridItems = selectedDate.map { it.weekStart() }.distinctUntilChanged().flatMapLatest { monday ->
        repository.observe(monday.minusWeeks(1).startMillis(), monday.plusWeeks(2).startMillis())
    }

    private data class Data(val list: List<AgendaItem>, val grid: List<AgendaItem>, val synced: Long?, val refresh: RefreshState)

    val uiState: StateFlow<AgendaUiState> = combine(
        combine(listItems, gridItems, repository.observeLastSynced(), refresher.state, ::Data),
        mode,
        selectedDate,
        today,
    ) { data, shownMode, selected, day ->
        val weekEnd = day.plusDays(7).startMillis()
        AgendaUiState(
            days = data.list.groupBy { it.dayOf() }
                .map { (date, dayItems) -> AgendaDay(date, dayItems.sortedBy { it.start }) }
                .sortedBy { it.date },
            today = day,
            mode = shownMode,
            selectedDate = selected,
            gridDays = data.grid.groupBy { it.dayOf() }.mapValues { (_, dayItems) -> dayItems.sortedBy { it.start } },
            // As on the Deadlines page, which counts course events still to come.
            deadlinesThisWeek = System.currentTimeMillis().let { now -> data.list.count { it.isDue(now) && it.start < weekEnd } },
            loading = data.list.isEmpty() && data.grid.isEmpty() && data.synced == null && !data.refresh.loaded,
            refreshing = data.refresh.refreshing,
            error = data.refresh.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

    init {
        // The view picked last time comes back; the refresh waits for it, to fetch what it shows.
        viewModelScope.launch {
            preferences.agendaMode()?.let { saved -> AgendaMode.entries.firstOrNull { it.name == saved } }?.let { mode.value = it }
            refresh()
        }
        // The lessons come in once the timetable link is known.
        viewModelScope.launch { repository.timetableFound.collect { refresh() } }
    }

    /** Fetches what the current view shows: the coming weeks for the list, else the selected week. */
    fun refresh() {
        if (mode.value == AgendaMode.LIST) refreshList() else refreshWeek(selectedDate.value.weekStart())
    }

    private fun refreshList() {
        val day = today.value
        refresher.refresh {
            repository.refresh(day.startMillis(), day.plusWeeks(WEEKS).startMillis()).also { result ->
                if (result.isSuccess) repeat(WEEKS.toInt() + 1) { loadedWeeks.add(day.weekStart().plusWeeks(it.toLong())) }
            }
        }
    }

    private fun refreshWeek(monday: LocalDate) {
        refresher.refresh {
            repository.refresh(monday.startMillis(), monday.plusWeeks(1).startMillis()).also { result ->
                if (result.isSuccess) loadedWeeks.add(monday)
            }
        }
    }

    /** Fetches the selected week the first time it is shown, once any refresh under way is done. */
    private fun ensureLoaded() {
        val monday = selectedDate.value.weekStart()
        if (mode.value == AgendaMode.LIST || monday in loadedWeeks) return
        viewModelScope.launch {
            refresher.state.first { !it.refreshing }
            if (monday !in loadedWeeks && monday == selectedDate.value.weekStart()) refreshWeek(monday)
        }
    }

    private val _otherGroups = MutableStateFlow<Pair<String, OtherGroups>?>(null)

    /** For the lesson with this id, the other groups' sessions that week (see [loadOtherGroups]). */
    val otherGroups: StateFlow<Pair<String, OtherGroups>?> = _otherGroups.asStateFlow()
    private var otherGroupsJob: Job? = null

    /** Looks up the other reeksen of [lesson] in the public timetable, for its sheet. */
    fun loadOtherGroups(lesson: AgendaItem) {
        if (lesson.timetableGroup == null || _otherGroups.value?.first == lesson.id) return
        otherGroupsJob?.cancel()
        _otherGroups.value = lesson.id to OtherGroups.Loading
        otherGroupsJob = viewModelScope.launch {
            val result = timeEdit.otherGroups(lesson)
            _otherGroups.value = lesson.id to (result.getOrNull()?.let(OtherGroups::Loaded) ?: OtherGroups.Failed)
        }
    }

    /** The other reeksen shown in the agenda besides the user's own. */
    val extraSeries: StateFlow<Set<String>> = preferences.preferences.map { it.extraSeries }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _seriesMenu = MutableStateFlow<List<SeriesChoice>?>(null)

    /** The reeksen of the user's courses for the top bar's menu; null while they're looked up. */
    val seriesMenu: StateFlow<List<SeriesChoice>?> = _seriesMenu.asStateFlow()

    /**
     * Looks up the reeksen of every course the user has one in (their own lessons name it), for
     * the menu: all of them, the user's own among them.
     */
    fun loadSeriesMenu() {
        val state = uiState.value
        val lessons = (state.days.flatMap { it.items } + state.gridDays.values.flatten()).filter { it.timetableGroup != null }
        val own = lessons.mapNotNull { it.timetableGroup }.toSet()
        val byCode = lessons.groupBy { TimetableGroups.courseCode(it.timetableGroup!!) }
        viewModelScope.launch {
            _seriesMenu.value = byCode.mapNotNull { (code, items) ->
                code ?: return@mapNotNull null
                val mine = items.first().timetableGroup!!
                val groups = timeEdit.groupsLike(mine).getOrNull().orEmpty().ifEmpty { listOf(mine) }
                if (groups.size < 2) return@mapNotNull null // no other reeks to pick
                SeriesChoice(items.first().title, groups, own = groups.filter { it in own }.toSet())
            }.sortedBy { it.title }
        }
    }

    /** Shows the reeks [group] in the agenda too, or no longer; the agenda refreshes to add or drop its sessions. */
    fun setExtraSeries(group: String, shown: Boolean) = setExtraSeries(setOf(group), shown)

    /**
     * Shows series [number] in every course that has one, besides the user's own, or no longer:
     * the short menu's one switch per number. A course's own reeks is never touched.
     */
    fun setSeriesNumber(number: Int, shown: Boolean) {
        val groups = seriesMenu.value.orEmpty().flatMap { choice ->
            choice.groups.filter { it !in choice.own && TimetableGroups.seriesNumber(it) == number }
        }.toSet()
        if (groups.isNotEmpty()) setExtraSeries(groups, shown)
    }

    private fun setExtraSeries(groups: Set<String>, shown: Boolean) {
        viewModelScope.launch {
            preferences.setExtraSeries(groups, shown)
            // Every week shown before has to be fetched again, with or without the reeks.
            loadedWeeks.clear()
            refresh()
        }
    }

    /** The screen is shown again: if a new day began meanwhile, move the window and refresh. */
    fun onResume() {
        val now = LocalDate.now(zone)
        if (now != today.value) {
            val following = selectedDate.value == today.value
            today.value = now
            if (following) selectedDate.value = now
            loadedWeeks.clear()
            refresh()
        }
    }

    fun setMode(value: AgendaMode) {
        mode.value = value
        rememberMode()
        ensureLoaded()
    }

    private fun rememberMode() {
        val value = mode.value
        viewModelScope.launch { preferences.setAgendaMode(value.name) }
    }

    /** Moves the shown day by [days], from the latest one: quick taps on next or previous each count. */
    fun stepDays(days: Long) = selectDate(selectedDate.value.plusDays(days))

    /** Moves the shown week by [weeks]. */
    fun stepWeeks(weeks: Long) = selectDate(selectedDate.value.plusWeeks(weeks))

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        ensureLoaded()
    }

    /** Shows [date] in the Day view, from a day heading of the Week view. */
    fun openDay(date: LocalDate) {
        selectedDate.value = date
        mode.value = AgendaMode.DAY
        rememberMode()
        ensureLoaded()
    }

    private fun AgendaItem.dayOf() = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()

    private fun LocalDate.startMillis() = atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        const val WEEKS = 8L
    }
}
