package be.tolly.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarIdsTest {
    @Test
    fun readsIdsFromAListOrAPage() {
        assertEquals(listOf("PERSONAL", "_1_1"), calendarIds(Json.parseToJsonElement("""[{"id":"PERSONAL","name":"Personal"},{"id":"_1_1"}]""")))
        assertEquals(listOf("INSTITUTION"), calendarIds(Json.parseToJsonElement("""{"results":[{"id":"INSTITUTION"}],"paging":{}}""")))
        assertEquals(emptyList<String>(), calendarIds(Json.parseToJsonElement("""{"status":403}""")))
    }
}
