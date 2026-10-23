package be.tolly.core.model

enum class AgendaItemKind {
    /** A lecture or exercise session from the KU Leuven timetable. */
    LESSON,

    /** A course event, such as a lecture or an exam session. */
    EVENT,

    /** The due date of an assignment, test or other gradable item. */
    DEADLINE,

    /** Institution-wide items, e.g. holidays. */
    INSTITUTION,
    OFFICE_HOURS,
    PERSONAL,
}

/** What kind of session a lesson is, from its title ("Gegevensbanken: oefeningen"). */
enum class SessionKind {
    /** A lecture, or anything not recognised as one of the others. */
    CLASS,

    /** An exercise session (oefenzitting, oefeningen, werkcollege). */
    EXERCISES,

    /** A practical, lab or pc session (practicum, labo, pc-sessie). */
    PRACTICAL,
    ;

    companion object {
        fun of(title: String): SessionKind {
            val text = title.lowercase()
            return when {
                listOf("practic", "practica", "labo", "lab session", "pc-", "pc sessie", "pc-sessie").any { it in text } -> PRACTICAL
                listOf("oefen", "werkcollege", "exercise", "tutorial").any { it in text } -> EXERCISES
                else -> CLASS
            }
        }
    }
}

/** An entry of the Toledo calendar, with the course's (customised) look when it belongs to one. */
data class AgendaItem(
    val id: String,
    val kind: AgendaItemKind,
    val title: String,
    val description: String?,
    val location: String?,
    val start: EpochMillis,
    val end: EpochMillis?,
    val courseId: String?,
    /** The course's name in Tolly, or the calendar's name for non-course items. */
    val calendarName: String?,
    val courseIcon: CourseIcon = CourseIcon.INITIALS,
    val courseColor: CourseColor? = null,
    /** A lesson's group in the timetable (`G0T43a_InfR4`: the course code, then the group). */
    val timetableGroup: String? = null,
    /** A lesson's activity in the timetable (`15 Oefenzitting`), the same for every group. */
    val timetableActivity: String? = null,
    /**
     * For a session of another reeks the user also shows in their agenda, the groups it is for
     * (several when they share the session); null for the user's own lessons and other items.
     */
    val extraSeries: List<String>? = null,
)

/** A session of another group (reeks) of a lesson's course, from the public timetable. */
data class GroupSession(
    /** The group as the timetable names it, `G0T43a_InfR1`. */
    val group: String,
    val start: EpochMillis,
    val end: EpochMillis?,
    val location: String?,
)

/** A lesson of a course that is going on now, or the next one to start today. */
data class ClassNow(
    val start: EpochMillis,
    val end: EpochMillis,
    val location: String?,
    val status: Status,
    /** For the next class: how many minutes until it starts, rounded up. */
    val minutesUntil: Int = 0,
    /** A lecture, an exercise session or a practical. */
    val kind: SessionKind = SessionKind.CLASS,
) {
    enum class Status { ONGOING, NEXT }

    /** Highlighted like an ongoing class: going on, or starting within [SOON_MINUTES]. */
    val prominent: Boolean get() = status == Status.ONGOING || minutesUntil <= SOON_MINUTES

    companion object {
        const val SOON_MINUTES = 15
    }
}

/** The signed-in user's result for one gradebook column. */
data class Grade(
    val columnId: String,
    val name: String,
    /** What Toledo shows ("15/20", "A", "Passed"), or null while not graded. */
    val displayGrade: String?,
    val score: Double?,
    val possible: Double?,
    val needsGrading: Boolean,
    val feedback: String?,
    val dueDate: EpochMillis?,
)
