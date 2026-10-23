package com.smsledger.ui.needs

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountCandidatesTest {

    @Test
    fun `offers amounts, masked digits and balances but not date fragments`() {
        val body = "Rs.1,450.00 spent on your HDFC Bank Card ending 4821 at RELIANCE SMART on 22-10-26. Avl bal Rs.23,180.55"
        assertEquals(listOf("1,450.00", "4821", "23,180.55"), amountCandidates(body))
    }

    @Test
    fun `drops a trailing comma that belongs to the sentence`() {
        assertEquals(listOf("500"), amountCandidates("Paid 500, thanks"))
    }

    @Test
    fun `repeated numbers are offered once`() {
        assertEquals(listOf("250"), amountCandidates("Debited 250 of 250"))
    }
}
