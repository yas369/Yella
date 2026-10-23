package com.smsledger.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smsledger.core.ledger.CategoryRules
import com.smsledger.core.ledger.Debt
import com.smsledger.core.ledger.EntryKind
import com.smsledger.core.ledger.LedgerEntry
import com.smsledger.core.ledger.LedgerMath
import com.smsledger.core.ledger.LentBook
import com.smsledger.core.ledger.LentEventKind
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Debtor
import com.smsledger.ui.model.LentEntry
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Design-preview scenarios, switchable from Settings so every Today state can be reviewed. */
enum class Scenario(val label: String) { Normal("Normal"), NearLimit("Near limit"), OverLimit("Over limit"), EmptyDay("Empty day") }

/**
 * In-memory state for the design preview. All money rules come from `core` (LedgerMath,
 * CategoryRules, LentBook) so they're the same rules the Room-backed app (T6+) will use.
 * Nothing is persisted yet: state resets when the app process restarts.
 */
class DemoStore(private val clock: () -> LocalDate = { LocalDate.now() }) {
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
    val needs = mutableStateListOf<NeedsItem>()
    var lentBook by mutableStateOf(LentBook())
        private set
    /** Payee key → category id: "tag once per payee and it sticks" (§11). */
    val payeeRules = mutableStateMapOf<String, String>()

    private var nextId = 1_000L

    init {
        applyScenario(Scenario.Normal)
    }

    val today: LocalDate get() = clock()

    fun applyScenario(value: Scenario) {
        scenario = value
        val base = SampleData.baseTransactions
        transactions.clear()
        transactions += when (value) {
            Scenario.Normal -> base
            Scenario.NearLimit -> base + SampleData.warningExtras
            Scenario.OverLimit -> base + SampleData.overExtras
            Scenario.EmptyDay -> base.filter { it.daysAgo > 0 }
        }.sortedForList()
        needs.clear()
        needs += SampleData.needs
        lentBook = SampleData.lentBook(today)
        payeeRules.clear()
    }

    // ---- Reads -----------------------------------------------------------------------------

    fun category(id: String?): Category? = categories.firstOrNull { it.id == id }

    private val notDaily: Set<String> get() = categories.filterNot { it.countsDaily }.map { it.id }.toSet()

    private fun Tx.toEntry() = LedgerEntry(
        id = id,
        payee = payee,
        amountPaise = amountPaise,
        kind = when (kind) {
            TxKind.Spend -> EntryKind.Debit
            TxKind.Income -> EntryKind.Credit
            TxKind.Transfer -> EntryKind.Transfer
            TxKind.Lent -> EntryKind.Lent
        },
        date = today.minusDays(daysAgo.toLong()),
        categoryId = categoryId,
        excluded = excluded,
    )

    private val entries: List<LedgerEntry> get() = transactions.map { it.toEntry() }

    val todayTransactions: List<Tx> get() = transactions.filter { it.daysAgo == 0 }

    val spentTodayPaise: Long get() = LedgerMath.spentOn(entries, today, notDaily)

    val monthSpentPaise: Long get() = LedgerMath.spentInMonth(entries, YearMonth.from(today))

    val owedPaise: Long get() = lentBook.totalOutstanding

    val debtors: List<Debtor> get() = lentBook.owing.map { it.toDebtor() }

    fun similarCount(tx: Tx): Int = CategoryRules.similarCount(entries, tx.toEntry())

    /** Untagged debits from [payee] — what a "Tag in one go" card covers. */
    fun untaggedCount(payee: String): Int =
        transactions.count { it.kind == TxKind.Spend && it.categoryId == null && CategoryRules.payeeKey(it.payee) == CategoryRules.payeeKey(payee) }

    private fun Debt.toDebtor(): Debtor {
        val f = DateTimeFormatter.ofPattern("d MMM")
        return Debtor(
            id = person.lowercase().hashCode().toLong(),
            name = person,
            handle = SampleData.handles[CategoryRules.payeeKey(person)] ?: person,
            since = since?.let { if (it == today) "since today" else "since ${it.format(f)}" } ?: "",
            entries = events.map {
                when (it.kind) {
                    LentEventKind.Lent -> LentEntry("Lent · ${it.date.format(f)}", it.amountPaise)
                    LentEventKind.Repaid -> LentEntry("Repaid · ${it.date.format(f)}", it.amountPaise, isRepayment = true)
                    LentEventKind.WrittenOff -> LentEntry("Written off · ${it.date.format(f)}", it.amountPaise, isRepayment = true)
                }
            },
            outstandingPaise = outstandingPaise,
        )
    }

