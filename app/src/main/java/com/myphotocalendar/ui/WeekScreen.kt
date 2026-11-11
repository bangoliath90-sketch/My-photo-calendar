package com.myphotocalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myphotocalendar.data.AppState
import com.myphotocalendar.data.EventItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CENTER = 5000
private const val COUNT = 10000
private val HourHeight = 56.dp
private val GutterWidth = 44.dp
private val NowRed = Color(0xFFD93025)

/** The Sunday that starts the week containing [date]. */
fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % 7).toLong())

private fun weekTitle(start: LocalDate): String {
    val end = start.plusDays(6)
    val sm = YearMonth.from(start)
    val em = YearMonth.from(end)
    return if (sm == em) monthLabel(sm)
    else if (start.year == end.year) "${start.format(DateTimeFormatter.ofPattern("MMM"))} – ${end.format(DateTimeFormatter.ofPattern("MMM yyyy"))}"
    else "${start.format(DateTimeFormatter.ofPattern("MMM yyyy"))} – ${end.format(DateTimeFormatter.ofPattern("MMM yyyy"))}"
}

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

/** A timed event placed in a day column: minutes from midnight, and which side-by-side lane. */
private data class Placed(val ev: EventItem, val startMin: Int, val endMin: Int, var lane: Int = 0, var lanes: Int = 1)

private fun layoutDay(events: List<EventItem>, date: LocalDate): List<Placed> {
    val zone = ZoneId.systemDefault()
    val placed = events.filter { !it.allDay && it.isOn(date) }.map { ev ->
        val s = Instant.ofEpochMilli(ev.begin).atZone(zone)
        val e = Instant.ofEpochMilli(ev.end).atZone(zone)
        val startMin = if (s.toLocalDate().isBefore(date)) 0 else s.hour * 60 + s.minute
        val endMin = if (e.toLocalDate().isAfter(date)) 1440 else e.hour * 60 + e.minute
        Placed(ev, startMin, maxOf(endMin, startMin + 25))
    }.sortedWith(compareBy<Placed> { it.startMin }.thenByDescending { it.endMin })

    // Group overlapping events, and give each its own lane within the group.
    var group = mutableListOf<Placed>()
    var groupEnd = -1
    val laneEnds = mutableListOf<Int>()
    fun closeGroup() {
        group.forEach { it.lanes = laneEnds.size.coerceAtLeast(1) }
        group = mutableListOf()
        laneEnds.clear()
    }
    for (p in placed) {
        if (p.startMin >= groupEnd) {
            closeGroup()
            groupEnd = -1
        }
        val free = laneEnds.indexOfFirst { it <= p.startMin }
        if (free >= 0) {
            p.lane = free
            laneEnds[free] = p.endMin
        } else {
            p.lane = laneEnds.size
            laneEnds += p.endMin
        }
        group += p
        groupEnd = maxOf(groupEnd, p.endMin)
    }
    closeGroup()
    return placed
}

@Composable
fun WeekScreen(
    state: AppState,
    weekOffset: Int,
    onWeekOffsetChange: (Int) -> Unit,
    onSwitchView: (ViewMode) -> Unit,
    onOpenSettings: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onOpenEvent: (EventItem, LocalDate) -> Unit,
    onNewEvent: (LocalDate, LocalTime?) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val thisWeek = remember { weekStart(today) }
    val pagerState = rememberPagerState(initialPage = CENTER + weekOffset) { COUNT }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { onWeekOffsetChange(pagerState.currentPage - CENTER) }

    val shownStart = thisWeek.plusWeeks((pagerState.currentPage - CENTER).toLong())
    val look = state.viewLook(state.photoOnWeek)

    Box(Modifier.fillMaxSize().background(if (look.photo) Color.Transparent else Color.White)) {
        if (look.photo) {
            PhotoBackground(state, state.photoFor(YearMonth.from(shownStart.plusDays(3)), today), Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewMenuButton(ViewMode.WEEK, look.iconTint, onSwitchView)
                Text(weekTitle(shownStart), style = look.text.sized(22), modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(CENTER) } }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Go to this week", tint = look.iconTint)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = look.iconTint)
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                WeekPage(
                    state = state,
                    start = thisWeek.plusWeeks((page - CENTER).toLong()),
                    today = today,
                    look = look,
                    onDayClick = onDayClick,
                    onOpenEvent = onOpenEvent,
                    onNewEvent = onNewEvent,
                )
            }
        }
        FloatingActionButton(
            onClick = { onNewEvent(if (shownStart == thisWeek) today else shownStart, null) },
            containerColor = TodayBlue,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "New event") }
    }
}

