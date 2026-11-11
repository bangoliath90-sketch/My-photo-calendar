package com.myphotocalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

private const val CENTER_PAGE = 6000
private const val PAGE_COUNT = 12000
private const val MAX_CHIPS = 3
val TodayBlue = Color(0xFF1A73E8)

fun monthLabel(month: YearMonth): String =
    month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()) + " " + month.year

/** Calendar colors come without a guaranteed alpha; make them solid. */
fun eventColor(argb: Int): Color = Color(argb or 0xFF000000.toInt())

@Composable
fun MonthScreen(
    state: AppState,
    monthOffset: Int,
    onMonthOffsetChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onAddEvent: () -> Unit,
    onRequestCalendarAccess: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val thisMonth = remember { YearMonth.from(today) }
    val pagerState = rememberPagerState(initialPage = CENTER_PAGE + monthOffset) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onMonthOffsetChange(pagerState.currentPage - CENTER_PAGE)
    }

    val shown = thisMonth.plusMonths((pagerState.currentPage - CENTER_PAGE).toLong())
    val photo = state.photoFor(shown, today)
    val textStyle = state.overlayTextStyle()

    Box(Modifier.fillMaxSize()) {
        PhotoBackground(state, photo, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = monthLabel(shown),
                    style = textStyle.copy(fontSize = 24.sp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(CENTER_PAGE) }
                }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Go to today", tint = state.fontColor)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Photo and text settings", tint = state.fontColor)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { letter ->
                    Text(
                        text = letter,
                        style = textStyle.copy(fontSize = 13.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                MonthGrid(
                    state = state,
                    month = thisMonth.plusMonths((page - CENTER_PAGE).toLong()),
                    today = today,
                    textStyle = textStyle,
                    lineColor = state.lineColor(),
                    onDayClick = onDayClick,
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 88.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.calendar.hasPermission) {
                Notice {
                    Text(
                        "Allow calendar access to see and add your Google Calendar events.",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    TextButton(onClick = onRequestCalendarAccess) {
                        Text("Allow access", color = Color(0xFF9FD3FF))
                    }
                }
            }
            if (state.photos.isEmpty()) {
                Notice {
                    Text(
                        "Tap the settings button at the top to add your photos.",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddEvent,
            containerColor = Color.White,
            contentColor = TodayBlue,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New event")
        }
    }
}

@Composable
private fun Notice(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) { content() }
}

@Composable
private fun MonthGrid(
    state: AppState,
    month: YearMonth,
    today: LocalDate,
    textStyle: TextStyle,
    lineColor: Color,
    onDayClick: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leadingDays = first.dayOfWeek.value % 7 // Sunday = 0
    val gridStart = first.minusDays(leadingDays.toLong())
    val weekCount = (leadingDays + month.lengthOfMonth() + 6) / 7
    val gridEnd = gridStart.plusDays(weekCount * 7L - 1)

    val events by produceState(
        initialValue = emptyList<EventItem>(),
        month, state.calendar.version, state.calendar.hasPermission,
    ) {
        value = withContext(Dispatchers.IO) { state.calendar.eventsBetween(gridStart, gridEnd) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp).padding(bottom = 8.dp)) {
        for (week in 0 until weekCount) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                    }
            ) {
                for (d in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + d).toLong())
                    DayCell(
                        date = date,
                        inMonth = date.monthValue == month.monthValue,
                        isToday = date == today,
                        events = events.filter { it.isOn(date) },
                        textStyle = textStyle,
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    events: List<EventItem>,
    textStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .clickable(onClickLabel = "Open ${date.dayOfMonth}") { onClick() }
            .padding(top = 4.dp, start = 1.dp, end = 1.dp)
            .alpha(if (inMonth) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (isToday) {
            Box(
                Modifier.size(28.dp).background(TodayBlue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                Text(date.dayOfMonth.toString(), style = textStyle.copy(fontSize = 14.sp))
            }
        }
        events.take(MAX_CHIPS).forEach { ev ->
            Text(
                ev.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(eventColor(ev.color), RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
        if (events.size > MAX_CHIPS) {
            Text("+${events.size - MAX_CHIPS}", style = textStyle.copy(fontSize = 10.sp))
        }
    }
}
