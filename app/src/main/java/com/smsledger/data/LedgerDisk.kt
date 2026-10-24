package com.smsledger.data

import android.content.Context
import com.smsledger.core.ledger.Debt
import com.smsledger.core.ledger.LentBook
import com.smsledger.core.ledger.LentEvent
import com.smsledger.core.ledger.LentEventKind
import com.smsledger.core.sms.Account
import com.smsledger.core.sms.LearnedTemplate
import com.smsledger.core.sms.SenderKind
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What is saved: the user's own entries, lent history, payee rules and SMS capture state. */
data class LedgerData(
    val transactions: List<Tx> = emptyList(),
    val lentBook: LentBook = LentBook(),
    val payeeRules: Map<String, String> = emptyMap(),
    val sms: SmsData = SmsData(),
)

/** A discovered account and the user's answer (§4). */
data class AccountState(val account: Account, val mine: Boolean)

/** A learned "Couldn't read" answer and the message it came from (§7). */
data class TemplateRecord(val template: LearnedTemplate, val example: String)

/**
 * SMS capture state (§3–§7). Only confirmed bank and wallet messages are ever kept, inside
 * transactions and To-review items; discovery reads everything else in memory only.
 */
data class SmsData(
    val accounts: List<AccountState> = emptyList(),
    val senders: Map<String, SenderKind> = emptyMap(),
    val templates: Map<String, TemplateRecord> = emptyMap(),
    val needs: List<NeedsItem> = emptyList(),
)

/** Small settings kept in SharedPreferences. */
data class Prefs(
    val onboarded: Boolean = false,
    val limitPaise: Long = DEFAULT_LIMIT_PAISE,
    /** null = follow the system theme. */
    val darkOverride: Boolean? = null,
    /** Automatic mode (§2): SMS capture on. Off until the user grants SMS access and confirms accounts. */
    val automatic: Boolean = false,
    /** Newest inbox message already scanned (epoch millis), for the catch-up scan. */
    val lastScanMillis: Long = 0,
    /** §13 alerts: limit alerts at 80% / 100%, and the lent reminder on the 1st. */
    val limitAlerts: Boolean = true,
    val lentReminder: Boolean = true,
    /** Day (epoch day) the [alertsSent] set belongs to; a new day starts empty. */
    val alertDay: Long = 0,
    val alertsSent: String = "",
    /** "2026-11" when the lent reminder last went out. */
    val lentReminderMonth: String = "",
)

/** Pre-filled in the first-run limit field; the user confirms or changes it. */
const val DEFAULT_LIMIT_PAISE = 50_000L

/** Where the ledger lives. The app uses [RoomLedgerDisk]; tests use an in-memory one. */
interface LedgerDisk {
    suspend fun load(): Pair<LedgerData, Prefs>
    /** Message keys already ingested, so a rescan never adds a message twice. */
    suspend fun processedKeys(): Set<String> = emptySet()
    /** Records ingested message keys. Append-only; kept apart from [save] so big imports stay cheap. */
    fun markProcessed(keys: Collection<String>) {}
    /** Writes what changed between [before] and [after], in call order, off the main thread. */
    fun save(before: LedgerData, after: LedgerData)
    fun savePrefs(prefs: Prefs)
    /** Erases every entry and setting. */
    fun clear()
}

/** Rows to upsert and ids to delete so storage matches [after]. */
internal fun txChanges(before: List<Tx>, after: List<Tx>): Pair<List<Tx>, List<Long>> {
    val old = before.associateBy { it.id }
    val changed = after.filter { old[it.id] != it }
    val afterIds = after.mapTo(HashSet()) { it.id }
    return changed to before.map { it.id }.filterNot { it in afterIds }
}

class RoomLedgerDisk(context: Context) : LedgerDisk {
    private val dao = LedgerDatabase.open(context).dao()
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    // One writer, so saves land in the order they were made. Lives as long as the process.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val zone: ZoneId get() = ZoneId.systemDefault()

