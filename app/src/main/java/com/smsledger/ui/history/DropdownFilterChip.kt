package com.smsledger.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smsledger.ui.components.LedgerChip

/**
 * Filter chip (§7 History). Empty: "Account ▾", tap opens the options. Active: filled with its
 * value and an ×; tapping it clears the filter, so the × is the whole chip, not a tiny target.
 */
@Composable
internal fun FilterToggleChip(label: String, value: String?, onOpen: () -> Unit, onClear: () -> Unit) {
    LedgerChip(
        label = value ?: label,
        selected = value != null,
        modifier = Modifier.semantics { contentDescription = if (value != null) "$label: $value. Clear" else "Filter by ${label.lowercase()}" },
        trailingIcon = {
            Icon(if (value != null) Icons.Outlined.Close else Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        onClick = { if (value != null) onClear() else onOpen() },
    )
}

/** [FilterToggleChip] whose options come from a menu. */
@Composable
internal fun DropdownFilterChip(label: String, selected: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterToggleChip(label, selected, onOpen = { open = true }, onClear = { onSelect(null) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); open = false },
                    trailingIcon = if (option == selected) ({ Icon(Icons.Outlined.Check, contentDescription = "Selected") }) else null,
                )
            }
        }
    }
}
