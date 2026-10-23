package com.smsledger.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.smsledger.core.money.formatRupees
import com.smsledger.core.money.formatRupeesWhole
import com.smsledger.ui.components.TransactionRow
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TypeOptions = listOf("Spent" to TxKind.Spend, "Received" to TxKind.Income, "Transfers" to TxKind.Transfer, "Lent" to TxKind.Lent)

/** History (flows.md §10): search, filters, month total, rows grouped by day. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    transactions: List<Tx>,
    categories: List<Category>,
    monthSpentPaise: Long,
    onTxClick: (Tx) -> Unit,
    contentPadding: PaddingValues,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    val categoryOf = { id: String? -> categories.firstOrNull { it.id == id } }

    val filtered = transactions.filter { tx ->
        val q = query.trim()
        val matchesQuery = q.isEmpty() || tx.payee.contains(q, ignoreCase = true) ||
            formatRupees(tx.amountPaise).replace(",", "").contains(q.replace(",", "").removePrefix("₹"))
        matchesQuery &&
            (account == null || tx.account == account) &&
            (categoryName == null || categoryOf(tx.categoryId)?.name == categoryName) &&
            (typeName == null || TypeOptions.first { it.first == typeName }.second == tx.kind)
    }
    val anyFilter = query.isNotBlank() || account != null || categoryName != null || typeName != null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") {
            Column(Modifier.padding(horizontal = Space.Gutter).padding(top = Space.XL)) {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(Space.L))
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${formatRupeesWhole(monthSpentPaise)} spent", style = MoneyStyles.Large)
                Spacer(Modifier.height(Space.L))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search payee or amount") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.M),
                horizontalArrangement = Arrangement.spacedBy(Space.S),
            ) {
                item { DropdownFilterChip("Account", account, transactions.map { it.account }.distinct().sorted()) { account = it } }
                item { DropdownFilterChip("Category", categoryName, categories.map { it.name }) { categoryName = it } }
                item { DropdownFilterChip("Type", typeName, TypeOptions.map { it.first }) { typeName = it } }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(Space.XXL), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (query.isNotBlank()) "No matches for “${query.trim()}”" else "Nothing matches these filters",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(Space.M))
                    OutlinedButton(onClick = { query = ""; account = null; categoryName = null; typeName = null }) { Text("Clear filters") }
                }
            }
        }
        filtered.groupBy { it.daysAgo }.toSortedMap().forEach { (daysAgo, dayTx) ->
            stickyHeader(key = "day-$daysAgo") { DayHeader(daysAgo, dayTx) }
            items(dayTx, key = { it.id }) { tx ->
                TransactionRow(tx, categoryOf(tx.categoryId), onClick = { onTxClick(tx) })
            }
        }
        if (anyFilter && filtered.isNotEmpty()) {
            item(key = "count") {
                Text(
                    "${filtered.size} of ${transactions.size} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.Gutter),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(daysAgo: Int, dayTx: List<Tx>) {
    val label = when (daysAgo) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> LocalDate.now().minusDays(daysAgo.toLong()).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    val spent = dayTx.filter { it.kind == TxKind.Spend }.sumOf { it.amountPaise }
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (spent > 0) {
                Text(formatRupeesWhole(spent), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
