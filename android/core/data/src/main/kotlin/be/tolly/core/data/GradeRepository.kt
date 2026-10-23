package be.tolly.core.data

import be.tolly.core.auth.AuthState
import be.tolly.core.auth.SessionManager
import be.tolly.core.database.GradeDao
import be.tolly.core.database.GradeEntity
import be.tolly.core.model.Grade
import be.tolly.core.network.UltraApi
import be.tolly.core.network.collectAllPages
import be.tolly.core.network.dto.GradeColumnDto
import be.tolly.core.network.dto.GradeDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in user's grades per course, cached for offline use. */
@Singleton
class GradeRepository @Inject constructor(
    private val api: UltraApi,
    private val gradeDao: GradeDao,
    private val sessionManager: SessionManager,
) {
    fun observeForCourse(courseId: String): Flow<List<Grade>> =
        gradeDao.observeForCourse(courseId).map { list -> list.map { it.toModel() } }

    /** Every cached grade, per course id. */
    fun observeAll(): Flow<Map<String, List<Grade>>> =
        gradeDao.observeAll().map { list -> list.groupBy({ it.courseId }, { it.toModel() }) }

    /**
     * Refreshes the grades of [courseIds], a few at a time. Fails on an expired session, or when
     * every course failed; a course that keeps its gradebook to itself isn't a failure.
     */
    suspend fun refreshAll(courseIds: List<String>): Result<Unit> = refreshCall {
        val failures = courseIds.mapParallel(CONCURRENCY) { refreshCourse(it).getOrThrow() }
            .mapNotNull { it.exceptionOrNull() }
            .filter { it.toDataError() != DataError.NOT_AVAILABLE }
        failures.firstOrNull { it.toDataError() == DataError.SESSION_EXPIRED }?.let { throw it }
        if (courseIds.isNotEmpty() && failures.size == courseIds.size) throw failures.first()
    }

    suspend fun refreshCourse(courseId: String): Result<Unit> = refreshCall {
        val userId = (sessionManager.state.value as? AuthState.SignedIn)?.user?.id
            ?: throw DataException(DataError.SESSION_EXPIRED)
        val grades = collectAllPages(api.userGrades(courseId, userId)) { api.gradesPage(it) }
        // Students can't always list the gradebook columns; then look up the graded ones one by one.
        val columns = listColumns(courseId) ?: grades.mapNotNull { grade -> column(courseId, grade.columnId) }
        val byColumn = grades.associateBy { it.columnId }
        val visible = columns.filter { it.availability?.available != "No" }
        val entities = visible.mapIndexed { index, column -> entity(courseId, column, byColumn[column.id], index) } +
            grades.filter { grade -> visible.none { it.id == grade.columnId } }
                .mapIndexed { index, grade -> entity(courseId, null, grade, visible.size + index) }
        gradeDao.replaceForCourse(courseId, entities.filter { it.name != null || it.displayGrade != null })
    }


    private companion object {
        const val CONCURRENCY = 4
    }

    private suspend fun listColumns(courseId: String): List<GradeColumnDto>? = try {
        collectAllPages(api.gradeColumns(courseId)) { api.gradeColumnsPage(it) }
    } catch (e: HttpException) {
        if (e.isSessionError()) throw e
        null
    }

    private suspend fun column(courseId: String, columnId: String): GradeColumnDto? = try {
        api.gradeColumn(courseId, columnId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.isSessionError()) throw e
        null
    }
}

private fun entity(courseId: String, column: GradeColumnDto?, grade: GradeDto?, position: Int): GradeEntity {
    val possible = grade?.displayGrade?.possible ?: column?.score?.possible
    val score = grade?.displayGrade?.score ?: grade?.score
    return GradeEntity(
        courseId = courseId,
        columnId = column?.id ?: grade?.columnId.orEmpty(),
        name = column?.displayName?.takeIf { it.isNotBlank() } ?: column?.name,
        displayGrade = grade?.displayGrade?.text?.takeIf { it.isNotBlank() }
            ?: grade?.text?.takeIf { it.isNotBlank() }
            ?: score?.let { formatScore(it, possible) },
        score = score,
        possible = possible,
        status = grade?.status,
        feedback = grade?.feedback?.takeIf { it.isNotBlank() },
        dueDate = parseInstant(column?.grading?.due),
        position = position,
    )
}

private fun formatScore(score: Double, possible: Double?): String {
    fun Double.short() = if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.ROOT, "%.2f", this).trimEnd('0').trimEnd('.')
    return if (possible != null && possible > 0) "${score.short()}/${possible.short()}" else score.short()
}

private fun GradeEntity.toModel() = Grade(
    columnId = columnId,
    name = name.orEmpty(),
    displayGrade = displayGrade,
    score = score,
    possible = possible,
    needsGrading = status.equals("NeedsGrading", ignoreCase = true),
    feedback = feedback,
    dueDate = dueDate,
)
