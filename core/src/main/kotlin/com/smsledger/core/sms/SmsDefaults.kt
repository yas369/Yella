package com.smsledger.core.sms

/**
 * Every matching window and tolerance in one place (spec §8, §9; plan defaults D3–D6).
 * Working defaults: change them here, never inline.
 */
object SmsDefaults {
    /** A merchant/payment-app SMS labels a bank payment within this window (§8). */
    const val LABEL_WINDOW_MINUTES = 30L
    /** A bank SMS merges with a manual entry within this window (§8 default "a few hours"). */
    const val MANUAL_MERGE_HOURS = 6L
    /** Debit and credit between own accounts this close can be a self-transfer (§9). */
    const val TRANSFER_WINDOW_MINUTES = 120L
    /** Exact amount this close is paired without asking. */
    const val AUTO_TRANSFER_MINUTES = 30L
    const val FEE_CAP_PAISE = 5_000L
    const val FEE_PERCENT = 2L

    /** Allowed self-transfer fee: at most ₹50 or 2% of the debit, whichever is smaller. */
    fun feeTolerance(debitPaise: Long): Long = minOf(FEE_CAP_PAISE, debitPaise * FEE_PERCENT / 100)
}
