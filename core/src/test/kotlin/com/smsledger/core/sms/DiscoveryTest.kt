package com.smsledger.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoveryTest {

    @Test
    fun `operator prefixes and suffixes are stripped to the brand (spec 3)`() {
        assertEquals("CUBANK", SenderNormalizer.brand("AD-CUBANK-S"))
        assertEquals("HDFCBK", SenderNormalizer.brand("VM-HDFCBK"))
        assertEquals("HDFCBK", SenderNormalizer.brand("jd-hdfcbk-t"))
        assertEquals("SBIUPI", SenderNormalizer.brand("SBIUPI"))
    }

    @Test
    fun `phone numbers are people, not brands`() {
        assertNull(SenderNormalizer.brand("+919876543210"))
        assertNull(SenderNormalizer.brand("9876543210"))
    }

    @Test
    fun `masked digits come in several shapes (spec 4)`() {
        assertEquals("1234", Patterns.maskedDigits("Your a/c XX1234 is debited for INR 500.00"))
        assertEquals("234", Patterns.maskedDigits("Rs.250 debited from A/c *234 on 22-10-26"))
        assertEquals("1234", Patterns.maskedDigits("Rs 99 spent from A/c 1234 at AMAZON"))
        assertEquals("4821", Patterns.maskedDigits("INR 1,450.00 spent on your HDFC Bank Card ending 4821"))
        assertNull(Patterns.maskedDigits("Your OTP is 482113. Do not share."))
    }

    @Test
    fun `a sender is a bank only when a message has masked digits and an amount (spec 3)`() {
        val kind = SenderClassifier.classify("HDFCBK", listOf("Rs.500.00 debited from a/c XX4821 on 22-10-26 to VPA abc@ybl"))
        assertEquals(SenderKind.Bank, kind)
    }

    @Test
    fun `money words alone never make a bank`() {
        val telecom = listOf("Rs 299 recharge successful. Your plan is credited with 2GB data.")
        val loanApp = listOf("Congrats! Loan of Rs 50,000 credited instantly. Apply now.")
        assertEquals(SenderKind.NonBank, SenderClassifier.classify("AIRTEL", telecom))
        assertEquals(SenderKind.NonBank, SenderClassifier.classify("KREDIT", loanApp))
    }

    @Test
    fun `wallets are recognised by sender name (spec 5)`() {
        assertEquals(SenderKind.Wallet, SenderClassifier.classify("AMAZONPAY", listOf("Rs 68 paid at Third Wave Coffee")))
        assertEquals(SenderKind.Wallet, SenderClassifier.classify("IPAYTM", listOf("Paid Rs.120 to Chai Point")))
    }

    @Test
    fun `senders that never mention money are ignored`() {
        assertEquals(SenderKind.Ignored, SenderClassifier.classify("SWIGGY", listOf("Your order is on the way!")))
    }

    @Test
    fun `accounts are discovered per bank from masked digits (spec 4)`() {
        val accounts = SenderClassifier.accounts(
            "HDFCBK",
            listOf(
                "Rs.500 debited from a/c XX4821 on 22-10-26",
                "Rs.120 debited from a/c XX4821 on 23-10-26",
                "INR 2,000 credited to a/c XX9821",
            ),
        )
        assertEquals(listOf("4821", "9821"), accounts)
    }
}
