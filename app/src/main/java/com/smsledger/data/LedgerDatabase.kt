package com.smsledger.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/** One ledger row. Amounts are paise; time is epoch millis in the phone's zone at save time. */
@Entity(tableName = "transactions")
data class TxEntity(
    @PrimaryKey val id: Long,
    val payee: String,
    val account: String,
    val atMillis: Long,
    val amountPaise: Long,
    /** [com.smsledger.ui.model.TxKind] name. */
    val kind: String,
    val categoryId: String?,
    val sms: String?,
    val note: String,
    val excluded: Boolean,
    /** Account key for SMS-backed rows (added in v2). */
    val accountKey: String? = null,
    /** UPI reference from the SMS (added in v2). */
    val upiRef: String? = null,
)

/** One event in a person's lent history; [seq] keeps their order. */
@Entity(tableName = "lent_events")
data class LentEventEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val person: String,
    val seq: Int,
    /** [com.smsledger.core.ledger.LentEventKind] name. */
    val kind: String,
    val amountPaise: Long,
    val epochDay: Long,
)

/** "Tag once per payee and it sticks" (§11). */
@Entity(tableName = "payee_rules")
data class PayeeRuleEntity(@PrimaryKey val payeeKey: String, val categoryId: String)

/** A discovered account and the user's answer (§4). Not-mine accounts are kept so they're never asked again. */
@Entity(tableName = "accounts")
data class AccountEntity(@PrimaryKey val key: String, val brand: String, val digits: String, val mine: Boolean)

/** What each sender brand is (§3), decided at discovery. */
@Entity(tableName = "senders")
data class SenderEntity(@PrimaryKey val brand: String, val kind: String)

/** A learned "Couldn't read" answer, keyed by the full template (§7). */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val key: String,
    val isTransaction: Boolean,
    val moneyIn: Boolean,
    val amountIndex: Int,
    /** The message it was learned from, shown in Settings → Learned messages. */
    val example: String,
)

/** Ingest is idempotent: a message key seen once is never processed again. No message content. */
@Entity(tableName = "processed_sms")
data class ProcessedSmsEntity(@PrimaryKey val key: String)

/** A saved "To review" item, so nothing raised by capture is lost on restart (§7). */
@Entity(tableName = "needs")
data class NeedEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val sender: String,
    val body: String,
    val atMillis: Long,
    val accountKey: String?,
    val label: String,
    val payee: String,
    val amountPaise: Long,
    val moneyIn: Boolean,
    /** Account keys for "Which account?", separated by '
'. */
    val options: String,
    val txId: Long,
    val otherTxId: Long,
    val upiRef: String?,
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions")
    suspend fun transactions(): List<TxEntity>

    @Query("SELECT * FROM lent_events ORDER BY person, seq")
    suspend fun lentEvents(): List<LentEventEntity>

    @Query("SELECT * FROM payee_rules")
    suspend fun payeeRules(): List<PayeeRuleEntity>

    @Upsert
    suspend fun upsert(rows: List<TxEntity>)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactions(ids: List<Long>)

    @Query("DELETE FROM lent_events")
    suspend fun clearLentEvents()

    @Insert
    suspend fun insertLentEvents(rows: List<LentEventEntity>)

    @Query("DELETE FROM payee_rules")
    suspend fun clearPayeeRules()

    @Insert
    suspend fun insertPayeeRules(rows: List<PayeeRuleEntity>)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    /** The lent book and rules are small, so they are rewritten whole when they change. */
    @Transaction
    suspend fun save(changed: List<TxEntity>, deletedIds: List<Long>, lent: List<LentEventEntity>?, rules: List<PayeeRuleEntity>?) {
        if (changed.isNotEmpty()) upsert(changed)
        if (deletedIds.isNotEmpty()) deleteTransactions(deletedIds)
        if (lent != null) { clearLentEvents(); insertLentEvents(lent) }
        if (rules != null) { clearPayeeRules(); insertPayeeRules(rules) }
    }

    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM senders") suspend fun senders(): List<SenderEntity>
    @Query("SELECT * FROM templates") suspend fun templates(): List<TemplateEntity>
    @Query("SELECT `key` FROM processed_sms") suspend fun processedKeys(): List<String>
    @Query("SELECT * FROM needs ORDER BY atMillis DESC") suspend fun needs(): List<NeedEntity>

    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM senders") suspend fun clearSenders()
    @Query("DELETE FROM templates") suspend fun clearTemplates()
    @Query("DELETE FROM needs") suspend fun clearNeeds()
    @Query("DELETE FROM processed_sms") suspend fun clearProcessed()
    @Insert suspend fun insertAccounts(rows: List<AccountEntity>)
    @Insert suspend fun insertSenders(rows: List<SenderEntity>)
    @Insert suspend fun insertTemplates(rows: List<TemplateEntity>)
    @Insert suspend fun insertNeeds(rows: List<NeedEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertProcessed(rows: List<ProcessedSmsEntity>)

    /** SMS state is small, so each table is rewritten whole when it changes (null = unchanged). */
    @Transaction
    suspend fun saveSms(
        accounts: List<AccountEntity>?,
        senders: List<SenderEntity>?,
        templates: List<TemplateEntity>?,
        needs: List<NeedEntity>?,
        processed: List<ProcessedSmsEntity>,
    ) {
        if (accounts != null) { clearAccounts(); insertAccounts(accounts) }
        if (senders != null) { clearSenders(); insertSenders(senders) }
        if (templates != null) { clearTemplates(); insertTemplates(templates) }
        if (needs != null) { clearNeeds(); insertNeeds(needs) }
        if (processed.isNotEmpty()) insertProcessed(processed)
    }

    @Transaction
    suspend fun clearAll() {
        clearTransactions()
        clearLentEvents()
        clearPayeeRules()
        clearAccounts()
        clearSenders()
        clearTemplates()
        clearNeeds()
        clearProcessed()
    }
}

@Database(
    entities = [
        TxEntity::class, LentEventEntity::class, PayeeRuleEntity::class,
        AccountEntity::class, SenderEntity::class, TemplateEntity::class, ProcessedSmsEntity::class, NeedEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // Additive only: v1 installs keep every manual entry. Never use destructive fallback.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        fun open(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db").build()
    }
}
