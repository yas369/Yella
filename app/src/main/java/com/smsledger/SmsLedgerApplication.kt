package com.smsledger

import android.app.Application
import com.smsledger.data.LedgerStore
import com.smsledger.data.RoomLedgerDisk

/** Holds the one [LedgerStore] for the process, so it survives activity recreation. */
class SmsLedgerApplication : Application() {
    val store: LedgerStore by lazy { LedgerStore(RoomLedgerDisk(this)) }
}
