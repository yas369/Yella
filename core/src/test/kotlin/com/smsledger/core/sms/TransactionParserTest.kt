package com.smsledger.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic messages in common Indian bank formats. Accuracy on real SMS is unmeasured until the corpus run (T2–T4). */
class TransactionParserTest {

    private fun parsed(body: String) = TransactionParser.parse(body) as ParseResult.Parsed

    private fun skipped(body: String) = (TransactionParser.parse(body) as ParseResult.Skipped).reason

    @Test
    fun `UPI debit gives amount, direction, digits, reference and VPA (spec 6)`() {
        val p = parsed("Rs.250.00 debited from a/c XX1234 on 22-10-26 to VPA swiggy@icici. UPI Ref 629114830271. Not you? Call 18001234")
        assertEquals(25_000L, p.amountPaise)
        assertEquals(Direction.Debit, p.direction)
        assertEquals("1234", p.maskedDigits)
        assertEquals("629114830271", p.upiRef)
        assertEquals("swiggy@icici", p.counterparty)
    }

    @Test
    fun `card spend gives the merchant`() {
        val p = parsed("INR 1,450.00 spent on your HDFC Bank Card ending 4821 at RELIANCE SMART on 22-10-26. Avl bal INR 23,180.55")
        assertEquals(145_000L, p.amountPaise)
        assertEquals(Direction.Debit, p.direction)
        assertEquals("4821", p.maskedDigits)
        assertEquals("RELIANCE SMART", p.counterparty)
    }

    @Test
    fun `the balance is never taken as the amount`() {
        val p = parsed("Avl Bal Rs 9,000.00. Rs 120 debited from A/c XX1234 for UPI txn.")
        assertEquals(12_000L, p.amountPaise)
    }

    @Test
    fun `credits are read as credits, with the payer`() {
        val p = parsed("Your A/c XX9876 is credited with INR 400.00 by UPI from 9876543210. Ref 829341027731")
        assertEquals(Direction.Credit, p.direction)
        assertEquals(40_000L, p.amountPaise)
        assertEquals("9876543210", p.counterparty)
    }

    @Test
    fun `debited from you and credited to someone else is a debit`() {
        val p = parsed("Rs 1000 debited from A/c XX1234 and credited to rahul.s@okaxis UPI Ref 712345678901")
        assertEquals(Direction.Debit, p.direction)
        assertEquals("rahul.s@okaxis", p.counterparty)
    }

    @Test
    fun `OTPs are skipped (spec 6)`() {
        assertEquals(SkipReason.Otp, skipped("482113 is your OTP for a txn of Rs 2,000 at AMAZON on card XX4821. Do not share."))
    }

    @Test
    fun `mandate set-ups are skipped, but a mandate payment that went through counts`() {
        assertEquals(SkipReason.NotCompleted, skipped("UPI mandate for Rs 499 created for NETFLIX on a/c XX1234. Valid till 2027."))
        assertEquals(Direction.Debit, parsed("Rs 499 debited from a/c XX1234 for UPI mandate NETFLIX. Ref 612345678901").direction)
    }

    @Test
    fun `future debits are skipped`() {
        assertEquals(SkipReason.NotCompleted, skipped("Rs 5,000 will be debited from a/c XX1234 on 05-11-26 towards EMI."))
        assertEquals(SkipReason.NotCompleted, skipped("Your SIP of Rs 2,000 is scheduled on 01-11-26 from a/c XX1234."))
    }

    @Test
    fun `failed and declined payments are skipped`() {
        assertEquals(SkipReason.NotCompleted, skipped("Txn of Rs 650 on card XX4821 at ZOMATO declined due to insufficient funds."))
        assertEquals(SkipReason.NotCompleted, skipped("UPI payment of Rs 300 from a/c XX1234 failed. Amount not debited."))
    }

    @Test
    fun `balance-only messages are skipped`() {
        assertEquals(SkipReason.BalanceOnly, skipped("Avl bal in a/c XX1234 is Rs 12,500.00 as on 24-10-26."))
    }

    @Test
    fun `money requests are skipped`() {
        assertEquals(SkipReason.NotCompleted, skipped("abc@ybl has requested Rs 500 from you on UPI. Approve in your app."))
    }

    @Test
    fun `unfamiliar wording is unreadable, never dropped (spec 7)`() {
        assertTrue(TransactionParser.parse("A/c XX1234: outflow of 2000.00 noted towards 829341027731 -CUB") is ParseResult.Unreadable)
    }

    @Test
    fun `a credit limit is never the amount, wherever it sits (logic 6_1)`() {
        val p = parsed("INR 1,450.00 spent on Card XX4821 at RELIANCE SMART. Avl Limit: INR 57,888.30")
        assertEquals(145_000L, p.amountPaise)
        val q = parsed("Available credit limit is Rs 80,000. Rs 300 spent on Card XX4821 at CAFE")
        assertEquals(30_000L, q.amountPaise)
    }

    @Test
    fun `the amount nearest the verb wins over an earlier figure`() {
        val p = parsed("Order worth Rs 999 placed. Rs 949.00 debited from a/c XX1234 to shop@upi")
        assertEquals(94_900L, p.amountPaise)
    }

    @Test
    fun `offers and reminders with an amount are not transactions (logic 5)`() {
        assertEquals(SkipReason.Promo, skipped("Get a pre-approved loan of Rs 5,00,000 on a/c XX1234. Apply now."))
        assertEquals(SkipReason.Promo, skipped("Upgrade your card XX4821 limit to Rs 2L today. T&C apply."))
        assertEquals(SkipReason.NotCompleted, skipped("Payment of Rs 4,200 for card XX4821 is due on 05-11-26. Minimum amount due Rs 210."))
        assertEquals(SkipReason.NotCompleted, skipped("Rs 15,000 is blocked in your a/c XX1234 for IPO application (ASBA)."))
        assertEquals(SkipReason.NotCompleted, skipped("Convert your spend of Rs 12,000 on card XX4821 to SmartEMI."))
        assertEquals(SkipReason.NotCompleted, skipped("You have received a voucher worth Rs 500 as a reward."))
    }

    @Test
    fun `a link or the word cashback does not reject a real payment (logic 5)`() {
        val p = parsed("Rs 300 debited from a/c XX1234 to cafe@upi. Cashback up to Rs 50 on next spend: https://bank.example/x")
        assertEquals(30_000L, p.amountPaise)
        assertEquals(Direction.Credit, parsed("Cashback of Rs 25 credited to a/c XX1234").direction)
    }

    @Test
    fun `long account numbers are read, and the year of a date is not an account (logic 6_3)`() {
        assertEquals("501000124821", Patterns.maskedDigits("Rs 10 debited from A/c No. 501000124821"))
        assertEquals("123", Patterns.maskedDigits("Rs 10 debited from a/c XX0123"))
        assertNull(Patterns.maskedDigits("Rs 10 debited on card 12/10/2026"))
        assertTrue(Patterns.compatible("8123", "123"))
        assertTrue(!Patterns.compatible("8123", "4123"))
    }

    @Test
    fun `no reference or counterparty is fine`() {
        val p = parsed("Rs 200 withdrawn at ATM from a/c XX1234 on 24-10-26")
        assertNull(p.upiRef)
        assertEquals(Direction.Debit, p.direction)
    }
}
