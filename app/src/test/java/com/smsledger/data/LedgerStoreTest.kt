package com.smsledger.data

import com.smsledger.ui.model.TxKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** In-memory [LedgerDisk] that applies saves the way storage does: by diff. */
private class MemoryDisk : LedgerDisk {
    var data = LedgerData()
    var prefs = Prefs()

    override suspend fun load() = data to prefs

    override fun save(before: LedgerData, after: LedgerData) {
        val (changed, deleted) = txChanges(before.transactions, after.transactions)
        val rows = data.transactions.associateBy { it.id }.toMutableMap()
        changed.forEach { rows[it.id] = it }
        deleted.forEach { rows.remove(it) }
        data = LedgerData(rows.values.toList(), after.lentBook, after.payeeRules, after.sms)
    }

    override fun savePrefs(prefs: Prefs) {
        this.prefs = prefs
    }

    override fun clear() {
        data = LedgerData()
        prefs = Prefs()
    }
}

/** Each test pins one rule from the v0 spec. Entries are synthetic and exist only here. */
class LedgerStoreTest {

    private var now = LocalDateTime.of(2026, 10, 24, 12, 0)
    private val disk = MemoryDisk()

    private fun store() = LedgerStore(disk) { now }.also { runBlocking { it.load() } }

    private fun LedgerStore.spend(paise: Long, payee: String, category: String? = null) =
        addManual(paise, TxKind.Spend, category, payee).let { transactions.first { it.payee == payee } }

    @Test
    fun `a fresh install has no data and starts at first run`() {
        val s = store()
        assertTrue(s.transactions.isEmpty())
        assertTrue(s.needs.isEmpty())
        assertTrue(s.debtors.isEmpty())
        assertFalse(s.onboarded)
        assertEquals(0L, s.spentTodayPaise)
    }

    @Test
    fun `today counts debits only, uncategorised included (spec 12)`() {
        val s = store()
        s.spend(9_200, "Cab", "travel")
        s.spend(6_800, "Coffee")
        s.setKind(s.spend(100_000, "Friend"), TxKind.Lent)
        s.addManual(50_000, TxKind.Income, null, "Refund")
        assertEquals(16_000L, s.spentTodayPaise)
    }

    @Test
    fun `month total covers only the current calendar month (spec 12)`() {
        val s = store()
        now = LocalDateTime.of(2026, 9, 30, 20, 0)
        s.spend(18_000_00, "Rent", "rent")
        now = LocalDateTime.of(2026, 10, 2, 9, 0)
        s.spend(5_000, "Tea")
        assertEquals(5_000L, s.monthSpentPaise)
    }

    @Test
    fun `all like this tags every payment from the payee and remembers it (spec 10, 11)`() {
        val s = store()
        s.spend(30_000, "Zomato")
        val second = s.spend(40_000, "Zomato")
        s.setCategory(second, "food", allLikeThis = true)
        assertTrue(s.transactions.all { it.categoryId == "food" })
        assertEquals("food", s.payeeRules["zomato"])
    }

    @Test
    fun `just this one tags a single row`() {
        val s = store()
        s.spend(30_000, "Zomato")
        s.setCategory(s.spend(40_000, "Zomato"), "food", allLikeThis = false)
        assertEquals(1, s.transactions.count { it.categoryId == "food" })
    }

    @Test
    fun `a manual spend is pre-tagged by the payee rule`() {
        val s = store()
        s.setCategory(s.spend(129_900, "Myntra"), "shopping", allLikeThis = false)
        s.addManual(50_000, TxKind.Spend, null, "myntra")
        assertEquals("shopping", s.transactions.first().categoryId)
    }

    @Test
    fun `tagging a not-daily category takes it out of today (spec 11)`() {
        val s = store()
        s.spend(9_200, "Cab")
        s.setCategory(s.spend(6_800, "Electricity"), "bills", allLikeThis = false)
        assertEquals(9_200L, s.spentTodayPaise)
    }

    @Test
    fun `marking lent removes it from spend and adds it to owed (spec 9)`() {
        val s = store()
        s.setKind(s.spend(6_800, "Asha"), TxKind.Lent)
        assertEquals(0L, s.spentTodayPaise)
        assertEquals(6_800L, s.owedPaise)
    }

    @Test
    fun `money received from someone who owes you reduces the debt (spec 9)`() {
        val s = store()
        s.setKind(s.spend(100_000, "Ravi"), TxKind.Lent)
        s.addManual(40_000, TxKind.Income, null, "Ravi")
        assertEquals(60_000L, s.lentBook.outstanding("Ravi"))
    }

    @Test
    fun `write off closes the debt`() {
        val s = store()
        s.setKind(s.spend(30_000, "Ravi"), TxKind.Lent)
        s.writeOff(s.debtors.single())
        assertTrue(s.debtors.isEmpty())
    }

    @Test
    fun `not a transaction keeps the row but stops counting it (spec 10)`() {
        val s = store()
        val row = s.spend(9_200, "Cab")
        s.setExcluded(row, true)
        assertTrue(s.transactions.single().excluded)
        assertEquals(0L, s.spentTodayPaise)
    }

    @Test
    fun `undo restores rows, debts and rules together`() {
        val s = store()
        val coffee = s.spend(6_800, "Coffee")
        val snap = s.snapshot()
        s.setKind(coffee, TxKind.Lent)
        s.setCategory(s.transactions.single(), "food", allLikeThis = true)
        s.restore(snap)
        assertEquals(TxKind.Spend, s.transactions.single().kind)
        assertNull(s.transactions.single().categoryId)
        assertEquals(snap.lentBook, s.lentBook)
        assertTrue(s.payeeRules.isEmpty())
    }

    @Test
    fun `entries, debts, rules and settings survive a restart`() {
        val s = store()
        s.finishOnboarding(80_000)
        s.setCategory(s.spend(6_800, "Coffee"), "food", allLikeThis = false)
        s.setKind(s.spend(50_000, "Asha"), TxKind.Lent)
        s.changeTheme(true)

        val reopened = store()
        assertTrue(reopened.onboarded)
        assertEquals(80_000L, reopened.limitPaise)
        assertEquals(true, reopened.darkOverride)
        assertEquals(s.transactions.toList(), reopened.transactions.toList())
        assertEquals(50_000L, reopened.owedPaise)
        assertEquals("food", reopened.payeeRules["coffee"])
    }

    @Test
    fun `undo is saved too, so a restart does not bring the undone change back`() {
        val s = store()
        val snap = s.snapshot()
        s.spend(6_800, "Coffee")
        s.restore(snap)
        assertTrue(store().transactions.isEmpty())
    }

    @Test
    fun `new entries after a restart get fresh ids`() {
        store().spend(6_800, "Coffee")
        val reopened = store()
        reopened.spend(9_200, "Cab")
        assertEquals(2, reopened.transactions.map { it.id }.distinct().size)
    }

    @Test
    fun `delete all erases storage and returns to first run`() {
        val s = store()
        s.finishOnboarding(80_000)
        s.setKind(s.spend(50_000, "Asha"), TxKind.Lent)
        s.deleteAll()
        assertFalse(s.onboarded)
        assertTrue(s.transactions.isEmpty())
        assertEquals(0L, s.owedPaise)
        val reopened = store()
        assertFalse(reopened.onboarded)
        assertTrue(reopened.transactions.isEmpty())
        assertEquals(DEFAULT_LIMIT_PAISE, reopened.limitPaise)
    }
}
