package com.myphotocalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myphotocalendar.data.AppState
import com.myphotocalendar.data.CalendarInfo
import com.myphotocalendar.data.EditScope
import com.myphotocalendar.data.EventDraft
import com.myphotocalendar.data.matchingOption
import com.myphotocalendar.data.repeatOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Gray = Color(0xFF5F6368)
private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val shortTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private val ReminderChoices: List<Pair<Int?, String>> = listOf(
    null to "No reminder",
    0 to "At time of event",
    10 to "10 minutes before",
    30 to "30 minutes before",
    60 to "1 hour before",
    120 to "2 hours before",
    1440 to "1 day before",
    10080 to "1 week before",
)

private fun reminderLabel(minutes: Int?): String =
    ReminderChoices.firstOrNull { it.first == minutes }?.second ?: "$minutes minutes before"

private fun newDraft(date: LocalDate, time: LocalTime?): EventDraft {
    val now = LocalDateTime.now()
    val start = if (time != null) {
        time
    } else if (date == now.toLocalDate() && now.hour < 23) {
        LocalTime.of(now.hour + 1, 0)
    } else {
        LocalTime.of(9, 0)
    }
    val end = start.plusHours(1)
    return EventDraft(
        startDate = date,
        startTime = start,
        endDate = if (end.isBefore(start)) date.plusDays(1) else date,
        endTime = end,
    )
}

