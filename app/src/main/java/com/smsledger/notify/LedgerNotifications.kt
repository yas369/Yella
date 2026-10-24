package com.smsledger.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smsledger.MainActivity
import com.smsledger.R
import com.smsledger.core.limit.LimitAlert
import com.smsledger.core.limit.limitStatus
import com.smsledger.core.money.formatRupeesWhole
import com.smsledger.data.LedgerStore

/**
 * Spec §13 notifications. If the permission is denied the limit still works on screen; this
 * simply posts nothing (design.md §9: never nag on Home).
 */
object LedgerNotifications {
    private const val LIMIT_CHANNEL = "limit"
    private const val LENT_CHANNEL = "lent"
    private const val LIMIT_ID = 1
    private const val LENT_ID = 2

    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts whatever is due now. Safe to call after every scan or change. */
    fun postDue(context: Context, store: LedgerStore) {
        val alerts = store.dueLimitAlerts()
        val lent = store.lentReminderDue()
        if (!permitted(context)) return
        ensureChannels(context)
        alerts.lastOrNull()?.let { alert ->
            val status = limitStatus(store.spentTodayPaise, store.limitPaise)
            val (title, text) = when (alert) {
                LimitAlert.Near -> "80% of today's limit used" to "${formatRupeesWhole(status.remainingPaise)} left today"
                LimitAlert.Reached -> "Daily limit reached" to
                    if (status.overPaise > 0) "${formatRupeesWhole(status.overPaise)} over limit" else "₹0 left today"
            }
            post(context, LIMIT_CHANNEL, LIMIT_ID, title, text)
        }
        if (lent) {
            val people = store.debtors.size
            post(context, LENT_CHANNEL, LENT_ID, "${formatRupeesWhole(store.owedPaise)} owed to you", "by $people ${if (people == 1) "person" else "people"}")
        }
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(LIMIT_CHANNEL, "Daily limit", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(LENT_CHANNEL, "Owed to you", NotificationManager.IMPORTANCE_LOW))
    }

    @android.annotation.SuppressLint("MissingPermission") // checked in permitted()
    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
