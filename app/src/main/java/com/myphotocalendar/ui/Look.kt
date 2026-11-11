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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.myphotocalendar.data.AppState
import com.myphotocalendar.data.PhotoCrop
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
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

/** The full-size photo, moved and zoomed as you set it, with the dim layer on top. */
@Composable
fun PhotoBackground(state: AppState, photo: File?, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF3A3A44))) {
        Crossfade(targetState = photo, label = "background") { file ->
            if (file != null) {
                FramedPhoto(file, state.cropFor(file), Modifier.fillMaxSize())
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

/** Keeps the last couple of full-size photos in memory so swiping months stays smooth. */
object PhotoCache {
    private val cache = object : LruCache<String, ImageBitmap>(3) {}

    fun load(file: File): ImageBitmap? {
        val key = file.path + file.lastModified()
        cache.get(key)?.let { return it }
        return try {
            BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { cache.put(key, it) }
        } catch (e: Throwable) {
            null
        }
    }
}

@Composable
fun FramedPhoto(file: File, crop: PhotoCrop, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { PhotoCache.load(file) }
    }
    Canvas(modifier) {
        image?.let { drawFramed(it, crop) }
    }
}

/** Draws [img] to fill the area, using [crop]'s zoom and center point. */
fun DrawScope.drawFramed(img: ImageBitmap, crop: PhotoCrop) {
    val iw = img.width.toFloat()
    val ih = img.height.toFloat()
    if (iw <= 0f || ih <= 0f || size.width <= 0f || size.height <= 0f) return
    val scale = max(size.width / iw, size.height / ih) * crop.zoom.coerceAtLeast(1f)
    val sw = min(iw, size.width / scale)
    val sh = min(ih, size.height / scale)
    val left = (crop.cx * iw - sw / 2f).coerceIn(0f, iw - sw)
    val top = (crop.cy * ih - sh / 2f).coerceIn(0f, ih - sh)
    val srcW = sw.toInt().coerceIn(1, img.width - left.toInt())
    val srcH = sh.toInt().coerceIn(1, img.height - top.toInt())
    drawImage(
        image = img,
        srcOffset = IntOffset(left.toInt(), top.toInt()),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.High,
    )
}
