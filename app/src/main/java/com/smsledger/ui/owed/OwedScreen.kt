package com.smsledger.ui.owed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.model.Debtor
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space

/** Owed to you (flows.md §9): one row per person; expand for history, repaid and write-off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwedScreen(debtors: List<Debtor>, onBack: () -> Unit, onRepaid: (Debtor) -> Unit, onWriteOff: (Debtor) -> Unit) {
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmWriteOff by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owed to you") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.Gutter, end = Space.Gutter, top = padding.calculateTopPadding(), bottom = Space.XXL),
            verticalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            item {
                Text(formatRupees(debtors.sumOf { it.outstandingPaise }), style = MoneyStyles.Large)
                Text(
                    if (debtors.isEmpty()) "No one owes you anything" else "owed by ${debtors.size} ${if (debtors.size == 1) "person" else "people"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.L))
            }
            items(debtors, key = { it.id }) { debtor ->
                DebtorCard(
                    debtor,
                    expanded = expandedId == debtor.id,
                    onToggle = { expandedId = if (expandedId == debtor.id) null else debtor.id },
                    onRepaid = { onRepaid(debtor) },
                    onWriteOff = { confirmWriteOff = debtor.id },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    debtors.firstOrNull { it.id == confirmWriteOff }?.let { debtor ->
        AlertDialog(
            onDismissRequest = { confirmWriteOff = null },
            title = { Text("Write off ${formatRupees(debtor.outstandingPaise)} from ${debtor.name}?") },
            text = { Text("It won't count as owed or appear in reminders. You can undo this right after.") },
            confirmButton = { TextButton(onClick = { confirmWriteOff = null; onWriteOff(debtor) }) { Text("Write off") } },
            dismissButton = { TextButton(onClick = { confirmWriteOff = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DebtorCard(
    debtor: Debtor,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRepaid: () -> Unit,
    onWriteOff: () -> Unit,
    modifier: Modifier,
) {
    Surface(onClick = onToggle, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(Space.L)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    Text(debtor.name.first().toString(), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(Space.M))
                Column(Modifier.weight(1f)) {
                    Text(debtor.name, style = MaterialTheme.typography.titleMedium)
                    Text("${debtor.handle} · ${debtor.since}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatRupees(debtor.outstandingPaise), style = MoneyStyles.Row)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = Space.M)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    debtor.entries.forEach { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.S)) {
                            Text(entry.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                (if (entry.isRepayment) "−" else "") + formatRupees(entry.amountPaise),
                                style = MoneyStyles.Row,
                                color = if (entry.isRepayment) LedgerTheme.colors.credit else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Text(
                        "Repayments from ${debtor.handle} are matched automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = Space.S), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onWriteOff) { Text("Write off", color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.width(Space.XS))
                        FilledTonalButton(onClick = onRepaid) { Text("Mark repaid") }
                    }
                }
            }
        }
    }
}
