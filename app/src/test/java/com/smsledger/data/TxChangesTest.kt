package com.smsledger.data

import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TxChangesTest {

    private val at = LocalDateTime.of(2026, 10, 24, 9, 0)
    private fun tx(id: Long, paise: Long = 100) = Tx(id, "Payee $id", "Manual", at, paise, TxKind.Spend)

    @Test
    fun `only new and edited rows are written, removed rows are deleted`() {
        val before = listOf(tx(1), tx(2), tx(3))
        val after = listOf(tx(1), tx(2, paise = 250), tx(4))
        val (changed, deleted) = txChanges(before, after)
        assertEquals(listOf(tx(2, paise = 250), tx(4)), changed)
        assertEquals(listOf(3L), deleted)
    }

    @Test
    fun `no change writes nothing`() {
        val rows = listOf(tx(1), tx(2))
        assertEquals(emptyList<Tx>() to emptyList<Long>(), txChanges(rows, rows))
    }
}
