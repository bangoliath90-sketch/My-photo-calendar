package com.myphotocalendar.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.myphotocalendar.MainActivity
import com.myphotocalendar.R
import com.myphotocalendar.widget.CalendarWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Receives two messages from the phone's calendar:
 *  - EVENT_REMINDER: an event's reminder time has come, so show a notification.
 *  - PROVIDER_CHANGED: events were added, changed or synced, so refresh the widget.
 */
class CalendarEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    CalendarContract.ACTION_EVENT_REMINDER -> {
                        showReminders(app, intent)
                        CalendarWidget().updateAll(app)
                    }
                    Intent.ACTION_PROVIDER_CHANGED -> CalendarWidget().updateAll(app)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun showReminders(context: Context, intent: Intent) {
        if (!Reminders.enabled(context)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val alarmTime = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val hidden = context.getSharedPreferences("calendars", Context.MODE_PRIVATE)
            .getStringSet("hidden", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

        val projection = arrayOf(
            CalendarAlerts.EVENT_ID,
            CalendarAlerts.TITLE,
            CalendarAlerts.BEGIN,
            CalendarAlerts.END,
            CalendarAlerts.ALL_DAY,
            CalendarAlerts.EVENT_LOCATION,
            CalendarAlerts.CALENDAR_ID,
        )
        Reminders.ensureChannel(context)
        context.contentResolver.query(
            CalendarAlerts.CONTENT_URI,
            projection,
            "${CalendarAlerts.ALARM_TIME} = ?",
            arrayOf(alarmTime.toString()),
            "${CalendarAlerts.BEGIN} ASC",
        )?.use { c ->
            val seen = mutableSetOf<String>()
            while (c.moveToNext()) {
                val eventId = c.getLong(0)
                val begin = c.getLong(2)
                if (!seen.add("$eventId-$begin")) continue
                if (c.getLong(6) in hidden) continue
                Reminders.notify(
                    context = context,
                    eventId = eventId,
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                    begin = begin,
                    end = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    location = c.getString(5) ?: "",
                )
            }
        }
    }
}

object Reminders {
    private const val CHANNEL = "event_reminders"
    const val EXTRA_OPEN_DATE = "openDate"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("notify", true)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            val channel = NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders before your calendar events"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun notify(
        context: Context,
        eventId: Long,
        title: String,
        begin: Long,
        end: Long,
        allDay: Boolean,
        location: String,
    ) {
        val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(begin).atZone(zone)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val whenText = if (allDay) {
            "All day · " + start.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        } else {
            val e = Instant.ofEpochMilli(end).atZone(zone)
            start.format(DateTimeFormatter.ofPattern("EEE, MMM d · ")) + start.format(time) + " – " + e.format(time)
        }
        val text = if (location.isNotBlank()) "$whenText · $location" else whenText

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_DATE, start.toLocalDate().toEpochDay())
        }
        val id = (eventId * 31 + begin / 60000).hashCode()
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(begin)
            .setShowWhen(false)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
