package com.smsledger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smsledger.ui.model.Category
import com.smsledger.ui.theme.Space

enum class Emphasis { Primary, Tonal, Outline }

/** Full-width, 56dp decision button: the "one tap per decision" building block (§15.3). */
@Composable
fun BigChoice(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Tonal,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val m = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
    val shape = MaterialTheme.shapes.medium
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.S))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    when (emphasis) {
        Emphasis.Primary -> Button(onClick, m, enabled, shape = shape) { content() }
        Emphasis.Tonal -> FilledTonalButton(onClick, m, enabled, shape = shape) { content() }
        Emphasis.Outline -> OutlinedButton(onClick, m, enabled, shape = shape) { content() }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.Gutter, top = Space.XL, bottom = Space.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Wrapping row of one-tap category chips. */
@OptIn(ExperimentalLayoutApi::class)
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
            shape = MaterialTheme.shapes.small,
        )
    }
    if (singleLine) {
        LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.S)) { items(categories, key = { it.id }) { chip(it) } }
    } else {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.S)) { categories.forEach { chip(it) } }
    }
}
