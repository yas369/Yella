package com.smsledger.data

import com.smsledger.core.sms.Account
import com.smsledger.core.sms.RawSms
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.model.TxKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** In-memory disk that keeps everything, including processed keys, like Room does. */
private class SmsMemoryDisk : LedgerDisk {
    var data = LedgerData()
    var prefs = Prefs()
    val processed = HashSet<String>()

    override suspend fun load() = data to prefs
    override suspend fun processedKeys() = processed.toSet()
    override fun markProcessed(keys: Collection<String>) { processed += keys }
    override fun save(before: LedgerData, after: LedgerData) { data = after }
    override fun savePrefs(prefs: Prefs) { this.prefs = prefs }
    override fun clear() { data = LedgerData(); prefs = Prefs(); processed.clear() }
}

/** The SMS pipeline end to end through the store. Every message is synthetic and lives only here. */
class SmsCaptureTest {

    private var now = LocalDateTime.of(2026, 10, 24, 18, 0)
    private val disk = SmsMemoryDisk()
    private var seq = 0

    private fun store() = LedgerStore(disk) { now }.also { runBlocking { it.load() } }

    private fun sms(sender: String, body: String, at: LocalDateTime = now.minusHours(1)) = RawSms("k${seq++}", sender, body, at)

    private val inbox = listOf(
        sms("AD-HDFCBK-S", "Rs.250.00 debited from a/c XX4821 to VPA swiggy@icici. UPI Ref 629114830271", now.minusHours(3)),
        sms("VM-HDFCBK", "INR 2,000.00 credited to a/c XX9821 from ACME PAYROLL", now.minusHours(2)),
        sms("JD-CUBANK", "Rs 120 debited from a/c XX1234 at CHAI POINT", now.minusHours(2)),
        sms("+919876543210", "dinner at 8?", now.minusHours(1)),
        sms("JD-AIRTEL", "Rs 299 recharge successful", now.minusHours(1)),
    )

    /** First run: discover, answer, import. */
    private fun LedgerStore.setUp(notMine: Set<Account> = emptySet()): LedgerStore {
        startDiscovery(inbox)
        val answers = discovery!!.accounts.associate { it.account to (it.account !in notMine) }
        confirmAccounts(answers, newestMillis = 1)
        return this
    }

    @Test
    fun `discovery lists bank accounts and nothing from people or telecoms (spec 3, 4)`() {
        val s = store()
        s.startDiscovery(inbox)
        assertEquals(
            listOf(Account("HDFCBK", "4821"), Account("HDFCBK", "9821"), Account("CUBANK", "1234")),
            s.discovery!!.accounts.map { it.account },
        )
    }

    @Test
    fun `confirming accounts turns automatic on and imports their transactions`() {
        val s = store().setUp()
        assertTrue(s.automatic)
        assertEquals(3, s.transactions.size)
        assertEquals(37_000L, s.spentTodayPaise) // ₹250 + ₹120; the ₹2,000 credit never counts
    }

    @Test
    fun `an account marked not mine is never counted (spec 4)`() {
        val s = store().setUp(notMine = setOf(Account("CUBANK", "1234")))
        assertEquals(25_000L, s.spentTodayPaise)
        assertFalse(s.transactions.any { it.accountKey == "CUBANK|1234" })
    }

    @Test
    fun `importing twice never adds a message twice`() {
        val s = store().setUp()
        val before = s.transactions.size
        s.ingestScan(inbox, newestMillis = 2)
        assertEquals(before, s.transactions.size)
    }

    @Test
    fun `processed keys and To review items survive a restart (spec 7)`() {
        val s = store().setUp()
        s.ingestScan(listOf(sms("JD-CUBANK", "A/c XX1234: outflow of 2000.00 noted towards 829341027731")), newestMillis = 3)
        assertEquals(1, s.needs.filterIsInstance<NeedsItem.Unreadable>().size)
        val reopened = store()
        assertEquals(1, reopened.needs.filterIsInstance<NeedsItem.Unreadable>().size)
        reopened.ingestScan(inbox, newestMillis = 4)
        assertEquals(s.transactions.size, reopened.transactions.size)
    }

