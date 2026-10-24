package com.smsledger

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.smsledger.ui.SmsLedgerApp
import androidx.lifecycle.lifecycleScope
import com.smsledger.data.LedgerStore
import kotlinx.coroutines.launch
import com.smsledger.ui.theme.SmsLedgerTheme

class MainActivity : ComponentActivity() {

    private val store: LedgerStore get() = (application as SmsLedgerApplication).store

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash up until the ledger has been read from disk, so no empty state flashes.
        installSplashScreen().setKeepOnScreenCondition { !store.loaded }
        super.onCreate(savedInstanceState)
        if (!store.loaded) lifecycleScope.launch { store.load() }
        handle(intent)
        enableEdgeToEdge()
        setContent {
            val dark = store.darkOverride ?: isSystemInDarkTheme()
            // Keep status/nav bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SmsLedgerTheme(darkTheme = dark) {
                if (store.loaded) SmsLedgerApp(store)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ADD_EXPENSE) store.pendingAdd = true
    }

    companion object {
        /** Launcher shortcut and widget "+" action (flows.md §12). */
        const val ACTION_ADD_EXPENSE = "com.smsledger.action.ADD_EXPENSE"

        fun addExpenseIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_ADD_EXPENSE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
