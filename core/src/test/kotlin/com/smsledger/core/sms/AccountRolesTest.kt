package com.smsledger.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Whose account a number is (logic.md §8.6) and "Same account?" (§8.4). Every message is synthetic. */
class AccountRolesTest {

    private fun own(body: String, direction: Direction?) = AccountRoles.own(body, direction).map { it.digits }

    @Test
    fun `in a debit the account money leaves from is the user's, the one it goes to is not`() {
        assertEquals(listOf("4821"), own("Rs 500 debited from a/c XX4821 and credited to a/c XX7777. Ref 611111111111", Direction.Debit))
        assertEquals(listOf("4821"), own("Rs 500 transferred to A/c XX7777 from A/c XX4821", Direction.Debit))
        assertEquals(listOf("4821"), own("Sent Rs.500 to RAHUL from A/c XX4821 on 24-10-26", Direction.Debit))
    }

    @Test
    fun `in a credit the account money arrives in is the user's, the one it came from is not`() {
        assertEquals(listOf("4821"), own("INR 900 credited to a/c XX4821 from a/c XX7777", Direction.Credit))
        assertEquals(listOf("4821"), own("Rs 900 received from A/c XX7777 in A/c XX4821", Direction.Credit))
    }

    @Test
    fun `your marks the user's account whatever the direction`() {
        assertEquals(listOf("4821"), own("Rs 300 credited to beneficiary a/c XX7777 and debited from your a/c XX4821", Direction.Debit))
        assertEquals(listOf("4821"), own("Payment of Rs 300 to a/c XX7777 made from your a/c XX4821", null))
    }

    @Test
    fun `a beneficiary's account is never the user's`() {
        assertTrue(own("Rs.900.00 credited to a/c XX8888 of your beneficiary. Ref 622222222222", Direction.Credit).isEmpty())
        assertTrue(own("Rs 900 sent to beneficiary A/c XX8888", Direction.Debit).isEmpty())
    }

    @Test
    fun `with no clue the first number is the account the alert is about`() {
        assertEquals(listOf("4821"), own("A/c XX4821 debited by Rs 500 on 24-10-26. Avl bal Rs 9,000", Direction.Debit))
        assertEquals(listOf("4821"), own("A/c XX4821 debited Rs 500; credited to a/c XX7777", Direction.Debit))
        assertEquals(listOf("9005"), own("INR 250.00 spent on ICICI Bank Card XX9005 at CAFE", Direction.Debit))
    }

    @Test
    fun `a number after the word card is a card`() {
        assertEquals(AccountKind.Card, AccountRoles.own("INR 250.00 spent on ICICI Bank Card XX9005 at CAFE", Direction.Debit).single().kind)
        assertEquals(AccountKind.Bank, AccountRoles.own("Rs 500 debited from a/c XX4821", Direction.Debit).single().kind)
    }

    @Test
    fun `accounts whose last digits match are paired, others are not`() {
        val a = Account("HDFCBK", "4821")
        val b = Account("HDFCBN", "4821")
        val c = Account("HDFCBK", "821")
        val d = Account("CUBANK", "1234")
        val wallet = Account("AMAZONPAY", Account.WALLET)
        val pairs = AccountGroups.pairs(listOf(a, b, c, d, wallet)).map { it.key }.toSet()
        assertEquals(setOf(AccountPair(a, b).key, AccountPair(a, c).key, AccountPair(b, c).key), pairs)
        assertEquals(AccountPair(a, b).key, AccountPair(b, a).key)
    }

    @Test
    fun `accounts the user calls the same become one, led by the fullest number`() {
        val at = LocalDateTime.of(2026, 10, 24, 12, 0)
        val short = FoundAccount(Account("HDFCBK", "821"), 10_000, Direction.Debit, at, messages = 9)
        val full = FoundAccount(Account("HDFCBK", "4821"), 20_000, Direction.Debit, at.minusDays(1), messages = 4)
        val other = FoundAccount(Account("CUBANK", "1234"), 5_000, Direction.Debit, at, messages = 6)
        val merged = AccountGroups.merge(listOf(short, full, other), listOf(AccountPair(short.account, full.account)))
        assertEquals(listOf(full.account, other.account), merged.accounts.map { it.account })
        assertEquals(13, merged.accounts.first().messages)
        assertEquals(10_000L, merged.accounts.first().lastAmountPaise) // the latest message of the group
        assertEquals(mapOf(short.account to full.account), merged.aliases)
        // With no answers nothing is merged.
        assertEquals(3, AccountGroups.merge(listOf(short, full, other), emptyList()).accounts.size)
    }
}
