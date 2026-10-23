package com.smsledger.ui.model

/** UI-facing models. Real data (T6+) maps into these; screens never see Room entities. */

enum class TxKind { Spend, Income, Transfer, Lent }

data class Category(val id: String, val name: String, val countsDaily: Boolean = true)

data class Tx(
    val id: Long,
    val payee: String,
    val account: String,
    val time: String,
    /** 0 = today, 1 = yesterday, … */
    val daysAgo: Int,
    val amountPaise: Long,
    val kind: TxKind,
    val categoryId: String? = null,
    val sms: String? = null,
    val note: String = "",
    /** Marked "not a transaction" (§10): stays in history, never counted. */
    val excluded: Boolean = false,
)

data class DiscoveredAccount(
    val id: String,
    val name: String,
    val detail: String,
    val evidence: String,
    val isWallet: Boolean = false,
)

/** Everything uncertain (§15.2). Each item carries what the ledger needs once the user answers. */
sealed interface NeedsItem {
    val id: Long

    /** A confirmed sender's message the parser couldn't read (§7). Teaching it adds the transaction. */
    data class Unreadable(
        override val id: Long,
        val sender: String,
        val body: String,
        val account: String,
        val payee: String,
        val daysAgo: Int,
    ) : NeedsItem

    /** 3-digit mask that fits more than one known account (§4). Picking one adds the transaction. */
    data class WhichAccount(
        override val id: Long,
        val prompt: String,
        val amountPaise: Long,
        val daysAgo: Int,
        val payee: String,
        val options: List<String>,
    ) : NeedsItem

    /** Debit + credit between own accounts, amounts within fee tolerance (§9). Both rows exist already. */
    data class PossibleTransfer(
        override val id: Long,
        val outTxId: Long,
        val inTxId: Long,
        val outLine: String,
        val inLine: String,
        val gap: String,
    ) : NeedsItem

    /** First debit from a payee with no category rule yet (§11). */
    data class NewPayee(override val id: Long, val txId: Long, val payee: String, val amountPaise: Long, val whenLabel: String) : NeedsItem

    /** Several untagged debits from one payee: one answer tags them all (§15.5). */
    data class Bulk(override val id: Long, val payee: String) : NeedsItem
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
