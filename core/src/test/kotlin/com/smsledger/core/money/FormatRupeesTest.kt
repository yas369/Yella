package com.smsledger.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatRupeesTest {

    @Test
    fun `zero is shown without decimals`() {
        assertEquals("₹0", formatRupees(0))
    }

    @Test
    fun `whole rupees drop the paise`() {
        assertEquals("₹500", formatRupees(50_000))
    }

    @Test
    fun `non-zero paise are shown with two decimals`() {
        assertEquals("₹250.50", formatRupees(25_050))
        assertEquals("₹0.05", formatRupees(5))
    }

    @Test
    fun `thousands use a single comma`() {
        assertEquals("₹999", formatRupees(99_900))
        assertEquals("₹1,000", formatRupees(100_000))
    }

    @Test
    fun `lakhs and crores use Indian grouping`() {
        assertEquals("₹1,23,456", formatRupees(12_345_600))
        assertEquals("₹1,23,45,678", formatRupees(1_234_567_800))
    }

    @Test
    fun `negative amounts put the sign before the rupee symbol`() {
        assertEquals("-₹1,500.25", formatRupees(-150_025))
    }
}
