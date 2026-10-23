package com.smsledger.core.ledger

import java.time.LocalDate

enum class LentEventKind { Lent, Repaid, WrittenOff }

data class LentEvent(val kind: LentEventKind, val amountPaise: Long, val date: LocalDate)

/** Everything lent to and repaid by one person since their last write-off. */
data class Debt(val person: String, val events: List<LentEvent>) {
    val lentPaise: Long get() = events.filter { it.kind == LentEventKind.Lent }.sumOf { it.amountPaise }
    val repaidPaise: Long get() = events.filter { it.kind == LentEventKind.Repaid }.sumOf { it.amountPaise }
    val writtenOff: Boolean get() = events.lastOrNull()?.kind == LentEventKind.WrittenOff
    val outstandingPaise: Long get() = if (writtenOff) 0 else (lentPaise - repaidPaise).coerceAtLeast(0)
    val since: LocalDate? get() = events.firstOrNull { it.kind == LentEventKind.Lent }?.date
}

/**
 * "Owed to you" (spec §9 Lent): set by the user, tracked per person (VPA or phone in the real app),
 * repayments close the debt instead of counting as income, partial repayments reduce it, and a
 * write-off closes it so it no longer appears in reminders. Immutable: every change returns a new book.
 */
data class LentBook(private val debts: Map<String, Debt> = emptyMap()) {

    private fun key(person: String) = CategoryRules.payeeKey(person)

    fun debt(person: String): Debt? = debts[key(person)]

    fun outstanding(person: String): Long = debt(person)?.outstandingPaise ?: 0

    /** People who still owe something, largest first. */
    val owing: List<Debt> get() = debts.values.filter { it.outstandingPaise > 0 }.sortedByDescending { it.outstandingPaise }

    val totalOutstanding: Long get() = owing.sumOf { it.outstandingPaise }

    fun lend(person: String, amountPaise: Long, date: LocalDate): LentBook {
        val existing = debt(person)?.takeUnless { it.writtenOff }
        val events = (existing?.events ?: emptyList()) + LentEvent(LentEventKind.Lent, amountPaise, date)
        return copy(debts = debts + (key(person) to Debt(existing?.person ?: person.trim(), events)))
    }

    /** Undo for "mark as lent": removes one lent event of that amount. */
    fun cancelLend(person: String, amountPaise: Long): LentBook {
        val existing = debt(person) ?: return this
        val index = existing.events.indexOfLast { it.kind == LentEventKind.Lent && it.amountPaise == amountPaise }
        if (index < 0) return this
        val events = existing.events.toMutableList().apply { removeAt(index) }
        return copy(debts = if (events.none { it.kind == LentEventKind.Lent }) debts - key(person) else debts + (key(person) to existing.copy(events = events)))
    }

    /**
     * A credit from [person]. Up to the outstanding amount closes the debt; anything beyond it is
     * ordinary income. Returns the new book and the income part.
     */
    fun repay(person: String, amountPaise: Long, date: LocalDate): Pair<LentBook, Long> {
        val existing = debt(person)
        val owed = existing?.outstandingPaise ?: 0
        if (existing == null || owed == 0L) return this to amountPaise
        val applied = minOf(owed, amountPaise)
        val updated = existing.copy(events = existing.events + LentEvent(LentEventKind.Repaid, applied, date))
        return copy(debts = debts + (key(person) to updated)) to (amountPaise - applied)
    }

    fun writeOff(person: String, date: LocalDate): LentBook {
        val existing = debt(person) ?: return this
        val updated = existing.copy(events = existing.events + LentEvent(LentEventKind.WrittenOff, existing.outstandingPaise, date))
        return copy(debts = debts + (key(person) to updated))
    }
}
