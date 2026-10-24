package com.smsledger

import android.app.Application
import com.smsledger.core.limit.limitStatus
import com.smsledger.data.LedgerStore
import com.smsledger.data.RoomLedgerDisk
import com.smsledger.sms.SmsAccess
import com.smsledger.sms.SmsInbox
import com.smsledger.widget.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Holds the one [LedgerStore] for the process, so it survives activity recreation. */
class SmsLedgerApplication : Application() {
    val store: LedgerStore by lazy { LedgerStore(RoomLedgerDisk(this)) }

    /** Main-thread scope for store work started outside a screen (receiver, catch-up). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val scanLock = Mutex()

    /**
     * Catch-up scan (§2, plan T12): reads inbox messages newer than the last scan (with a day of
     * overlap, since ingest is idempotent) and ingests them. Also re-reads the whole inbox once
     * after a new account is confirmed as mine. Safe to call from anywhere; scans never overlap.
     */
    suspend fun catchUp() = scanLock.withLock {
        if (!store.loaded) store.load()
        if (!store.automatic || !SmsAccess.granted(this)) return@withLock
        val full = store.rescanFromMillis != null
        val since = if (full) 0L else store.lastScanMillis - OVERLAP_MS
        val read = withContext(Dispatchers.IO) { SmsInbox.read(this@SmsLedgerApplication, since) }
        store.ingestScan(read.messages, read.newestMillis)
        if (full) store.rescanFromMillis = null
        val spent = store.spentTodayPaise
        WidgetSnapshot.publish(this, limitStatus(spent, store.limitPaise), spent, store.limitPaise)
    }

    private companion object {
        const val OVERLAP_MS = 24 * 60 * 60 * 1000L
    }
}
