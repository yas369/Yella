package com.smsledger.core.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LentBookTest {

    private val day = LocalDate.of(2026, 10, 24)

    @Test
    fun `lending records an outstanding amount per person`() {
        val book = LentBook().lend("Rahul S", 100_000, day)
        assertEquals(100_000L, book.outstanding("Rahul S"))
        assertEquals(1, book.owing.size)
        assertEquals(100_000L, book.totalOutstanding)
    }

    @Test
    fun `a partial repayment reduces what is owed and is not income`() {
        val (book, income) = LentBook().lend("Rahul S", 100_000, day).repay("rahul s", 40_000, day)
        assertEquals(60_000L, book.outstanding("Rahul S"))
        assertEquals(0L, income)
    }

    @Test
    fun `repaying more than is owed closes the entry and only the excess is income`() {
        val (book, income) = LentBook().lend("Rahul S", 100_000, day).repay("Rahul S", 150_000, day)
        assertEquals(0L, book.outstanding("Rahul S"))
        assertEquals(50_000L, income)
        assertTrue(book.owing.isEmpty())
    }

    @Test
    fun `money from someone who owes nothing is all income`() {
        val (book, income) = LentBook().repay("Priya", 40_000, day)
        assertEquals(40_000L, income)
        assertTrue(book.owing.isEmpty())
    }

    @Test
    fun `writing off closes the debt and stops it counting as owed`() {
        val book = LentBook().lend("Arjun", 30_000, day).writeOff("Arjun", day)
        assertEquals(0L, book.outstanding("Arjun"))
        assertTrue(book.owing.isEmpty())
    }

    @Test
    fun `un-marking a lent payment removes that amount`() {
        val book = LentBook().lend("Rahul S", 100_000, day).lend("Rahul S", 20_000, day).cancelLend("Rahul S", 100_000)
        assertEquals(20_000L, book.outstanding("Rahul S"))
    }

    @Test
    fun `lending again after a write-off starts a fresh debt`() {
        val book = LentBook().lend("Arjun", 30_000, day).writeOff("Arjun", day).lend("Arjun", 10_000, day)
        assertEquals(10_000L, book.outstanding("Arjun"))
    }

    @Test
    fun `a book rebuilt from all its debts is the same book`() {
        val book = LentBook()
            .lend("Asha", 50_000, LocalDate.of(2026, 10, 1))
            .repay("asha", 20_000, LocalDate.of(2026, 10, 3)).first
            .lend("Ravi", 10_000, LocalDate.of(2026, 10, 2))
            .writeOff("Ravi", LocalDate.of(2026, 10, 4))
        assertEquals(book, LentBook.of(book.all))
    }
}
