package be.tolly.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeEditTest {
    @Test
    fun searchPicksTheNewestRunOfTheCourse() {
        val html = """
            <div data-id="282191.13" data-idonly="282191" data-name="G0T42a, Logica voor informatici, 2022"></div>
            <div data-id="1417674.13" data-idonly="1417674" data-name="G0T42a_2026, Logica voor informatici, 2026"></div>
            <div data-id="1158053.13" data-idonly="1158053" data-name="G0T42a_2025, Logica voor informatici, 2025"></div>
            <div data-id="1417632.13" data-idonly="1417632" data-name="G0T43a_2026, Logica voor informatici: oefenzittingen, 2026"></div>
        """
        assertEquals("1417674", TimeEditRepository.newestRun("G0T42a", html))
        assertNull(TimeEditRepository.newestRun("G0N02a", html))
    }

    @Test
    fun descriptionNamesActivityAndGroup() {
        val details = "15 Oefenzitting \nG0T43a_InfR4\nID 7255735"
        assertEquals("15 Oefenzitting", TimetableGroups.activity(details))
        assertEquals("G0T43a_InfR4", TimetableGroups.group(details))
        assertEquals("G0T43a", TimetableGroups.courseCode("G0T43a_InfR4"))
        assertEquals(4, TimetableGroups.seriesNumber("G0T43a_InfR4"))
        assertNull(TimetableGroups.group("01 College \nG0T42a\nID 7119031"))
        assertEquals(listOf("G0T43a_InfR1", "G0T43a_InfR2"), TimetableGroups.groups("15 Oefenzitting\nG0T43a_InfR1\nG0T43a_InfR2"))
        assertEquals(listOf("G0T43a_InfR2", "G0T43a_InfR10", "G0T43a_X"), listOf("G0T43a_X", "G0T43a_InfR10", "G0T43a_InfR2").sortedWith(TimetableGroups.order))
    }
}
