package com.smsledger

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.smsledger.ui.SmsLedgerApp
import com.smsledger.ui.demo.DemoStore
import com.smsledger.ui.theme.SmsLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Design preview: sample data only. Room-backed state replaces DemoStore from T6.
            val store = remember { DemoStore() }
            val dark = store.darkOverride ?: isSystemInDarkTheme()
            // Keep status/nav bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SmsLedgerTheme(darkTheme = dark) {
                SmsLedgerApp(store)
            }
        }
    }
}