    @Test
    fun `teaching all like this reads the waiting messages with the same wording (spec 7)`() {
        val s = store().setUp()
        s.ingestScan(
            listOf(
                sms("JD-CUBANK", "A/c XX1234: outflow of 2000.00 noted towards 829341027731"),
                sms("JD-CUBANK", "A/c XX1234: outflow of 75.50 noted towards 129341027700"),
            ),
            newestMillis = 5,
        )
        val first = s.needs.filterIsInstance<NeedsItem.Unreadable>().first()
        s.learn(first, 200_000, moneyIn = false, allLikeThis = true)
        assertTrue(s.needs.none { it is NeedsItem.Unreadable })
        assertEquals(1, s.templates.size)
        // The next message in that format is read without asking.
        s.ingestScan(listOf(sms("JD-CUBANK", "A/c XX1234: outflow of 10.00 noted towards 229341027799")), newestMillis = 6)
        assertTrue(s.needs.none { it is NeedsItem.Unreadable })
    }

    @Test
    fun `forgetting a template sends its rows back to To review (spec 7)`() {
        val s = store().setUp()
        s.ingestScan(listOf(sms("JD-CUBANK", "A/c XX1234: outflow of 2000.00 noted towards 829341027731")), newestMillis = 7)
        s.learn(s.needs.filterIsInstance<NeedsItem.Unreadable>().first(), 200_000, moneyIn = false, allLikeThis = true)
        val key = s.templates.keys.single()
        s.forgetTemplate(key)
        assertTrue(s.templates.isEmpty())
        assertEquals(1, s.needs.filterIsInstance<NeedsItem.Unreadable>().size)
        assertFalse(s.transactions.any { it.amountPaise == 200_000L && it.accountKey == "CUBANK|1234" })
    }

    @Test
    fun `a new account at a known bank is asked once, then its message is read (spec 4)`() {
        val s = store().setUp()
        val newMsg = sms("HDFCBK", "Rs 100 debited from a/c XX5555")
        s.ingestScan(listOf(newMsg), newestMillis = 8)
        val ask = s.needs.filterIsInstance<NeedsItem.NewAccount>().single()
        s.answerNewAccount(ask, mine = true)
        assertNotNull(s.rescanFromMillis)
        s.ingestScan(listOf(newMsg), newestMillis = 9) // the rescan the app runs next
        assertTrue(s.transactions.any { it.accountKey == "HDFCBK|5555" })
    }

    @Test
    fun `a bank SMS confirms a manual entry instead of adding a second row (spec 8)`() {
        val s = store().setUp()
        s.addManual(45_000, TxKind.Spend, "food", "Lunch")
        val count = s.transactions.size
        s.ingestScan(listOf(sms("HDFCBK", "Rs 450 debited from a/c XX4821", now.plusMinutes(30))), newestMillis = 10)
        assertEquals(count, s.transactions.size)
        val merged = s.transactions.first { it.payee == "Lunch" }
        assertEquals("HDFCBK|4821", merged.accountKey)
        assertEquals("food", merged.categoryId)
    }

    @Test
    fun `a merchant SMS names the bank payment and adds no money (spec 8)`() {
        val s = store().setUp()
        s.ingestScan(listOf(sms("JD-SWIGGY", "Rs 250 paid for your order #4512", now.minusHours(3).plusMinutes(2))), newestMillis = 11)
        assertEquals(3, s.transactions.size)
        assertEquals("SWIGGY", s.transactions.first { it.amountPaise == 25_000L }.payee)
    }

    @Test
    fun `money from someone who owes you closes the debt, by VPA (spec 9)`() {
        val s = store().setUp()
        s.ingestScan(listOf(sms("HDFCBK", "Rs 1000 debited from a/c XX4821 to rahul.s@okaxis")), newestMillis = 12)
        s.setKind(s.transactions.first { it.payee == "rahul.s@okaxis" }, TxKind.Lent)
        s.ingestScan(listOf(sms("HDFCBK", "Your a/c XX4821 is credited with Rs 400 by rahul.s@okaxis", now)), newestMillis = 13)
        assertEquals(60_000L, s.lentBook.outstanding("rahul.s@okaxis"))
    }

    @Test
    fun `manual mode stops capture but keeps every row (spec 2)`() {
        val s = store().setUp()
        s.turnOffAutomatic()
        assertEquals(0, s.ingestScan(listOf(sms("HDFCBK", "Rs 99 debited from a/c XX4821")), newestMillis = 14))
        assertEquals(3, s.transactions.size)
    }
}
