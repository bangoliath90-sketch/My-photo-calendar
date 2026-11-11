package com.myphotocalendar

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.myphotocalendar.data.AppState
import com.myphotocalendar.ui.DayScreen
import com.myphotocalendar.ui.EventEditScreen
import com.myphotocalendar.ui.MonthScreen
import com.myphotocalendar.ui.ScheduleScreen
import com.myphotocalendar.ui.SettingsScreen
import com.myphotocalendar.ui.ViewMode
import com.myphotocalendar.ui.WeekScreen
import java.time.LocalDate
import java.time.LocalTime

/** The pages of the app. The month view is always at the bottom of the stack. */
sealed interface Screen {
    data object Settings : Screen
    data class Day(val date: LocalDate) : Screen
    data class EditEvent(val eventId: Long?, val date: LocalDate, val time: LocalTime? = null) : Screen
}

class MainActivity : ComponentActivity() {

    private lateinit var appState: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appState = AppState(applicationContext)
        appState.calendar.refreshPermission()

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1A73E8))) {
                var monthOffset by rememberSaveable { mutableIntStateOf(0) }
                var weekOffset by rememberSaveable { mutableIntStateOf(0) }
                var viewName by rememberSaveable { mutableStateOf(appState.startView) }
                val viewMode = ViewMode.entries.firstOrNull { it.name == viewName } ?: ViewMode.MONTH
                fun switchView(mode: ViewMode) {
                    viewName = mode.name
                    appState.updateStartView(mode.name)
                }
                val stack = remember { mutableStateListOf<Screen>() }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { appState.calendar.refreshPermission() }

                fun requestCalendar() {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    )
                }

                // Ask once when the app opens, if access hasn't been given yet.
                LaunchedEffect(Unit) {
                    if (!appState.calendar.hasPermission) requestCalendar()
                }

                fun back() {
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                }

                if (stack.isNotEmpty()) BackHandler { back() }

                when (val top = stack.lastOrNull()) {
                    null -> when (viewMode) {
                        ViewMode.MONTH -> MonthScreen(
                            state = appState,
                            monthOffset = monthOffset,
                            onMonthOffsetChange = { monthOffset = it },
                            onOpenSettings = { stack.add(Screen.Settings) },
                            onDayClick = { stack.add(Screen.Day(it)) },
                            onAddEvent = { stack.add(Screen.EditEvent(null, LocalDate.now())) },
                            onRequestCalendarAccess = { requestCalendar() },
                            onSwitchView = { switchView(it) },
                        )
                        ViewMode.WEEK -> WeekScreen(
                            state = appState,
                            weekOffset = weekOffset,
                            onWeekOffsetChange = { weekOffset = it },
                            onSwitchView = { switchView(it) },
                            onOpenSettings = { stack.add(Screen.Settings) },
                            onDayClick = { stack.add(Screen.Day(it)) },
                            onOpenEvent = { id, day -> stack.add(Screen.EditEvent(id, day)) },
                            onNewEvent = { day, time -> stack.add(Screen.EditEvent(null, day, time)) },
                        )
                        ViewMode.SCHEDULE -> ScheduleScreen(
                            state = appState,
                            onSwitchView = { switchView(it) },
                            onOpenSettings = { stack.add(Screen.Settings) },
                            onDayClick = { stack.add(Screen.Day(it)) },
                            onOpenEvent = { id, day -> stack.add(Screen.EditEvent(id, day)) },
                            onNewEvent = { stack.add(Screen.EditEvent(null, it)) },
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        state = appState,
                        monthOffset = monthOffset,
                        onBack = { back() },
                    )
                    is Screen.Day -> DayScreen(
                        state = appState,
                        date = top.date,
                        onDateChange = { stack[stack.lastIndex] = Screen.Day(it) },
                        onBack = { back() },
                        onAddEvent = { stack.add(Screen.EditEvent(null, it)) },
                        onOpenEvent = { stack.add(Screen.EditEvent(it, top.date)) },
                    )
                    is Screen.EditEvent -> key(top) {
                        EventEditScreen(
                            state = appState,
                            eventId = top.eventId,
                            date = top.date,
                            startTime = top.time,
                            onDone = { back() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up access granted from the phone's Settings app, and any synced changes.
        if (::appState.isInitialized) appState.calendar.refreshPermission()
    }
}
