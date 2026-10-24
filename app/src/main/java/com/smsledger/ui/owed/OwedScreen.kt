package com.smsledger.ui.owed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.LedgerButton
import com.smsledger.ui.components.RowDivider
import com.smsledger.ui.model.Debtor
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Size
import com.smsledger.ui.theme.Space

/** Owed to you (§7): one row per person; tap to see their entries, then Mark repaid or Write off. */
@Composable
fun OwedScreen(debtors: List<Debtor>, onBack: () -> Unit, onRepaid: (Debtor) -> Unit, onWriteOff: (Debtor) -> Unit) {
    val c = LedgerTheme.colors
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmWriteOff by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = { Text("Owed to you") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.XXL),
        ) {
            item(key = "summary") {
                Text(
                    if (debtors.isEmpty()) {
                        "Nobody owes you anything."
                    } else {
                        "${formatRupees(debtors.sumOf { it.outstandingPaise })} owed by ${debtors.size} ${if (debtors.size == 1) "person" else "people"}"
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (debtors.isEmpty()) c.textSecondary else c.text,
                    modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.M),
                )
            }
            itemsIndexed(debtors, key = { _, d -> d.id }) { index, debtor ->
                Column(Modifier.animateItem()) {
                    if (index == 0) RowDivider()
                    DebtorRow(
                        debtor,
                        expanded = expandedId == debtor.id,
                        onToggle = { expandedId = if (expandedId == debtor.id) null else debtor.id },
                        onRepaid = { onRepaid(debtor) },
                        onWriteOff = { confirmWriteOff = debtor.id },
                    )
                    RowDivider()
                }
            }
        }
    }
    debtors.firstOrNull { it.id == confirmWriteOff }?.let { debtor ->
        AlertDialog(
            onDismissRequest = { confirmWriteOff = null },
            title = { Text("Write off ${formatRupees(debtor.outstandingPaise)} from ${debtor.name}?") },
            text = { Text("It leaves this list and stops counting as owed. You can undo it right after.") },
            confirmButton = { LedgerButton("Write off", { confirmWriteOff = null; onWriteOff(debtor) }, emphasis = Emphasis.Text) },
            dismissButton = { LedgerButton("Cancel", { confirmWriteOff = null }, emphasis = Emphasis.Quiet) },
        )
    }
}

@Composable
private fun DebtorRow(debtor: Debtor, expanded: Boolean, onToggle: () -> Unit, onRepaid: () -> Unit, onWriteOff: () -> Unit) {
    val c = LedgerTheme.colors
    val motion = MaterialTheme.motionScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .defaultMinSize(minHeight = Size.Row)
                .padding(horizontal = Space.Gutter, vertical = Space.M)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(debtor.name, style = MaterialTheme.typography.bodyLarge, color = c.text)
                Text("${debtor.handle} · ${debtor.since}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.width(Space.M))
            Text(formatRupees(debtor.outstandingPaise), style = MoneyStyles.Row, color = c.text)
        }
        AnimatedVisibility(
            expanded,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(Modifier.padding(horizontal = Space.Gutter).padding(bottom = Space.M)) {
                debtor.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.XS)) {
                        Text(entry.label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text(formatRupees(entry.amountPaise), style = MoneyStyles.Label, color = c.textSecondary)
                    }
                }
                Text(
                    "Money you add as received from ${debtor.name} is taken off this first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Space.S),
                )
                Row(Modifier.fillMaxWidth().padding(top = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.S, Alignment.End)) {
                    LedgerButton("Write off", onWriteOff, emphasis = Emphasis.Quiet)
                    LedgerButton("Mark repaid", onRepaid, emphasis = Emphasis.Primary)
                }
            }
        }
    }
}
