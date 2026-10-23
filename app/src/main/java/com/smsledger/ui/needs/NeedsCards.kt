package com.smsledger.ui.needs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.CategoryChips
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.theme.Space

@Composable
internal fun NeedsCard(
    item: NeedsItem,
    categories: List<Category>,
    actions: NeedsActions,
    untaggedCount: (String) -> Int,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            when (item) {
                is NeedsItem.Unreadable -> {
                    Kind("Couldn't read")
                    MessageBubble(item.sender, item.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        BigChoice("Teach", { actions.teach(item) }, Modifier.weight(1f), Emphasis.Primary)
                        BigChoice("Not a transaction", { actions.notATransaction(item) }, Modifier.weight(1f), Emphasis.Outline)
                    }
                }
                is NeedsItem.WhichAccount -> {
                    Kind("Which account?")
                    Title("${item.prompt} could be either", "${formatRupees(item.amountPaise)} debited · ${daysLabel(item.daysAgo)}")
                    item.options.forEach { option -> BigChoice(option, { actions.pickAccount(item, option) }) }
                    BigChoice("Neither is mine", { actions.pickAccount(item, null) }, emphasis = Emphasis.Outline)
                }
                is NeedsItem.PossibleTransfer -> {
                    Kind("Possible self-transfer")
                    Leg(Icons.AutoMirrored.Outlined.CallMade, item.outLine)
                    Leg(Icons.AutoMirrored.Outlined.CallReceived, item.inLine)
                    Text(item.gap, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        BigChoice("Self-transfer", { actions.transfer(item, true) }, Modifier.weight(1f), Emphasis.Primary)
                        BigChoice("Separate", { actions.transfer(item, false) }, Modifier.weight(1f), Emphasis.Outline)
                    }
                }
                is NeedsItem.NewPayee -> {
                    Kind("New payee")
                    Title(item.payee, "${formatRupees(item.amountPaise)} · ${item.whenLabel}")
                    CategoryChips(categories, null, { actions.tag(item, it) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = { actions.lent(item) }, label = { Text("Lent") })
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { actions.skip(item) }) { Text("Skip") }
                    }
                }
                is NeedsItem.Bulk -> {
                    Kind("Tag in one go")
                    Title("Tag all ${untaggedCount(item.payee)} ${item.payee} payments", "Future ones will be tagged too")
                    CategoryChips(categories, null, { actions.tag(item, it) }, singleLine = true)
                }
            }
        }
    }
}

/** What each Needs-you answer does; the route maps these to store actions with Undo. */
interface NeedsActions {
    fun teach(item: NeedsItem.Unreadable)
    fun notATransaction(item: NeedsItem)
    fun pickAccount(item: NeedsItem.WhichAccount, account: String?)
    fun transfer(item: NeedsItem.PossibleTransfer, isTransfer: Boolean)
    fun tag(item: NeedsItem, category: Category)
    fun lent(item: NeedsItem.NewPayee)
    fun skip(item: NeedsItem)
}

private fun daysLabel(daysAgo: Int) = when (daysAgo) {
    0 -> "today"
    1 -> "yesterday"
    else -> "$daysAgo days ago"
}

@Composable
private fun Kind(text: String) {
    Text(text, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.tertiary)
}

@Composable
private fun Title(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Leg(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.M))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

private val NumberToken = Regex("""\d[\d,]*(?:\.\d+)?""")

/** Numbers worth offering as the amount: skips 1–2 digit fragments such as date parts. */
internal fun amountCandidates(body: String): List<String> =
    NumberToken.findAll(body).map { it.value.trimEnd(',') }.filter { it.length >= 3 }.distinct().toList()

/** The SMS as a quiet bubble, with candidate numbers emphasised so "tap the amount" feels obvious. */
@Composable
internal fun MessageBubble(sender: String, body: String) {
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val onHighlight = MaterialTheme.colorScheme.onPrimaryContainer
    val candidates = amountCandidates(body).toSet()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Space.M)) {
            Text(sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Space.XS))
            Text(
                buildAnnotatedString {
                    var last = 0
                    NumberToken.findAll(body).forEach { match ->
                        append(body.substring(last, match.range.first))
                        if (match.value.trimEnd(',') in candidates) {
                            withStyle(SpanStyle(background = highlight, color = onHighlight, fontWeight = FontWeight.SemiBold)) { append(match.value) }
                        } else {
                            append(match.value)
                        }
                        last = match.range.last + 1
                    }
                    append(body.substring(last))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
