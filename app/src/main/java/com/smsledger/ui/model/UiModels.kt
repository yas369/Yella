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
)

data class DiscoveredAccount(
    val id: String,
    val name: String,
    val detail: String,
    val evidence: String,
    val isWallet: Boolean = false,
)

sealed interface NeedsItem {
    val id: Long

    data class Unreadable(override val id: Long, val sender: String, val body: String) : NeedsItem
    data class WhichAccount(override val id: Long, val prompt: String, val amount: String, val options: List<String>) : NeedsItem
    data class PossibleTransfer(
        override val id: Long,
        val outLine: String,
        val inLine: String,
        val gap: String,
    ) : NeedsItem
    data class NewPayee(override val id: Long, val payee: String, val amountPaise: Long, val whenLabel: String) : NeedsItem
    data class Bulk(override val id: Long, val payee: String, val count: Int) : NeedsItem
}

data class LentEntry(val label: String, val amountPaise: Long, val isRepayment: Boolean = false)

data class Debtor(
    val id: Long,
    val name: String,
    val handle: String,
    val since: String,
    val entries: List<LentEntry>,
) {
    val outstandingPaise: Long
        get() = entries.sumOf { if (it.isRepayment) -it.amountPaise else it.amountPaise }.coerceAtLeast(0)
}
