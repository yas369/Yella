package com.smsledger.core.limit

enum class LimitLevel { Ok, Warning, Over }

/** Where today's spending stands against the daily limit (§12). Never exposes a negative remaining amount. */
data class LimitStatus(
    val remainingPaise: Long,
    val overPaise: Long,
    /** Share of the limit used, clamped to 0..1 for meters. */
    val fraction: Float,
    val level: LimitLevel,
)

const val WARNING_FRACTION = 0.8

fun limitStatus(spentPaise: Long, limitPaise: Long): LimitStatus {
    require(limitPaise > 0) { "Daily limit must be positive" }
    val level = when {
        spentPaise > limitPaise -> LimitLevel.Over
        spentPaise >= limitPaise * WARNING_FRACTION -> LimitLevel.Warning
        else -> LimitLevel.Ok
    }
    return LimitStatus(
        remainingPaise = (limitPaise - spentPaise).coerceAtLeast(0),
        overPaise = (spentPaise - limitPaise).coerceAtLeast(0),
        fraction = (spentPaise.toFloat() / limitPaise).coerceIn(0f, 1f),
        level = level,
    )
}
