package com.smsledger.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** One SMS in, one decision out. Synthetic messages only. */
class SmsIngestTest {

    private val t0 = LocalDateTime.of(2026, 10, 24, 12, 0)
    private val hdfc = Account("HDFCBK", "4821")
    private val hdfc2 = Account("HDFCBK", "9821")
    private val cub = Account("CUBANK", "1234")
    private val amazon = Account("AMAZONPAY", Account.WALLET)

    private val ctx = IngestContext(
        senders = mapOf("HDFCBK" to SenderKind.Bank, "CUBANK" to SenderKind.Bank, "AMAZONPAY" to SenderKind.Wallet, "SWIGGY" to SenderKind.NonBank),
        mine = listOf(hdfc, hdfc2, cub, amazon),
        notMine = emptyList(),
    )

    private fun sms(sender: String, body: String, minutes: Long = 0) = RawSms("k$minutes$sender", sender, body, t0.plusMinutes(minutes))

    @Test
    fun `a bank debit on a known account is added`() {
        val out = SmsIngest.process(sms("AD-HDFCBK-S", "Rs.250.00 debited from a/c XX4821 to VPA swiggy@icici. UPI Ref 629114830271"), ctx)
        out as Outcome.Add
        assertEquals(25_000L, out.amountPaise)
        assertEquals(Direction.Debit, out.direction)
        assertEquals(hdfc, out.account)
        assertEquals("swiggy@icici", out.counterparty)
        assertEquals("629114830271", out.upiRef)
    }

    @Test
    fun `personal messages and ignored senders are ignored`() {
        assertEquals(Outcome.Ignore, SmsIngest.process(sms("+919876543210", "Paid you Rs 500 for dinner"), ctx))
        assertEquals(Outcome.Ignore, SmsIngest.process(sms("VM-PROMOS", "Sale! Flat 50% off"), ctx.copy(senders = ctx.senders + ("PROMOS" to SenderKind.Ignored))))
    }

    @Test
    fun `an account marked not mine is never counted (spec 4)`() {
        val out = SmsIngest.process(sms("HDFCBK", "Rs 999 debited from a/c XX4821"), ctx.copy(mine = listOf(cub), notMine = listOf(hdfc)))
        assertEquals(Outcome.Ignore, out)
    }

    @Test
    fun `three digits that fit two accounts ask the user (spec 4)`() {
        val out = SmsIngest.process(sms("HDFCBK", "Rs 760 debited from A/c *821 at ZOMATO"), ctx)
        out as Outcome.AskAccount
        assertEquals(listOf(hdfc, hdfc2), out.candidates)
        assertEquals(76_000L, out.amountPaise)
    }

    @Test
    fun `unknown digits at a known bank raise a new account question`() {
        val out = SmsIngest.process(sms("HDFCBK", "Rs 100 debited from a/c XX5555"), ctx)
        assertEquals(Outcome.NewAccount(Account("HDFCBK", "5555")), out)
    }

    @Test
    fun `not-a-transaction messages are skipped, unreadable ones are surfaced (spec 6, 7)`() {
        assertEquals(Outcome.Skip(SkipReason.Otp), SmsIngest.process(sms("HDFCBK", "482113 is your OTP for Rs 2,000 on card XX4821"), ctx))
        val out = SmsIngest.process(sms("CUBANK", "A/c XX1234: outflow of 2000.00 noted towards 829341027731"), ctx)
        assertEquals(Outcome.Unreadable(cub), out)
    }

    @Test
    fun `a learned template reads the next message with the same wording (spec 7)`() {
        val taught = Templatizer.template("A/c XX1234: outflow of 2000.00 noted towards 829341027731")
        val index = taught.numbers.indexOfFirst { it.text == "2000.00" }
        val learned = ctx.copy(templates = mapOf(taught.key to LearnedTemplate(taught.key, isTransaction = true, moneyIn = false, amountIndex = index)))
        val out = SmsIngest.process(sms("CUBANK", "A/c XX1234: outflow of 75.50 noted towards 129341027700"), learned)
        out as Outcome.Add
        assertEquals(7_550L, out.amountPaise)
        assertEquals(cub, out.account)
    }

