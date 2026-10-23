package be.tolly.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionKindTest {
    @Test
    fun tellsSessionsApartByTitle() {
        assertEquals(SessionKind.EXERCISES, SessionKind.of("Gegevensbanken: oefeningen"))
        assertEquals(SessionKind.EXERCISES, SessionKind.of("Logica voor informatici: oefenzittingen"))
        assertEquals(SessionKind.PRACTICAL, SessionKind.of("Beginselen van programmeren: practicum"))
        assertEquals(SessionKind.PRACTICAL, SessionKind.of("Informaticawerktuigen: pc-sessie"))
        assertEquals(SessionKind.CLASS, SessionKind.of("Beginselen van programmeren: college"))
        assertEquals(SessionKind.CLASS, SessionKind.of("Wiskunde I"))
    }
}