    // ---- Undo ------------------------------------------------------------------------------

    /** Everything an action can change, so one Undo restores all of it. */
    data class Snapshot(val transactions: List<Tx>, val needs: List<NeedsItem>, val lentBook: LentBook, val rules: Map<String, String>)

    fun snapshot() = Snapshot(transactions.toList(), needs.toList(), lentBook, payeeRules.toMap())

    fun restore(s: Snapshot) {
        transactions.clear(); transactions += s.transactions
        needs.clear(); needs += s.needs
        lentBook = s.lentBook
        payeeRules.clear(); payeeRules += s.rules
    }

    // ---- Actions (each returns the message for the Undo snackbar) ---------------------------

    /**
     * Manual entry (§2, §6). A spend with no category is pre-tagged by the payee's rule (§11).
     * Money received from someone who owes you closes or reduces that debt instead of counting
     * as income (§9).
     */
    fun addManual(amountPaise: Long, kind: TxKind, categoryId: String?, payee: String): String {
        val name = payee.trim().ifBlank { if (kind == TxKind.Income) "Received" else "Cash spend" }
        if (kind == TxKind.Income) {
            val owed = lentBook.outstanding(name)
            val (book, income) = lentBook.repay(name, amountPaise, today)
            lentBook = book
            val repaid = amountPaise - income
            val note = when {
                repaid == 0L -> ""
                income == 0L -> "Repaid"
                else -> "Repaid + income"
            }
            insert(Tx(nextId++, name, "Manual", "now", 0, amountPaise, TxKind.Income, note = note))
            return when {
                repaid == 0L -> "Added ${formatRupees(amountPaise)} received"
                repaid < owed -> "$name repaid ${formatRupees(repaid)} · ${formatRupees(owed - repaid)} still owed"
                income > 0 -> "$name paid back in full · ${formatRupees(income)} counted as income"
                else -> "$name paid back in full"
            }
        }
        val category = categoryId ?: CategoryRules.categoryFor(name, payeeRules)
        if (categoryId != null) payeeRules[CategoryRules.payeeKey(name)] = categoryId
        insert(Tx(nextId++, name, "Manual", "now", 0, amountPaise, TxKind.Spend, category))
        return if (categoryId == null && category != null) "Added ${formatRupees(amountPaise)} · tagged ${category(category)?.name}" else "Added ${formatRupees(amountPaise)}"
    }

    /** Category from the edit sheet or Needs you. Always becomes the payee's rule (§11). */
    fun setCategory(tx: Tx, categoryId: String, allLikeThis: Boolean): String {
        val target = transactions.firstOrNull { it.id == tx.id } ?: return ""
        val updated = CategoryRules.apply(entries, target.toEntry(), categoryId, allLikeThis).associate { it.id to it.categoryId }
        transactions.replaceAll { it.copy(categoryId = updated[it.id]) }
        payeeRules[CategoryRules.payeeKey(tx.payee)] = categoryId
        val name = category(categoryId)
        val scope = if (allLikeThis) "all ${tx.payee} payments" else tx.payee
        val note = if (name?.countsDaily == false) " · not counted toward the daily limit" else ""
        return "${name?.name} for $scope$note"
    }

    /** Lent / self-transfer toggles (§9). Marking lent opens a debt; un-marking removes it. */
    fun setKind(tx: Tx, kind: TxKind): String {
        val current = transactions.firstOrNull { it.id == tx.id } ?: return ""
        if (current.kind == TxKind.Lent && kind != TxKind.Lent) lentBook = lentBook.cancelLend(current.payee, current.amountPaise)
        if (kind == TxKind.Lent && current.kind != TxKind.Lent) lentBook = lentBook.lend(current.payee, current.amountPaise, today.minusDays(current.daysAgo.toLong()))
        replace(current.copy(kind = kind))
        return when (kind) {
            TxKind.Lent -> "Lent to ${current.payee} · not counted, added to Owed to you"
            TxKind.Transfer -> "Marked as self-transfer · not counted"
            else -> if (current.kind == TxKind.Lent) "No longer lent" else "No longer a transfer"
        }
    }

    /** Amount, direction and payee edits (§10). A lent row's debt follows its new amount. */
    fun editDetails(tx: Tx, amountPaise: Long, kind: TxKind, payee: String): String {
        val current = transactions.firstOrNull { it.id == tx.id } ?: return ""
        val name = payee.trim().ifBlank { current.payee }
        if (current.kind == TxKind.Lent) {
            lentBook = lentBook.cancelLend(current.payee, current.amountPaise).lend(name, amountPaise, today.minusDays(current.daysAgo.toLong()))
        }
        replace(current.copy(amountPaise = amountPaise, kind = kind, payee = name))
        return "Updated"
    }

