package com.smsledger.core.limit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AlertPolicyTest {

    private fun due(spent: Long, sent: Set<LimitAlert> = emptySet()) = AlertPolicy.due(limitStatus(spent, 50_000), sent)

    @Test
    fun `nothing below 80 percent`() {
        assertEquals(emptyList<LimitAlert>(), due(39_900))
    }

    @Test
    fun `one alert at 80 percent, then never again that day (spec 13)`() {
        assertEquals(listOf(LimitAlert.Near), due(40_000))
        assertEquals(emptyList<LimitAlert>(), due(45_000, sent = setOf(LimitAlert.Near)))
    }

    @Test
    fun `one alert when the limit is reached`() {
        assertEquals(listOf(LimitAlert.Reached), due(50_000, sent = setOf(LimitAlert.Near)))
        assertEquals(emptyList<LimitAlert>(), due(70_000, sent = setOf(LimitAlert.Near, LimitAlert.Reached)))
    }

    @Test
    fun `jumping straight past the limit sends only the limit alert`() {
        assertEquals(listOf(LimitAlert.Reached), due(60_000))
    }

    @Test
    fun `the lent reminder goes out on the 1st, once a month, only when something is owed (spec 13)`() {
        val first = LocalDate.of(2026, 11, 1)
        assertTrue(AlertPolicy.lentReminderDue(first, lastSent = YearMonth.of(2026, 10), outstandingPaise = 250_000))
        assertFalse(AlertPolicy.lentReminderDue(first, lastSent = YearMonth.of(2026, 11), outstandingPaise = 250_000))
        assertFalse(AlertPolicy.lentReminderDue(first, lastSent = null, outstandingPaise = 0))
        assertFalse(AlertPolicy.lentReminderDue(LocalDate.of(2026, 11, 2), lastSent = null, outstandingPaise = 250_000))
    }
}
