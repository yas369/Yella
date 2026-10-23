package com.smsledger.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.formatRupees
import com.smsledger.core.money.formatRupeesWhole
import com.smsledger.ui.components.TransactionRow
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TypeOptions = listOf("Spent" to TxKind.Spend, "Received" to TxKind.Income, "Transfers" to TxKind.Transfer, "Lent" to TxKind.Lent)
private const val DAY_MS = 86_400_000L

private fun Tx.date(): LocalDate = LocalDate.now().minusDays(daysAgo.toLong())

/** Payee or amount match: "swiggy", "250", "₹1,250". */
internal fun Tx.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return payee.contains(q, ignoreCase = true) ||
        formatRupees(amountPaise).replace(",", "").contains(q.replace(",", "").removePrefix("₹"))
}

/** History (flows.md §11): search app bar → full-screen search, filters incl. a date range, day groups. */
@Composable
fun HistoryScreen(
    transactions: List<Tx>,
    categories: List<Category>,
    monthSpentPaise: Long,
    onTxClick: (Tx) -> Unit,
    contentPadding: PaddingValues,
) {
    val text = rememberTextFieldState()
    val searchState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    var account by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    var range by rememberSaveable { mutableStateOf<Pair<Long, Long>?>(null) }
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    val categoryOf = { id: String? -> categories.firstOrNull { it.id == id } }
    val query = text.text.toString()

    val filtered = transactions.filter { tx ->
        val day = tx.date().toEpochDay() * DAY_MS
        tx.matches(query) &&
            (account == null || tx.account == account) &&
            (categoryName == null || categoryOf(tx.categoryId)?.name == categoryName) &&
            (typeName == null || TypeOptions.first { it.first == typeName }.second == tx.kind) &&
            (range == null || day in range!!.first..range!!.second)
    }
    val anyFilter = query.isNotBlank() || account != null || categoryName != null || typeName != null || range != null
    fun clearAll() {
        text.clearText(); account = null; categoryName = null; typeName = null; range = null
    }

    val inputField: @Composable () -> Unit = {
        val expanded = searchState.currentValue == SearchBarValue.Expanded
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = searchState,
            onSearch = { scope.launch { searchState.animateToCollapsed() } },
            placeholder = { Text("Search payee or amount") },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = { scope.launch { searchState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
            },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { text.clearText() }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") } }
            } else {
                null
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopSearchBar(state = searchState, inputField = inputField, scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + Space.L),
        ) {
            item(key = "month") {
                Column(Modifier.padding(horizontal = Space.Gutter).padding(top = Space.S)) {
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${formatRupeesWhole(monthSpentPaise)} spent", style = MoneyStyles.Large)
                }
            }
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.M),
                    horizontalArrangement = Arrangement.spacedBy(Space.S),
                ) {
                    item {
                        FilterChip(
                            selected = range != null,
                            onClick = { pickingDates = true },
                            label = { Text(range?.let { rangeLabel(it) } ?: "Date") },
                            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                        )
                    }
                    item { DropdownFilterChip("Account", account, transactions.map { it.account }.distinct().sorted()) { account = it } }
                    item { DropdownFilterChip("Category", categoryName, categories.map { it.name }) { categoryName = it } }
                    item { DropdownFilterChip("Type", typeName, TypeOptions.map { it.first }) { typeName = it } }
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") { NoResults(query, onClear = ::clearAll) }
            }
            filtered.groupBy { it.daysAgo }.toSortedMap().forEach { (daysAgo, dayTx) ->
                stickyHeader(key = "day-$daysAgo") { DayHeader(daysAgo, dayTx) }
                items(dayTx, key = { it.id }) { tx -> TransactionRow(tx, categoryOf(tx.categoryId), onClick = { onTxClick(tx) }) }
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

    ExpandedFullScreenSearchBar(state = searchState, inputField = inputField) {
        SearchResults(
            query = query,
            results = transactions.filter { it.matches(query) },
            suggestions = transactions.groupingBy { it.payee }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key },
            categoryOf = categoryOf,
            onSuggestion = { text.setTextAndPlaceCursorAtEnd(it) },
            onResult = { tx -> scope.launch { searchState.animateToCollapsed() }; onTxClick(tx) },
        )
    }

    if (pickingDates) {
        val pickerState = rememberDateRangePickerState(range?.first, range?.second)
        DatePickerDialog(
            onDismissRequest = { pickingDates = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        range = start?.let { it to (pickerState.selectedEndDateMillis ?: it) }
                        pickingDates = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { range = null; pickingDates = false }, shapes = ButtonDefaults.shapes()) { Text("Any date") }
            },
        ) {
            DateRangePicker(pickerState, Modifier.weight(1f))
        }
    }
}

private fun rangeLabel(range: Pair<Long, Long>): String {
    val f = DateTimeFormatter.ofPattern("d MMM")
    val start = LocalDate.ofEpochDay(range.first / DAY_MS)
    val end = LocalDate.ofEpochDay(range.second / DAY_MS)
    return if (start == end) start.format(f) else "${start.format(f)} – ${end.format(f)}"
}

@Composable
private fun SearchResults(
    query: String,
    results: List<Tx>,
    suggestions: List<String>,
    categoryOf: (String?) -> Category?,
    onSuggestion: (String) -> Unit,
    onResult: (Tx) -> Unit,
) {
    if (query.isBlank()) {
        Column(Modifier.padding(Space.Gutter)) {
            Text("Frequent payees", style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Space.S))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                suggestions.forEach { payee -> SuggestionChip(onClick = { onSuggestion(payee) }, label = { Text(payee) }) }
            }
        }
        return
    }
    if (results.isEmpty()) {
        Text(
            "No matches for “${query.trim()}”",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(Space.Gutter),
        )
        return
    }
    LazyColumn {
        items(results, key = { it.id }) { tx -> TransactionRow(tx, categoryOf(tx.categoryId), onClick = { onResult(tx) }) }
    }
}

@Composable
private fun NoResults(query: String, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Space.XXL), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (query.isNotBlank()) "No matches for “${query.trim()}”" else "Nothing matches these filters",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Space.M))
        OutlinedButton(onClick = onClear, shapes = ButtonDefaults.shapes()) { Text("Clear filters") }
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
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (spent > 0) {
                Text(formatRupeesWhole(spent), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
