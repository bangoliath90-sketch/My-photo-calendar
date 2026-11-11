package com.myphotocalendar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.myphotocalendar.data.AppState
import com.myphotocalendar.data.ShuffleFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val Gray = Color(0xFF5F6368)
private val PageBg = Color(0xFFF6F6F8)

private val FontColors = listOf(
    "White" to Color(0xFFFFFFFF),
    "Black" to Color(0xFF1B1B1F),
    "Cream" to Color(0xFFFFF3D6),
    "Yellow" to Color(0xFFFFD54A),
    "Sky blue" to Color(0xFF9FD3FF),
    "Pink" to Color(0xFFFFB3C7),
    "Mint" to Color(0xFFB4F0CF),
    "Navy" to Color(0xFF1F2A5C),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, monthOffset: Int, onBack: () -> Unit) {
    val today = remember { LocalDate.now() }
    val month = remember(monthOffset) { YearMonth.from(today).plusMonths(monthOffset.toLong()) }
    val monthName = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var importing by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf(false) }
    // When true, tapping a photo pins it to this month.
    var choosingPin by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        importMessage = null
        scope.launch {
            val added = withContext(Dispatchers.IO) { uris.mapNotNull { state.photoStore.import(it) } }
            state.addPhotos(added)
            importing = false
            val failed = uris.size - added.size
            importMessage = when {
                failed == 0 -> "Added ${added.size} photo${if (added.size == 1) "" else "s"}."
                else -> "Added ${added.size}. $failed couldn't be read."
            }
        }
    }

    fun openPicker() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to calendar")
            }
            Text("$monthName look", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Live preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                PhotoBackground(state, state.photoFor(month, today), Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(monthLabel(month), style = state.overlayTextStyle().copy(fontSize = 26.sp))
                    Text("Sun  Mon  Tue  Wed  Thu  Fri  Sat", style = state.overlayTextStyle().copy(fontSize = 13.sp))
                }
            }

            // ---- Background photo ----
            Section("BACKGROUND PHOTO") {
                val pinned = state.isPinned(month.monthValue)
                Text("$monthName uses", fontSize = 14.sp)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !pinned && !choosingPin,
                        onClick = {
                            choosingPin = false
                            state.unpin(month.monthValue)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Shuffle") }
                    SegmentedButton(
                        selected = pinned || choosingPin,
                        onClick = { choosingPin = true; managing = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("One photo") }
                }

                if (choosingPin || pinned) {
                    Text(
                        if (state.photos.isEmpty()) "Add some photos below first."
                        else "Tap a photo below to use it for every $monthName.",
                        fontSize = 13.sp,
                        color = Gray,
                    )
                } else {
                    Text("Shuffle changes the photo", fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShuffleFrequency.entries.forEach { f ->
                            FilterChip(
                                selected = state.shuffleFrequency == f,
                                onClick = { state.updateShuffleFrequency(f) },
                                label = { Text(f.label, fontSize = 12.sp) },
                            )
                        }
                    }
                    Text("Shuffle picks from all your photos. This setting applies to every month.", fontSize = 12.sp, color = Gray)
                }
            }

            // ---- Your photos ----
            Section("YOUR PHOTOS (${state.photos.size})") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { openPicker() }, enabled = !importing) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Add photos")
                    }
                    if (state.photos.isNotEmpty()) {
                        TextButton(onClick = { managing = !managing; choosingPin = false }) {
                            Text(if (managing) "Done" else "Remove photos")
                        }
                    }
                }
                Text(
                    "Choose Google Photos in the picker to add photos from your Google Photos library. You can select many at once.",
                    fontSize = 12.sp,
                    color = Gray,
                )
                if (importing) {
                    Text("Adding photos…", fontSize = 13.sp)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                importMessage?.let { Text(it, fontSize = 13.sp) }

                val pinnedName = state.pins[month.monthValue]
                state.photos.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { file ->
                            PhotoThumb(
                                file = file,
                                isPinnedHere = file.name == pinnedName,
                                managing = managing,
                                onTap = {
                                    if (!managing && (choosingPin || pinnedName != null)) {
                                        state.pin(month.monthValue, file)
                                        choosingPin = false
                                    }
                                },
                                onRemove = { state.removePhoto(file) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // ---- Text and readability ----
            Section("TEXT AND READABILITY") {
                Text("Dim photo · ${(state.dim * 100).toInt()}%", fontSize = 14.sp)
                Slider(
                    value = state.dim,
                    onValueChange = { state.updateDim(it) },
                    valueRange = 0f..0.8f,
                    steps = 15,
                )
                Text(
                    if (state.darkText()) "Dark text lightens the photo instead." else "Light text darkens the photo.",
                    fontSize = 12.sp,
                    color = Gray,
                )

                Text("Font color", fontSize = 14.sp)
                FontColors.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { (name, color) ->
                            val selected = state.fontColor == color
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .border(
                                        BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color(0xFF1A73E8) else Color(0xFFC4C7CC)),
                                        CircleShape,
                                    )
                                    .padding(4.dp)
                                    .background(color, CircleShape)
                                    .clickable(onClickLabel = name) { state.updateFontColor(color) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "$name selected",
                                        tint = if (state.darkText()) Color.White else Color.Black,
                                    )
                                }
                            }
                        }
                    }
                }

                SwitchRow("Bold text", state.bold) { state.updateBold(it) }
                SwitchRow("Text shadow", state.textShadow) { state.updateTextShadow(it) }
            }

            Text(
                "My Photo Calendar · version ${appVersion(context)}",
                fontSize = 12.sp,
                color = Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gray)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PhotoThumb(
    file: File,
    isPinnedHere: Boolean,
    managing: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                BorderStroke(if (isPinnedHere) 3.dp else 0.dp, if (isPinnedHere) Color(0xFF1A73E8) else Color.Transparent),
                RoundedCornerShape(10.dp),
            )
            .clickable { onTap() }
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isPinnedHere) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color(0xFF1A73E8), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) { Text("This month", color = Color.White, fontSize = 11.sp) }
        }
        if (managing) {
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove photo")
            }
        }
    }
}

private fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}
