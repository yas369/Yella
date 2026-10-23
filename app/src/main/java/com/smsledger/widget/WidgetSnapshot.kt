package com.smsledger.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.smsledger.core.limit.LimitLevel
import com.smsledger.core.limit.LimitStatus
import com.smsledger.core.money.formatRupeesWhole

/**
 * What the home-screen widget shows. The app publishes one whenever today's numbers change; the
 * widget only reads it, so it never computes ledger rules itself.
 */
data class WidgetSnapshot(
    val headline: String,
    val label: String,
    val detail: String,
    val fraction: Float,
    val level: LimitLevel,
) {
    fun toStored(): Map<String, String> = mapOf(
        "headline" to headline,
        "label" to label,
        "detail" to detail,
        "fraction" to fraction.toString(),
        "level" to level.name,
    )

    companion object {
        private const val PREFS = "widget_snapshot"

        /** Shown before the app has published anything (e.g. widget added before first launch). */
        val Placeholder = WidgetSnapshot("—", "Open SMS Ledger", "", 0f, LimitLevel.Ok)

        fun from(status: LimitStatus, spentPaise: Long, limitPaise: Long): WidgetSnapshot {
            val over = status.level == LimitLevel.Over
            return WidgetSnapshot(
                headline = formatRupeesWhole(if (over) status.overPaise else status.remainingPaise),
                label = if (over) "over limit" else "left today",
                detail = "${formatRupeesWhole(spentPaise)} of ${formatRupeesWhole(limitPaise)}",
                fraction = status.fraction,
                level = status.level,
            )
        }

        fun fromStored(values: Map<String, String>): WidgetSnapshot? {
            return WidgetSnapshot(
                headline = values["headline"] ?: return null,
                label = values["label"] ?: return null,
                detail = values["detail"].orEmpty(),
                fraction = values["fraction"]?.toFloatOrNull() ?: 0f,
                level = values["level"]?.let { runCatching { LimitLevel.valueOf(it) }.getOrNull() } ?: LimitLevel.Ok,
            )
        }

        fun load(context: Context): WidgetSnapshot {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            @Suppress("UNCHECKED_CAST")
            return fromStored(prefs.all as Map<String, String>) ?: Placeholder
        }

        /** Saves the snapshot and redraws every placed widget. */
        suspend fun publish(context: Context, status: LimitStatus, spentPaise: Long, limitPaise: Long) {
            val snapshot = from(status, spentPaise, limitPaise)
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            snapshot.toStored().forEach { (key, value) -> editor.putString(key, value) }
            editor.apply()
            LedgerWidget().updateAll(context)
        }
    }
}
