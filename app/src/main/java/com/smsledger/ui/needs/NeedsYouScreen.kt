package com.smsledger.ui.needs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Space

/**
 * To review (§7): a vertical list of decision cards. Answering a card slides it out and the count
 * updates; swiping a card sideways skips it. Every answer comes with Undo in the snackbar.
 */
@Composable
fun NeedsYouScreen(
    items: List<NeedsItem>,
    categories: List<Category>,
    onBack: () -> Unit,
    actions: NeedsActions,
    untaggedCount: (String) -> Int,
) {
    val c = LedgerTheme.colors
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("To review")
                        AnimatedContent(items.size, transitionSpec = {
                            fadeIn(fast) togetherWith fadeOut(fast)
                        }, label = "reviewCount") { n ->
                            if (n > 0) Text(" ($n)", color = c.textSecondary, fontWeight = FontWeight.Normal)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Text(
                "Nothing to review.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
                modifier = Modifier.padding(padding).padding(horizontal = Space.Gutter, vertical = Space.L),
            )
            return@Scaffold
        }
        val motion = MaterialTheme.motionScheme
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.Gutter, end = Space.Gutter,
                top = padding.calculateTopPadding() + Space.S,
                bottom = padding.calculateBottomPadding() + Space.XXL,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            items(items, key = { it.id }) { item ->
                SwipeToSkip(
                    onSkip = { actions.skip(item) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = motion.defaultEffectsSpec(),
                        placementSpec = motion.defaultSpatialSpec(),
                        fadeOutSpec = motion.fastEffectsSpec(),
                    ),
                ) {
                    NeedsCard(item, categories, actions, untaggedCount)
                }
            }
        }
    }
}

/**
 * Horizontal swipe = Skip, the one dismissive answer every card has. The card slides off; what
 * shows behind it says what the swipe does. Screen readers get the same thing as a custom action.
 */
@Composable
private fun SwipeToSkip(onSkip: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Skip") { onSkip(); true })
        },
        onDismiss = { value -> if (value != SwipeToDismissBoxValue.Settled) onSkip() },
        backgroundContent = {
            val alignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(Modifier.fillMaxSize().padding(horizontal = Space.XL), contentAlignment = alignment) {
                if (state.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Text("Skip", style = MaterialTheme.typography.labelLarge, color = LedgerTheme.colors.textSecondary)
                }
            }
        },
    ) { content() }
}