    /** "Not a transaction" (§10): kept in history, removed from every total. */
    fun setExcluded(tx: Tx, excluded: Boolean): String {
        val current = transactions.firstOrNull { it.id == tx.id } ?: return ""
        if (excluded && current.kind == TxKind.Lent) lentBook = lentBook.cancelLend(current.payee, current.amountPaise)
        replace(current.copy(excluded = excluded))
        return if (excluded) "Not a transaction · removed from totals" else "Counted again"
    }

    /** "Mark repaid" on Owed to you: settles the rest in cash, nothing added as income. */
    fun markRepaid(debtor: Debtor): String {
        lentBook = lentBook.repay(debtor.name, debtor.outstandingPaise, today).first
        return "${debtor.name} paid back ${formatRupees(debtor.outstandingPaise)}"
    }

    fun writeOff(debtor: Debtor): String {
        lentBook = lentBook.writeOff(debtor.name, today)
        return "Wrote off ${formatRupees(debtor.outstandingPaise)} from ${debtor.name}"
    }

    // ---- Needs you (§15.2) -------------------------------------------------------------------

    /** Teach (§7): the message becomes a transaction now; the template would handle the next one. */
    fun learn(item: NeedsItem.Unreadable, amountPaise: Long, moneyIn: Boolean): String {
        needs.remove(item)
        val kind = if (moneyIn) TxKind.Income else TxKind.Spend
        val category = if (moneyIn) null else CategoryRules.categoryFor(item.payee, payeeRules)
        insert(Tx(nextId++, item.payee, item.account, "from SMS", item.daysAgo, amountPaise, kind, category, sms = item.body))
        return "Learned · ${formatRupees(amountPaise)} ${if (moneyIn) "received" else "spent"} added"
    }

    fun ignoreLikeThis(item: NeedsItem.Unreadable): String {
        needs.remove(item)
        return "Messages like this will be ignored"
    }

    fun notATransaction(item: NeedsItem): String {
        needs.remove(item)
        return "Marked not a transaction"
    }

    /** Ambiguous 3-digit mask resolved (§4): the debit is added to the chosen account. */
    fun pickAccount(item: NeedsItem.WhichAccount, account: String?): String {
        needs.remove(item)
        if (account == null) return "Ignored · neither account is yours"
        insert(Tx(nextId++, item.payee, account, "from SMS", item.daysAgo, item.amountPaise, TxKind.Spend, CategoryRules.categoryFor(item.payee, payeeRules)))
        return "Added ${formatRupees(item.amountPaise)} to $account"
    }

    /** Self-transfer pair (§9): both rows stay, both stop counting. */
    fun resolveTransfer(item: NeedsItem.PossibleTransfer, isTransfer: Boolean): String {
        needs.remove(item)
        if (!isTransfer) return "Kept as separate payments"
        transactions.replaceAll { if (it.id == item.outTxId || it.id == item.inTxId) it.copy(kind = TxKind.Transfer) else it }
        return "Paired as self-transfer · not counted"
    }

    fun tagPayee(item: NeedsItem, categoryId: String): String {
        needs.remove(item)
        val payee = when (item) {
            is NeedsItem.NewPayee -> item.payee
            is NeedsItem.Bulk -> item.payee
            else -> return ""
        }
        val sample = transactions.firstOrNull { it.kind == TxKind.Spend && CategoryRules.payeeKey(it.payee) == CategoryRules.payeeKey(payee) }
            ?: run { payeeRules[CategoryRules.payeeKey(payee)] = categoryId; return "${category(categoryId)?.name} for future $payee payments" }
        return setCategory(sample, categoryId, allLikeThis = true)
    }

    fun lentFromNeeds(item: NeedsItem.NewPayee): String {
        needs.remove(item)
        val tx = transactions.firstOrNull { it.id == item.txId } ?: return ""
        return setKind(tx, TxKind.Lent)
    }

    fun skip(item: NeedsItem): String {
        needs.remove(item)
        return "Skipped"
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private fun insert(tx: Tx) {
        transactions += tx
        val sorted = transactions.sortedForList()
        transactions.clear(); transactions += sorted
    }

    private fun replace(tx: Tx) {
        val index = transactions.indexOfFirst { it.id == tx.id }
        if (index >= 0) transactions[index] = tx
    }

    /** Newest day first; within a day, most recently added first. */
    private fun List<Tx>.sortedForList() = sortedWith(compareBy<Tx> { it.daysAgo }.thenByDescending { it.id })
}