@Composable
private fun WeekPage(
    state: AppState,
    start: LocalDate,
    today: LocalDate,
    look: ViewLook,
    onDayClick: (LocalDate) -> Unit,
    onOpenEvent: (EventItem, LocalDate) -> Unit,
    onNewEvent: (LocalDate, LocalTime?) -> Unit,
) {
    val days = (0..6).map { start.plusDays(it.toLong()) }
    val events by produceState(emptyList<EventItem>(), start, state.calendar.version, state.calendar.hasPermission) {
        value = withContext(Dispatchers.IO) { state.calendar.eventsBetween(days.first(), days.last()) }
    }
    val density = LocalDensity.current
    val scroll = rememberScrollState(initial = with(density) { (HourHeight * 7).roundToPx() })

    Column(Modifier.fillMaxSize()) {
        // Day headers: tap one to open that day's schedule.
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(GutterWidth))
            days.forEach { day ->
                Column(
                    Modifier.weight(1f).clickable { onDayClick(day) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).take(3),
                        style = look.text.sized(12).copy(color = if (day == today) TodayBlue else look.subtle),
                    )
                    if (day == today) {
                        Box(Modifier.size(30.dp).background(TodayBlue, CircleShape), contentAlignment = Alignment.Center) {
                            Text(day.dayOfMonth.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                            Text(day.dayOfMonth.toString(), style = look.text.sized(15))
                        }
                    }
                }
            }
        }

        // All-day events
        val allDay = days.map { d -> events.filter { it.allDay && it.isOn(d) } }
        if (allDay.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(bottom = 4.dp)) {
                Box(Modifier.width(GutterWidth))
                allDay.forEachIndexed { i, list ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                        list.take(2).forEach { ev ->
                            Text(
                                ev.title,
                                color = Color.White,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp)
                                    .background(eventColor(ev.color), RoundedCornerShape(4.dp))
                                    .clickable { onOpenEvent(ev, days[i]) }
                                    .padding(horizontal = 3.dp, vertical = 1.dp),
                            )
                        }
                        if (list.size > 2) {
                            Text(
                                "+${list.size - 2}",
                                style = look.text.sized(10),
                                modifier = Modifier.clickable { onDayClick(days[i]) },
                            )
                        }
                    }
                }
            }
        }

        // Hour grid
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
        ) {
            Column(Modifier.width(GutterWidth)) {
                for (h in 0 until 24) {
                    Box(Modifier.height(HourHeight).fillMaxWidth()) {
                        if (h > 0) {
                            Text(
                                hourLabel(h),
                                style = look.text.sized(10).copy(color = look.subtle, fontWeight = FontWeight.Normal),
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth().offset(y = (-7).dp).padding(end = 6.dp),
                            )
                        }
                    }
                }
            }
            days.forEach { day ->
                DayColumn(
                    day = day,
                    isToday = day == today,
                    placed = layoutDay(events, day),
                    look = look,
                    onOpenEvent = { onOpenEvent(it, day) },
                    onTapHour = { hour -> onNewEvent(day, LocalTime.of(hour, 0)) },
                    modifier = Modifier.weight(1f).height(HourHeight * 24),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    isToday: Boolean,
    placed: List<Placed>,
    look: ViewLook,
    onOpenEvent: (EventItem) -> Unit,
    onTapHour: (Int) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val hourPx = with(density) { HourHeight.toPx() }
    BoxWithConstraints(
        modifier
            .drawBehind {
                for (h in 0 until 24) {
                    drawLine(look.line, Offset(0f, h * hourPx), Offset(size.width, h * hourPx), strokeWidth = 1f)
                }
                drawLine(look.line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            }
            .pointerInput(day) {
                detectTapGestures { pos -> onTapHour((pos.y / hourPx).toInt().coerceIn(0, 23)) }
            }
    ) {
        val colWidth = maxWidth
        placed.forEach { p ->
            val laneWidth = colWidth / p.lanes
            Box(
                Modifier
                    .offset(x = laneWidth * p.lane, y = HourHeight * (p.startMin / 60f))
                    .width(laneWidth)
                    .height(HourHeight * ((p.endMin - p.startMin) / 60f))
                    .padding(1.dp)
                    .background(eventColor(p.ev.color), RoundedCornerShape(4.dp))
                    .clickable { onOpenEvent(p.ev) }
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text(p.ev.title, color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, overflow = TextOverflow.Clip)
            }
        }
        if (isToday) {
            val now = LocalTime.now()
            val y = HourHeight * ((now.hour * 60 + now.minute) / 60f)
            Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(NowRed))
            Box(Modifier.offset(x = (-4).dp, y = y - 5.dp).size(10.dp).background(NowRed, CircleShape))
        }
        Box(Modifier.fillMaxHeight())
    }
}
