package com.smsledger.core.ledger

import java.time.LocalDate
import java.time.YearMonth

/** Transaction types from spec §9. */
enum class EntryKind { Debit, Credit, Transfer, Lent }

/** One ledger row, as the rules see it. UI and storage models map to this. */
data class LedgerEntry(
    val id: Long,
    val payee: String,
    val amountPaise: Long,
    val kind: EntryKind,
    val date: LocalDate,
    val categoryId: String? = null,
    /** User marked it "not a transaction" (§10): kept for history, never counted. */
    val excluded: Boolean = false,
)

/** Totals from spec §9 and §12. */
object LedgerMath {

    /**
     * Counts toward the daily limit: debits only (never credits, self-transfers or lent), not in a
     * "not daily" category. Uncategorised debits count until tagged (§12 default).
     */
    fun countsTowardDailyLimit(entry: LedgerEntry, notDailyCategories: Set<String>): Boolean =
        !entry.excluded && entry.kind == EntryKind.Debit && (entry.categoryId == null || entry.categoryId !in notDailyCategories)

    fun spentOn(entries: List<LedgerEntry>, day: LocalDate, notDailyCategories: Set<String>): Long =
        entries.filter { it.date == day && countsTowardDailyLimit(it, notDailyCategories) }.sumOf { it.amountPaise }

    /** Month view (§12): total spent in the calendar month, every debit category included, no target. */
    fun spentInMonth(entries: List<LedgerEntry>, month: YearMonth): Long =
        entries.filter { !it.excluded && it.kind == EntryKind.Debit && YearMonth.from(it.date) == month }.sumOf { it.amountPaise }
}

/** Categories (§10, §11): "Just this one" / "All like this", and sticky payee rules. */
object CategoryRules {

    /** Same payee regardless of case and surrounding spaces ("MYNTRA" = "Myntra "). */
    fun payeeKey(payee: String): String = payee.trim().lowercase()

    private fun sameDebitPayee(a: LedgerEntry, b: LedgerEntry) =
        a.kind == EntryKind.Debit && payeeKey(a.payee) == payeeKey(b.payee)

    fun apply(entries: List<LedgerEntry>, target: LedgerEntry, categoryId: String, allLikeThis: Boolean): List<LedgerEntry> =
        entries.map { entry ->
            val hit = entry.id == target.id || (allLikeThis && sameDebitPayee(entry, target))
            if (hit) entry.copy(categoryId = categoryId) else entry
        }

    fun similarCount(entries: List<LedgerEntry>, target: LedgerEntry): Int = entries.count { sameDebitPayee(it, target) }

    /** Pre-tag for a new debit: the category the user set for this payee before, if any. */
    fun categoryFor(payee: String, rules: Map<String, String>): String? = rules[payeeKey(payee)]
}
