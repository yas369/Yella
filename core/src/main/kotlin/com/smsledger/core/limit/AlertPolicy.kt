package com.smsledger.core.limit

import java.time.LocalDate
import java.time.YearMonth

enum class LimitAlert { Near, Reached }

/** Notification rules (spec §13). */
object AlertPolicy {

    /**
     * Daily-limit alerts due now: one at 80% and one at 100%, each at most once per day. When a
     * single payment jumps past 100%, only the limit alert is sent (the 80% one is then redundant).
     */
    fun due(status: LimitStatus, sentToday: Set<LimitAlert>): List<LimitAlert> {
        val reached = status.overPaise > 0 || status.remainingPaise == 0L
        return when {
            reached && LimitAlert.Reached !in sentToday -> listOf(LimitAlert.Reached)
            !reached && status.level == LimitLevel.Warning && LimitAlert.Near !in sentToday -> listOf(LimitAlert.Near)
            else -> emptyList()
        }
    }

    /** One lent reminder on the 1st of each month, only while someone owes you. */
    fun lentReminderDue(today: LocalDate, lastSent: YearMonth?, outstandingPaise: Long): Boolean =
        today.dayOfMonth == 1 && outstandingPaise > 0 && lastSent != YearMonth.from(today)
}
