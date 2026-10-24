package com.smsledger.ui.needs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.CategoryChips
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.LedgerButton
import com.smsledger.ui.components.SpecialChip
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Radius
import com.smsledger.ui.theme.SmsStyle
import com.smsledger.ui.theme.Space

/**
 * A decision card (§6): what kind of decision, the transaction, the SMS that raised it, the actions.
 * Cards appear only here: they are the one place the app asks something of the user.
 */
@Composable
internal fun NeedsCard(
    item: NeedsItem,
    categories: List<Category>,
    actions: NeedsActions,
    untaggedCount: (String) -> Int,
    modifier: Modifier = Modifier,
) {
    val c = LedgerTheme.colors
    Surface(
        modifier.fillMaxWidth(),
        color = c.surface,
        shape = Radius.Card,
        border = BorderStroke(1.dp, c.divider),
    ) {
        Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            when (item) {
                is NeedsItem.Unreadable -> {
                    Kind("Couldn't read this message")
                    Headline(item.sender, "${item.account} · ${daysLabel(item.daysAgo)}", amount = null)
                    SmsExcerpt(item.sms, maxLines = 4)
                    SideBySide(
                        quiet = "Not a transaction" to { actions.notATransaction(item) },
                        primary = "Pick the amount" to { actions.teach(item) },
                    )
                }
                is NeedsItem.WhichAccount -> {
                    Kind("Which account?")
                    Headline(item.payee, "${item.prompt} · ${daysLabel(item.daysAgo)}", item.amountPaise)
                    SmsExcerpt(item.sms)
                    // Equal choices, so stacked full width and none of them filled.
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
                        item.options.forEach { option -> BigChoice(option, { actions.pickAccount(item, option) }) }
                        BigChoice("Neither is mine", { actions.pickAccount(item, null) }, emphasis = Emphasis.Quiet)
                    }
                }
                is NeedsItem.PossibleTransfer -> {
                    Kind("Move between your own accounts?")
                    Text(
                        "${item.outLine} and ${item.inLine.replaceFirstChar { it.lowercase() }}, ${item.gap}.",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        color = c.text,
                    )
                    SmsExcerpt(item.sms)
                    SideBySide(
                        quiet = "Separate payments" to { actions.transfer(item, false) },
                        primary = "Self-transfer" to { actions.transfer(item, true) },
                    )
                }
                is NeedsItem.NewPayee -> {
                    Kind("Which category?")
                    Headline(item.payee, item.whenLabel, item.amountPaise)
                    SmsExcerpt(item.sms)
                    CategoryChips(
                        categories, null, { actions.tag(item, it) },
                        special = listOf(SpecialChip("Lent", selected = false) { actions.lent(item) }),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        LedgerButton("Skip", { actions.skip(item) }, emphasis = Emphasis.Quiet)
                    }
                }
                is NeedsItem.Bulk -> {
                    val count = untaggedCount(item.payee)
                    Kind("Tag in one go")
                    Headline("$count payments to ${item.payee}", "New ones get the same category", amount = null)
                    SmsExcerpt(item.sms)
                    CategoryChips(categories, null, { actions.tag(item, it) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        LedgerButton("Skip", { actions.skip(item) }, emphasis = Emphasis.Quiet)
                    }
                }
            }
        }
    }
}

/** What each answer does; the route maps these to store actions with Undo. */
interface NeedsActions {
    fun teach(item: NeedsItem.Unreadable)
    fun notATransaction(item: NeedsItem)
    fun pickAccount(item: NeedsItem.WhichAccount, account: String?)
    fun transfer(item: NeedsItem.PossibleTransfer, isTransfer: Boolean)
    fun tag(item: NeedsItem, category: Category)
    fun lent(item: NeedsItem.NewPayee)
    fun skip(item: NeedsItem)
}

/** §8 dates: "Today", "Yesterday", then "n days ago". */
private fun daysLabel(daysAgo: Int) = when (daysAgo) {
    0 -> "Today"
    1 -> "Yesterday"
    else -> "$daysAgo days ago"
}

@Composable
private fun Kind(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LedgerTheme.colors.textSecondary)
}

/** Payee and when on the left, amount on the right: the same reading order as a ledger row. */
@Composable
private fun Headline(title: String, caption: String, amount: Long?) {
    val c = LedgerTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (amount != null) {
            Text(formatRupees(amount), style = MoneyStyles.Title, color = c.text, modifier = Modifier.padding(start = Space.M))
        }
    }
}

/** Dismissive answer as a quiet text button, the main answer filled, side by side (§6). */
@Composable
private fun SideBySide(quiet: Pair<String, () -> Unit>, primary: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        LedgerButton(quiet.first, quiet.second, emphasis = Emphasis.Quiet)
        LedgerButton(primary.first, primary.second, emphasis = Emphasis.Primary)
    }
}

/** The evidence (§1 "Honest"): the raw SMS in Plex Mono on the paper colour, never paraphrased. */
@Composable
internal fun SmsExcerpt(body: String, modifier: Modifier = Modifier, maxLines: Int = 2) {
    val c = LedgerTheme.colors
    Text(
        body,
        style = SmsStyle,
        color = c.textSecondary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().background(c.bg, Radius.Control).padding(horizontal = Space.M, vertical = Space.S),
    )
}

private val NumberToken = Regex("""\d[\d,]*(?:\.\d+)?""")

/** Numbers worth offering as the amount: skips 1–2 digit fragments such as date parts. */
internal fun amountCandidates(body: String): List<String> =
    NumberToken.findAll(body).map { it.value.trimEnd(',') }.filter { it.length >= 3 }.distinct().toList()

/** Full SMS for the "Pick the amount" sheet, with the candidate amounts underlined in accent. */
@Composable
internal fun MessageBubble(sender: String, body: String) {
    val c = LedgerTheme.colors
    val candidates = amountCandidates(body).toSet()
    Column(Modifier.fillMaxWidth().background(c.bg, Radius.Control).padding(Space.M)) {
        Text(sender, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(
            buildAnnotatedString {
                var last = 0
                NumberToken.findAll(body).forEach { match ->
                    append(body.substring(last, match.range.first))
                    if (match.value.trimEnd(',') in candidates) {
                        withStyle(SpanStyle(color = c.accent, textDecoration = TextDecoration.Underline)) { append(match.value) }
                    } else {
                        append(match.value)
                    }
                    last = match.range.last + 1
                }
                append(body.substring(last))
            },
            style = SmsStyle,
            color = c.text,
            modifier = Modifier.padding(top = Space.XS),
        )
    }
}
