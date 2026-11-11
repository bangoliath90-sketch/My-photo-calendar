package com.myphotocalendar.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarInfo(
    val id: Long,
    val name: String,
    val color: Int,
    val account: String,
    val isPrimary: Boolean,
    val canEdit: Boolean = true,
)

/** One occurrence of an event, as shown on the calendar. */
data class EventItem(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String,
    val recurring: Boolean,
    val startDate: LocalDate,
    val lastDate: LocalDate,
) {
    fun isOn(date: LocalDate) = !date.isBefore(startDate) && !date.isAfter(lastDate)
}

/** An event being created or edited. */
data class EventDraft(
    val id: Long? = null,
    val title: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val calendarId: Long? = null,
    val location: String = "",
    val description: String = "",
    /** Minutes before the event, or null for no reminder. */
    val reminderMinutes: Int? = 30,
    val recurring: Boolean = false,
)

/**
 * Reads and writes events in the phone's calendar, which Android keeps in sync
 * with your Google account. Anything saved here shows up in Google Calendar.
 */
class CalendarRepo(private val context: Context) {

    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("calendars", Context.MODE_PRIVATE)

    /** Calendars you've switched off in this app. Doesn't affect other calendar apps. */
    var hiddenCalendars by mutableStateOf(
        prefs.getStringSet("hidden", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    )
        private set

    fun setCalendarShown(id: Long, shown: Boolean) {
        hiddenCalendars = if (shown) hiddenCalendars - id else hiddenCalendars + id
        prefs.edit().putStringSet("hidden", hiddenCalendars.map { it.toString() }.toSet()).apply()
        version++
    }

    var hasPermission by mutableStateOf(checkPermission())
        private set

    /** Goes up whenever calendar data changes, so screens know to reload. */
    var version by mutableIntStateOf(0)
        private set

    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            version++
        }
    }

    private fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun refreshPermission() {
        hasPermission = checkPermission()
        if (hasPermission && !observing) {
            resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        }
        version++
    }

    /** Calendars you can add events to. */
    fun writableCalendars(): List<CalendarInfo> {
        if (!hasPermission) return emptyList()
        val list = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.CALENDAR_COLOR,
            Calendars.ACCOUNT_NAME,
            Calendars.IS_PRIMARY,
        )
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${Calendars.VISIBLE} = 1"
        try {
            resolver.query(Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
                while (c.moveToNext()) {
                    list += CalendarInfo(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Calendar",
                        color = c.getInt(2),
                        account = c.getString(3) ?: "",
                        isPrimary = c.getInt(4) == 1,
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        // Your main Google calendar first.
        return list.sortedWith(compareByDescending<CalendarInfo> { it.isPrimary }.thenBy { it.name.lowercase() })
    }

    /** Every calendar synced to this phone, including ones you can only read (like holidays). */
    fun allCalendars(): List<CalendarInfo> {
        if (!hasPermission) return emptyList()
        val list = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.CALENDAR_COLOR,
            Calendars.ACCOUNT_NAME,
            Calendars.IS_PRIMARY,
            Calendars.CALENDAR_ACCESS_LEVEL,
        )
        try {
            resolver.query(Calendars.CONTENT_URI, projection, "${Calendars.VISIBLE} = 1", null, null)?.use { c ->
                while (c.moveToNext()) {
                    list += CalendarInfo(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Calendar",
                        color = c.getInt(2),
                        account = c.getString(3) ?: "",
                        isPrimary = c.getInt(4) == 1,
                        canEdit = c.getInt(5) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return list.sortedWith(
            compareBy<CalendarInfo> { it.account.lowercase() }
                .thenByDescending { it.isPrimary }
                .thenBy { it.name.lowercase() }
        )
    }

    /** Events that happen on any day from [first] to [last], inclusive. */
    fun eventsBetween(first: LocalDate, last: LocalDate): List<EventItem> {
        if (!hasPermission) return emptyList()
        val zone = ZoneId.systemDefault()
        // Widened by a day on each side to catch all-day events, which are stored in UTC.
        val begin = first.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = last.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.DISPLAY_COLOR,
            Instances.EVENT_LOCATION,
            Instances.RRULE,
            Instances.CALENDAR_ID,
        )
        val hidden = hiddenCalendars
        val items = mutableListOf<EventItem>()
        try {
            resolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val b = c.getLong(2)
                    val e = c.getLong(3)
                    val allDay = c.getInt(4) == 1
                    val startDate: LocalDate
                    val lastDate: LocalDate
                    if (allDay) {
                        startDate = Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate()
                        lastDate = maxOf(startDate, Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1))
                    } else {
                        startDate = Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
                        lastDate = maxOf(startDate, Instant.ofEpochMilli(maxOf(b, e - 1)).atZone(zone).toLocalDate())
                    }
                    if (lastDate.isBefore(first) || startDate.isAfter(last)) continue
                    val calendarId = c.getLong(8)
                    if (calendarId in hidden) continue
                    items += EventItem(
                        eventId = c.getLong(0),
                        calendarId = calendarId,
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        begin = b,
                        end = e,
                        allDay = allDay,
                        color = c.getInt(5),
                        location = c.getString(6) ?: "",
                        recurring = !c.getString(7).isNullOrBlank(),
                        startDate = startDate,
                        lastDate = lastDate,
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        // All-day events first on each day, then by start time.
        return items.sortedWith(compareBy<EventItem> { !it.allDay }.thenBy { it.begin })
    }

    /** Loads an event for editing, or null if it no longer exists. */
    fun loadDraft(eventId: Long): EventDraft? {
        if (!hasPermission) return null
        val zone = ZoneId.systemDefault()
        val projection = arrayOf(
            Events.TITLE,
            Events.DTSTART,
            Events.DTEND,
            Events.ALL_DAY,
            Events.CALENDAR_ID,
            Events.EVENT_LOCATION,
            Events.DESCRIPTION,
            Events.RRULE,
        )
        val draft = try {
            resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val allDay = c.getInt(3) == 1
                val start = c.getLong(1)
                val end = if (c.isNull(2)) start + 3_600_000L else c.getLong(2)
                val tz = if (allDay) ZoneOffset.UTC else zone
                val s = Instant.ofEpochMilli(start).atZone(tz).toLocalDateTime()
                var e = Instant.ofEpochMilli(end).atZone(tz).toLocalDateTime()
                if (allDay) e = e.minusDays(1)
                EventDraft(
                    id = eventId,
                    title = c.getString(0) ?: "",
                    allDay = allDay,
                    startDate = s.toLocalDate(),
                    startTime = s.toLocalTime(),
                    endDate = maxOf(s.toLocalDate(), e.toLocalDate()),
                    endTime = e.toLocalTime(),
                    calendarId = c.getLong(4),
                    location = c.getString(5) ?: "",
                    description = c.getString(6) ?: "",
                    reminderMinutes = null,
                    recurring = !c.getString(7).isNullOrBlank(),
                )
            }
        } catch (e: SecurityException) {
            null
        } ?: return null

        var minutes: Int? = null
        try {
            resolver.query(
                Reminders.CONTENT_URI,
                arrayOf(Reminders.MINUTES),
                "${Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                "${Reminders.MINUTES} ASC",
            )?.use { c -> if (c.moveToFirst()) minutes = c.getInt(0) }
        } catch (_: SecurityException) {
        }
        return draft.copy(reminderMinutes = minutes)
    }

    /** Saves a new or changed event. Returns false if it couldn't be saved. */
    fun save(draft: EventDraft): Boolean {
        if (!hasPermission) return false
        val zone = ZoneId.systemDefault()
        val values = ContentValues().apply {
            put(Events.TITLE, draft.title.trim())
            put(Events.EVENT_LOCATION, draft.location.trim())
            put(Events.DESCRIPTION, draft.description.trim())
            if (draft.allDay) {
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.DTSTART, draft.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(Events.DTEND, draft.endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            } else {
                put(Events.ALL_DAY, 0)
                put(Events.EVENT_TIMEZONE, zone.id)
                put(Events.DTSTART, LocalDateTime.of(draft.startDate, draft.startTime).atZone(zone).toInstant().toEpochMilli())
                put(Events.DTEND, LocalDateTime.of(draft.endDate, draft.endTime).atZone(zone).toInstant().toEpochMilli())
            }
        }
        return try {
            val eventId: Long
            if (draft.id == null) {
                val calendarId = draft.calendarId ?: return false
                values.put(Events.CALENDAR_ID, calendarId)
                val uri = resolver.insert(Events.CONTENT_URI, values) ?: return false
                eventId = ContentUris.parseId(uri)
            } else {
                eventId = draft.id
                resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null)
                resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
            }
            draft.reminderMinutes?.let { minutes ->
                resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                    put(Reminders.EVENT_ID, eventId)
                    put(Reminders.MINUTES, minutes)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                })
            }
            version++
            true
        } catch (e: Exception) {
            false
        }
    }

    fun delete(eventId: Long): Boolean = try {
        val rows = resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
        version++
        rows > 0
    } catch (e: Exception) {
        false
    }
}
