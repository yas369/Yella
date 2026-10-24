package com.smsledger.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MatchingTest {

    private val t0 = LocalDateTime.of(2026, 10, 24, 12, 0)

    private fun bank(id: Long, amount: Long, dir: Direction, minutes: Long, account: String = "HDFCBK|4821", ref: String? = null) =
        Candidate(id, amount, dir, t0.plusMinutes(minutes), account, ref)

    @Test
    fun `a label with the same UPI reference matches regardless of time (spec 8)`() {
        val label = LabelSms(25_000, Direction.Debit, t0.plusHours(5), upiRef = "629114830271")
        val txs = listOf(bank(1, 25_000, Direction.Debit, 0, ref = "629114830271"), bank(2, 25_000, Direction.Debit, 290))
        assertEquals(1L, LabelMatcher.match(label, txs))
    }

    @Test
    fun `without a reference, same amount and direction within 30 minutes matches the nearest`() {
        val label = LabelSms(25_000, Direction.Debit, t0.plusMinutes(20))
        val txs = listOf(bank(1, 25_000, Direction.Debit, 0), bank(2, 25_000, Direction.Debit, 25), bank(3, 25_000, Direction.Credit, 20))
        assertEquals(2L, LabelMatcher.match(label, txs))
    }

    @Test
    fun `outside the window or a different amount, nothing matches`() {
        val label = LabelSms(25_000, Direction.Debit, t0.plusMinutes(45))
        assertNull(LabelMatcher.match(label, listOf(bank(1, 25_000, Direction.Debit, 0), bank(2, 24_900, Direction.Debit, 44))))
    }

    @Test
    fun `an exact debit and credit between own accounts close together pair automatically (spec 9)`() {
        val out = bank(1, 100_000, Direction.Debit, 0, account = "CUBANK|1234")
        val inn = bank(2, 100_000, Direction.Credit, 3, account = "WALLET|AMAZONPAY")
        assertEquals(Pairing(1, Confidence.Auto), TransferPairer.find(inn, listOf(out)))
    }

    @Test
    fun `a small fee difference is a possible transfer for the user to confirm`() {
        val out = bank(1, 100_000, Direction.Debit, 0, account = "CUBANK|1234")
        val inn = bank(2, 99_500, Direction.Credit, 2, account = "WALLET|AMAZONPAY")
        assertEquals(Pairing(1, Confidence.Possible), TransferPairer.find(inn, listOf(out)))
    }

    @Test
    fun `too big a difference, same account, or over 2 hours is not a transfer`() {
        val inn = bank(9, 70_000, Direction.Credit, 5, account = "WALLET|AMAZONPAY")
        assertNull(TransferPairer.find(inn, listOf(bank(1, 100_000, Direction.Debit, 0, account = "CUBANK|1234"))))
        val sameAccount = bank(9, 100_000, Direction.Credit, 5, account = "CUBANK|1234")
        assertNull(TransferPairer.find(sameAccount, listOf(bank(1, 100_000, Direction.Debit, 0, account = "CUBANK|1234"))))
        val late = bank(9, 100_000, Direction.Credit, 121, account = "WALLET|AMAZONPAY")
        assertNull(TransferPairer.find(late, listOf(bank(1, 100_000, Direction.Debit, 0, account = "CUBANK|1234"))))
    }

    @Test
    fun `the fee tolerance is at most 50 rupees or 2 percent, whichever is smaller`() {
        assertEquals(2_000L, SmsDefaults.feeTolerance(100_000))   // 2% of ₹1,000 = ₹20
        assertEquals(5_000L, SmsDefaults.feeTolerance(1_000_000)) // 2% of ₹10,000 = ₹200 → capped at ₹50
    }

    @Test
    fun `a bank SMS merges with a matching manual entry within 6 hours (spec 8)`() {
        val sms = bank(10, 25_000, Direction.Debit, 0)
        val manual = listOf(
            Candidate(1, 25_000, Direction.Debit, t0.minusHours(5), "Manual"),
            Candidate(2, 25_000, Direction.Debit, t0.minusHours(1), "Manual"),
            Candidate(3, 25_000, Direction.Credit, t0, "Manual"),
        )
        assertEquals(2L, ManualMerger.match(sms, manual))
        assertNull(ManualMerger.match(sms, listOf(Candidate(4, 25_000, Direction.Debit, t0.minusHours(7), "Manual"))))
    }
}
