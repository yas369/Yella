package com.smsledger.core.limit

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitStatusTest {

    @Test
    fun `nothing spent leaves the whole limit`() {
        val status = limitStatus(spentPaise = 0, limitPaise = 50_000)
        assertEquals(50_000L, status.remainingPaise)
        assertEquals(0L, status.overPaise)
        assertEquals(0f, status.fraction)
        assertEquals(LimitLevel.Ok, status.level)
    }

    @Test
    fun `below 80 percent is ok`() {
        val status = limitStatus(spentPaise = 39_900, limitPaise = 50_000)
        assertEquals(10_100L, status.remainingPaise)
        assertEquals(LimitLevel.Ok, status.level)
    }

    @Test
    fun `80 percent and above is a warning`() {
        assertEquals(LimitLevel.Warning, limitStatus(spentPaise = 40_000, limitPaise = 50_000).level)
    }

    @Test
    fun `spending exactly the limit leaves zero and is not yet over`() {
        val status = limitStatus(spentPaise = 50_000, limitPaise = 50_000)
        assertEquals(0L, status.remainingPaise)
        assertEquals(0L, status.overPaise)
        assertEquals(1f, status.fraction)
        assertEquals(LimitLevel.Warning, status.level)
    }

    @Test
    fun `exceeding the limit reports a positive over amount, never a negative remaining`() {
        val status = limitStatus(spentPaise = 60_000, limitPaise = 50_000)
        assertEquals(0L, status.remainingPaise)
        assertEquals(10_000L, status.overPaise)
        assertEquals(1f, status.fraction)
        assertEquals(LimitLevel.Over, status.level)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero limit is rejected`() {
        limitStatus(spentPaise = 0, limitPaise = 0)
    }
}
