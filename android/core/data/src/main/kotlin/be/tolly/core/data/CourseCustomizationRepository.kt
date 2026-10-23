package be.tolly.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.model.Course
import be.tolly.core.model.CourseColor
import be.tolly.core.model.CourseCustomization
import be.tolly.core.model.CourseIcon
import be.tolly.core.model.CourseIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.customizationStore: DataStore<Preferences> by preferencesDataStore(name = "course_customizations")

/** Everything the user customised about their courses, plus their own course order. */
data class CourseCustomizations(
    val byCourse: Map<String, CourseCustomization> = emptyMap(),
    /** Course ids in the user's order; courses that aren't listed sort after these, by name. */
    val order: List<String> = emptyList(),
)

/**
 * Names, icons, colours and order the user picked for their courses. Kept apart from the Room
 * cache, so clearing the cache doesn't throw the user's work away.
 */
@Singleton
class CourseCustomizationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val customizations: Flow<CourseCustomizations> = context.customizationStore.data.map { prefs ->
        val byCourse = mutableMapOf<String, CourseCustomization>()
        for ((key, value) in prefs.asMap()) {
            val (kind, courseId) = key.name.split('/', limit = 2).takeIf { it.size == 2 } ?: continue
            val current = byCourse[courseId] ?: CourseCustomization()
            byCourse[courseId] = when (kind) {
                NICKNAME -> current.copy(nickname = value as? String)
                ICON -> current.copy(icon = (value as? String)?.let { runCatching { CourseIcon.valueOf(it) }.getOrNull() })
                COLOR -> current.copy(color = (value as? String)?.let { runCatching { CourseColor.valueOf(it) }.getOrNull() })
                else -> current
            }
        }
        CourseCustomizations(
            byCourse = byCourse,
            order = prefs[ORDER_KEY]?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
        )
    }

    /** Forgets everything, when another account signs in. */
    suspend fun clearAll() {
        context.customizationStore.edit { it.clear() }
    }

    suspend fun setNickname(courseId: String, nickname: String?) = set(NICKNAME, courseId, nickname?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun setIcon(courseId: String, icon: CourseIcon?) = set(ICON, courseId, icon?.name)

    suspend fun setColor(courseId: String, color: CourseColor?) = set(COLOR, courseId, color?.name)

    suspend fun reset(courseId: String) {
        context.customizationStore.edit { prefs ->
            listOf(NICKNAME, ICON, COLOR).forEach { prefs.remove(key(it, courseId)) }
        }
    }

    /** Puts [courseIds] (one section of the course list) in this order, keeping the other sections' order. */
    suspend fun setOrder(courseIds: List<String>) {
        context.customizationStore.edit { prefs ->
            val others = prefs[ORDER_KEY]?.split(',')?.filter { it.isNotBlank() && it !in courseIds }.orEmpty()
            prefs[ORDER_KEY] = (courseIds + others).joinToString(",")
        }
    }

    private suspend fun set(kind: String, courseId: String, value: String?) {
        context.customizationStore.edit { prefs ->
            if (value == null) prefs.remove(key(kind, courseId)) else prefs[key(kind, courseId)] = value
        }
    }

    private fun key(kind: String, courseId: String) = stringPreferencesKey("$kind/$courseId")

    private companion object {
        const val NICKNAME = "name"
        const val ICON = "icon"
        const val COLOR = "color"
        val ORDER_KEY = stringPreferencesKey("order")
    }
}

/** The course as the user sees it: their name, icon, colour and position, or the automatic ones. */
fun Course.customizedWith(customizations: CourseCustomizations): Course {
    val custom = customizations.byCourse[id]
    val icon = custom?.icon ?: CourseIcons.guess(name, kind)
    return copy(
        nickname = custom?.nickname,
        icon = icon,
        color = custom?.color ?: icon.defaultColor,
        position = customizations.order.indexOf(id).takeIf { it >= 0 },
    )
}
