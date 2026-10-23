package be.tolly.feature.agenda

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimeGridTest {
    @Test
    fun windowKeepsEightToSixFromTheFirstItemsHourToAnHourAfterTheLast() {
        assertEquals(HourWindow(8, 18), hourWindow(emptyList()))
        assertEquals(HourWindow(8, 18), hourWindow(listOf(MinuteSpan(10 * 60, 12 * 60))))
        assertEquals(HourWindow(8, 21), hourWindow(listOf(MinuteSpan(8 * 60, 9 * 60), MinuteSpan(19 * 60 + 30, 20 * 60))))
        assertEquals(HourWindow(7, 18), hourWindow(listOf(MinuteSpan(7 * 60 + 30, 9 * 60))))
        assertEquals(HourWindow(0, 24), hourWindow(listOf(MinuteSpan(30, 23 * 60 + 59))))
    }

    @Test
    fun hoursFromTopCountsFromTheWindowStart() {
        assertEquals(2.5f, HourWindow(8, 18).hoursFromTop(10 * 60 + 30), 0.001f)
    }

    @Test
    fun coveredMinutesCountsOverlapOnce() {
        val spans = listOf(MinuteSpan(480, 600), MinuteSpan(540, 660), MinuteSpan(720, 780))
        assertEquals(180 + 60, coveredMinutes(spans))
        assertEquals(0, coveredMinutes(emptyList()))
    }

    @Test
    fun overlappingItemsShareColumns() {
        val slots = layoutColumns(listOf(MinuteSpan(480, 600), MinuteSpan(540, 660), MinuteSpan(660, 720)))
        assertEquals(ColumnSlot(0, 2), slots[0])
        assertEquals(ColumnSlot(1, 2), slots[1])
        assertEquals(ColumnSlot(0, 1), slots[2])
    }

    @Test
    fun weekStartIsMonday() {
        assertEquals(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 22).weekStart())
        assertEquals(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25).weekStart())
        assertEquals(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19).weekStart())
    }

    @Test
    fun abbreviationsAreShort() {
        assertEquals("IS", abbreviation("Inleiding Statistiek"))
        assertEquals("Wiskunde", abbreviation("Wiskunde I"))
        assertEquals("Fysica", abbreviation("Fysica"))
    }
}
