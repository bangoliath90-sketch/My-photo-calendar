package com.myphotocalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val DAYS_AHEAD = 365L

private sealed interface ScheduleRow {
    data class MonthHeader(val month: YearMonth) : ScheduleRow
    data class Day(val date: LocalDate, val events: List<EventItem>) : ScheduleRow
}

/** A scrolling list of everything coming up, starting today. */
@Composable
fun ScheduleScreen(
    state: AppState,
    onSwitchView: (ViewMode) -> Unit,
    onOpenSettings: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onOpenEvent: (EventItem, LocalDate) -> Unit,
    onNewEvent: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val look = state.viewLook(state.photoOnSchedule)

    val rows by produceState<List<ScheduleRow>?>(null, state.calendar.version, state.calendar.hasPermission) {
        value = withContext(Dispatchers.IO) {
            val last = today.plusDays(DAYS_AHEAD)
            val events = state.calendar.eventsBetween(today, last)
            val out = mutableListOf<ScheduleRow>()
            var month: YearMonth? = null
            var date = today
            while (!date.isAfter(last)) {
                val onDay = events.filter { it.isOn(date) }
                if (onDay.isNotEmpty() || date == today) {
                    val m = YearMonth.from(date)
                    if (m != month) {
                        out += ScheduleRow.MonthHeader(m)
                        month = m
                    }
                    out += ScheduleRow.Day(date, onDay)
                }
                date = date.plusDays(1)
            }
            out
        }
    }

    Box(Modifier.fillMaxSize().background(if (look.photo) Color.Transparent else Color(0xFFF6F6F8))) {
        if (look.photo) {
            PhotoBackground(state, state.photoFor(YearMonth.from(today), today), Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewMenuButton(ViewMode.SCHEDULE, look.iconTint, onSwitchView)
                Text("Schedule", style = look.text.sized(22), modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = look.iconTint)
                }
            }

            val list = rows
            when {
                !state.calendar.hasPermission -> Text(
                    "Calendar access is off. Switch to the month view and tap Allow access.",
                    style = look.text.sized(15),
                    modifier = Modifier.padding(20.dp),
                )
                list == null -> Text("Loading…", style = look.text, modifier = Modifier.padding(20.dp))
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 100.dp),
                ) {
                    items(list, key = {
                        when (it) {
                            is ScheduleRow.MonthHeader -> "m${it.month}"
                            is ScheduleRow.Day -> "d${it.date}"
                        }
                    }) { row ->
                        when (row) {
                            is ScheduleRow.MonthHeader -> Text(
                                monthLabel(row.month),
                                style = look.text.sized(18).copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp),
                            )
                            is ScheduleRow.Day -> DayBlock(row, today, look, onDayClick, onOpenEvent)
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onNewEvent(today) },
            containerColor = TodayBlue,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "New event") }
    }
}

@Composable
private fun DayBlock(
    row: ScheduleRow.Day,
    today: LocalDate,
    look: ViewLook,
    onDayClick: (LocalDate) -> Unit,
    onOpenEvent: (EventItem, LocalDate) -> Unit,
) {
    val isToday = row.date == today
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(
            Modifier.width(52.dp).clickable { onDayClick(row.date) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                row.date.format(DateTimeFormatter.ofPattern("EEE")),
                style = look.text.sized(12).copy(color = if (isToday) TodayBlue else look.subtle),
            )
            if (isToday) {
                Box(Modifier.size(32.dp).background(TodayBlue, CircleShape), contentAlignment = Alignment.Center) {
                    Text(row.date.dayOfMonth.toString(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                    Text(row.date.dayOfMonth.toString(), style = look.text.sized(16))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (row.events.isEmpty()) {
                Text(
                    "Nothing scheduled today",
                    style = look.text.sized(14).copy(color = look.subtle),
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            row.events.forEach { ev ->
                val whenText = when {
                    ev.allDay -> "All day"
                    ev.startDate == row.date && ev.lastDate == row.date -> "${formatTime(ev.begin)} – ${formatTime(ev.end)}"
                    ev.startDate == row.date -> "Starts ${formatTime(ev.begin)}"
                    ev.lastDate == row.date -> "Until ${formatTime(ev.end)}"
                    else -> "All day"
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(eventColor(ev.color), RoundedCornerShape(10.dp))
                        .clickable { onOpenEvent(ev, row.date) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(ev.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        whenText + if (ev.location.isNotBlank()) " · ${ev.location}" else "",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
