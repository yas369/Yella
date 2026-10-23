package com.smsledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smsledger.ui.model.Category
import com.smsledger.ui.theme.Space

enum class Emphasis { Primary, Tonal, Outline }

/**
 * Full-width, 56dp decision button: the "one tap per decision" building block (§15.3).
 * Uses Expressive button shapes, which morph from a pill to a rounded square while pressed.
 */
@Composable
fun BigChoice(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Tonal,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val m = modifier.fillMaxWidth().defaultMinSize(minHeight = ButtonDefaults.MediumContainerHeight)
    val shapes = ButtonDefaults.shapes()
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.S))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
    when (emphasis) {
        Emphasis.Primary -> Button(onClick, shapes, m, enabled) { content() }
        Emphasis.Tonal -> FilledTonalButton(onClick, shapes, m, enabled) { content() }
        Emphasis.Outline -> OutlinedButton(onClick, shapes, m, enabled) { content() }
    }
}

/**
 * Connected button group (Material 3 Expressive) for choosing one of a few options:
 * Mine | Not mine, Spent | Received, Money out | Money in. The selected button rounds fully.
 */
@Composable
fun ConnectedChoice(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                icons.getOrNull(index)?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.S))
                }
                Text(label, maxLines = 1)
            }
        }
    }
}

/** Content inside an Expressive shape (cookie, sunny, …). Used for counts, badges and empty states. */
@Composable
fun ShapeBadge(
    shape: Shape,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) { content() }
}

/** Named Expressive shapes used across the app, so screens stay consistent. */
object LedgerShapes {
    val Count: Shape @Composable get() = MaterialShapes.Cookie9Sided.toShape()
    val Hero: Shape @Composable get() = MaterialShapes.Cookie12Sided.toShape()
    val Done: Shape @Composable get() = MaterialShapes.SoftBurst.toShape()
    val Person: Shape @Composable get() = MaterialShapes.Cookie6Sided.toShape()
    val Info: Shape @Composable get() = MaterialShapes.Sunny.toShape()
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.Gutter, top = Space.XL, bottom = Space.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** One-tap category chips: wrapping, or one scrolling row inside queue cards. */
@Composable
fun CategoryChips(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    val chip: @Composable (Category) -> Unit = { category ->
        FilterChip(
            selected = category.id == selectedId,
            onClick = { onSelect(category) },
            label = { Text(category.name) },
        )
    }
    if (singleLine) {
        LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.S)) { items(categories, key = { it.id }) { chip(it) } }
    } else {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.S)) { categories.forEach { chip(it) } }
    }
}
