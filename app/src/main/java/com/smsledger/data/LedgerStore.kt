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
import com.smsledger.core.limit.AlertPolicy
import com.smsledger.core.limit.LimitAlert
import com.smsledger.core.limit.limitStatus
import com.smsledger.core.money.formatRupees
import com.smsledger.core.money.formatRupeesWhole
import com.smsledger.core.sms.Account
import com.smsledger.core.sms.BankNames
import com.smsledger.core.sms.Candidate
import com.smsledger.core.sms.Confidence
import com.smsledger.core.sms.Direction
import com.smsledger.core.sms.DiscoveryResult
import com.smsledger.core.sms.IngestContext
import com.smsledger.core.sms.LearnedTemplate
import com.smsledger.core.sms.Outcome
import com.smsledger.core.sms.ParseResult
import com.smsledger.core.sms.Patterns
import com.smsledger.core.sms.RawSms
import com.smsledger.core.sms.SenderKind
import com.smsledger.core.sms.SenderNormalizer
import com.smsledger.core.sms.SmsDefaults
import com.smsledger.core.sms.SmsDiscovery
import com.smsledger.core.sms.SmsIngest
import com.smsledger.core.sms.Templatizer
import com.smsledger.core.sms.TransactionParser
import java.time.Duration
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

/** Account shown on rows the user added by hand. */
const val MANUAL = "Manual"

/** New-payee questions are raised for payments this recent; older imports go straight in (§15). */
private const val NEW_PAYEE_DAYS = 30L

