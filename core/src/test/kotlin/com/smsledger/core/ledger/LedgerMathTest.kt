package com.smsledger.core.ledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerMathTest {

    private val today = LocalDate.of(2026, 10, 24)
    private val notDaily = setOf("rent", "bills")

    private fun debit(amount: Long, date: LocalDate = today, category: String? = null, excluded: Boolean = false) =
        LedgerEntry(id = amount, payee = "X", amountPaise = amount, kind = EntryKind.Debit, date = date, categoryId = category, excluded = excluded)

    @Test
    fun `daily spend counts today's debits, including uncategorised ones`() {
        val entries = listOf(debit(10_000, category = "food"), debit(5_000, category = null))
        assertEquals(15_000L, LedgerMath.spentOn(entries, today, notDaily))
    }

    @Test
    fun `daily spend ignores not-daily categories`() {
        val entries = listOf(debit(10_000, category = "food"), debit(1_800_000, category = "rent"))
        assertEquals(10_000L, LedgerMath.spentOn(entries, today, notDaily))
    }

    @Test
    fun `daily spend never counts credits, self-transfers or lent`() {
        val entries = listOf(
            debit(10_000),
            LedgerEntry(2, "Salary", 8_500_000, EntryKind.Credit, today),
            LedgerEntry(3, "To wallet", 50_000, EntryKind.Transfer, today),
            LedgerEntry(4, "Rahul", 100_000, EntryKind.Lent, today),
        )
        assertEquals(10_000L, LedgerMath.spentOn(entries, today, notDaily))
    }

    @Test
    fun `daily spend ignores other days and rows marked not a transaction`() {
        val entries = listOf(debit(10_000), debit(7_000, date = today.minusDays(1)), debit(3_000, excluded = true))
        assertEquals(10_000L, LedgerMath.spentOn(entries, today, notDaily))
    }

    @Test
    fun `month total is every debit in that calendar month, not-daily included`() {
        val entries = listOf(
            debit(10_000, date = LocalDate.of(2026, 10, 1)),
            debit(1_800_000, date = LocalDate.of(2026, 10, 2), category = "rent"),
            debit(99_900, date = LocalDate.of(2026, 9, 30)),
            debit(5_000, date = LocalDate.of(2026, 10, 3), excluded = true),
            LedgerEntry(9, "Rahul", 100_000, EntryKind.Lent, LocalDate.of(2026, 10, 4)),
        )
        assertEquals(1_810_000L, LedgerMath.spentInMonth(entries, YearMonth.of(2026, 10)))
    }
}
