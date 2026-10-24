package com.smsledger.data

import android.content.Context
import com.smsledger.core.ledger.Debt
import com.smsledger.core.ledger.LentBook
import com.smsledger.core.ledger.LentEvent
import com.smsledger.core.ledger.LentEventKind
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

/** What is saved: the user's own entries, lent history and payee rules. Nothing else. */
data class LedgerData(
    val transactions: List<Tx> = emptyList(),
    val lentBook: LentBook = LentBook(),
    val payeeRules: Map<String, String> = emptyMap(),
)

/** Small settings kept in SharedPreferences. */
data class Prefs(
    val onboarded: Boolean = false,
    val limitPaise: Long = DEFAULT_LIMIT_PAISE,
    /** null = follow the system theme. */
    val darkOverride: Boolean? = null,
)

/** Pre-filled in the first-run limit field; the user confirms or changes it. */
const val DEFAULT_LIMIT_PAISE = 50_000L

/** Where the ledger lives. The app uses [RoomLedgerDisk]; tests use an in-memory one. */
interface LedgerDisk {
    suspend fun load(): Pair<LedgerData, Prefs>
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
        )
        val dark = prefs.getInt(KEY_DARK, -1)
        val settings = Prefs(
            onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
            limitPaise = prefs.getLong(KEY_LIMIT, DEFAULT_LIMIT_PAISE),
            darkOverride = if (dark < 0) null else dark == 1,
        )
        return data to settings
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
        writer.launch { dao.save(rows, deleted, lent, rules) }
    }

    override fun savePrefs(prefs: Prefs) {
        this.prefs.edit()
            .putBoolean(KEY_ONBOARDED, prefs.onboarded)
            .putLong(KEY_LIMIT, prefs.limitPaise)
            .putInt(KEY_DARK, when (prefs.darkOverride) { null -> -1; false -> 0; true -> 1 })
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
    )

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
    )

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_LIMIT = "limit_paise"
        const val KEY_DARK = "dark_override"
    }
}