    override suspend fun load(): Pair<LedgerData, Prefs> {
        val data = LedgerData(
            transactions = dao.transactions().map { it.toTx() },
            lentBook = LentBook.of(
                dao.lentEvents().groupBy { it.person }.map { (person, rows) ->
                    Debt(person, rows.map { LentEvent(LentEventKind.valueOf(it.kind), it.amountPaise, LocalDate.ofEpochDay(it.epochDay)) })
                },
            ),
            payeeRules = dao.payeeRules().associate { it.payeeKey to it.categoryId },
            sms = SmsData(
                accounts = dao.accounts().map { AccountState(Account(it.brand, it.digits), it.mine) },
                senders = dao.senders().mapNotNull { row -> runCatching { row.brand to SenderKind.valueOf(row.kind) }.getOrNull() }.toMap(),
                templates = dao.templates().associate {
                    it.key to TemplateRecord(LearnedTemplate(it.key, it.isTransaction, it.moneyIn, it.amountIndex), it.example)
                },
                needs = dao.needs().mapNotNull { it.toNeed() },
            ),
        )
        val dark = prefs.getInt(KEY_DARK, -1)
        val settings = Prefs(
            onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
            limitPaise = prefs.getLong(KEY_LIMIT, DEFAULT_LIMIT_PAISE),
            darkOverride = if (dark < 0) null else dark == 1,
            automatic = prefs.getBoolean(KEY_AUTOMATIC, false),
            lastScanMillis = prefs.getLong(KEY_LAST_SCAN, 0),
            limitAlerts = prefs.getBoolean(KEY_LIMIT_ALERTS, true),
            lentReminder = prefs.getBoolean(KEY_LENT_REMINDER, true),
            alertDay = prefs.getLong(KEY_ALERT_DAY, 0),
            alertsSent = prefs.getString(KEY_ALERTS_SENT, "").orEmpty(),
            lentReminderMonth = prefs.getString(KEY_LENT_MONTH, "").orEmpty(),
        )
        return data to settings
    }

    override suspend fun processedKeys(): Set<String> = dao.processedKeys().toHashSet()

    override fun markProcessed(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val rows = keys.map { ProcessedSmsEntity(it) }
        writer.launch { dao.insertProcessed(rows) }
    }

    override fun save(before: LedgerData, after: LedgerData) {
        if (before == after) return
        val (changed, deleted) = txChanges(before.transactions, after.transactions)
        val lent = if (before.lentBook != after.lentBook) {
            after.lentBook.all.flatMap { debt ->
                debt.events.mapIndexed { i, e -> LentEventEntity(person = debt.person, seq = i, kind = e.kind.name, amountPaise = e.amountPaise, epochDay = e.date.toEpochDay()) }
            }
        } else {
            null
        }
        val rules = if (before.payeeRules != after.payeeRules) after.payeeRules.map { (k, v) -> PayeeRuleEntity(k, v) } else null
        val rows = changed.map { it.toEntity() }
        val b = before.sms
        val a = after.sms
        val accounts = if (b.accounts != a.accounts) a.accounts.map { AccountEntity(it.account.key, it.account.brand, it.account.digits, it.mine) } else null
        val senders = if (b.senders != a.senders) a.senders.map { (brand, kind) -> SenderEntity(brand, kind.name) } else null
        val templates = if (b.templates != a.templates) {
            a.templates.values.map { TemplateEntity(it.template.key, it.template.isTransaction, it.template.moneyIn, it.template.amountIndex, it.example) }
        } else {
            null
        }
        val needs = if (b.needs != a.needs) a.needs.map { it.toEntity() } else null
        writer.launch {
            dao.save(rows, deleted, lent, rules)
            if (accounts != null || senders != null || templates != null || needs != null) {
                dao.saveSms(accounts, senders, templates, needs, emptyList())
            }
        }
    }

