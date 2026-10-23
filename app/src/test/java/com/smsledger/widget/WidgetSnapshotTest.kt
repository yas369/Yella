package com.smsledger.widget

import com.smsledger.core.limit.LimitLevel
import com.smsledger.core.limit.limitStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSnapshotTest {

    @Test
    fun `under the limit shows what is left`() {
        val snapshot = WidgetSnapshot.from(limitStatus(16_000, 50_000), spentPaise = 16_000, limitPaise = 50_000)
        assertEquals("₹340", snapshot.headline)
        assertEquals("left today", snapshot.label)
        assertEquals("₹160 of ₹500", snapshot.detail)
        assertEquals(LimitLevel.Ok, snapshot.level)
    }

    @Test
    fun `over the limit shows the overspend, never a negative amount`() {
        val snapshot = WidgetSnapshot.from(limitStatus(62_000, 50_000), spentPaise = 62_000, limitPaise = 50_000)
        assertEquals("₹120", snapshot.headline)
        assertEquals("over limit", snapshot.label)
        assertEquals(LimitLevel.Over, snapshot.level)
        assertEquals(1f, snapshot.fraction)
    }

    @Test
    fun `survives a round trip through stored values`() {
        val snapshot = WidgetSnapshot.from(limitStatus(40_900, 50_000), spentPaise = 40_900, limitPaise = 50_000)
        assertEquals(snapshot, WidgetSnapshot.fromStored(snapshot.toStored()))
    }
}
