package com.smsledger.data

import com.smsledger.core.sms.RawSms
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

private class BigMemoryDisk : LedgerDisk {
    var data = LedgerData()
    var prefs = Prefs()
    override suspend fun load() = data to prefs
    override fun save(before: LedgerData, after: LedgerData) { data = after }
    override fun savePrefs(prefs: Prefs) { this.prefs = prefs }
    override fun clear() { data = LedgerData(); prefs = Prefs() }
}

/**
 * A first import the size of a real phone's inbox, and messages built to break the readers.
 * Every message is synthetic.
 */
class BigInboxTest {

    private val now = LocalDateTime.of(2026, 10, 24, 18, 0)

    private fun store() = LedgerStore(BigMemoryDisk()) { now }.also { runBlocking { it.load() } }

    /** Two years of mixed SMS: bank alerts across three banks, a wallet, OTPs, offers and people. */
    private fun inbox(count: Int): List<RawSms> = (0 until count).map { i ->
        val at = now.minusMinutes((count - i) * 100L)
        val ref = 600000000000L + i
        val (sender, body) = when (i % 10) {
            0, 1 -> "AD-HDFCBK-S" to "Rs.${100 + i % 900}.00 debited from a/c XX4821 to VPA shop${i % 60}@okaxis on 24-10-26. UPI Ref $ref. Avl bal Rs 12,500.00"
            2 -> "JD-CUBANK" to "Rs ${50 + i % 400} debited from a/c XX1234 at STORE ${i % 40}. Ref $ref"
            3 -> "VM-ICICIB" to "INR ${200 + i % 700}.00 spent on ICICI Bank Card XX9005 at MERCHANT ${i % 80}. Avl Limit: INR 57,888.30"
            4 -> "VM-HDFCBK" to "INR ${1000 + i % 5000}.00 credited to a/c XX4821 from PERSON ${i % 30}. Ref $ref"
            5 -> "AD-HDFCBK-S" to "${100000 + i} is your OTP for a txn of Rs ${i % 900} on card XX4821. Do not share."
            6 -> "JM-AMAZONPAY" to "Paid Rs ${20 + i % 300} to Seller ${i % 25} using Amazon Pay balance. Txn id $ref"
            7 -> "AD-OFFERS" to "Get a pre-approved loan of Rs 5,00,000 today. Apply now: https://bank.example/x"
            8 -> "+9198765432${10 + i % 80}" to "see you at 8?"
            else -> "JD-AIRTEL" to "Rs 299 recharge successful for your number."
        }
        RawSms("k$i", sender, body, at)
    }

    @Test
    fun `a first import of a big inbox finishes quickly`() {
        val s = store()
        val messages = inbox(12_000)
        val t0 = System.nanoTime()
        s.startDiscovery(messages)
        val t1 = System.nanoTime()
        val answers = s.discovery!!.accounts.associate { it.account to true }
        val added = s.confirmAccounts(answers, newestMillis = 1)
        val t2 = System.nanoTime()
        println("BIGINBOX discovery=${(t1 - t0) / 1_000_000}ms import=${(t2 - t1) / 1_000_000}ms added=$added needs=${s.needs.size}")
        assertTrue("added $added", added > 5_000)
        assertTrue("import took ${(t2 - t1) / 1_000_000} ms", (t2 - t1) / 1_000_000 < 5_000)
    }

    @Test
    fun `importing in steps gives the same ledger as importing at once`() {
        val messages = inbox(3_000)
        val whole = store().also { it.startDiscovery(messages); it.confirmAccounts(it.discovery!!.accounts.associate { a -> a.account to true }, 1) }
        val stepped = store().also { it.startDiscovery(messages) }
        val progress = ArrayList<Float>()
        runBlocking { stepped.confirmAccountsInSteps(stepped.discovery!!.accounts.associate { a -> a.account to true }, 1) { progress += it } }
        assertEquals(whole.transactions.map { it.copy(id = 0) }, stepped.transactions.map { it.copy(id = 0) })
        // The same cards are raised; only the example message a "tag in one go" card shows may differ.
        assertEquals(whole.needs.map { it.javaClass.simpleName }.sorted(), stepped.needs.map { it.javaClass.simpleName }.sorted())
        assertTrue(stepped.automatic)
        assertEquals(1f, progress.last())
        assertTrue(progress.size > 5)
    }

    @Test
    fun `messages with absurd numbers never crash discovery or import`() {
        val nasty = listOf(
            "Rs 123456789012345678901234567890 debited from a/c XX4821 to shop@upi",
            "INR 99999999999999999999.99 credited to a/c XX4821",
            "Rs.250 debited from a/c XX4821. Ref 12345678901234567890123456789012345",
            "Rs 1,,,,,,,, debited from a/c XX4821",
            "Rs 0000000000000000000000000001 spent on card XX4821 at SHOP",
            "a/c XX4821 debited by Rs " + "9".repeat(400),
            "x".repeat(5000) + " Rs 10 debited from a/c XX4821",
            "Rs 10 debited from a/c " + "X".repeat(3000) + "4821",
            ("Rs 5 debited from a/c XX4821 to a@b. ").repeat(300),
            "",
            " ",
            "₹",
            "Rs. debited a/c",
        )
        val messages = listOf(RawSms("seed", "AD-HDFCBK-S", "Rs.250.00 debited from a/c XX4821 to VPA shop@okaxis. UPI Ref 629114830271", now.minusDays(2))) +
            nasty.mapIndexed { i, body -> RawSms("n$i", "AD-HDFCBK-S", body, now.minusHours(20L - i)) }
        val s = store()
        s.startDiscovery(messages)
        s.confirmAccounts(s.discovery!!.accounts.associate { it.account to true }, newestMillis = 1)
        // Teaching an unreadable message must cope with any number in it too.
        s.needs.filterIsInstance<com.smsledger.ui.model.NeedsItem.Unreadable>().forEach { s.learn(it, 1_000, moneyIn = false) }
        assertTrue(s.transactions.isNotEmpty())
    }
}
