package com.smsledger.ui.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** UI-facing models. Stored data maps into these; screens never see Room entities. */

enum class TxKind { Spend, Income, Transfer, Lent }

data class Category(val id: String, val name: String, val countsDaily: Boolean = true)

data class Tx(
    val id: Long,
    val payee: String,
    val account: String,
    val at: LocalDateTime,
    val amountPaise: Long,
    val kind: TxKind,
    val categoryId: String? = null,
    val sms: String? = null,
    val note: String = "",
    /** Marked "not a transaction" (§10): stays in history, never counted. */
    val excluded: Boolean = false,
) {
    val date: LocalDate get() = at.toLocalDate()

    /** "9:12 am" */
    val time: String get() = at.format(TimeFormat).lowercase(Locale.ENGLISH)

    /** 0 = today, 1 = yesterday, … */
    val daysAgo: Int get() = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
}

private val TimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** design.md §8 dates: "Today", "Yesterday", then "12 Sep"; the year only when it isn't this year. */
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", Locale.ENGLISH))
}

/** Everything uncertain (§15.2). Each item carries what the ledger needs once the user answers. */
sealed interface NeedsItem {
    val id: Long

    /** The SMS that raised the question. Every decision card shows it (design.md §6). */
    val sms: String

    /** A confirmed sender's message the parser couldn't read (§7). Teaching it adds the transaction. */
    data class Unreadable(
        override val id: Long,
        val sender: String,
        val body: String,
        val account: String,
        val payee: String,
        val daysAgo: Int,
    ) : NeedsItem {
        override val sms: String get() = body
    }

    /** 3-digit mask that fits more than one known account (§4). Picking one adds the transaction. */
    data class WhichAccount(
        override val id: Long,
        val prompt: String,
        val amountPaise: Long,
        val daysAgo: Int,
        val payee: String,
        val options: List<String>,
        override val sms: String,
    ) : NeedsItem

    /** Debit + credit between own accounts, amounts within fee tolerance (§9). Both rows exist already. */
    data class PossibleTransfer(
        override val id: Long,
        val outTxId: Long,
        val inTxId: Long,
        val outLine: String,
        val inLine: String,
        val gap: String,
        override val sms: String,
    ) : NeedsItem

    /** First debit from a payee with no category rule yet (§11). */
    data class NewPayee(
        override val id: Long,
        val txId: Long,
        val payee: String,
        val amountPaise: Long,
        val whenLabel: String,
        override val sms: String,
    ) : NeedsItem

    /** Several untagged debits from one payee: one answer tags them all (§15.5). */
    data class Bulk(override val id: Long, val payee: String, override val sms: String) : NeedsItem
}

data class LentEntry(val label: String, val amountPaise: Long, val isRepayment: Boolean = false)

/** One person in "Owed to you", built from the core LentBook. */
data class Debtor(
    val id: Long,
    val name: String,
    val handle: String,
    val since: String,
    val entries: List<LentEntry>,
    val outstandingPaise: Long,
)
