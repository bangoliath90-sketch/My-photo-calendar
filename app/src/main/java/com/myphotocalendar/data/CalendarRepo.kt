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
    /** How it repeats (an RRULE without its end), or null if it doesn't. */
    val rule: String? = null,
    /** Last day it repeats, or null for no end date. */
    val repeatUntil: LocalDate? = null,
    /** Number of times it repeats, kept from rules made in Google Calendar. */
    val repeatCount: Int? = null,
    /** Set when editing an existing repeating event. */
    val series: SeriesInfo? = null,
)

/** Where an existing repeating event came from, needed to change part of the series. */
data class SeriesInfo(
    val rule: String,
    val allDay: Boolean,
    val firstDate: LocalDate,
    val instanceDate: LocalDate,
    val instanceBegin: Long,
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

    /**
     * Loads an event for editing, or null if it no longer exists. For a repeating
     * event, [instanceBegin] says which occurrence was tapped.
     */
    fun loadDraft(eventId: Long, instanceBegin: Long?): EventDraft? {
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
            Events.DURATION,
        )
        val draft = try {
            resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val allDay = c.getInt(3) == 1
                val rrule = c.getString(7)?.takeIf { it.isNotBlank() }
                val seriesStart = c.getLong(1)
                val tz = if (allDay) ZoneOffset.UTC else zone
                val start: Long
                val end: Long
                if (rrule != null) {
                    val length = (parseDurationSeconds(c.getString(8)) ?: if (allDay) 86_400L else 3_600L) * 1000
                    start = instanceBegin ?: seriesStart
                    end = start + length
                } else {
                    start = seriesStart
                    end = if (c.isNull(2)) start + 3_600_000L else c.getLong(2)
                }
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
                    rule = rrule?.let { baseRule(it) },
                    repeatUntil = rrule?.let { untilDate(it) },
                    repeatCount = rrule?.let { countOf(it) },
                    series = rrule?.let {
                        SeriesInfo(
                            rule = it,
                            allDay = allDay,
                            firstDate = millisToDate(seriesStart, allDay),
                            instanceDate = s.toLocalDate(),
                            instanceBegin = start,
                        )
                    },
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

    private fun startMillis(d: EventDraft, date: LocalDate = d.startDate): Long =
        if (d.allDay) date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else LocalDateTime.of(date, d.startTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun endMillis(d: EventDraft): Long =
        if (d.allDay) d.endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else LocalDateTime.of(d.endDate, d.endTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Title, place, notes and time zone: the parts every save writes. */
    private fun baseValues(d: EventDraft) = ContentValues().apply {
        put(Events.TITLE, d.title.trim())
        put(Events.EVENT_LOCATION, d.location.trim())
        put(Events.DESCRIPTION, d.description.trim())
        put(Events.ALL_DAY, if (d.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (d.allDay) "UTC" else ZoneId.systemDefault().id)
    }

    /** Adds the start, and either an end (one-off) or a length and rule (repeating). */
    private fun ContentValues.putTiming(d: EventDraft, start: Long) {
        val rule = d.rule
        val length = endMillis(d) - startMillis(d)
        put(Events.DTSTART, start)
        if (rule != null) {
            putNull(Events.DTEND)
            put(Events.DURATION, if (d.allDay) "P${maxOf(1L, length / 86_400_000L)}D" else "P${maxOf(0L, length / 1000)}S")
            put(Events.RRULE, fullRule(rule, d.repeatUntil, d.repeatCount, d.allDay))
        } else {
            put(Events.DTEND, start + length)
            putNull(Events.DURATION)
            putNull(Events.RRULE)
        }
    }

    private fun setReminder(eventId: Long, minutes: Int?) {
        resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        if (minutes != null) {
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, minutes)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
    }

    private fun insertNew(d: EventDraft, calendarId: Long): Boolean {
        val values = baseValues(d).apply {
            put(Events.CALENDAR_ID, calendarId)
            putTiming(d, startMillis(d))
        }
        val uri = resolver.insert(Events.CONTENT_URI, values) ?: return false
        setReminder(ContentUris.parseId(uri), d.reminderMinutes)
        return true
    }

    /** Ends a series on the day before [instanceDate]. */
    private fun endSeriesBefore(eventId: Long, series: SeriesInfo) {
        val rule = fullRule(baseRule(series.rule), series.instanceDate.minusDays(1), null, series.allDay)
        resolver.update(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            ContentValues().apply { put(Events.RRULE, rule) },
            null,
            null,
        )
    }

    /** Saves a new or changed event. Returns false if it couldn't be saved. */
    fun save(draft: EventDraft, scope: EditScope = EditScope.ALL): Boolean {
        if (!hasPermission) return false
        return try {
            val id = draft.id
            val series = draft.series
            when {
                id == null -> insertNew(draft, draft.calendarId ?: return false)

                series == null -> {
                    val values = baseValues(draft).apply { putTiming(draft, startMillis(draft)) }
                    resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values, null, null)
                    setReminder(id, draft.reminderMinutes)
                    true
                }

                scope == EditScope.THIS -> {
                    // A one-off change to a single occurrence.
                    val values = baseValues(draft).apply {
                        put(Events.ORIGINAL_INSTANCE_TIME, series.instanceBegin)
                        put(Events.ORIGINAL_ALL_DAY, if (series.allDay) 1 else 0)
                        put(Events.DTSTART, startMillis(draft))
                        put(Events.DTEND, endMillis(draft))
                        put(Events.STATUS, Events.STATUS_CONFIRMED)
                    }
                    val uri = resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, id), values)
                        ?: return false
                    val newId = ContentUris.parseId(uri)
                    if (newId > 0) setReminder(newId, draft.reminderMinutes)
                    true
                }

                scope == EditScope.FOLLOWING && series.instanceDate.isAfter(series.firstDate) -> {
                    endSeriesBefore(id, series)
                    insertNew(draft.copy(id = null, series = null), draft.calendarId ?: return false)
                }

                else -> {
                    // The whole series. Move its first date by however far this occurrence moved.
                    val shift = java.time.temporal.ChronoUnit.DAYS.between(series.instanceDate, draft.startDate)
                    val firstDate = series.firstDate.plusDays(shift)
                    val start = if (draft.rule == null) startMillis(draft) else startMillis(draft, firstDate)
                    val values = baseValues(draft).apply { putTiming(draft, start) }
                    resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values, null, null)
                    setReminder(id, draft.reminderMinutes)
                    true
                }
            }.also { version++ }
        } catch (e: Exception) {
            false
        }
    }

    /** Deletes an event, or part of a repeating one. */
    fun delete(draft: EventDraft, scope: EditScope = EditScope.ALL): Boolean {
        val id = draft.id ?: return false
        val series = draft.series
        return try {
            val ok = when {
                series == null || scope == EditScope.ALL ||
                    (scope == EditScope.FOLLOWING && !series.instanceDate.isAfter(series.firstDate)) ->
                    resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) > 0

                scope == EditScope.THIS -> {
                    val values = ContentValues().apply {
                        put(Events.ORIGINAL_INSTANCE_TIME, series.instanceBegin)
                        put(Events.STATUS, Events.STATUS_CANCELED)
                    }
                    resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, id), values) != null
                }

                else -> {
                    endSeriesBefore(id, series)
                    true
                }
            }
            version++
            ok
        } catch (e: Exception) {
            false
        }
    }
}
