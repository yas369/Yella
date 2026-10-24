package com.smsledger.ui.history

import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HistoryMatchTest {

    private val swiggy = Tx(1, "Swiggy", "HDFC ••4821", LocalDateTime.of(2026, 10, 23, 20, 45), 125_000, TxKind.Spend)

    @Test
    fun `blank query matches everything`() {
        assertTrue(swiggy.matches("  "))
    }

    @Test
    fun `payee matches case-insensitively`() {
        assertTrue(swiggy.matches("swig"))
    }

    @Test
    fun `amount matches with or without grouping and rupee sign`() {
        assertTrue(swiggy.matches("1250"))
        assertTrue(swiggy.matches("1,250"))
        assertTrue(swiggy.matches("₹1,250"))
    }

    @Test
    fun `a shorter number does not match a longer amount that contains it`() {
        assertFalse(swiggy.matches("250"))
        assertFalse(swiggy.matches("125"))
    }

    @Test
    fun `an exact amount with paise matches`() {
        assertTrue(swiggy.matches("1250.00"))
        assertFalse(swiggy.matches("1250.50"))
    }

    @Test
    fun `unrelated text does not match`() {
        assertFalse(swiggy.matches("dmart"))
    }
}
