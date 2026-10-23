package be.tolly.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.model.Highlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.highlightStore: DataStore<Preferences> by preferencesDataStore(name = "highlights")

/** What a highlight is on; each kind has its own ids. */
enum class HighlightTarget(internal val prefix: String) {
    COURSE("course"),
    CONTENT("content"),
    ANNOUNCEMENT("news"),
}

/** The user's highlights, per target kind and id. */
data class Highlights(val byTarget: Map<HighlightTarget, Map<String, Highlight>> = emptyMap()) {
    operator fun get(target: HighlightTarget, id: String): Highlight? = byTarget[target]?.get(id)
}

/**
 * The colours the user marked courses, content items and announcements with. Kept apart from
 * the Room cache, like course customisations, so clearing the cache keeps them.
 */
@Singleton
class HighlightRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val highlights: Flow<Highlights> = context.highlightStore.data.map { prefs ->
        val byTarget = mutableMapOf<HighlightTarget, MutableMap<String, Highlight>>()
        for ((key, value) in prefs.asMap()) {
            val (prefix, id) = key.name.split('/', limit = 2).takeIf { it.size == 2 } ?: continue
            val target = HighlightTarget.entries.firstOrNull { it.prefix == prefix } ?: continue
            val highlight = (value as? String)?.let { runCatching { Highlight.valueOf(it) }.getOrNull() } ?: continue
            byTarget.getOrPut(target) { mutableMapOf() }[id] = highlight
        }
        Highlights(byTarget)
    }

    /** Forgets everything, when another account signs in. */
    suspend fun clearAll() {
        context.highlightStore.edit { it.clear() }
    }

    /** Marks [id] with [highlight], or clears its mark when it already has that one. */
    suspend fun toggle(target: HighlightTarget, id: String, highlight: Highlight) {
        val key = stringPreferencesKey("${target.prefix}/$id")
        context.highlightStore.edit { prefs ->
            if (prefs[key] == highlight.name) prefs.remove(key) else prefs[key] = highlight.name
        }
    }
}
