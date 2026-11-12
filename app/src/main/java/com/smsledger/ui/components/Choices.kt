package com.smsledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsledger.ui.model.Category
import com.smsledger.ui.theme.ButtonStyle
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Radius
import com.smsledger.ui.theme.SectionStyle
import com.smsledger.ui.theme.Size
import com.smsledger.ui.theme.Space

/**
 * design.md §6 Buttons. Primary is filled accent and appears at most once per card or screen;
 * Outline is the secondary, a neutral fill with accent text; Text is a plain accent label; Quiet is
 * the plain grey label for dismissive answers ("Skip", "Neither is mine", "Not a transaction").
 */
enum class Emphasis { Primary, Outline, Text, Quiet }

private val ButtonPadding = PaddingValues(horizontal = Space.L, vertical = Space.M)
private val TextButtonPadding = PaddingValues(horizontal = Space.M, vertical = Space.S)

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
    val filled = emphasis == Emphasis.Primary || emphasis == Emphasis.Outline
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.S))
        }
        Text(text, style = if (filled) ButtonStyle else MaterialTheme.typography.bodyLarge, maxLines = 2)
    }
    val c = LedgerTheme.colors
    when (emphasis) {
        Emphasis.Primary -> Button(
            onClick, m, enabled, shape = Radius.Control, contentPadding = ButtonPadding,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent, disabledContainerColor = c.fill, disabledContentColor = c.textSecondary),
            elevation = null,
        ) { content() }
        Emphasis.Outline -> Button(
            onClick, m, enabled, shape = Radius.Control, contentPadding = ButtonPadding,
            colors = ButtonDefaults.buttonColors(containerColor = c.fill, contentColor = c.accent, disabledContainerColor = c.fill, disabledContentColor = c.textSecondary),
            elevation = null,
        ) { content() }
        Emphasis.Text, Emphasis.Quiet -> TextButton(
            onClick, m, enabled, shape = Radius.Control, contentPadding = TextButtonPadding,
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
 * Segmented control (Spent | Received, Mine | Not mine, System | Light | Dark): a neutral track with
 * the selected segment raised on it. The whole 48dp-tall segment is the touch target.
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
    Row(
        modifier.fillMaxWidth().clip(Radius.Segmented).background(c.fill).padding(Space.XS).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.XS),
    ) {
        options.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Row(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = Size.Segment)
                    .clip(Radius.SegmentedInner)
                    .background(if (selected) c.raised else c.fill)
                    .clickable { onSelect(index) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    }
                    .padding(horizontal = Space.S),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icons.getOrNull(index)?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(20.dp), tint = c.text)
                    Spacer(Modifier.width(Space.S))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = c.text,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Header above a grouped section: small, grey, in capitals, aligned with the section's content. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.Gutter + Space.L, end = Space.Gutter, top = Space.XL, bottom = Space.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = SectionStyle, color = LedgerTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Hairline between rows of a grouped section, inset on the leading side to line up with the text. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(start = Space.L), thickness = Size.Hairline, color = LedgerTheme.colors.divider)
}

/** Small grey chevron at the end of a row that opens another screen. */
@Composable
fun Chevron(modifier: Modifier = Modifier) {
    Box(modifier) {
        Text("›", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal), color = LedgerTheme.colors.lent)
    }
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

/** Capsule chip: neutral fill, accent when selected. No border. */
@Composable
fun LedgerChip(label: String, selected: Boolean, modifier: Modifier = Modifier, trailingIcon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier = modifier,
        trailingIcon = trailingIcon,
        shape = Radius.Pill,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.fill,
            labelColor = c.text,
            iconColor = c.textSecondary,
            selectedContainerColor = c.accent,
            selectedLabelColor = c.onAccent,
            selectedTrailingIconColor = c.onAccent,
        ),
        border = null,
    )
}
