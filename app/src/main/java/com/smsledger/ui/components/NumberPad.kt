package com.smsledger.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsledger.core.money.AmountInput
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space

private val Keys = listOf("123", "456", "789", ".0<")

/** Custom keypad so amount entry never waits on the system keyboard (§15: no forms). */
@Composable
fun NumberPad(onKey: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                row.forEach { key ->
                    PadKey(
                        label = key,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            if (key == '<') onBackspace() else onKey(key)
                        },
                    )
                }
            }
        }
    }
}

/** Tonal pill key that squashes into a rounded square while pressed (Expressive shape motion). */
@Composable
private fun RowScope.PadKey(label: Char, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 14.dp else 30.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "keyCorner")
    val backspace = label == '<'
    Box(
        Modifier
            .weight(1f)
            .height(60.dp)
            .clip(RoundedCornerShape(corner))
            .background(
                if (backspace) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(interaction, ripple(), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (backspace) {
            Icon(
                Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            Text(label.toString(), style = MaterialTheme.typography.headlineSmallEmphasized)
        }
    }
}

/** Large amount readout for the keypad; shows a quiet ₹0 placeholder when empty. */
@Composable
fun AmountReadout(text: String, modifier: Modifier = Modifier) {
    val empty = text.isEmpty()
    val shown = if (empty) "0" else formatTyped(text)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 30.sp, color = muted)) { append("₹") }
            withStyle(SpanStyle(color = if (empty) muted.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)) { append(shown) }
        },
        style = MoneyStyles.Large,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Groups the rupee part while typing but keeps a trailing "." or partial decimals as typed. */
private fun formatTyped(text: String): String {
    val rupees = formatRupees(AmountInput.toPaise(text.substringBefore('.'))).removePrefix("₹")
    return if ('.' in text) rupees + "." + text.substringAfter('.') else rupees
}