    @Test
    fun `a template learned as not a transaction skips future matches`() {
        val t = Templatizer.template("A/c XX1234: outflow of 2000.00 noted towards 829341027731")
        val learned = ctx.copy(templates = mapOf(t.key to LearnedTemplate(t.key, isTransaction = false, moneyIn = false, amountIndex = -1)))
        assertEquals(Outcome.Skip(null), SmsIngest.process(sms("CUBANK", "A/c XX1234: outflow of 99.00 noted towards 829341027799"), learned))
    }

    @Test
    fun `merchant SMS only label a matching bank payment, never add money (spec 8)`() {
        val withTx = ctx.copy(recent = listOf(Candidate(7, 25_000, Direction.Debit, t0, hdfc.key)))
        assertEquals(Outcome.Label(7, "SWIGGY"), SmsIngest.process(sms("SWIGGY", "Rs 250 paid for your order #4512", minutes = 4), withTx))
        assertEquals(Outcome.Ignore, SmsIngest.process(sms("SWIGGY", "Rs 999 paid for your order #4513", minutes = 4), withTx))
    }

    @Test
    fun `wallet balance spends count, wallet UPI payments only label (spec 5)`() {
        val spend = SmsIngest.process(sms("AMAZONPAY", "Rs 68 paid from your Amazon Pay balance at Third Wave Coffee"), ctx)
        assertEquals(amazon, (spend as Outcome.Add).account)
        val withTx = ctx.copy(recent = listOf(Candidate(3, 12_000, Direction.Debit, t0, hdfc.key)))
        val label = SmsIngest.process(sms("AMAZONPAY", "Paid Rs 120 to Chai Point using Amazon Pay UPI from your HDFC Bank a/c", minutes = 1), withTx)
        assertEquals(Outcome.Label(3, "Chai Point"), label)
    }

    @Test
    fun `a bank to wallet top-up is offered as a possible self-transfer (logic 12)`() {
        val withDebit = ctx.copy(recent = listOf(Candidate(5, 50_000, Direction.Debit, t0, cub.key)))
        val out = SmsIngest.process(sms("AMAZONPAY", "Rs 500 added to your Amazon Pay balance", minutes = 2), withDebit) as Outcome.Add
        assertEquals(Direction.Credit, out.direction)
        assertEquals(Pairing(5, Confidence.Possible), out.transfer)
    }

    @Test
    fun `a transfer between own accounts with one UPI reference links without asking`() {
        val withDebit = ctx.copy(recent = listOf(Candidate(5, 50_000, Direction.Debit, t0, cub.key, "629114830271")))
        val out = SmsIngest.process(sms("HDFCBK", "Rs 500 credited to a/c XX4821. UPI Ref 629114830271", minutes = 1), withDebit) as Outcome.Add
        assertEquals(Pairing(5, Confidence.Auto), out.transfer)
    }

    @Test
    fun `offers, reminders and balance notices from a bank create nothing (logic 5)`() {
        fun out(body: String) = SmsIngest.process(sms("HDFCBK", body), ctx)
        assertEquals(Outcome.Skip(SkipReason.Promo), out("Pre-approved loan of Rs 5,00,000 on your a/c XX4821. Apply now"))
        assertEquals(Outcome.Skip(SkipReason.NotCompleted), out("Rs 5,000 will be debited from a/c XX4821 on 05-11-26"))
        assertEquals(Outcome.Skip(SkipReason.BalanceOnly), out("Avl bal in a/c XX4821 is Rs 12,500.00"))
    }

    @Test
    fun `a message naming no account of the user's is dropped unless it is transaction-shaped (logic 8_0)`() {
        assertEquals(Outcome.Skip(SkipReason.NoAccount), SmsIngest.process(sms("HDFCBK", "Your statement for October is ready. Total Rs 4,200."), ctx))
        assertEquals(Outcome.Skip(SkipReason.NoAccount), SmsIngest.process(sms("HDFCBK", "Statement for a/c XX7777 is ready. Total Rs 4,200."), ctx))
    }

    @Test
    fun `no digits and one account at the bank counts only with a reference or balance, else the user is asked`() {
        val sure = SmsIngest.process(sms("CUBANK", "Rs 90 debited to tea@upi. UPI Ref 629114830271"), ctx)
        assertEquals(cub, (sure as Outcome.Add).account)
        val unsure = SmsIngest.process(sms("CUBANK", "Rs 90 debited to tea@upi"), ctx) as Outcome.AskAccount
        assertEquals(listOf(cub), unsure.candidates)
        assertEquals(null, unsure.digits)
        val twoAtBank = SmsIngest.process(sms("HDFCBK", "Rs 90 debited to tea@upi. UPI Ref 629114830271"), ctx) as Outcome.AskAccount
        assertEquals(listOf(hdfc, hdfc2), twoAtBank.candidates)
    }

