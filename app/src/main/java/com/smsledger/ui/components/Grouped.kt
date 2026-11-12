package com.smsledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Radius
import com.smsledger.ui.theme.Space

/** Where a row sits in its grouped section; decides which corners are rounded. */
enum class GroupPosition {
    Single, First, Middle, Last;

    companion object {
        fun of(index: Int, count: Int): GroupPosition = when {
            count <= 1 -> Single
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }
    }
}

private val Corner = 12.dp
private val TopShape = RoundedCornerShape(topStart = Corner, topEnd = Corner)
private val BottomShape = RoundedCornerShape(bottomStart = Corner, bottomEnd = Corner)

private fun GroupPosition.shape(): Shape = when (this) {
    GroupPosition.Single -> Radius.Card
    GroupPosition.First -> TopShape
    GroupPosition.Middle -> RectangleShape
    GroupPosition.Last -> BottomShape
}

/**
 * One row of an inset grouped section (design.md §6): a `surface` block inset from the screen
 * edges, rounded only on the section's outer corners. Lets the rows of a lazy list read as one
 * rounded section without putting them in a single item.
 */
@Composable
fun Modifier.grouped(position: GroupPosition): Modifier {
    val shape = position.shape()
    return this
        .fillMaxWidth()
        .padding(horizontal = Space.Gutter)
        .clip(shape)
        .background(LedgerTheme.colors.surface, shape)
}

/** Separator drawn between two [grouped] rows that are separate list items. */
@Composable
fun GroupedDivider() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter).background(LedgerTheme.colors.surface)) { RowDivider() }
}

/** A whole grouped section when its rows are known up front. */
@Composable
fun GroupedSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.grouped(GroupPosition.Single), content = content)
}
