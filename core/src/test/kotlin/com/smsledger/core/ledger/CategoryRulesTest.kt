package com.smsledger.core.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CategoryRulesTest {

    private val day = LocalDate.of(2026, 10, 24)
    private val myntra1 = LedgerEntry(1, "MYNTRA", 129_900, EntryKind.Debit, day)
    private val myntra2 = LedgerEntry(2, "Myntra ", 49_900, EntryKind.Debit, day.minusDays(3))
    private val swiggy = LedgerEntry(3, "Swiggy", 41_800, EntryKind.Debit, day)
    private val myntraRefund = LedgerEntry(4, "MYNTRA", 49_900, EntryKind.Credit, day)

    @Test
    fun `just this one tags only the chosen row`() {
        val result = CategoryRules.apply(listOf(myntra1, myntra2, swiggy), target = myntra1, categoryId = "shopping", allLikeThis = false)
        assertEquals("shopping", result.first { it.id == 1L }.categoryId)
        assertNull(result.first { it.id == 2L }.categoryId)
    }

    @Test
    fun `all like this tags every debit from the same payee, ignoring case and spaces`() {
        val result = CategoryRules.apply(listOf(myntra1, myntra2, swiggy, myntraRefund), target = myntra1, categoryId = "shopping", allLikeThis = true)
        assertEquals(listOf("shopping", "shopping", null, null), result.map { it.categoryId })
    }

    @Test
    fun `a payee rule pre-tags new debits from that payee`() {
        val rules = mapOf(CategoryRules.payeeKey("MYNTRA") to "shopping")
        assertEquals("shopping", CategoryRules.categoryFor("myntra", rules))
        assertNull(CategoryRules.categoryFor("Swiggy", rules))
    }

    @Test
    fun `counting similar rows uses the same payee matching`() {
        assertEquals(2, CategoryRules.similarCount(listOf(myntra1, myntra2, swiggy, myntraRefund), myntra1))
    }
}
