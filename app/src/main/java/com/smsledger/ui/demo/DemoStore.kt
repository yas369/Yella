package com.smsledger.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind

/** Design-preview scenarios, switchable from Settings so every Today state can be reviewed. */
enum class Scenario(val label: String) { Normal("Normal"), NearLimit("Near limit"), OverLimit("Over limit"), EmptyDay("Empty day") }

/**
 * In-memory state for the design preview. Replaced by Room-backed repositories from T6 onwards;
 * screens only depend on the plain values and callbacks the app shell passes them.
 */
class DemoStore {
    var onboarded by mutableStateOf(false)
    var automatic by mutableStateOf(true)
    var limitPaise by mutableLongStateOf(50_000)
    var scenario by mutableStateOf(Scenario.Normal)
        private set
    var alertsCardVisible by mutableStateOf(true)
    var limitAlerts by mutableStateOf(false)
    var lentReminder by mutableStateOf(true)
    /** null = follow the system theme. */
    var darkOverride by mutableStateOf<Boolean?>(null)
    /** Material You wallpaper colours (Android 12+). */
    var dynamicColor by mutableStateOf(true)
    /** Set by the "Add expense" launcher shortcut / widget; the shell opens the Add sheet and clears it. */
    var pendingAdd by mutableStateOf(false)

    val categories: List<Category> = SampleData.categories
    val transactions = mutableStateListOf<Tx>()
    val needs = mutableStateListOf<NeedsItem>().apply { addAll(SampleData.needs) }
    val debtors = mutableStateListOf(*SampleData.debtors.toTypedArray())

    private var nextId = 1_000L

    init {
        applyScenario(Scenario.Normal)
    }

    fun applyScenario(value: Scenario) {
        scenario = value
        val base = SampleData.baseTransactions
        transactions.clear()
        transactions += when (value) {
            Scenario.Normal -> base
            Scenario.NearLimit -> base + SampleData.warningExtras
            Scenario.OverLimit -> base + SampleData.overExtras
            Scenario.EmptyDay -> base.filter { it.daysAgo > 0 }
        }.sortedWith(compareBy<Tx> { it.daysAgo }.thenByDescending { it.id })
    }

    fun category(id: String?): Category? = categories.firstOrNull { it.id == id }

    /** Debits that count toward the daily limit (§12): spends whose category isn't "not daily". */
    fun countsTowardLimit(tx: Tx): Boolean = tx.kind == TxKind.Spend && (category(tx.categoryId)?.countsDaily ?: true)

    val today: List<Tx> get() = transactions.filter { it.daysAgo == 0 }

    val spentTodayPaise: Long get() = today.filter(::countsTowardLimit).sumOf { it.amountPaise }

    val monthSpentPaise: Long get() = transactions.filter { it.kind == TxKind.Spend }.sumOf { it.amountPaise }

    val owedPaise: Long get() = debtors.sumOf { it.outstandingPaise }

    fun add(amountPaise: Long, kind: TxKind, categoryId: String?, payee: String): Tx {
        val tx = Tx(nextId++, payee.ifBlank { if (kind == TxKind.Income) "Received" else "Cash spend" }, "Manual", "now", 0, amountPaise, kind, categoryId)
        transactions.add(0, tx)
        return tx
    }

    fun remove(id: Long): Pair<Int, Tx>? {
        val index = transactions.indexOfFirst { it.id == id }
        if (index < 0) return null
        return index to transactions.removeAt(index)
    }

    fun restore(index: Int, tx: Tx) = transactions.add(index.coerceIn(0, transactions.size), tx)

    fun update(tx: Tx): Tx? {
        val index = transactions.indexOfFirst { it.id == tx.id }
        if (index < 0) return null
        val previous = transactions[index]
        transactions[index] = tx
        return previous
    }

    fun resolve(item: NeedsItem): Int {
        val index = needs.indexOf(item)
        needs.remove(item)
        return index
    }

    fun restoreNeed(index: Int, item: NeedsItem) = needs.add(index.coerceIn(0, needs.size), item)
}
