package com.myphotocalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.myphotocalendar.data.AppState
import com.myphotocalendar.ui.MonthScreen
import com.myphotocalendar.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appState = AppState(applicationContext)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1A73E8))) {
                // How many months away from the current month the calendar is showing.
                var monthOffset by rememberSaveable { mutableIntStateOf(0) }
                var showSettings by rememberSaveable { mutableStateOf(false) }

                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        state = appState,
                        monthOffset = monthOffset,
                        onBack = { showSettings = false },
                    )
                } else {
                    MonthScreen(
                        state = appState,
                        monthOffset = monthOffset,
                        onMonthOffsetChange = { monthOffset = it },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
