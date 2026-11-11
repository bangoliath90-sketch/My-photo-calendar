package com.myphotocalendar.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.myphotocalendar.data.AppState
import java.io.File

/** True when the chosen font color is dark, so the photo is lightened instead of darkened. */
fun AppState.darkText(): Boolean = fontColor.luminance() < 0.5f

/** The base text style for words drawn over the photo. */
fun AppState.overlayTextStyle(): TextStyle = TextStyle(
    color = fontColor,
    fontFamily = fontFamilyFor(fontId),
    fontSize = (16f * textScale).sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    shadow = if (textShadow) {
        Shadow(
            color = if (darkText()) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f),
            offset = Offset(0f, 2f),
            blurRadius = 6f,
        )
    } else {
        null
    },
)

/** The same style at a size relative to a normal 16 sp, keeping the chosen text size. */
fun TextStyle.sized(size: Int): TextStyle {
    val base = if (fontSize.isSpecified) fontSize.value else 16f
    return copy(fontSize = (base * size / 16f).sp)
}

/** Grid lines that show up on either a dark or light photo. */
fun AppState.lineColor(): Color =
    if (darkText()) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.28f)

/** The full-size photo with the dim layer on top. */
@Composable
fun PhotoBackground(state: AppState, photo: File?, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF3A3A44))) {
        Crossfade(targetState = photo, label = "background") { file ->
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val dimColor = if (state.darkText()) Color.White else Color.Black
        Box(
            Modifier
                .fillMaxSize()
                .background(dimColor.copy(alpha = state.dim))
        )
    }
}
