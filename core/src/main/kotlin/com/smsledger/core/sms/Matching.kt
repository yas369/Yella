package com.smsledger.core.sms

import java.time.Duration
import java.time.LocalDateTime

/** A ledger row as the matchers see it. [account] is an account key, or "Manual". */
data class Candidate(
    val id: Long,
    val amountPaise: Long,
    val direction: Direction,
    val at: LocalDateTime,
    val account: String,
    val upiRef: String? = null,
)

/** A merchant / payment-app SMS: it may label a transaction, never adds money (§8). */
data class LabelSms(val amountPaise: Long, val direction: Direction, val at: LocalDateTime, val upiRef: String? = null)

private fun minutesBetween(a: LocalDateTime, b: LocalDateTime) = Duration.between(a, b).abs().toMinutes()

/** §8.2: UPI reference first; otherwise same amount + direction within ±30 min, nearest wins. */
object LabelMatcher {
    fun match(label: LabelSms, candidates: List<Candidate>): Long? {
        if (label.upiRef != null) candidates.firstOrNull { it.upiRef == label.upiRef }?.let { return it.id }
        return candidates
            .filter { it.upiRef == null || label.upiRef == null }
            .filter { it.amountPaise == label.amountPaise && it.direction == label.direction }
            .filter { minutesBetween(it.at, label.at) <= SmsDefaults.LABEL_WINDOW_MINUTES }
            .minByOrNull { minutesBetween(it.at, label.at) }?.id
    }
}

enum class Confidence { Auto, Possible }

data class Pairing(val otherId: Long, val confidence: Confidence)

/**
 * §9 self-transfer: a debit from one own account and a credit to another, close in time, amounts
 * within the fee tolerance. Exact and quick → paired automatically; otherwise the user confirms.
 */
object TransferPairer {
    fun find(tx: Candidate, others: List<Candidate>): Pairing? = others
        .filter { it.id != tx.id && it.direction != tx.direction && it.account != tx.account && it.account != "Manual" }
        .filter { minutesBetween(it.at, tx.at) <= SmsDefaults.TRANSFER_WINDOW_MINUTES }
        .mapNotNull { other ->
            val (debit, credit) = if (tx.direction == Direction.Debit) tx to other else other to tx
            val fee = debit.amountPaise - credit.amountPaise
            if (fee < 0 || fee > SmsDefaults.feeTolerance(debit.amountPaise)) return@mapNotNull null
            val auto = fee == 0L && minutesBetween(debit.at, credit.at) <= SmsDefaults.AUTO_TRANSFER_MINUTES
            Triple(other, fee, Pairing(other.id, if (auto) Confidence.Auto else Confidence.Possible))
        }
        .minWithOrNull(compareBy<Triple<Candidate, Long, Pairing>> { it.second }.thenBy { minutesBetween(it.first.at, tx.at) })
        ?.third
}

/** §8 default: a bank SMS matching a manual entry (same amount + direction, within 6 h) merges with it. */
object ManualMerger {
    fun match(sms: Candidate, manual: List<Candidate>): Long? = manual
        .filter { it.amountPaise == sms.amountPaise && it.direction == sms.direction }
        .filter { minutesBetween(it.at, sms.at) <= SmsDefaults.MANUAL_MERGE_HOURS * 60 }
        .minByOrNull { minutesBetween(it.at, sms.at) }?.id
}
