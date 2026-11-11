package com.myphotocalendar.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** When changing or deleting one occurrence of a repeating event. */
enum class EditScope(val label: String) {
    THIS("This event"),
    FOLLOWING("This and following events"),
    ALL("All events"),
}

/** One choice in the Repeat menu. [rule] is an RRULE without its end (UNTIL/COUNT). */
data class RepeatOption(val label: String, val rule: String?)

private val dayCodes = mapOf(
    DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
)

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

private val weekWords = listOf("first", "second", "third", "fourth")

/** The standard repeat choices for an event that starts on [date]. */
fun repeatOptions(date: LocalDate): List<RepeatOption> {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val code = dayCodes.getValue(date.dayOfWeek)
    val nth = (date.dayOfMonth - 1) / 7 + 1
    val isLast = date.plusWeeks(1).monthValue != date.monthValue
    val options = mutableListOf(
        RepeatOption("Does not repeat", null),
        RepeatOption("Every day", "FREQ=DAILY"),
        RepeatOption("Every weekday (Mon–Fri)", "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"),
        RepeatOption("Every week on $dayName", "FREQ=WEEKLY;BYDAY=$code"),
        RepeatOption("Every month on the ${ordinal(date.dayOfMonth)}", "FREQ=MONTHLY;BYMONTHDAY=${date.dayOfMonth}"),
    )
    if (nth <= 4) options += RepeatOption("Every month on the ${weekWords[nth - 1]} $dayName", "FREQ=MONTHLY;BYDAY=$nth$code")
    if (isLast) options += RepeatOption("Every month on the last $dayName", "FREQ=MONTHLY;BYDAY=-1$code")
    options += RepeatOption(
        "Every year on ${date.format(DateTimeFormatter.ofPattern("MMMM d"))}",
        "FREQ=YEARLY",
    )
    return options
}

/** Splits an RRULE into its parts, upper-cased keys. */
fun ruleParts(rule: String): Map<String, String> =
    rule.removePrefix("RRULE:").split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(0, i).uppercase() to part.substring(i + 1)
    }.toMap()

/** The rule without UNTIL, COUNT or WKST, so two ways of writing the same rule compare equal. */
fun baseRule(rule: String): String =
    ruleParts(rule).filterKeys { it != "UNTIL" && it != "COUNT" && it != "WKST" }
        .entries.joinToString(";") { "${it.key}=${it.value}" }

private fun normalized(rule: String, date: LocalDate): Map<String, String> {
    val p = ruleParts(baseRule(rule)).toMutableMap()
    val code = dayCodes.getValue(date.dayOfWeek)
    if (p["FREQ"] == "WEEKLY" && p["BYDAY"] == null) p["BYDAY"] = code
    if (p["FREQ"] == "MONTHLY" && p["BYDAY"] == null && p["BYMONTHDAY"] == null) p["BYMONTHDAY"] = date.dayOfMonth.toString()
    if (p["FREQ"] == "YEARLY") {
        p.remove("BYMONTH")
        p.remove("BYMONTHDAY")
    }
    if (p["INTERVAL"] == "1") p.remove("INTERVAL")
    return p
}

/** The menu choice that matches [rule], or null if it's a custom rule made elsewhere. */
fun matchingOption(rule: String?, date: LocalDate): RepeatOption? {
    if (rule == null) return repeatOptions(date).first()
    val target = normalized(rule, date)
    return repeatOptions(date).firstOrNull { it.rule != null && normalized(it.rule, date) == target }
}

/** Reads the UNTIL date of a rule, if it has one. */
fun untilDate(rule: String): LocalDate? {
    val v = ruleParts(rule)["UNTIL"] ?: return null
    return try {
        if (v.length == 8) {
            LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE)
        } else {
            LocalDateTime.parse(v.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                .atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }
    } catch (e: Exception) {
        null
    }
}

fun countOf(rule: String): Int? = ruleParts(rule)["COUNT"]?.toIntOrNull()

/** An UNTIL value that ends the rule after [lastDay]. */
fun untilValue(lastDay: LocalDate, allDay: Boolean): String =
    if (allDay) {
        lastDay.format(DateTimeFormatter.BASIC_ISO_DATE)
    } else {
        val endOfDay = lastDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusSeconds(1)
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(endOfDay)
    }

/** Builds the full rule: base plus an end date or a count. */
fun fullRule(base: String, until: LocalDate?, count: Int?, allDay: Boolean): String = when {
    until != null -> "$base;UNTIL=${untilValue(until, allDay)}"
    count != null -> "$base;COUNT=$count"
    else -> base
}

/** Reads an RFC 2445 duration like P3600S, PT1H30M or P1D, in seconds. */
fun parseDurationSeconds(duration: String?): Long? {
    if (duration.isNullOrBlank()) return null
    val m = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""").find(duration.trim())
        ?: return null
    fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
    return g(2) * 604800 + g(3) * 86400 + g(4) * 3600 + g(5) * 60 + g(6)
}

fun millisToDate(millis: Long, allDay: Boolean): LocalDate =
    Instant.ofEpochMilli(millis).atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