/** The page for adding a new event (eventId = null) or changing an existing one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    state: AppState,
    eventId: Long?,
    date: LocalDate,
    startTime: LocalTime? = null,
    instanceBegin: Long? = null,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<EventDraft?>(if (eventId == null) newDraft(date, startTime) else null) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // For repeating events: asks "this event / following / all" before saving or deleting.
    var askScopeFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(eventId) {
        val cals = withContext(Dispatchers.IO) { state.calendar.writableCalendars() }
        calendars = cals
        if (eventId != null) {
            val loaded = withContext(Dispatchers.IO) { state.calendar.loadDraft(eventId, instanceBegin) }
            if (loaded == null) {
                error = "This event couldn't be found. It may have been deleted."
            } else {
                draft = loaded
            }
        } else {
            draft = draft?.copy(calendarId = draft?.calendarId ?: cals.firstOrNull()?.id)
        }
    }

    val d = draft
    val readOnly = false

    fun runSave(current: EventDraft, scopeChoice: EditScope) {
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { state.calendar.save(current, scopeChoice) }
            saving = false
            if (ok) onDone() else error = "Couldn't save the event. Please try again."
        }
    }

    fun runDelete(current: EventDraft, scopeChoice: EditScope) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { state.calendar.delete(current, scopeChoice) }
            if (ok) onDone() else error = "Couldn't delete the event."
        }
    }

    fun save() {
        val current = draft ?: return
        val start = LocalDateTime.of(current.startDate, if (current.allDay) LocalTime.MIDNIGHT else current.startTime)
        val end = LocalDateTime.of(current.endDate, if (current.allDay) LocalTime.MIDNIGHT else current.endTime)
        if (end.isBefore(start)) {
            error = "The end is before the start."
            return
        }
        if (current.id == null && current.calendarId == null) {
            error = "No calendar to save to. Make sure your Google account is synced on this phone."
            return
        }
        if (current.series != null) askScopeFor = "save" else runSave(current, EditScope.ALL)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
            Text(
                if (eventId == null) "New event" else if (readOnly) "Event" else "Edit event",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!readOnly && d != null) {
                Button(onClick = { save() }, enabled = !saving, modifier = Modifier.padding(end = 8.dp)) {
                    Text(if (saving) "Saving…" else "Save")
                }
            }
        }

        if (d == null) {
            Text(error ?: "Loading…", modifier = Modifier.padding(20.dp), fontSize = 15.sp)
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (readOnly) {
                Text(
                    "This is a repeating event. For now, change or delete it in the Google Calendar app. Editing repeating events here is coming in a later version.",
                    fontSize = 14.sp,
                    color = Gray,
                    modifier = Modifier.background(Color(0xFFF1F3F4), RoundedCornerShape(12.dp)).padding(12.dp),
                )
            }

            OutlinedTextField(
                value = d.title,
                onValueChange = { draft = d.copy(title = it) },
                label = { Text("Title") },
                singleLine = true,
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("All day", fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = d.allDay,
                    onCheckedChange = { draft = d.copy(allDay = it) },
                    enabled = !readOnly,
                )
            }

            DateTimeRow(
                label = "Starts",
                date = d.startDate,
                time = d.startTime,
                showTime = !d.allDay,
                enabled = !readOnly,
                onDate = { newDate ->
                    // Keep the same length when the start moves.
                    val shift = Duration.between(d.startDate.atStartOfDay(), newDate.atStartOfDay()).toDays()
                    draft = d.copy(startDate = newDate, endDate = d.endDate.plusDays(shift))
                },
                onTime = { newTime ->
                    val oldStart = LocalDateTime.of(d.startDate, d.startTime)
                    val oldEnd = LocalDateTime.of(d.endDate, d.endTime)
                    val length = Duration.between(oldStart, oldEnd)
                    val newEnd = LocalDateTime.of(d.startDate, newTime).plus(length)
                    draft = d.copy(startTime = newTime, endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
                },
            )
            DateTimeRow(
                label = "Ends",
                date = d.endDate,
                time = d.endTime,
                showTime = !d.allDay,
                enabled = !readOnly,
                onDate = { draft = d.copy(endDate = it) },
                onTime = { draft = d.copy(endTime = it) },
            )

            if (d.id == null) {
                CalendarChooser(calendars, d.calendarId) { draft = d.copy(calendarId = it) }
            } else {
                calendars.firstOrNull { it.id == d.calendarId }?.let { cal ->
                    LabeledValue("Calendar", cal.name, dot = eventColor(cal.color))
                }
            }

            RepeatChooser(d) { draft = it }

            ReminderChooser(d.reminderMinutes, enabled = !readOnly) { draft = d.copy(reminderMinutes = it) }

            OutlinedTextField(
                value = d.location,
                onValueChange = { draft = d.copy(location = it) },
                label = { Text("Location") },
                singleLine = true,
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = d.description,
                onValueChange = { draft = d.copy(description = it) },
                label = { Text("Notes") },
                minLines = 3,
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = Color(0xFFC5221F), fontSize = 14.sp) }

            if (d.id != null && !readOnly) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete event", color = Color(0xFFC5221F))
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (confirmDelete && d != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this event?") },
            text = { Text("It will also be removed from your Google Calendar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (d.series != null) askScopeFor = "delete" else runDelete(d, EditScope.ALL)
                }) { Text("Delete", color = Color(0xFFC5221F)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    val action = askScopeFor
    if (action != null && d != null) {
        var choice by remember(action) { mutableStateOf(EditScope.THIS) }
        AlertDialog(
            onDismissRequest = { askScopeFor = null },
            title = { Text(if (action == "save") "Save repeating event" else "Delete repeating event") },
            text = {
                Column {
                    EditScope.entries.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable { choice = option }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == option, onClick = { choice = option })
                            Text(option.label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askScopeFor = null
                    if (action == "save") runSave(d, choice) else runDelete(d, choice)
                }) { Text(if (action == "save") "Save" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { askScopeFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RepeatChooser(d: EventDraft, onChange: (EventDraft) -> Unit) {
    val options = repeatOptions(d.startDate)
    val match = matchingOption(d.rule, d.startDate)
    val label = match?.label ?: "Custom (made in Google Calendar)"
    var open by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }

    Box {
        LabeledValue(label = "Repeat", value = label, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        open = false
                        onChange(d.copy(rule = option.rule, repeatCount = if (option.rule == null) null else d.repeatCount))
                    },
                )
            }
        }
    }

    if (d.rule != null) {
        var endsOpen by remember { mutableStateOf(false) }
        val endsLabel = when {
            d.repeatUntil != null -> "Until ${d.repeatUntil.format(dateFormat)}"
            d.repeatCount != null -> "After ${d.repeatCount} times"
            else -> "Never"
        }
        Box {
            LabeledValue(label = "Ends", value = endsLabel, onClick = { endsOpen = true })
            DropdownMenu(expanded = endsOpen, onDismissRequest = { endsOpen = false }) {
                DropdownMenuItem(text = { Text("Never") }, onClick = {
                    endsOpen = false
                    onChange(d.copy(repeatUntil = null, repeatCount = null))
                })
                DropdownMenuItem(text = { Text("On a date…") }, onClick = {
                    endsOpen = false
                    pickUntil = true
                })
            }
        }
    }

    if (pickUntil) {
        val initial = d.repeatUntil ?: d.startDate.plusMonths(3)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickUntil = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val chosen = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(d.copy(repeatUntil = maxOf(chosen, d.startDate), repeatCount = null))
                    }
                    pickUntil = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickUntil = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    showTime: Boolean,
    enabled: Boolean,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(0.25f))
        TextButton(onClick = { pickDate = true }, enabled = enabled, modifier = Modifier.weight(0.5f)) {
            Text(date.format(dateFormat), fontSize = 15.sp)
        }
        if (showTime) {
            TextButton(onClick = { pickTime = true }, enabled = enabled, modifier = Modifier.weight(0.25f)) {
                Text(time.format(shortTime), fontSize = 15.sp)
            }
        } else {
            Spacer(Modifier.weight(0.25f))
        }
    }

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickTime) {
        val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onTime(LocalTime.of(timeState.hour, timeState.minute))
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun CalendarChooser(calendars: List<CalendarInfo>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.id == selectedId }
    Box {
        LabeledValue(
            label = "Calendar",
            value = selected?.name ?: if (calendars.isEmpty()) "No calendars found" else "Choose a calendar",
            dot = selected?.let { eventColor(it.color) },
            onClick = if (calendars.isNotEmpty()) ({ open = true }) else null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            calendars.forEach { cal ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(cal.name)
                            if (cal.account.isNotBlank() && cal.account != cal.name) {
                                Text(cal.account, fontSize = 12.sp, color = Gray)
                            }
                        }
                    },
                    leadingIcon = { Box(Modifier.size(14.dp).background(eventColor(cal.color), CircleShape)) },
                    onClick = { onSelect(cal.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun ReminderChooser(minutes: Int?, enabled: Boolean, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        LabeledValue(
            label = "Reminder",
            value = reminderLabel(minutes),
            onClick = if (enabled) ({ open = true }) else null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ReminderChoices.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, dot: Color? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F3F4), RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(0.35f))
        if (dot != null) {
            Box(Modifier.size(12.dp).background(dot, CircleShape))
            Spacer(Modifier.size(8.dp))
        }
        Text(value, fontSize = 15.sp, color = if (onClick != null) TodayBlue else Color.Black, modifier = Modifier.weight(0.65f))
    }
}