    @Test
    fun `a longer account number ending in known digits is the same account (logic 8_1)`() {
        val out = SmsIngest.process(sms("HDFCBK", "Rs 40 debited from A/c No. 501000124821 to tea@upi"), ctx)
        assertEquals(hdfc, (out as Outcome.Add).account)
    }

    @Test
    fun `a second alert for the same payment is not counted twice (logic 11_2)`() {
        val counted = ctx.copy(recent = listOf(Candidate(1, 25_000, Direction.Debit, t0, hdfc.key, "629114830271")))
        val again = SmsIngest.process(sms("HDFCBK", "Rs 250 debited from a/c XX4821. UPI Ref 629114830271", minutes = 90), counted)
        assertEquals(Outcome.Skip(SkipReason.Duplicate), again)
    }

    @Test
    fun `a learned template also reads slightly different wording, but not a different message (logic 9)`() {
        val taught = Templatizer.template("A/c XX1234: outflow of 2000.00 noted towards 829341027731 at the branch counter today")
        val index = taught.numbers.indexOfFirst { it.text == "2000.00" }
        val learned = ctx.copy(templates = mapOf(taught.key to LearnedTemplate(taught.key, isTransaction = true, moneyIn = false, amountIndex = index)))
        val close = SmsIngest.process(sms("CUBANK", "A/c XX1234: outflow of 75.50 noted towards 129341027700 at the branch counter now"), learned)
        assertEquals(7_550L, (close as Outcome.Add).amountPaise)
        val different = SmsIngest.process(sms("CUBANK", "A/c XX1234: cheque book of 25 leaves dispatched, ref 129341027700"), learned)
        assertEquals(Outcome.Unreadable(cub), different)
    }

    @Test
    fun `an unconfirmed wallet is only asked about for a real wallet payment`() {
        val fresh = ctx.copy(mine = listOf(hdfc))
        assertEquals(Outcome.NewAccount(amazon), SmsIngest.process(sms("AMAZONPAY", "Rs 68 paid from your Amazon Pay balance"), fresh))
        assertEquals(Outcome.Skip(SkipReason.NoAccount), SmsIngest.process(sms("AMAZONPAY", "Your Amazon Pay KYC is complete"), fresh))
    }

    @Test
    fun `a bank SMS matching a manual entry merges with it (spec 8)`() {
        val withManual = ctx.copy(manual = listOf(Candidate(11, 25_000, Direction.Debit, t0.minusHours(1), "Manual")))
        val out = SmsIngest.process(sms("HDFCBK", "Rs 250 debited from a/c XX4821"), withManual) as Outcome.Add
        assertEquals(11L, out.mergeWithManual)
    }

    @Test
    fun `two identical bank debits are both added, never merged (spec 8)`() {
        val first = SmsIngest.process(sms("HDFCBK", "Rs 20 debited from a/c XX4821", minutes = 0), ctx)
        val withFirst = ctx.copy(recent = listOf(Candidate(1, 2_000, Direction.Debit, t0, hdfc.key)))
        val second = SmsIngest.process(sms("HDFCBK", "Rs 20 debited from a/c XX4821", minutes = 1), withFirst)
        assertTrue(first is Outcome.Add && second is Outcome.Add)
    }

    @Test
    fun `discovery finds banks, their accounts and wallets, and skips everything else (spec 3, 4)`() {
        val found = SmsDiscovery.discover(
            listOf(
                sms("AD-HDFCBK-S", "Rs.500 debited from a/c XX4821 on 22-10-26", 0),
                sms("VM-HDFCBK", "INR 2,000 credited to a/c XX9821", 5),
                sms("JD-AIRTEL", "Rs 299 recharge successful", 6),
                sms("AMAZONPAY", "Rs 68 paid from your Amazon Pay balance", 7),
                sms("+919876543210", "see you at 8", 8),
            ),
        )
        assertEquals(listOf(hdfc, hdfc2, amazon), found.accounts.map { it.account })
        assertEquals(SenderKind.NonBank, found.senders["AIRTEL"])
        assertEquals(false, found.senders.containsKey("+919876543210"))
    }
}