/** This many untagged payments to one payee become one "Tag in one go" card (§15.5). */
private const val BULK_MIN = 3

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
    /** Questions raised by SMS capture (§15.2). Saved, so nothing raised is lost on restart (§7). */
    val needs = mutableStateListOf<NeedsItem>()

    /** Automatic mode (§2): SMS capture on. */
    var automatic by mutableStateOf(false)
        private set
    /** Newest inbox message already scanned (epoch millis), for the catch-up scan. */
    var lastScanMillis = 0L
        private set
    /** Discovered accounts and the user's Mine / Not mine answers (§4). */
    val accounts = mutableStateListOf<AccountState>()
    /** Brand → what that sender is (§3). */
    val senders = mutableStateMapOf<String, SenderKind>()
    /** Full template key → learned answer (§7). */
    val templates = mutableStateMapOf<String, TemplateRecord>()
    private val processed = HashSet<String>()
    private var nextNeedId = 1L

    /** Accounts found by discovery, waiting for Mine / Not mine. In memory only. */
    var discovery by mutableStateOf<DiscoveryResult?>(null)
        private set
    private var discoveryInbox: List<RawSms> = emptyList()

    /** §13: daily-limit alerts and the lent reminder. */
    var limitAlerts by mutableStateOf(true)
        private set
    var lentReminder by mutableStateOf(true)
        private set
    private var alertDay = 0L
    private var alertsSent: Set<LimitAlert> = emptySet()
    private var lentReminderMonth: YearMonth? = null

    /** Set when an answer needs part of the inbox read again (a new account confirmed as mine). */
    var rescanFromMillis by mutableStateOf<Long?>(null)
    var lentBook by mutableStateOf(LentBook())
        private set
    /** Payee key → category id: "tag once per payee and it sticks" (§11). */
    val payeeRules = mutableStateMapOf<String, String>()

    private var nextId = 1L

    suspend fun load() {
        val (data, prefs) = disk.load()
        put(data)
        nextId = (data.transactions.maxOfOrNull { it.id } ?: 0L) + 1
        nextNeedId = (data.sms.needs.maxOfOrNull { it.id } ?: 0L) + 1
        processed.clear(); processed += disk.processedKeys()
        onboarded = prefs.onboarded
        limitPaise = prefs.limitPaise
        darkOverride = prefs.darkOverride
        automatic = prefs.automatic
        lastScanMillis = prefs.lastScanMillis
        limitAlerts = prefs.limitAlerts
        lentReminder = prefs.lentReminder
        alertDay = prefs.alertDay
        alertsSent = prefs.alertsSent.split(',').mapNotNull { name -> LimitAlert.entries.firstOrNull { it.name == name } }.toSet()
        lentReminderMonth = prefs.lentReminderMonth.takeIf { it.isNotEmpty() }?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        loaded = true
    }

    val today: LocalDate get() = clock().toLocalDate()

    // ---- Settings ------------------------------------------------------------------------------

    private fun prefs() = Prefs(
        onboarded, limitPaise, darkOverride, automatic, lastScanMillis,
        limitAlerts, lentReminder, alertDay, alertsSent.joinToString(",") { it.name }, lentReminderMonth?.toString().orEmpty(),
    )

    fun changeLimitAlerts(on: Boolean) {
        limitAlerts = on
        disk.savePrefs(prefs())
    }

    fun changeLentReminder(on: Boolean) {
        lentReminder = on
        disk.savePrefs(prefs())
    }

    /** §13: limit alerts due now (80% / 100%, each at most once a day). Marks them sent. */
    fun dueLimitAlerts(): List<LimitAlert> {
        if (!limitAlerts) return emptyList()
        val day = today.toEpochDay()
        if (day != alertDay) { alertDay = day; alertsSent = emptySet() }
        val due = AlertPolicy.due(limitStatus(spentTodayPaise, limitPaise), alertsSent)
        if (due.isNotEmpty()) {
            alertsSent = alertsSent + due + (if (LimitAlert.Reached in due) setOf(LimitAlert.Near) else emptySet())
            disk.savePrefs(prefs())
        }
        return due
    }

    /** §13: the lent reminder, on the 1st, once a month, only while someone owes you. Marks it sent. */
    fun lentReminderDue(): Boolean {
        if (!lentReminder || !AlertPolicy.lentReminderDue(today, lentReminderMonth, owedPaise)) return false
        lentReminderMonth = YearMonth.from(today)
        disk.savePrefs(prefs())
        return true
    }

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
        nextId = 1
        nextNeedId = 1
        processed.clear()
        discovery = null
        discoveryInbox = emptyList()
        limitPaise = DEFAULT_LIMIT_PAISE
        darkOverride = null
        automatic = false
        lastScanMillis = 0
        limitAlerts = true
        lentReminder = true
        alertDay = 0
        alertsSent = emptySet()
        lentReminderMonth = null
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
    data class Snapshot(
        val transactions: List<Tx>,
        val needs: List<NeedsItem>,
        val lentBook: LentBook,
        val rules: Map<String, String>,
        val accounts: List<AccountState> = emptyList(),
        val senders: Map<String, SenderKind> = emptyMap(),
        val templates: Map<String, TemplateRecord> = emptyMap(),
    )

    fun snapshot() = Snapshot(transactions.toList(), needs.toList(), lentBook, payeeRules.toMap(), accounts.toList(), senders.toMap(), templates.toMap())

    fun restore(s: Snapshot) = write {
        transactions.clear(); transactions += s.transactions
        needs.clear(); needs += s.needs
        lentBook = s.lentBook
        payeeRules.clear(); payeeRules += s.rules
        accounts.clear(); accounts += s.accounts
        senders.clear(); senders += s.senders
        templates.clear(); templates += s.templates
    }

    private fun data() = LedgerData(
        transactions.toList(), lentBook, payeeRules.toMap(),
        SmsData(accounts.toList(), senders.toMap(), templates.toMap(), needs.toList()),
    )

    private fun put(data: LedgerData) {
        transactions.clear(); transactions += data.transactions.sortedForList()
        lentBook = data.lentBook
        payeeRules.clear(); payeeRules += data.payeeRules
        accounts.clear(); accounts += data.sms.accounts
        senders.clear(); senders += data.sms.senders
        templates.clear(); templates += data.sms.templates
        needs.clear(); needs += data.sms.needs
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

    /**
     * Teach (§7): the message becomes a transaction now. "All like this" also saves the answer
     * against the full template, and every waiting message with the same template is read too.
     */
    fun learn(item: NeedsItem.Unreadable, amountPaise: Long, moneyIn: Boolean, allLikeThis: Boolean = true): String = write {
        needs.remove(item)
        insert(unreadableTx(item, amountPaise, moneyIn))
        if (!allLikeThis) return@write "${formatRupees(amountPaise)} ${if (moneyIn) "received" else "spent"} added"
        val template = Templatizer.template(item.body)
        val index = template.numbers.indexOfFirst { Patterns.toPaise(it.text.filter { c -> c.isDigit() || c == '.' }) == amountPaise }
        templates[template.key] = TemplateRecord(LearnedTemplate(template.key, isTransaction = true, moneyIn = moneyIn, amountIndex = index), item.body)
        val same = needs.filterIsInstance<NeedsItem.Unreadable>().filter { Templatizer.template(it.body).key == template.key }
        var more = 0
        same.forEach { other ->
            val amount = Templatizer.amountAt(Templatizer.template(other.body), index) ?: return@forEach
            needs.remove(other)
            insert(unreadableTx(other, amount, moneyIn))
            more++
        }
        val base = "Learned · ${formatRupees(amountPaise)} ${if (moneyIn) "received" else "spent"} added"
        if (more > 0) "$base · $more more read" else base
    }

    private fun unreadableTx(item: NeedsItem.Unreadable, amountPaise: Long, moneyIn: Boolean): Tx {
        val kind = if (moneyIn) TxKind.Income else TxKind.Spend
        val payee = item.payee.ifBlank { item.account }
        val category = if (moneyIn) null else CategoryRules.categoryFor(payee, payeeRules)
        return Tx(nextId++, payee, item.account, item.at, amountPaise, kind, category, sms = item.body, accountKey = item.accountKey)
    }

    /** "Not a transaction · ignore messages like this": saved as a template so the next one is skipped too. */
    fun ignoreLikeThis(item: NeedsItem.Unreadable): String = write {
        val key = Templatizer.template(item.body).key
        templates[key] = TemplateRecord(LearnedTemplate(key, isTransaction = false, moneyIn = false, amountIndex = -1), item.body)
        val same = needs.filterIsInstance<NeedsItem.Unreadable>().filter { Templatizer.template(it.body).key == key }
        needs.removeAll(same.toSet() + item)
        "Messages like this will be ignored"
    }

    fun notATransaction(item: NeedsItem): String {
        needs.remove(item)
        return "Marked not a transaction"
    }

    /** Ambiguous 3-digit mask resolved (§4): the debit is added to the chosen account. */
    fun pickAccount(item: NeedsItem.WhichAccount, account: String?): String = write {
        needs.remove(item)
        if (account == null) return@write "Ignored · neither account is yours"
        val key = item.optionKeys.getOrNull(item.options.indexOf(account))
        val kind = if (item.moneyIn) TxKind.Income else TxKind.Spend
        insert(
            Tx(
                nextId++, item.payee, account, item.at, item.amountPaise, kind,
                if (item.moneyIn) null else CategoryRules.categoryFor(item.payee, payeeRules), sms = item.sms,
                accountKey = key, upiRef = item.upiRef,
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

    // ---- SMS capture (§2–§9) ------------------------------------------------------------------

    val mineAccounts: List<Account> get() = accounts.filter { it.mine }.map { it.account }

    /** First run / "Turn on" (§3): classify every sender and list accounts. Bodies stay in memory. */
    fun startDiscovery(inbox: List<RawSms>) {
        discoveryInbox = inbox
        discovery = SmsDiscovery.discover(inbox)
    }

    fun cancelDiscovery() {
        discovery = null
        discoveryInbox = emptyList()
    }

    /**
     * "Done" on account confirmation (§4): saves the answers, turns automatic mode on and reads
     * every past message from confirmed senders. Returns how many transactions were added.
     */
    fun confirmAccounts(answers: Map<Account, Boolean>, newestMillis: Long): Int {
        val found = discovery ?: return 0
        write {
            senders.putAll(found.senders)
            found.accounts.forEach { f ->
                val mine = answers[f.account] ?: true
                accounts.removeAll { it.account == f.account }
                accounts += AccountState(f.account, mine)
            }
        }
        val added = ingest(discoveryInbox)
        automatic = true
        lastScanMillis = maxOf(lastScanMillis, newestMillis)
        disk.savePrefs(prefs())
        cancelDiscovery()
        return added
    }

    /** Manual mode keeps everything except capture (§2). Turning on again goes through discovery. */
    fun turnOffAutomatic() {
        automatic = false
        disk.savePrefs(prefs())
    }

    /** Catch-up scan and live capture: ingest whatever is new, then remember how far we read. */
    fun ingestScan(messages: List<RawSms>, newestMillis: Long): Int {
        if (!automatic) return 0
        val added = ingest(messages)
        if (newestMillis > lastScanMillis) {
            lastScanMillis = newestMillis
            disk.savePrefs(prefs())
        }
        return added
    }

    /** Settings → Accounts: change a Mine / Not mine answer. Past rows stay; new SMS follow the answer. */
    fun setAccountMine(account: Account, mine: Boolean): String = write {
        val index = accounts.indexOfFirst { it.account == account }
        if (index >= 0) accounts[index] = AccountState(account, mine) else accounts += AccountState(account, mine)
        if (mine) "${account.label} is yours" else "${account.label} won't be counted"
    }

    /** "New account found" (§4): saves the answer; if mine, its messages are read on the next scan. */
    fun answerNewAccount(item: NeedsItem.NewAccount, mine: Boolean): String = write {
        val account = Account.fromKey(item.accountKey) ?: return@write ""
        needs.removeAll { it is NeedsItem.NewAccount && it.accountKey == item.accountKey }
        accounts.removeAll { it.account == account }
        accounts += AccountState(account, mine)
        senders[account.brand] = if (account.isWallet) SenderKind.Wallet else SenderKind.Bank
        if (mine) {
            rescanFromMillis = Long.MIN_VALUE
            "${account.label} added · its messages are being read"
        } else {
            "${account.label} won't be counted"
        }
    }

    /** Settings → Learned messages: forget a template. Rows it read go back to "Couldn't read" (§7). */
    fun forgetTemplate(key: String): String = write {
        val record = templates.remove(key) ?: return@write ""
        if (record.template.isTransaction) {
            val produced = transactions.filter { tx ->
                tx.sms != null && TransactionParser.parse(tx.sms) is ParseResult.Unreadable && Templatizer.template(tx.sms).key == key
            }
            transactions.removeAll(produced.toSet())
            produced.forEach { tx ->
                needs += NeedsItem.Unreadable(nextNeedId++, tx.account, tx.sms!!, tx.account, tx.payee, tx.at, tx.accountKey)
            }
            "Forgot this message format · ${produced.size} back in To review"
        } else {
            "Forgot this message format"
        }
    }

    /**
     * Idempotent ingest (§6–§9): every message key is read once. New rows are batched and the list
     * is sorted once, so a first import of thousands of messages stays fast.
     */
    fun ingest(messages: List<RawSms>): Int {
        val fresh = messages.filter { it.key !in processed }.sortedBy { it.at }
        if (fresh.isEmpty()) return 0
        val done = ArrayList<String>(fresh.size)
        var added = 0
        write {
            val batch = ArrayList<Tx>()
            fresh.forEach { sms ->
                val outcome = SmsIngest.process(sms, context(sms, batch))
                if (outcome !is Outcome.NewAccount) done += sms.key
                if (apply(sms, outcome, batch)) added++
            }
            if (batch.isNotEmpty()) {
                val sorted = (transactions + batch).sortedForList()
                transactions.clear(); transactions += sorted
            }
            refreshBulk()
        }
        processed += done
        disk.markProcessed(done)
        return added
    }

    private fun Tx.direction(): Direction = if (kind == TxKind.Income) Direction.Credit else Direction.Debit

    private fun context(sms: RawSms, batch: List<Tx>): IngestContext {
        val near = (transactions.asSequence() + batch.asSequence()).filter { !it.excluded && it.kind != TxKind.Transfer }
        fun minutes(tx: Tx) = Duration.between(tx.at, sms.at).abs().toMinutes()
        val recent = near
            .filter { it.accountKey != null && minutes(it) <= SmsDefaults.TRANSFER_WINDOW_MINUTES }
            .map { Candidate(it.id, it.amountPaise, it.direction(), it.at, it.accountKey!!, it.upiRef) }.toList()
        val manual = near
            .filter { it.accountKey == null && it.account == MANUAL && minutes(it) <= SmsDefaults.MANUAL_MERGE_HOURS * 60 }
            .map { Candidate(it.id, it.amountPaise, it.direction(), it.at, MANUAL) }.toList()
        return IngestContext(
            senders = senders,
            mine = mineAccounts,
            notMine = accounts.filterNot { it.mine }.map { it.account },
            templates = templates.mapValues { it.value.template },
            recent = recent,
            manual = manual,
        )
    }

    /** Applies one decision. Returns true when a new ledger row was added. */
    private fun apply(sms: RawSms, outcome: Outcome, batch: MutableList<Tx>): Boolean {
        val brand = SenderNormalizer.brand(sms.sender) ?: sms.sender
        when (outcome) {
            Outcome.Ignore, is Outcome.Skip -> return false
            is Outcome.Label -> {
                update(outcome.txId, batch) { tx -> if (tx.payee.looksGeneric(tx)) tx.copy(payee = outcome.payee) else tx }
                return false
            }
            is Outcome.Unreadable -> {
                val account = outcome.account
                needs += NeedsItem.Unreadable(
                    nextNeedId++, account?.shortLabel ?: BankNames.name(brand), sms.body, account?.shortLabel ?: BankNames.name(brand),
                    Patterns.counterparty(sms.body).orEmpty(), sms.at, account?.key,
                )
                return false
            }
            is Outcome.AskAccount -> {
                needs += NeedsItem.WhichAccount(
                    nextNeedId++, "${BankNames.short(outcome.brand)} ••${outcome.digits ?: "?"}", outcome.amountPaise, sms.at,
                    outcome.counterparty ?: BankNames.name(outcome.brand), outcome.candidates.map { it.shortLabel }, sms.body,
                    outcome.candidates.map { it.key }, outcome.direction == Direction.Credit, outcome.upiRef,
                )
                return false
            }
            is Outcome.NewAccount -> {
                if (needs.none { it is NeedsItem.NewAccount && it.accountKey == outcome.account.key }) {
                    needs += NeedsItem.NewAccount(nextNeedId++, outcome.account.key, outcome.account.label, brand, sms.body, sms.at)
                }
                return false
            }
            is Outcome.Add -> return add(sms, outcome, batch)
        }
    }

    private fun add(sms: RawSms, out: Outcome.Add, batch: MutableList<Tx>): Boolean {
        val account = out.account
        out.mergeWithManual?.let { manualId ->
            // §8: the bank SMS supplies account and time; the manual entry keeps category and note.
            update(manualId, batch) { it.copy(account = account.shortLabel, accountKey = account.key, at = sms.at, sms = sms.body, upiRef = out.upiRef) }
            return false
        }
        val moneyIn = out.direction == Direction.Credit
        val payee = out.counterparty ?: if (account.isWallet && moneyIn) "Added to ${account.shortLabel}" else account.shortLabel
        var note = ""
        var kind = if (moneyIn) TxKind.Income else TxKind.Spend
        if (moneyIn && out.counterparty != null && lentBook.outstanding(payee) > 0) {
            // §9: a repayment from someone who owes you closes the debt instead of counting as income.
            val (book, income) = lentBook.repay(payee, out.amountPaise, sms.at.toLocalDate())
            lentBook = book
            note = if (income == 0L) "Repaid" else "Repaid + income"
        }
        val id = nextId++
        val pairing = out.transfer
        when (pairing?.confidence) {
            Confidence.Auto -> {
                kind = TxKind.Transfer
                update(pairing.otherId, batch) { it.copy(kind = TxKind.Transfer) }
            }
            Confidence.Possible -> {
                val other = find(pairing.otherId, batch)
                if (other != null) {
                    val (debit, credit) = if (moneyIn) other to null else null to other
                    val outLine = if (debit != null) "${formatRupees(debit.amountPaise)} out of ${debit.account}" else "${formatRupees(out.amountPaise)} out of ${account.shortLabel}"
                    val inLine = if (credit != null) "${formatRupees(credit.amountPaise)} into ${credit.account}" else "${formatRupees(out.amountPaise)} into ${account.shortLabel}"
                    val gapMin = Duration.between(other.at, sms.at).abs().toMinutes()
                    val fee = kotlin.math.abs(other.amountPaise - out.amountPaise)
                    needs += NeedsItem.PossibleTransfer(
                        nextNeedId++, if (moneyIn) other.id else id, if (moneyIn) id else other.id, outLine, inLine,
                        "$gapMin min apart" + if (fee > 0) " · fee ${formatRupees(fee)}" else "", sms.body,
                    )
                }
            }
            null -> Unit
        }
        val category = if (kind == TxKind.Spend) CategoryRules.categoryFor(payee, payeeRules) else null
        batch += Tx(id, payee, account.shortLabel, sms.at, out.amountPaise, kind, category, sms.body, note, accountKey = account.key, upiRef = out.upiRef)
        // §11/§15: a new payee is asked once; older imports don't flood the queue.
        if (kind == TxKind.Spend && category == null && Duration.between(sms.at, clock()).toDays() <= NEW_PAYEE_DAYS &&
            needs.none { (it as? NeedsItem.NewPayee)?.payee?.let(CategoryRules::payeeKey) == CategoryRules.payeeKey(payee) }
        ) {
            needs += NeedsItem.NewPayee(nextNeedId++, id, payee, out.amountPaise, com.smsledger.ui.model.dayLabel(sms.at.toLocalDate(), today), sms.body)
        }
        return true
    }

    /** Three or more untagged payments to one payee become one "Tag in one go" card (§15.5). */
    private fun refreshBulk() {
        val untagged = transactions.filter { it.kind == TxKind.Spend && it.categoryId == null && !it.excluded }
            .groupBy { CategoryRules.payeeKey(it.payee) }.filter { it.value.size >= BULK_MIN }
        untagged.forEach { (key, rows) ->
            if (needs.any { it is NeedsItem.Bulk && CategoryRules.payeeKey(it.payee) == key }) return@forEach
            needs.removeAll { it is NeedsItem.NewPayee && CategoryRules.payeeKey(it.payee) == key }
            needs += NeedsItem.Bulk(nextNeedId++, rows.first().payee, rows.first().sms ?: rows.first().payee)
        }
    }

    private fun find(id: Long, batch: List<Tx>): Tx? = batch.firstOrNull { it.id == id } ?: transactions.firstOrNull { it.id == id }

    private fun update(id: Long, batch: MutableList<Tx>, change: (Tx) -> Tx) {
        val inBatch = batch.indexOfFirst { it.id == id }
        if (inBatch >= 0) { batch[inBatch] = change(batch[inBatch]); return }
        val index = transactions.indexOfFirst { it.id == id }
        if (index >= 0) transactions[index] = change(transactions[index])
    }

    /** A VPA, phone number or account label: a merchant label can replace it (§8). */
    private fun String.looksGeneric(tx: Tx): Boolean = isBlank() || contains('@') || all { it.isDigit() || it == '+' } || this == tx.account

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
