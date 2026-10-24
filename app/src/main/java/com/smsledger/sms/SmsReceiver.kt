package com.smsledger.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smsledger.SmsLedgerApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live capture. Rather than parsing the broadcast's PDUs, it triggers an inbox catch-up scan once
 * the default SMS app has saved the message, so every message is read from one source with one
 * key and never counted twice. Phones that kill background work are covered by the catch-up scan
 * that runs every time the app opens.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as SmsLedgerApplication
        val pending = goAsync()
        app.scope.launch {
            try {
                delay(FIRST_SCAN_MS)
                app.catchUp()
                delay(SECOND_SCAN_MS)
                app.catchUp()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // The default SMS app usually writes the inbox row within a second or two. Stay under ~10 s total.
        const val FIRST_SCAN_MS = 2_000L
        const val SECOND_SCAN_MS = 5_000L
    }
}
