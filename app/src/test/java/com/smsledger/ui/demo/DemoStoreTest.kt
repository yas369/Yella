package com.smsledger.ui.demo

import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.model.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Each test pins one rule from the v0 spec against the preview store. */
class DemoStoreTest {

    private fun store(today: LocalDate = LocalDate.of(2026, 10, 24)) = DemoStore { today }

    private fun DemoStore.tx(id: Long) = transactions.first { it.id == id }

    @Test
    fun `today counts debits only, uncategorised included (spec 12)`() {
        // Rapido ₹92 (travel) + Third Wave ₹68 (untagged). Lent ₹1,000 and the ₹500 transfer don't count.
        assertEquals(16_000L, store().spentTodayPaise)
    }

    @Test
    fun `month total covers only the current calendar month (spec 12)`() {
        // On 5 Oct the ₹18,000 rent from 22 days earlier (13 Sep) belongs to September.
        val early = store(LocalDate.of(2026, 10, 5))
        val sameMonthSpends = early.transactions.filter { it.kind == TxKind.Spend && it.daysAgo <= 4 }.sumOf { it.amountPaise }
        assertEquals(sameMonthSpends, early.monthSpentPaise)
    }

    @Test
    fun `all like this tags every payment from the payee and remembers the payee (spec 10, 11)`() {
        val s = store()
        val zomato = s.transactions.first { it.payee == "Zomato" }
        s.setCategory(zomato, "food", allLikeThis = true)
        assertTrue(s.transactions.filter { it.payee == "Zomato" }.all { it.categoryId == "food" })
        assertEquals("food", s.payeeRules["zomato"])
    }

    @Test
    fun `just this one tags a single row`() {
        val s = store()
        val zomato = s.transactions.first { it.payee == "Zomato" }
        s.setCategory(zomato, "food", allLikeThis = false)
        assertEquals(1, s.transactions.count { it.payee == "Zomato" && it.categoryId == "food" })
    }

    @Test
    fun `a manual spend is pre-tagged by the payee rule`() {
        val s = store()
        s.setCategory(s.tx(12), "shopping", allLikeThis = false) // MYNTRA
        s.addManual(50_000, TxKind.Spend, null, "myntra")
        assertEquals("shopping", s.transactions.first { it.id >= 1_000 }.categoryId)
    }

    @Test
    fun `tagging a not-daily category takes it out of today (spec 11)`() {
        val s = store()
        s.setCategory(s.tx(2), "bills", allLikeThis = false) // Third Wave ₹68
        assertEquals(9_200L, s.spentTodayPaise)
    }

    @Test
    fun `marking lent removes it from spend and adds it to owed (spec 9)`() {
        val s = store()
        val before = s.owedPaise
        s.setKind(s.tx(2), TxKind.Lent) // Third Wave ₹68
        assertEquals(9_200L, s.spentTodayPaise)
        assertEquals(before + 6_800, s.owedPaise)
    }

    @Test
    fun `money received from someone who owes you reduces the debt (spec 9)`() {
        val s = store()
        s.addManual(40_000, TxKind.Income, null, "Rahul S")
        assertEquals(60_000L, s.lentBook.outstanding("Rahul S"))
    }

    @Test
    fun `write off closes the debt`() {
        val s = store()
        val arjun = s.debtors.first { it.name == "Arjun K" }
        s.writeOff(arjun)
        assertFalse(s.debtors.any { it.name == "Arjun K" })
    }

    @Test
    fun `not a transaction keeps the row but stops counting it (spec 10)`() {
        val s = store()
        s.setExcluded(s.tx(1), true) // Rapido ₹92
        assertTrue(s.tx(1).excluded)
        assertEquals(6_800L, s.spentTodayPaise)
    }

    @Test
    fun `teaching a message adds its transaction (spec 7)`() {
        val s = store()
        val cub = s.needs.filterIsInstance<NeedsItem.Unreadable>().first { it.daysAgo == 0 }
        s.learn(cub, 200_000, moneyIn = false)
        assertEquals(216_000L, s.spentTodayPaise)
        assertFalse(s.needs.contains(cub))
    }

    @Test
    fun `pairing a self-transfer stops both rows counting (spec 9)`() {
        val s = store()
        val monthBefore = s.monthSpentPaise
        s.resolveTransfer(s.needs.filterIsInstance<NeedsItem.PossibleTransfer>().first(), isTransfer = true)
        assertEquals(TxKind.Transfer, s.tx(21).kind)
        assertEquals(TxKind.Transfer, s.tx(22).kind)
        assertEquals(monthBefore - 100_000, s.monthSpentPaise)
    }

    @Test
    fun `undo restores rows, debts and rules together`() {
        val s = store()
        val snap = s.snapshot()
        s.setKind(s.tx(2), TxKind.Lent)
        s.setCategory(s.tx(12), "shopping", allLikeThis = true)
        s.restore(snap)
        assertEquals(TxKind.Spend, s.tx(2).kind)
        assertNull(s.tx(12).categoryId)
        assertEquals(snap.lentBook, s.lentBook)
        assertTrue(s.payeeRules.isEmpty())
    }
}
