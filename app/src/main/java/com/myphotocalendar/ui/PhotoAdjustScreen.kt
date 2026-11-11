package com.myphotocalendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myphotocalendar.data.AppState
import com.myphotocalendar.data.PhotoCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import kotlin.math.max

/** Full-screen page to drag and pinch a photo into place. */
@Composable
fun PhotoAdjustScreen(state: AppState, file: File, month: YearMonth, onDone: () -> Unit) {
    var crop by remember(file) { mutableStateOf(state.cropFor(file)) }
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { PhotoCache.load(file) }
    }
    val dimColor = if (state.darkText()) Color.White else Color.Black

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(image) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        val img = image ?: return@detectTransformGestures
                        val iw = img.width.toFloat()
                        val ih = img.height.toFloat()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val zoom = (crop.zoom * zoomChange).coerceIn(1f, 5f)
                        val scale = max(w / iw, h / ih) * zoom
                        // Half the visible part of the photo, as a fraction of the photo.
                        val halfW = (w / scale / iw / 2f).coerceAtMost(0.5f)
                        val halfH = (h / scale / ih / 2f).coerceAtMost(0.5f)
                        val cx = (crop.cx - pan.x / scale / iw).coerceIn(halfW, 1f - halfW)
                        val cy = (crop.cy - pan.y / scale / ih).coerceIn(halfH, 1f - halfH)
                        crop = PhotoCrop(zoom, cx, cy)
                    }
                }
        ) {
            image?.let { drawFramed(it, crop) }
        }
        Box(Modifier.fillMaxSize().background(dimColor.copy(alpha = state.dim)))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Text(monthLabel(month), style = state.overlayTextStyle().sized(24))
            Text(
                "Drag to move · pinch to zoom",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Box(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { crop = PhotoCrop() },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
                ) { Text("Reset") }
                OutlinedButton(
                    onClick = onDone,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
                ) { Text("Cancel") }
                Box(Modifier.weight(1f))
                Button(onClick = {
                    state.updateCrop(file, crop)
                    onDone()
                }) { Text("Done") }
            }
        }
    }
}
