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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.myphotocalendar.data.CalendarInfo
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
fun SettingsScreen(state: AppState, monthOffset: Int, onBack: () -> Unit, onAdjustPhoto: (File) -> Unit) {
    val today = remember { LocalDate.now() }
    val month = remember(monthOffset) { YearMonth.from(today).plusMonths(monthOffset.toLong()) }
    LaunchedEffect(month) { state.displayMonth = month.monthValue }
    val monthName = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Which drop-down section is open; opening one closes the others.
    var openSection by rememberSaveable { mutableStateOf<String?>(null) }
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
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Live preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                val shownPhoto = state.photoFor(month, today)
                PhotoBackground(state, shownPhoto, Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(monthLabel(month), style = state.overlayTextStyle().sized(26))
                    Text("Sun  Mon  Tue  Wed  Thu  Fri  Sat", style = state.overlayTextStyle().sized(13))
                }
                if (shownPhoto != null) {
                    Button(
                        onClick = { onAdjustPhoto(shownPhoto) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) { Text("Move & zoom", fontSize = 13.sp) }
                }
            }

            // ---- Background photo ----
            Section("Background photo", openSection, { openSection = it }) {
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
            Section("Your photos (${state.photos.size})", openSection, { openSection = it }) {
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
            // ---- Text style ----
            Section("Text style", openSection, { openSection = it }) {
                val ownLook = state.hasOwnLook(month.monthValue)
                Text("Style settings apply to", fontSize = 14.sp)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !ownLook,
                        onClick = { state.removeOwnLook(month.monthValue) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("All months") }
                    SegmentedButton(
                        selected = ownLook,
                        onClick = { state.giveOwnLook(month.monthValue) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Only $monthName") }
                }
                Text(
                    if (ownLook) "$monthName has its own style. Changes below (including dimming) affect only $monthName. Tap All months to go back to the shared style."
                    else "Changes below affect every month that doesn't have its own style.",
                    fontSize = 12.sp,
                    color = Gray,
                )
                val customMonths = state.monthLooks.keys.sorted()
                if (customMonths.isNotEmpty()) {
                    Text(
                        "Months with their own style: " + customMonths.joinToString(", ") {
                            java.time.Month.of(it).getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
                        },
                        fontSize = 12.sp,
                        color = Gray,
                    )
                }

                Text("Templates", fontSize = 14.sp)
                Text("Tap one to use its font, color and size. You can fine-tune it below.", fontSize = 12.sp, color = Gray)
                val previewPhoto = state.photoFor(month, today)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(StyleTemplates) { t ->
                        TemplateCard(
                            state = state,
                            template = t,
                            photo = previewPhoto,
                            month = month,
                            selected = state.fontId == t.fontId && state.fontColor == t.color &&
                                state.textScale == t.scale && state.bold == t.bold && state.textShadow == t.shadow,
                            onClick = { state.applyStyle(t.fontId, t.color, t.scale, t.bold, t.shadow) },
                        )
                    }
                }

                FontChooser(state)

                Text("Text size · ${(state.textScale * 100).toInt()}%", fontSize = 14.sp)
                Slider(
                    value = state.textScale,
                    onValueChange = { state.updateTextScale((it * 20).toInt() / 20f) },
                    valueRange = 0.8f..1.5f,
                    steps = 13,
                )
            }

            Section("Font color and readability", openSection, { openSection = it }) {
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

                CustomColor(state)

                SwitchRow("Bold text", state.bold) { state.updateBold(it) }
                SwitchRow("Text shadow", state.textShadow) { state.updateTextShadow(it) }
            }

            // ---- Photos on other views ----
            Section("Photos on other views", openSection, { openSection = it }) {
                Text("The month view always shows the photo. Choose which other views show it too.", fontSize = 12.sp, color = Gray)
                SwitchRow("Week view", state.photoOnWeek) { state.updatePhotoOnWeek(it) }
                SwitchRow("Day page", state.photoOnDay) { state.updatePhotoOnDay(it) }
                SwitchRow("Schedule view", state.photoOnSchedule) { state.updatePhotoOnSchedule(it) }
            }

            // ---- Reminders ----
            Section("Reminders", openSection, { openSection = it }) {
                SwitchRow("Show reminder notifications", state.notify) { state.updateNotify(it) }
                Text(
                    "Reminders appear at the time you set on each event. If your phone's calendar app also shows them, " +
                        "you can turn off that app's notifications in your phone's Settings to avoid getting two.",
                    fontSize = 12.sp,
                    color = Gray,
                )
                val notifPermission = rememberNotificationPermission()
                if (state.notify && !notifPermission.granted) {
                    Text("Notifications are turned off for this app.", fontSize = 13.sp, color = Color(0xFFC5221F))
                    TextButton(onClick = notifPermission.request) { Text("Allow notifications") }
                }
            }

            // ---- Calendars ----
            val calendars by produceState(emptyList<CalendarInfo>(), state.calendar.hasPermission) {
                value = withContext(Dispatchers.IO) { state.calendar.allCalendars() }
            }
            Section("Calendars", openSection, { openSection = it }) {
                if (!state.calendar.hasPermission) {
                    Text("Allow calendar access on the month view to see your calendars here.", fontSize = 13.sp, color = Gray)
                } else if (calendars.isEmpty()) {
                    Text("No calendars found. Make sure your Google account is synced on this phone.", fontSize = 13.sp, color = Gray)
                } else {
                    Text("Switch off a calendar to hide its events in this app. Nothing is deleted.", fontSize = 12.sp, color = Gray)
                    var lastAccount: String? = null
                    calendars.forEach { cal ->
                        if (cal.account != lastAccount) {
                            Text(cal.account, fontSize = 12.sp, color = Gray, modifier = Modifier.padding(top = 4.dp))
                            lastAccount = cal.account
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).background(eventColor(cal.color), CircleShape))
                            Spacer(Modifier.size(10.dp))
                            Text(cal.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = cal.id !in state.calendar.hiddenCalendars,
                                onCheckedChange = { state.calendar.setCalendarShown(cal.id, it) },
                            )
                        }
                    }
                }
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
private fun Section(
    title: String,
    openSection: String?,
    onOpenChange: (String?) -> Unit,
    content: @Composable () -> Unit,
) {
    // The part before any "(count)" names the section, so it stays open when a count changes.
    val key = title.substringBefore(" (")
    val open = openSection == key
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Close $key" else "Open $key") {
                    onOpenChange(if (open) null else key)
                }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Gray,
            )
        }
        AnimatedVisibility(visible = open) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        }
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

