package com.smsledger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smsledger.ui.model.Category
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Radius
import com.smsledger.ui.theme.Size
import com.smsledger.ui.theme.Space

/**
 * design.md §6 Buttons. Primary is filled accent and appears at most once per card or screen;
 * Outline is the secondary; Quiet is the plain text-secondary button for dismissive answers
 * ("Skip", "Neither is mine", "Not a transaction").
 */
enum class Emphasis { Primary, Outline, Text, Quiet }

private val ButtonPadding = PaddingValues(horizontal = Space.L, vertical = Space.S)

@Composable
fun LedgerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Outline,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val m = modifier.defaultMinSize(minHeight = Size.Touch)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.S))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2)
    }
    val c = LedgerTheme.colors
    when (emphasis) {
        Emphasis.Primary -> Button(
            onClick, m, enabled, shape = Radius.Control, contentPadding = ButtonPadding,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            elevation = null,
        ) { content() }
        Emphasis.Outline -> OutlinedButton(
            onClick, m, enabled, shape = Radius.Control, contentPadding = ButtonPadding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
            border = BorderStroke(1.dp, c.divider),
        ) { content() }
        Emphasis.Text, Emphasis.Quiet -> TextButton(
            onClick, m, enabled, shape = Radius.Control, contentPadding = ButtonPadding,
            colors = ButtonDefaults.textButtonColors(contentColor = if (emphasis == Emphasis.Quiet) c.textSecondary else c.accent),
        ) { content() }
    }
}

/** Full-width button, for stacked equal choices (e.g. picking between two accounts). */
@Composable
fun BigChoice(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Outline,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) = LedgerButton(text, onClick, modifier.fillMaxWidth(), emphasis, icon, enabled)

/**
 * One-of-a-few toggle (Spent | Received, Mine | Not mine, System | Light | Dark). Outlined 8dp
 * segments; the selected one fills with accent, which is what "selected" means everywhere.
 */
@Composable
fun ConnectedChoice(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
) {
    val c = LedgerTheme.colors
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        options.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            val m = Modifier.weight(1f).defaultMinSize(minHeight = Size.Touch).semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            val content: @Composable () -> Unit = {
                icons.getOrNull(index)?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.S))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            if (selected) {
                Button(
                    { onSelect(index) }, m, shape = Radius.Control, contentPadding = ButtonPadding, elevation = null,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                ) { content() }
            } else {
                OutlinedButton(
                    { onSelect(index) }, m, shape = Radius.Control, contentPadding = ButtonPadding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
                    border = BorderStroke(1.dp, c.divider),
                ) { content() }
            }
        }
    }
}

/** Section title (Title, 20sp) with an optional trailing action. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.S, top = Space.XL, bottom = Space.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(vertical = Space.S))
        trailing?.invoke()
    }
}

/** 1dp row divider, inset to the gutter. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(horizontal = Space.Gutter), thickness = 1.dp, color = LedgerTheme.colors.divider)
}

/** A chip that changes how money is counted (Lent, Self-transfer); shown apart from categories. */
data class SpecialChip(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * Category chips (§6): always wrap, never scroll sideways. [special] chips sit after a gap, because
 * they change how money is counted rather than which bucket it falls in.
 */
@Composable
fun CategoryChips(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
    special: List<SpecialChip> = emptyList(),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.M)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            categories.forEach { category ->
                LedgerChip(category.name, category.id == selectedId) { onSelect(category) }
            }
        }
        if (special.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                special.forEach { LedgerChip(it.label, it.selected, onClick = it.onClick) }
            }
        }
    }
}

@Composable
fun LedgerChip(label: String, selected: Boolean, modifier: Modifier = Modifier, trailingIcon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        trailingIcon = trailingIcon,
        shape = Radius.Control,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surface,
            labelColor = c.text,
            iconColor = c.textSecondary,
            selectedContainerColor = c.accent,
            selectedLabelColor = c.onAccent,
            selectedTrailingIconColor = c.onAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = c.divider, selectedBorderColor = c.accent, borderWidth = 1.dp, selectedBorderWidth = 1.dp,
        ),
    )
}
