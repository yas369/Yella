package com.smsledger.ui.needs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.smsledger.ui.components.LedgerShapes
import com.smsledger.ui.components.ShapeBadge
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.theme.Space

/** The one queue for everything uncertain (§15.2). Answered cards animate out; Undo comes from the snackbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedsYouScreen(
    items: List<NeedsItem>,
    categories: List<Category>,
    onBack: () -> Unit,
    actions: NeedsActions,
    untaggedCount: (String) -> Int,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Needs you") },
                subtitle = if (items.isNotEmpty()) ({ Text("${items.size} to go · one tap each") }) else null,
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            AllCaughtUp(onBack, Modifier.padding(padding))
            return@Scaffold
        }
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
                NeedsCard(item, categories, actions, untaggedCount, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun AllCaughtUp(onBack: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(Space.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeBadge(LedgerShapes.Done, MaterialTheme.colorScheme.primaryContainer, size = 112.dp) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(Space.XL))
        Text("All caught up", style = MaterialTheme.typography.headlineSmallEmphasized)
        Spacer(Modifier.height(Space.S))
        Text(
            "New questions show up here when a message needs you.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.XL))
        Button(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text("Back to Today") }
    }
}
