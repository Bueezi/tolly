package be.tolly.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test
    fun usesFirstLettersOfSignificantWords() {
        assertEquals("LI", initialsOf("Logica voor informatici"))
        assertEquals("BW", initialsOf("Bibliotheek Wetenschap & Technologie"))
        assertEquals("WI", initialsOf("Wiskunde I"))
    }

    @Test
    fun oneWordNameGetsTwoLetters() {
        assertEquals("GE", initialsOf("Gegevensbanken"))
        assertEquals("IN", initialsOf("Informaticawerktuigen"))
    }

    @Test
    fun fallsBackForEmptyNames() {
        assertEquals("?", initialsOf(""))
        assertEquals("X", initialsOf("X"))
    }
}
