package com.smsledger.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import com.smsledger.core.sms.RawSms
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** SMS permissions (§1): restricted by Play; asked only after the disclosure screen. */
object SmsAccess {
    val PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    fun granted(context: Context): Boolean =
        PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}

/** Reads the phone's SMS inbox. Messages stay in memory; the store keeps only confirmed senders' messages. */
object SmsInbox {

    data class Read(val messages: List<RawSms>, val newestMillis: Long)

    /** Every inbox message newer than [sinceMillis], oldest first. Must run off the main thread. */
    fun read(context: Context, sinceMillis: Long): Read {
        if (!SmsAccess.granted(context)) return Read(emptyList(), sinceMillis)
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val zone = ZoneId.systemDefault()
        val out = ArrayList<RawSms>()
        var newest = sinceMillis
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(sinceMillis.coerceAtLeast(0).toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val address = cursor.getString(addressCol) ?: continue
                val body = cursor.getString(bodyCol) ?: continue
                val date = cursor.getLong(dateCol)
                // Provider id + time + body: stable across rescans, distinct for two identical SMS (§8).
                val key = "${cursor.getLong(idCol)}|$date|${body.hashCode()}"
                out += RawSms(key, address, body, LocalDateTime.ofInstant(Instant.ofEpochMilli(date), zone))
                if (date > newest) newest = date
            }
        }
        return Read(out, newest)
    }
}
