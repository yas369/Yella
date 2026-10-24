package com.smsledger.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Radius
import com.smsledger.ui.theme.Size
import com.smsledger.ui.theme.Space

/** Dark ink on the amber badge in both themes; white on amber falls below 4.5:1. */
@Composable
private fun onNear(): Color = LedgerTheme.colors.let { if (it.bg.luminance() > 0.5f) it.text else it.bg }

/** §7 Home: a single row, "N to review", with a small count badge. The caller hides it at 0. */
@Composable
internal fun NeedsEntryRow(count: Int, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Size.Row)
            .padding(horizontal = Space.Gutter, vertical = Space.M)
            .clearAndSetSemantics { contentDescription = "$count to review"; role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("To review", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        Box(Modifier.background(c.near, Radius.Control).padding(horizontal = Space.S, vertical = Space.XS)) {
            AnimatedContent(count, transitionSpec = {
                fadeIn(fast) togetherWith fadeOut(fast)
            }, label = "needsCount") { n ->
                Text("$n", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = onNear())
            }
        }
    }
}