    override fun savePrefs(prefs: Prefs) {
        this.prefs.edit()
            .putBoolean(KEY_ONBOARDED, prefs.onboarded)
            .putLong(KEY_LIMIT, prefs.limitPaise)
            .putInt(KEY_DARK, when (prefs.darkOverride) { null -> -1; false -> 0; true -> 1 })
            .putBoolean(KEY_AUTOMATIC, prefs.automatic)
            .putLong(KEY_LAST_SCAN, prefs.lastScanMillis)
            .putBoolean(KEY_LIMIT_ALERTS, prefs.limitAlerts)
            .putBoolean(KEY_LENT_REMINDER, prefs.lentReminder)
            .putLong(KEY_ALERT_DAY, prefs.alertDay)
            .putString(KEY_ALERTS_SENT, prefs.alertsSent)
            .putString(KEY_LENT_MONTH, prefs.lentReminderMonth)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
        writer.launch { dao.clearAll() }
    }

    private fun TxEntity.toTx() = Tx(
        id = id,
        payee = payee,
        account = account,
        at = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), zone),
        amountPaise = amountPaise,
        kind = TxKind.valueOf(kind),
        categoryId = categoryId,
        sms = sms,
        note = note,
        excluded = excluded,
        accountKey = accountKey,
        upiRef = upiRef,
    )

    private fun millis(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()
    private fun time(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    private fun NeedsItem.toEntity(): NeedEntity = when (this) {
        is NeedsItem.Unreadable -> NeedEntity(id, "unreadable", sender, body, millis(at), accountKey, account, payee, 0, false, "", 0, 0, null)
        is NeedsItem.WhichAccount -> NeedEntity(id, "which_account", prompt, sms, millis(at), null, prompt, payee, amountPaise, moneyIn, optionKeys.joinToString("\n"), 0, 0, upiRef)
        is NeedsItem.NewAccount -> NeedEntity(id, "new_account", sender, body, millis(at), accountKey, label, "", 0, false, "", 0, 0, null)
        is NeedsItem.PossibleTransfer -> NeedEntity(id, "transfer", "", sms, 0, null, outLine, inLine, 0, false, gap, outTxId, inTxId, null)
        is NeedsItem.NewPayee -> NeedEntity(id, "new_payee", "", sms, 0, null, whenLabel, payee, amountPaise, false, "", txId, 0, null)
        is NeedsItem.Bulk -> NeedEntity(id, "bulk", "", sms, 0, null, "", payee, 0, false, "", 0, 0, null)
    }

    private fun NeedEntity.toNeed(): NeedsItem? = when (type) {
        "unreadable" -> NeedsItem.Unreadable(id, sender, body, label, payee, time(atMillis), accountKey)
        "which_account" -> {
            val keys = options.split('\n').filter { it.isNotEmpty() }
            NeedsItem.WhichAccount(
                id, label, amountPaise, time(atMillis), payee,
                keys.map { Account.fromKey(it)?.shortLabel ?: it }, body, keys, moneyIn, upiRef,
            )
        }
        "new_account" -> NeedsItem.NewAccount(id, accountKey.orEmpty(), label, sender, body, time(atMillis))
        "transfer" -> NeedsItem.PossibleTransfer(id, txId, otherTxId, label, payee, options, body)
        "new_payee" -> NeedsItem.NewPayee(id, txId, payee, amountPaise, label, body)
        "bulk" -> NeedsItem.Bulk(id, payee, body)
        else -> null
    }

    private fun Tx.toEntity() = TxEntity(
        id = id,
        payee = payee,
        account = account,
        atMillis = at.atZone(zone).toInstant().toEpochMilli(),
        amountPaise = amountPaise,
        kind = kind.name,
        categoryId = categoryId,
        sms = sms,
        note = note,
        excluded = excluded,
        accountKey = accountKey,
        upiRef = upiRef,
    )

    private companion object {
        const val KEY_LIMIT_ALERTS = "limit_alerts"
        const val KEY_LENT_REMINDER = "lent_reminder"
        const val KEY_ALERT_DAY = "alert_day"
        const val KEY_ALERTS_SENT = "alerts_sent"
        const val KEY_LENT_MONTH = "lent_reminder_month"
        const val KEY_AUTOMATIC = "automatic"
        const val KEY_LAST_SCAN = "last_scan_millis"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_LIMIT = "limit_paise"
        const val KEY_DARK = "dark_override"
    }
}
