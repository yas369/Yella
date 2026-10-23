package com.smsledger.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.LedgerShapes
import com.smsledger.ui.components.ShapeBadge
import com.smsledger.ui.theme.Space

@Composable
internal fun NeedsBanner(count: Int, summary: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(Space.L), verticalAlignment = Alignment.CenterVertically) {
            // Expressive cookie shape makes the count read as a badge, not a button.
            ShapeBadge(LedgerShapes.Count, MaterialTheme.colorScheme.onTertiaryContainer, size = 44.dp) {
                Text("$count", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.tertiaryContainer)
            }
            Spacer(Modifier.width(Space.M))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 thing needs you" else "$count things need you",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
internal fun AlertsCard(onTurnOn: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(start = Space.L, end = Space.S, top = Space.L, bottom = Space.S)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(Space.M))
                Column(Modifier.weight(1f).padding(end = Space.S)) {
                    Text("Get a heads-up before you overspend", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "One alert at 80% and one at 100% of your limit. Never more than that.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Not now") }
                Spacer(Modifier.width(Space.XS))
                Button(onClick = onTurnOn, shapes = ButtonDefaults.shapes()) { Text("Turn on") }
            }
        }
    }
}

@Composable
internal fun OwedCard(owedPaise: Long, people: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(Space.L), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Handshake, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.M))
            Column(Modifier.weight(1f)) {
                Text("${formatRupees(owedPaise)} owed to you", style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    if (people == 1) "by 1 person" else "by $people people",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
internal fun ManualModeNote() {
    Row(Modifier.padding(vertical = Space.XS), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.S))
        Text(
            "Manual mode: add what you spend with +",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
