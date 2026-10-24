package com.smsledger.data

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
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Starter categories every new install gets. Product defaults, not user data. */
val DefaultCategories = listOf(
    Category("food", "Food"),
    Category("groceries", "Groceries"),
    Category("travel", "Travel"),
    Category("shopping", "Shopping"),
    Category("health", "Health"),
    Category("fun", "Fun"),
    Category("bills", "Bills", countsDaily = false),
    Category("rent", "Rent", countsDaily = false),
    Category("emi", "EMI", countsDaily = false),
)

/**
 * The app's state: the user's own entries, kept in Compose state for the screens and written
 * through to [disk] after every change. All money rules come from `core` (LedgerMath,
 * CategoryRules, LentBook). Starts empty; [load] fills it from disk.
 */
class LedgerStore(
    private val disk: LedgerDisk,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
) {
    /** False until [load] has read the disk; the splash screen stays up until then. */
    var loaded by mutableStateOf(false)
        private set
    var onboarded by mutableStateOf(false)
        private set
    var limitPaise by mutableLongStateOf(DEFAULT_LIMIT_PAISE)
        private set
    /** null = follow the system theme. */
    var darkOverride by mutableStateOf<Boolean?>(null)
        private set
    /** Set by the "Add expense" launcher shortcut / widget; the shell opens the Add sheet and clears it. */
    var pendingAdd by mutableStateOf(false)

    val categories: List<Category> = DefaultCategories
    val transactions = mutableStateListOf<Tx>()
    /** Questions raised by SMS capture. Empty until capture exists; nothing in manual mode adds to it. */
    val needs = mutableStateListOf<NeedsItem>()
    var lentBook by mutableStateOf(LentBook())
        private set
    /** Payee key → category id: "tag once per payee and it sticks" (§11). */
    val payeeRules = mutableStateMapOf<String, String>()

    private var nextId = 1L

    suspend fun load() {
        val (data, prefs) = disk.load()
        put(data)
        nextId = (data.transactions.maxOfOrNull { it.id } ?: 0L) + 1
        onboarded = prefs.onboarded
        limitPaise = prefs.limitPaise
        darkOverride = prefs.darkOverride
        loaded = true
    }

    val today: LocalDate get() = clock().toLocalDate()

    // ---- Settings ------------------------------------------------------------------------------

    private fun prefs() = Prefs(onboarded, limitPaise, darkOverride)

    fun finishOnboarding(limitPaise: Long) {
        this.limitPaise = limitPaise
        onboarded = true
        disk.savePrefs(prefs())
    }

    fun changeLimit(paise: Long) {
        limitPaise = paise
        disk.savePrefs(prefs())
    }

    fun changeTheme(dark: Boolean?) {
        darkOverride = dark
        disk.savePrefs(prefs())
    }

    /** Erases everything and returns to first run. Permanent: there is no Undo. */
    fun deleteAll() {
        disk.clear()
        put(LedgerData())
        needs.clear()
        nextId = 1
        limitPaise = DEFAULT_LIMIT_PAISE
        darkOverride = null
        onboarded = false
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
        date = date,
        categoryId = categoryId,
        excluded = excluded,
    )

    private val entries: List<LedgerEntry> get() = transactions.map { it.toEntry() }

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
            // Manual entries know people by name; SMS capture will add the VPA or phone (§9).
            handle = person,
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

    fun restore(s: Snapshot) = write {
        transactions.clear(); transactions += s.transactions
        needs.clear(); needs += s.needs
        lentBook = s.lentBook
        payeeRules.clear(); payeeRules += s.rules
    }

    private fun data() = LedgerData(transactions.toList(), lentBook, payeeRules.toMap())

    private fun put(data: LedgerData) {
        transactions.clear(); transactions += data.transactions.sortedForList()
        lentBook = data.lentBook
        payeeRules.clear(); payeeRules += data.payeeRules
    }

    /** Runs [block], then saves whatever it changed. Every mutation goes through here. */
    private fun <T> write(block: () -> T): T {
        val before = data()
        val result = block()
        disk.save(before, data())
        return result
    }

    // ---- Actions (each returns the message for the Undo snackbar) ---------------------------

    /**
     * Manual entry (§2, §6). A spend with no category is pre-tagged by the payee's rule (§11).
     * Money received from someone who owes you closes or reduces that debt instead of counting
     * as income (§9).
     */
    fun addManual(amountPaise: Long, kind: TxKind, categoryId: String?, payee: String): String = write {
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
            insert(Tx(nextId++, name, "Manual", clock(), amountPaise, TxKind.Income, note = note))
            return@write when {
                repaid == 0L -> "Added ${formatRupees(amountPaise)} received"
                repaid < owed -> "$name repaid ${formatRupees(repaid)} · ${formatRupees(owed - repaid)} still owed"
                income > 0 -> "$name paid back in full · ${formatRupees(income)} counted as income"
                else -> "$name paid back in full"
            }
        }
        val category = categoryId ?: CategoryRules.categoryFor(name, payeeRules)
        if (categoryId != null) payeeRules[CategoryRules.payeeKey(name)] = categoryId
        insert(Tx(nextId++, name, "Manual", clock(), amountPaise, TxKind.Spend, category))
        if (categoryId == null && category != null) "Added ${formatRupees(amountPaise)} · tagged ${category(category)?.name}" else "Added ${formatRupees(amountPaise)}"
    }

    /** Category from the edit sheet or Needs you. Always becomes the payee's rule (§11). */
    fun setCategory(tx: Tx, categoryId: String, allLikeThis: Boolean): String = write {
        val target = transactions.firstOrNull { it.id == tx.id } ?: return@write ""
        val updated = CategoryRules.apply(entries, target.toEntry(), categoryId, allLikeThis).associate { it.id to it.categoryId }
        transactions.replaceAll { it.copy(categoryId = updated[it.id]) }
        payeeRules[CategoryRules.payeeKey(tx.payee)] = categoryId
        val name = category(categoryId)
        val scope = if (allLikeThis) "all ${tx.payee} payments" else tx.payee
        val note = if (name?.countsDaily == false) " · not counted toward the daily limit" else ""
        "${name?.name} for $scope$note"
    }

    /** Lent / self-transfer toggles (§9). Marking lent opens a debt; un-marking removes it. */
    fun setKind(tx: Tx, kind: TxKind): String = write {
        val current = transactions.firstOrNull { it.id == tx.id } ?: return@write ""
        if (current.kind == TxKind.Lent && kind != TxKind.Lent) lentBook = lentBook.cancelLend(current.payee, current.amountPaise)
        if (kind == TxKind.Lent && current.kind != TxKind.Lent) lentBook = lentBook.lend(current.payee, current.amountPaise, current.date)
        replace(current.copy(kind = kind))
        when (kind) {
            TxKind.Lent -> "Lent to ${current.payee} · not counted, added to Owed to you"
            TxKind.Transfer -> "Marked as self-transfer · not counted"
            else -> if (current.kind == TxKind.Lent) "No longer lent" else "No longer a transfer"
        }
    }

    /** Amount, direction and payee edits (§10). A lent row's debt follows its new amount. */
    fun editDetails(tx: Tx, amountPaise: Long, kind: TxKind, payee: String): String = write {
        val current = transactions.firstOrNull { it.id == tx.id } ?: return@write ""
        val name = payee.trim().ifBlank { current.payee }
        if (current.kind == TxKind.Lent) {
            lentBook = lentBook.cancelLend(current.payee, current.amountPaise).lend(name, amountPaise, current.date)
        }
        replace(current.copy(amountPaise = amountPaise, kind = kind, payee = name))
        "Updated"
    }

    /** "Not a transaction" (§10): kept in history, removed from every total. */
    fun setExcluded(tx: Tx, excluded: Boolean): String = write {
        val current = transactions.firstOrNull { it.id == tx.id } ?: return@write ""
        if (excluded && current.kind == TxKind.Lent) lentBook = lentBook.cancelLend(current.payee, current.amountPaise)
        replace(current.copy(excluded = excluded))
        if (excluded) "Not a transaction · removed from totals" else "Counted again"
    }

    /** "Mark repaid" on Owed to you: settles the rest in cash, nothing added as income. */
    fun markRepaid(debtor: Debtor): String = write {
        lentBook = lentBook.repay(debtor.name, debtor.outstandingPaise, today).first
        "${debtor.name} paid back ${formatRupees(debtor.outstandingPaise)}"
    }

    fun writeOff(debtor: Debtor): String = write {
        lentBook = lentBook.writeOff(debtor.name, today)
        "Wrote off ${formatRupees(debtor.outstandingPaise)} from ${debtor.name}"
    }

    // ---- Needs you (§15.2) -------------------------------------------------------------------

    /** Teach (§7): the message becomes a transaction now; the template would handle the next one. */
    fun learn(item: NeedsItem.Unreadable, amountPaise: Long, moneyIn: Boolean): String = write {
        needs.remove(item)
        val kind = if (moneyIn) TxKind.Income else TxKind.Spend
        val category = if (moneyIn) null else CategoryRules.categoryFor(item.payee, payeeRules)
        insert(Tx(nextId++, item.payee, item.account, clock().minusDays(item.daysAgo.toLong()), amountPaise, kind, category, sms = item.body))
        "Learned · ${formatRupees(amountPaise)} ${if (moneyIn) "received" else "spent"} added"
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
    fun pickAccount(item: NeedsItem.WhichAccount, account: String?): String = write {
        needs.remove(item)
        if (account == null) return@write "Ignored · neither account is yours"
        insert(
            Tx(
                nextId++, item.payee, account, clock().minusDays(item.daysAgo.toLong()), item.amountPaise, TxKind.Spend,
                CategoryRules.categoryFor(item.payee, payeeRules), sms = item.sms,
            ),
        )
        "Added ${formatRupees(item.amountPaise)} to $account"
    }

    /** Self-transfer pair (§9): both rows stay, both stop counting. */
    fun resolveTransfer(item: NeedsItem.PossibleTransfer, isTransfer: Boolean): String = write {
        needs.remove(item)
        if (!isTransfer) return@write "Kept as separate payments"
        transactions.replaceAll { if (it.id == item.outTxId || it.id == item.inTxId) it.copy(kind = TxKind.Transfer) else it }
        "Paired as self-transfer · not counted"
    }

    fun tagPayee(item: NeedsItem, categoryId: String): String = write {
        needs.remove(item)
        val payee = when (item) {
            is NeedsItem.NewPayee -> item.payee
            is NeedsItem.Bulk -> item.payee
            else -> return@write ""
        }
        val sample = transactions.firstOrNull { it.kind == TxKind.Spend && CategoryRules.payeeKey(it.payee) == CategoryRules.payeeKey(payee) }
        if (sample == null) {
            payeeRules[CategoryRules.payeeKey(payee)] = categoryId
            "${category(categoryId)?.name} for future $payee payments"
        } else {
            setCategory(sample, categoryId, allLikeThis = true)
        }
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

    /** Newest first. */
    private fun List<Tx>.sortedForList() = sortedWith(compareByDescending<Tx> { it.at }.thenByDescending { it.id })
}
