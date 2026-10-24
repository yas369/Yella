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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.smsledger.core.money.AmountInput
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.LedgerButton
import com.smsledger.ui.components.RowDivider
import com.smsledger.ui.components.TransactionRow
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import com.smsledger.ui.model.dayLabel
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Radius
import com.smsledger.ui.theme.Space
import java.time.LocalDate

private val TypeOptions = listOf("Spent" to TxKind.Spend, "Received" to TxKind.Income, "Self-transfer" to TxKind.Transfer, "Lent" to TxKind.Lent)
private const val DAY_MS = 86_400_000L

/**
 * Payee or amount match (§14): "swiggy" by name; "250", "₹1,250" or "250.5" by amount. A whole-rupee
 * query matches that rupee amount (₹250 and ₹250.50), never a longer number that contains it (₹1,250).
 */
internal fun Tx.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (payee.contains(q, ignoreCase = true)) return true
    val digits = q.removePrefix("₹").replace(",", "").trim()
    if (digits.isEmpty() || !digits.all { it.isDigit() || it == '.' }) return false
    return if ('.' in digits) {
        val wanted = AmountInput.toPaise(digits)
        wanted == amountPaise
    } else {
        digits.toLongOrNull() == amountPaise / 100
    }
}

/** History (§7): search, wrapping filters, rows grouped by day with the day's total spent. */
@Composable
fun HistoryScreen(
    transactions: List<Tx>,
    categories: List<Category>,
    onTxClick: (Tx) -> Unit,
    contentPadding: PaddingValues,
) {
    val c = LedgerTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    var range by rememberSaveable { mutableStateOf<Pair<Long, Long>?>(null) }
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    val categoryOf = { id: String? -> categories.firstOrNull { it.id == id } }

    val filtered = transactions.filter { tx ->
        val day = tx.date.toEpochDay() * DAY_MS
        tx.matches(query) &&
            (account == null || tx.account == account) &&
            (categoryName == null || categoryOf(tx.categoryId)?.name == categoryName) &&
            (typeName == null || TypeOptions.first { it.first == typeName }.second == tx.kind) &&
            (range == null || day in range!!.first..range!!.second)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Space.L),
    ) {
        item(key = "statusBar") { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "title") {
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.L))
        }
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Payee or amount") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") } }
                } else {
                    null
                },
                singleLine = true,
                shape = Radius.Control,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = c.surface,
                    focusedContainerColor = c.surface,
                    unfocusedBorderColor = c.divider,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.M),
            )
        }
        item(key = "filters") {
            FlowRow(
                Modifier.padding(horizontal = Space.Gutter).padding(bottom = Space.S),
                horizontalArrangement = Arrangement.spacedBy(Space.S),
            ) {
                FilterToggleChip("Date", range?.let { rangeLabel(it) }, onOpen = { pickingDates = true }, onClear = { range = null })
                DropdownFilterChip("Account", account, transactions.map { it.account }.distinct().sorted()) { account = it }
                DropdownFilterChip("Category", categoryName, categories.map { it.name }) { categoryName = it }
                DropdownFilterChip("Type", typeName, TypeOptions.map { it.first }) { typeName = it }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No transactions match.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.L),
                )
            }
        }
        filtered.groupBy { it.date }.toSortedMap(reverseOrder()).forEach { (date, dayTx) ->
            stickyHeader(key = "day-$date") { DayHeader(date, dayTx) }
            itemsIndexed(dayTx, key = { _, tx -> tx.id }) { index, tx ->
                Column {
                    if (index > 0) RowDivider()
                    TransactionRow(tx, categoryOf(tx.categoryId), onClick = { onTxClick(tx) })
                }
            }
        }
    }

    if (pickingDates) {
        val pickerState = rememberDateRangePickerState(range?.first, range?.second)
        DatePickerDialog(
            onDismissRequest = { pickingDates = false },
            confirmButton = {
                LedgerButton("Apply", {
                    val start = pickerState.selectedStartDateMillis
                    range = start?.let { it to (pickerState.selectedEndDateMillis ?: it) }
                    pickingDates = false
                }, emphasis = Emphasis.Text)
            },
            dismissButton = { LedgerButton("Cancel", { pickingDates = false }, emphasis = Emphasis.Quiet) },
        ) {
            DateRangePicker(pickerState, Modifier.weight(1f))
        }
    }
}

private fun rangeLabel(range: Pair<Long, Long>): String {
    val start = LocalDate.ofEpochDay(range.first / DAY_MS)
    val end = LocalDate.ofEpochDay(range.second / DAY_MS)
    return if (start == end) dayLabel(start) else "${dayLabel(start)} – ${dayLabel(end)}"
}

/** Day header: date on the left, what was spent that day on the right (§7). */
@Composable
private fun DayHeader(date: LocalDate, dayTx: List<Tx>) {
    val c = LedgerTheme.colors
    val spent = dayTx.filter { it.kind == TxKind.Spend && !it.excluded }.sumOf { it.amountPaise }
    Surface(color = c.bg) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.Gutter, top = Space.L, bottom = Space.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dayLabel(date), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                if (spent > 0) Text(formatRupees(spent), style = MoneyStyles.Row, color = c.text)
            }
            RowDivider()
        }
    }
}
