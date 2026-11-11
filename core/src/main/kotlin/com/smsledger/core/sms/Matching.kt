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
    /** Already one half of a self-transfer: never paired again (§12), still checked for duplicate alerts (§11.2). */
    val linked: Boolean = false,
)

/** A merchant / payment-app SMS: it may label a transaction, never adds money (§8). */
data class LabelSms(val amountPaise: Long, val direction: Direction, val at: LocalDateTime, val upiRef: String? = null)

private fun minutesBetween(a: LocalDateTime, b: LocalDateTime) = Duration.between(a, b).abs().toMinutes()

/** §8.2: UPI reference first; otherwise same amount + direction within ±30 min, nearest wins. */
object LabelMatcher {
    fun match(label: LabelSms, candidates: List<Candidate>): Long? {
        if (label.upiRef != null) candidates.firstOrNull { it.upiRef == label.upiRef }?.let { return it.id }
        return candidates
            .filter { !it.linked && (it.upiRef == null || label.upiRef == null) }
            .filter { it.amountPaise == label.amountPaise && it.direction == label.direction }
            .filter { minutesBetween(it.at, label.at) <= SmsDefaults.LABEL_WINDOW_MINUTES }
            .minByOrNull { minutesBetween(it.at, label.at) }?.id
    }
}

enum class Confidence { Auto, Possible }

data class Pairing(val otherId: Long, val confidence: Confidence)

/**
 * §12 self-transfer: a debit from one own account and a credit to another within 30 minutes, amounts
 * within the fee tolerance; the nearest in time wins. Linked without asking only when both carry the
 * same UPI reference and the amounts are equal; anything else is put to the user.
 */
object TransferPairer {
    fun find(tx: Candidate, others: List<Candidate>): Pairing? {
        val other = others
            .filter { it.id != tx.id && !it.linked && it.direction != tx.direction && it.account != tx.account && it.account != "Manual" }
            .filter { minutesBetween(it.at, tx.at) <= SmsDefaults.TRANSFER_WINDOW_MINUTES }
            .filter { other ->
                val (debit, credit) = if (tx.direction == Direction.Debit) tx to other else other to tx
                val fee = debit.amountPaise - credit.amountPaise
                fee >= 0 && fee <= SmsDefaults.feeTolerance(debit.amountPaise)
            }
            .minByOrNull { minutesBetween(it.at, tx.at) } ?: return null
        val auto = tx.upiRef != null && tx.upiRef == other.upiRef && tx.amountPaise == other.amountPaise
        return Pairing(other.id, if (auto) Confidence.Auto else Confidence.Possible)
    }
}

/** §11.2: a second alert for a payment already counted — same account, UPI reference, amount and direction within 24 h. */
object DuplicateAlert {
    fun isDuplicate(tx: Candidate, existing: List<Candidate>): Boolean = tx.upiRef != null && existing.any {
        it.account == tx.account && it.upiRef == tx.upiRef && it.amountPaise == tx.amountPaise && it.direction == tx.direction &&
            minutesBetween(it.at, tx.at) <= SmsDefaults.DUPLICATE_WINDOW_HOURS * 60
    }
}

/** §8 default: a bank SMS matching a manual entry (same amount + direction, within 6 h) merges with it. */
object ManualMerger {
    fun match(sms: Candidate, manual: List<Candidate>): Long? = manual
        .filter { it.amountPaise == sms.amountPaise && it.direction == sms.direction }
        .filter { minutesBetween(it.at, sms.at) <= SmsDefaults.MANUAL_MERGE_HOURS * 60 }
        .minByOrNull { minutesBetween(it.at, sms.at) }?.id
}
