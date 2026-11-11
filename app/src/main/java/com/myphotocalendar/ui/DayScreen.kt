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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myphotocalendar.data.AppState
import com.myphotocalendar.data.EventItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Gray = Color(0xFF5F6368)
private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat)

/** One day's schedule: everything on that day, plus a button to add an event. */
@Composable
fun DayScreen(
    state: AppState,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onAddEvent: (LocalDate) -> Unit,
    onOpenEvent: (Long) -> Unit,
) {
    val events by produceState(
        initialValue = emptyList<EventItem>(),
        date, state.calendar.version, state.calendar.hasPermission,
    ) {
        value = withContext(Dispatchers.IO) { state.calendar.eventsBetween(date, date) }
    }
    val isToday = date == LocalDate.now()

    Box(Modifier.fillMaxSize().background(Color(0xFFF6F6F8))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to month")
                }
                Column(Modifier.weight(1f)) {
                    Text(date.format(dayTitleFormat), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (isToday) "Today" else date.year.toString(), fontSize = 13.sp, color = if (isToday) TodayBlue else Gray)
                }
                IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }

            if (!state.calendar.hasPermission) {
                Text(
                    "Calendar access is off, so events can't be shown or added. Go back to the month view and tap Allow access.",
                    modifier = Modifier.padding(20.dp),
                    fontSize = 15.sp,
                )
            } else if (events.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing scheduled", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Tap Add event to schedule something.", fontSize = 14.sp, color = Gray)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(events, key = { "${it.eventId}-${it.begin}" }) { ev ->
                        EventRow(ev, date) { onOpenEvent(ev.eventId) }
                    }
                }
            }
        }

        if (state.calendar.hasPermission) {
            ExtendedFloatingActionButton(
                onClick = { onAddEvent(date) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add event") },
                containerColor = TodayBlue,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            )
        }
    }
}

@Composable
private fun EventRow(ev: EventItem, date: LocalDate, onClick: () -> Unit) {
    val whenText = when {
        ev.allDay -> "All day"
        ev.startDate == date && ev.lastDate == date -> "${formatTime(ev.begin)} – ${formatTime(ev.end)}"
        ev.startDate == date -> "Starts ${formatTime(ev.begin)}"
        ev.lastDate == date -> "Until ${formatTime(ev.end)}"
        else -> "All day"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(44.dp)
                .background(eventColor(ev.color), RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ev.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(whenText, fontSize = 14.sp, color = Gray)
            if (ev.location.isNotBlank()) Text(ev.location, fontSize = 13.sp, color = Gray)
        }
    }
}
