package be.tolly.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseIconsTest {
    private fun guess(name: String, kind: CourseKind = CourseKind.COURSE) = CourseIcons.guess(name, kind)

    @Test
    fun recognisesCoursesOutsideComputerScience() {
        assertEquals(CourseIcon.ELECTRONICS, guess("Elektrische netwerken"))
        assertEquals(CourseIcon.PHYSICS, guess("Elektriciteit en magnetisme"))
        assertEquals(CourseIcon.BIOLOGY, guess("Plantenfysiologie"))
        assertEquals(CourseIcon.STATISTICS, guess("Kansrekenen"))
        assertEquals(CourseIcon.MEDICINE, guess("Immunologie"))
        assertEquals(CourseIcon.ECONOMICS, guess("Accountancy"))
        assertEquals(CourseIcon.ENGINEERING, guess("Sterkteleer"))
        assertEquals(CourseIcon.SOCIOLOGY, guess("Methoden en technieken van sociaalwetenschappelijk onderzoek"))
        assertEquals(CourseIcon.MEDIA, guess("Communicatiewetenschappen"))
        assertEquals(CourseIcon.GEOGRAPHY, guess("Hydrologie"))
        assertEquals(CourseIcon.LAW, guess("Verbintenissenrecht"))
    }

    @Test
    fun recognisesCommonSubjects() {
        assertEquals(CourseIcon.MATH, guess("Wiskunde I"))
        assertEquals(CourseIcon.CODE, guess("Beginselen van Programmeren - BvP"))
        assertEquals(CourseIcon.DATABASE, guess("Gegevensbanken"))
        assertEquals(CourseIcon.LOGIC, guess("Logica voor informatici"))
        assertEquals(CourseIcon.LOGIC, guess("Bewijzen en redeneren voor informatici: Colleges"))
        assertEquals(CourseIcon.TOOLS, guess("Informaticawerktuigen"))
        assertEquals(CourseIcon.STATISTICS, guess("Statistiek en data-analyse"))
        assertEquals(CourseIcon.AI, guess("Artificiële intelligentie"))
        assertEquals(CourseIcon.BIOLOGY, guess("Biochemie"))
    }

    @Test
    fun recognisesMoreSubjects() {
        assertEquals(CourseIcon.THESIS, guess("Bachelorproef informatica"))
        assertEquals(CourseIcon.ELECTRONICS, guess("Computerarchitectuur")) // not a building
        assertEquals(CourseIcon.CLOUD, guess("Gedistribueerde systemen"))
        assertEquals(CourseIcon.HCI, guess("Mens-machine communicatie"))
        assertEquals(CourseIcon.GRAPHICS, guess("Computergrafiek"))
        assertEquals(CourseIcon.ASTRONOMY, guess("Inleiding tot de sterrenkunde"))
        assertEquals(CourseIcon.PHARMACY, guess("Farmacologie"))
        assertEquals(CourseIcon.POLITICS, guess("Internationale betrekkingen"))
        assertEquals(CourseIcon.SOCIOLOGY, guess("Sociologie"))
        assertEquals(CourseIcon.EDUCATION, guess("Didactiek van de wiskunde"))
        assertEquals(CourseIcon.THEOLOGY, guess("Godsdienstwetenschappen"))
        assertEquals(CourseIcon.LITERATURE, guess("Nederlandse literatuur"))
        assertEquals(CourseIcon.LANGUAGE, guess("Frans I"))
        assertEquals(CourseIcon.ENVIRONMENT, guess("Klimaat en duurzaamheid"))
        assertEquals(CourseIcon.AGRICULTURE, guess("Voedingsleer"))
        assertEquals(CourseIcon.SPORTS, guess("Lichamelijke opvoeding")) // "voeding" inside a word isn't food
        assertEquals(CourseIcon.CALCULATOR, guess("Oefenzittingen")) // with a subject in the name, the subject wins
    }

    @Test
    fun communitiesFallBackToTheCommunityIcon() {
        assertEquals(CourseIcon.LIBRARY, guess("Bibliotheek Wetenschap & Technologie", CourseKind.COMMUNITY))
        assertEquals(CourseIcon.COMMUNITY, guess("Faculteit Wetenschappen", CourseKind.COMMUNITY))
    }

    @Test
    fun unknownCoursesKeepTheirInitials() {
        assertEquals(CourseIcon.INITIALS, guess("Keuzevak"))
        assertEquals(CourseIcon.INITIALS, guess("Totaalbeeld")) // "taal" inside a word isn't a language course
    }
}
