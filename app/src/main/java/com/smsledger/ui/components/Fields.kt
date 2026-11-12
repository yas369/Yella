package com.smsledger.ui.components

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.smsledger.ui.theme.LedgerTheme

/** Text fields (design.md §6): a neutral filled block with no border, in sheets and on screens alike. */
@Composable
fun fieldColors(): TextFieldColors {
    val c = LedgerTheme.colors
    return OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = c.fill,
        focusedContainerColor = c.fill,
        unfocusedBorderColor = Color.Transparent,
        focusedBorderColor = Color.Transparent,
        cursorColor = c.accent,
    )
}