@Composable
private fun TemplateCard(
    state: AppState,
    template: StyleTemplate,
    photo: File?,
    month: YearMonth,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val style = TextStyle(
        color = template.color,
        fontFamily = fontFamilyFor(template.fontId),
        fontWeight = if (template.bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = (18f * template.scale).sp,
        shadow = if (template.shadow) {
            Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 2f), 6f)
        } else {
            null
        },
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 120.dp, height = 84.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    BorderStroke(if (selected) 3.dp else 0.dp, if (selected) Color(0xFF1A73E8) else Color.Transparent),
                    RoundedCornerShape(12.dp),
                )
                .clickable(onClickLabel = "Use ${template.name} style") { onClick() }
        ) {
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF6B7A8F)))
            }
            val darkText = template.color.luminance() < 0.5f
            Box(Modifier.fillMaxSize().background((if (darkText) Color.White else Color.Black).copy(alpha = 0.3f)))
            Text(
                month.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()) + " 11",
                style = style,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(template.name, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FontChooser(state: AppState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F3F4), RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Font", fontSize = 14.sp, modifier = Modifier.weight(0.3f))
            Text(
                fontNameFor(state.fontId),
                fontFamily = fontFamilyFor(state.fontId),
                fontSize = 18.sp,
                color = Color(0xFF1A73E8),
                modifier = Modifier.weight(0.7f),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FontChoices.forEach { f ->
                DropdownMenuItem(
                    text = { Text(f.name, fontFamily = f.family, fontSize = 20.sp) },
                    trailingIcon = if (f.id == state.fontId) ({ Icon(Icons.Filled.Check, contentDescription = "Selected") }) else null,
                    onClick = {
                        state.updateFont(f.id)
                        open = false
                    },
                )
            }
        }
    }
}

/** Any color, picked with a hue slider and a lightness slider. */
@Composable
private fun CustomColor(state: AppState) {
    var open by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(200f) }
    var light by remember { mutableFloatStateOf(0.75f) }
    val color = Color.hsl(hue, 0.85f, light)
    TextButton(onClick = { open = !open }) { Text(if (open) "Hide custom color" else "Custom color…") }
    if (open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(color, CircleShape).border(1.dp, Color(0xFFC4C7CC), CircleShape))
            Spacer(Modifier.size(12.dp))
            Button(onClick = { state.updateFontColor(color) }) { Text("Use this color") }
        }
        Text("Color", fontSize = 13.sp, color = Gray)
        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
        Text("Light or dark", fontSize = 13.sp, color = Gray)
        Slider(value = light, onValueChange = { light = it }, valueRange = 0.1f..0.95f)
    }
}

class NotificationPermission(val granted: Boolean, val request: () -> Unit)

@Composable
private fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    fun check() = android.os.Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(check()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = check() }
    return NotificationPermission(granted) {
        if (android.os.Build.VERSION.SDK_INT >= 33) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
