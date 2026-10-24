package com.smsledger.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateAndWalletTest {

    @Test
    fun `the spec example becomes the spec template (spec 7)`() {
        assertEquals(
            "INR {AMT} spent from your account ending {DIGITS}",
            Templatizer.template("INR 500.00 spent from your account ending 1234").key,
        )
    }

    @Test
    fun `messages that differ only in variable parts share a template`() {
        val a = Templatizer.template("A/c XX1234: outflow of 2000.00 noted towards 829341027731 on 22-10-26 -CUB")
        val b = Templatizer.template("A/c XX5678: outflow of 75.50 noted towards 129341027700 on 01-11-26 -CUB")
        assertEquals(a.key, b.key)
    }

    @Test
    fun `the key is the full template, not a prefix`() {
        val a = Templatizer.template("A/c XX1234: outflow of 2000.00 noted")
        val b = Templatizer.template("A/c XX1234: outflow of 2000.00 noted, reversed")
        assertNotEquals(a.key, b.key)
    }

    @Test
    fun `a learned answer reads the amount from the same number position`() {
        val taught = Templatizer.template("A/c XX1234: outflow of 2000.00 noted towards 829341027731 -CUB")
        val index = taught.numbers.indexOfFirst { it.text == "2000.00" }
        val next = Templatizer.template("A/c XX5678: outflow of 75.50 noted towards 129341027700 -CUB")
        assertEquals(7_550L, Templatizer.amountAt(next, index))
    }

    @Test
    fun `paid from wallet balance counts, paid through the wallet's UPI is a label (spec 5)`() {
        assertEquals(WalletKind.BalanceSpend, WalletRules.classify("Rs 68 paid from your Amazon Pay balance at Third Wave Coffee"))
        assertEquals(WalletKind.UpiLabel, WalletRules.classify("Paid Rs 120 to Chai Point using Paytm UPI from your HDFC Bank a/c"))
        assertEquals(WalletKind.TopUp, WalletRules.classify("Rs 500 added to your Amazon Pay balance"))
    }

    @Test
    fun `4 digits match directly, 3 digits only when exactly one account fits (spec 4)`() {
        val accounts = listOf(Account("HDFCBK", "4821"), Account("HDFCBK", "9821"), Account("CUBANK", "1234"))
        assertEquals(AccountMatch.Matched(Account("HDFCBK", "4821")), AccountMatcher.match("HDFCBK", "4821", accounts))
        assertEquals(AccountMatch.Ambiguous(listOf(Account("HDFCBK", "4821"), Account("HDFCBK", "9821"))), AccountMatcher.match("HDFCBK", "821", accounts))
        assertEquals(AccountMatch.Matched(Account("CUBANK", "1234")), AccountMatcher.match("CUBANK", "234", accounts))
        assertEquals(AccountMatch.Unknown, AccountMatcher.match("HDFCBK", "5555", accounts))
    }

    @Test
    fun `unknown digits stay unknown even if another bank has them`() {
        assertNull((AccountMatcher.match("SBIINB", "1234", listOf(Account("CUBANK", "1234"))) as? AccountMatch.Matched))
    }
}
