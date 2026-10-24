package com.smsledger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
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

    @Transaction
    suspend fun clearAll() {
        clearTransactions()
        clearLentEvents()
        clearPayeeRules()
    }
}

@Database(entities = [TxEntity::class, LentEventEntity::class, PayeeRuleEntity::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        fun open(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db").build()
    }
}
