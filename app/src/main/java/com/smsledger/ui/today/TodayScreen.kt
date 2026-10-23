package com.smsledger.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smsledger.core.limit.LimitStatus
import com.smsledger.core.money.formatRupeesWhole
import com.smsledger.ui.components.LimitHero
import com.smsledger.ui.components.SectionHeader
import com.smsledger.ui.components.TransactionRow
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Everything Today needs, already computed. Real data arrives in T7. */
data class TodayState(
    val status: LimitStatus,
    val spentPaise: Long,
    val limitPaise: Long,
    val transactions: List<Tx>,
    val needsCount: Int,
    val needsSummary: String,
    val owedPaise: Long,
    val owedPeople: Int,
    val monthSpentPaise: Long,
    val showAlertsCard: Boolean,
    val manualMode: Boolean,
)

/** Home (flows.md §5): the answer with zero taps, then only what needs attention. */
@Composable
fun TodayScreen(
    state: TodayState,
    categoryOf: (String?) -> Category?,
    onOpenNeeds: () -> Unit,
    onOpenOwed: () -> Unit,
    onOpenHistory: () -> Unit,
    onAlertsTurnOn: () -> Unit,
    onAlertsDismiss: () -> Unit,
    onTxClick: (Tx) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val today = LocalDate.now()
    // No top app bar: the hero is the title. The list scrolls under the status bar (edge-to-edge).
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item(key = "statusBar") { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "date") {
            Text(
                today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.XL),
            )
        }
        item(key = "hero") {
            LimitHero(
                state.status, state.spentPaise, state.limitPaise,
                Modifier.padding(horizontal = Space.Gutter, vertical = Space.M),
            )
        }
        item(key = "cards") {
            Column(
                Modifier.padding(horizontal = Space.Gutter).padding(top = Space.L),
                verticalArrangement = Arrangement.spacedBy(Space.S),
            ) {
                if (state.manualMode) ManualModeNote()
                if (state.needsCount > 0) NeedsBanner(state.needsCount, state.needsSummary, onOpenNeeds)
                if (state.showAlertsCard) AlertsCard(onAlertsTurnOn, onAlertsDismiss)
                if (state.owedPaise > 0) OwedCard(state.owedPaise, state.owedPeople, onOpenOwed)
            }
        }
        item(key = "todayHeader") {
            SectionHeader("Today")
        }
        if (state.transactions.isEmpty()) {
            item(key = "empty") { EmptyDay() }
        } else {
            items(state.transactions, key = { it.id }) { tx ->
                TransactionRow(tx, categoryOf(tx.categoryId), onClick = { onTxClick(tx) }, modifier = Modifier.animateItem())
            }
        }
        item(key = "month") {
            HorizontalDivider(Modifier.padding(horizontal = Space.Gutter, vertical = Space.S), color = MaterialTheme.colorScheme.outlineVariant)
            MonthLine(today, state.monthSpentPaise, onOpenHistory)
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun EmptyDay() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No spending yet today", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Space.XS))
        Text(
            "Payments show up here as your bank messages arrive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MonthLine(today: LocalDate, spentPaise: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.Gutter, vertical = Space.L),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                today.format(DateTimeFormatter.ofPattern("MMMM")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${formatRupeesWhole(spentPaise)} spent", style = MoneyStyles.Row.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Open history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
