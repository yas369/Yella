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
    /** Debit and credit between own accounts this close can be a self-transfer (logic.md §12). */
    const val TRANSFER_WINDOW_MINUTES = 30L
    /** A second alert with the same UPI reference this close is the same payment (logic.md §11.2). */
    const val DUPLICATE_WINDOW_HOURS = 24L
    /** A catch-up scan re-reads this far before the last scan, for late deliveries (logic.md §20). */
    const val CATCH_UP_OVERLAP_DAYS = 3L
    /** A message matches a learned template when this share of its words is the same (logic.md §9). */
    const val TEMPLATE_OVERLAP = 0.8
    /**
     * An account or wallet is put to the user ("is this yours?") only once this many transaction
     * messages name it. One-off numbers in a message (the other party's account, a reference) never reach it.
     */
    const val ACCOUNT_MIN_MESSAGES = 10
    const val FEE_CAP_PAISE = 5_000L
    const val FEE_PERCENT = 2L

    /** Allowed self-transfer fee: at most ₹50 or 2% of the debit, whichever is smaller. */
    fun feeTolerance(debitPaise: Long): Long = minOf(FEE_CAP_PAISE, debitPaise * FEE_PERCENT / 100)
}
