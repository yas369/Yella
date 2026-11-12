package com.smsledger.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.smsledger.core.limit.LimitStatus
import com.smsledger.core.money.formatRupeesWhole
import com.smsledger.ui.components.Chevron
import com.smsledger.ui.components.GroupPosition
import com.smsledger.ui.components.GroupedSection
import com.smsledger.ui.components.LimitHero
import com.smsledger.ui.components.RowDivider
import com.smsledger.ui.components.SectionHeader
import com.smsledger.ui.components.TransactionRow
import com.smsledger.ui.components.grouped
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.theme.LargeTitleStyle
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.SectionStyle
import com.smsledger.ui.theme.Size
import com.smsledger.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** How many rows Home shows before "See all" (§7). */
const val RECENT_ROWS = 10

/** Everything Home needs, already computed. */
data class TodayState(
    val status: LimitStatus,
    val spentPaise: Long,
    val limitPaise: Long,
    /** Newest first; Home shows the first [RECENT_ROWS]. */
    val recent: List<Tx>,
    val needsCount: Int,
    val monthSpentPaise: Long,
    /** Manual mode or SMS access revoked: Home shows one "Turn on" row (design.md §9). */
    val captureOff: Boolean = false,
)

/**
 * Home (design.md §7), top to bottom: the date and large title, the day's answer in its own
 * section, a section with the month and the review entry, then recent rows as one section.
 */
@Composable
fun TodayScreen(
    state: TodayState,
    categoryOf: (String?) -> Category?,
    onOpenNeeds: () -> Unit,
    onOpenHistory: () -> Unit,
    onTxClick: (Tx) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onTurnOnCapture: () -> Unit = {},
) {
    val today = LocalDate.now()
    val c = LedgerTheme.colors
    val motion = MaterialTheme.motionScheme
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item(key = "statusBar") { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "title") {
            Column(Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.L, bottom = Space.M)) {
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                    style = SectionStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary,
                )
                Text("Today", style = LargeTitleStyle, color = c.text)
            }
        }
        item(key = "hero") {
            GroupedSection { LimitHero(state.status, state.spentPaise, state.limitPaise, Modifier.padding(Space.L)) }
        }
        item(key = "summary") {
            GroupedSection(Modifier.padding(top = Space.L)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenHistory)
                        .defaultMinSize(minHeight = Size.Row)
                        .padding(horizontal = Space.L, vertical = Space.M),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${formatRupeesWhole(state.monthSpentPaise)} spent in ${today.format(DateTimeFormatter.ofPattern("MMMM"))}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.weight(1f),
                    )
                    Chevron()
                }
                AnimatedVisibility(
                    state.needsCount > 0,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.defaultEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
                ) {
                    Column {
                        RowDivider()
                        NeedsEntryRow(state.needsCount, onOpenNeeds)
                    }
                }
                if (state.captureOff) {
                    RowDivider()
                    Text(
                        "Automatic capture is off · Turn on",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onTurnOnCapture)
                            .defaultMinSize(minHeight = Size.Row)
                            .padding(horizontal = Space.L, vertical = Space.L),
                    )
                }
            }
        }
        item(key = "recentHeader") { SectionHeader("Recent") }
        val recent = state.recent.take(RECENT_ROWS)
        if (recent.isEmpty()) {
            item(key = "empty") {
                GroupedSection {
                    Text(
                        if (state.captureOff) "No transactions yet. Tap + to add one." else "No transactions yet. They'll appear here as bank SMS arrive.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textSecondary,
                        modifier = Modifier.padding(Space.L),
                    )
                }
            }
        } else {
            // The rows and "See all" read as one section: only its outer corners are rounded.
            itemsIndexed(recent, key = { _, tx -> tx.id }) { index, tx ->
                Column(Modifier.animateItem().grouped(GroupPosition.of(index, recent.size + 1))) {
                    if (index > 0) RowDivider()
                    TransactionRow(tx, categoryOf(tx.categoryId), onClick = { onTxClick(tx) }, showDay = true)
                }
            }
            item(key = "seeAll") {
                Column(Modifier.grouped(GroupPosition.Last)) {
                    RowDivider()
                    Text(
                        "See all",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenHistory)
                            .defaultMinSize(minHeight = Size.Touch)
                            .padding(horizontal = Space.L, vertical = Space.M),
                    )
                }
            }
        }
        // Room for the add button so it never covers the last row.
        item(key = "fabSpace") { Spacer(Modifier.height(Size.Row + Space.XXL)) }
    }
}
