package com.smsledger.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.smsledger.MainActivity
import com.smsledger.core.limit.LimitLevel

/**
 * Home-screen widget (flows.md §12): "₹340 left today" without opening the app.
 * 2×1 shows the number and meter; taller sizes add the spend line and a + (Add expense) button.
 */
class LedgerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Tall))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshot.load(context)
        provideContent { GlanceTheme { WidgetContent(snapshot) } }
    }

    private companion object {
        val Compact = DpSize(160.dp, 80.dp)
        val Tall = DpSize(160.dp, 130.dp)
    }
}

class LedgerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LedgerWidget()
}

// Fixed status colours (same as the app's LedgerColors): meaning never follows the wallpaper.
private val WarningColor = ColorProvider(day = Color(0xFFB06500), night = Color(0xFFFFB95C))
private val OverColor = ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFFB4AB))

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val tall = LocalSize.current.height >= 120.dp
    val levelColor = when (snapshot.level) {
        LimitLevel.Ok -> GlanceTheme.colors.primary
        LimitLevel.Warning -> WarningColor
        LimitLevel.Over -> OverColor
    }
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                snapshot.headline,
                style = TextStyle(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (snapshot.level == LimitLevel.Over) OverColor else GlanceTheme.colors.onSurface,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                snapshot.label,
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.padding(bottom = 6.dp),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        LinearProgressIndicator(
            progress = snapshot.fraction,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
            color = levelColor,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        if (tall) {
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snapshot.detail,
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Box(
                    GlanceModifier
                        .size(40.dp)
                        .cornerRadius(20.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .clickable(actionStartActivity(MainActivity.addExpenseIntent(context))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimaryContainer))
                }
            }
        }
    }
}
