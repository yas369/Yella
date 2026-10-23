package com.smsledger.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    private fun type(keys: String): String = keys.fold("") { text, key -> AmountInput.press(text, key) }

    @Test
    fun `digits append`() {
        assertEquals("250", type("250"))
    }

    @Test
    fun `a leading zero is replaced by the next digit`() {
        assertEquals("5", type("05"))
        assertEquals("0", type("00"))
    }

    @Test
    fun `a decimal point on empty input becomes 0 point`() {
        assertEquals("0.", type("."))
    }

    @Test
    fun `only one decimal point is accepted`() {
        assertEquals("2.5", type("2..5"))
    }

    @Test
    fun `at most two decimal places are accepted`() {
        assertEquals("2.55", type("2.555"))
    }

    @Test
    fun `at most seven rupee digits are accepted`() {
        assertEquals("9999999", type("99999999"))
    }

    @Test
    fun `backspace removes the last character`() {
        assertEquals("25", AmountInput.backspace("250"))
        assertEquals("", AmountInput.backspace(""))
    }

    @Test
    fun `converts text to paise`() {
        assertEquals(0L, AmountInput.toPaise(""))
        assertEquals(0L, AmountInput.toPaise("0."))
        assertEquals(25_000L, AmountInput.toPaise("250"))
        assertEquals(25_050L, AmountInput.toPaise("250.5"))
        assertEquals(25_005L, AmountInput.toPaise("250.05"))
    }
}
