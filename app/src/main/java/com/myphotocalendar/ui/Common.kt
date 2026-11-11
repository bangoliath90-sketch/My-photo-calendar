package com.myphotocalendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.myphotocalendar.data.AppState

enum class ViewMode(val label: String) {
    MONTH("Month"),
    WEEK("Week"),
    SCHEDULE("Schedule"),
}

/** The menu button at the top left that switches between month, week and schedule. */
@Composable
fun ViewMenuButton(current: ViewMode, tint: Color, onSelect: (ViewMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Menu, contentDescription = "Change view", tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    trailingIcon = if (mode == current) ({ Icon(Icons.Filled.Check, contentDescription = "Current view") }) else null,
                    onClick = {
                        open = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

/** How text and lines look on a view: over the photo, or on a plain background. */
data class ViewLook(
    val photo: Boolean,
    val text: TextStyle,
    val subtle: Color,
    val line: Color,
    val iconTint: Color,
    val cardColor: Color,
)

private val PlainText = Color(0xFF1B1B1F)

fun AppState.viewLook(photoOn: Boolean): ViewLook =
    if (photoOn) {
        val style = overlayTextStyle()
        ViewLook(
            photo = true,
            text = style,
            subtle = fontColor.copy(alpha = 0.85f),
            line = lineColor(),
            iconTint = fontColor,
            cardColor = Color.White.copy(alpha = 0.92f),
        )
    } else {
        ViewLook(
            photo = false,
            text = TextStyle(color = PlainText),
            subtle = Color(0xFF5F6368),
            line = Color(0xFFE0E0E0),
            iconTint = PlainText,
            cardColor = Color.White,
        )
    }
