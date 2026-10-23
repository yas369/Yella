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
import com.smsledger.ui.demo.DemoStore
import com.smsledger.ui.theme.SmsLedgerTheme

class MainActivity : ComponentActivity() {

    // Design preview: sample data only. Room-backed state replaces DemoStore from T6.
    private val store = DemoStore()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
            SmsLedgerTheme(darkTheme = dark, dynamicColor = store.dynamicColor) {
                SmsLedgerApp(store)
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
