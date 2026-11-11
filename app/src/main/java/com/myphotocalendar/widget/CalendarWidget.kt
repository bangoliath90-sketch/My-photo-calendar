package com.myphotocalendar.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myphotocalendar.MainActivity
import com.myphotocalendar.data.AppState
import com.myphotocalendar.data.EventItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CalendarWidget()
}

private data class WidgetData(
    val today: LocalDate,
    val photo: Bitmap?,
    val events: List<EventItem>,
    val fontColor: Color,
    val bold: Boolean,
    val serif: Boolean,
    val dim: Float,
    val hasPermission: Boolean,
)

/** The home-screen widget: this month's photo with your next few events. */
class CalendarWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { load(context) }
        provideContent { WidgetContent(data) }
    }

    private fun load(context: Context): WidgetData {
        val state = AppState(context.applicationContext)
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val events = state.calendar.eventsBetween(today, today.plusDays(30))
            .filter { it.allDay && !it.lastDate.isBefore(today) || !it.allDay && it.end > now }
            .sortedBy { if (it.allDay) maxOf(it.startDate, today).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() else it.begin }
            .take(8)
        return WidgetData(
            today = today,
            photo = state.photoFor(YearMonth.from(today), today)?.let { f -> decodeSmall(f)?.let { framed(it, state.cropFor(f)) } },
            events = events,
            fontColor = state.fontColor,
            bold = state.bold,
            serif = state.fontId in setOf("serif", "playfair", "lora", "abril"),
            dim = state.dim,
            hasPermission = state.calendar.hasPermission,
        )
    }

    /** Applies the photo's zoom and center, so the widget shows the part you chose. */
    private fun framed(bmp: Bitmap, crop: com.myphotocalendar.data.PhotoCrop): Bitmap {
        if (crop.zoom <= 1.01f && crop.cx == 0.5f && crop.cy == 0.5f) return bmp
        val w = (bmp.width / crop.zoom).toInt().coerceIn(1, bmp.width)
        val h = (bmp.height / crop.zoom).toInt().coerceIn(1, bmp.height)
        val x = (crop.cx * bmp.width - w / 2f).toInt().coerceIn(0, bmp.width - w)
        val y = (crop.cy * bmp.height - h / 2f).toInt().coerceIn(0, bmp.height - h)
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }

    /** Widgets have a small memory limit, so the photo is shrunk first. */
    private fun decodeSmall(file: File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 900) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun whenLabel(ev: EventItem, today: LocalDate): String {
    val zone = ZoneId.systemDefault()
    val day = if (ev.allDay) maxOf(ev.startDate, today) else Instant.ofEpochMilli(ev.begin).atZone(zone).toLocalDate().let { maxOf(it, today) }
    val dayText = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    return if (ev.allDay) "$dayText · All day"
    else "$dayText · " + Instant.ofEpochMilli(ev.begin).atZone(zone).format(timeFormat)
}

@Composable
private fun WidgetContent(data: WidgetData) {
    val size = LocalSize.current
    val text = ColorProvider(data.fontColor)
    val weight = if (data.bold) FontWeight.Bold else FontWeight.Normal
    val family = if (data.serif) FontFamily.Serif else null
    val darkText = data.fontColor.luminance() < 0.5f
    val dimColor = (if (darkText) Color.White else Color.Black).copy(alpha = maxOf(data.dim, 0.3f))
    val rows = ((size.height.value - 70f) / 40f).toInt().coerceIn(0, 8)

    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(Color(0xFF3A3A44))
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (data.photo != null) {
            Image(
                provider = ImageProvider(data.photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        Box(GlanceModifier.fillMaxSize().background(dimColor)) {}
        Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            Row(GlanceModifier.fillMaxWidth()) {
                Text(
                    data.today.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
                    style = TextStyle(color = text, fontSize = 22.sp, fontWeight = weight, fontFamily = family),
                )
            }
            Text(
                data.today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                style = TextStyle(color = text, fontSize = 13.sp, fontFamily = family),
            )
            Spacer(GlanceModifier.height(8.dp))
            when {
                !data.hasPermission -> Text(
                    "Open the app to allow calendar access.",
                    style = TextStyle(color = text, fontSize = 13.sp),
                )
                data.events.isEmpty() -> Text(
                    "Nothing coming up",
                    style = TextStyle(color = text, fontSize = 14.sp),
                )
                else -> data.events.take(rows).forEach { ev ->
                    Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Box(
                            GlanceModifier
                                .width(4.dp)
                                .height(32.dp)
                                .cornerRadius(2.dp)
                                .background(Color(ev.color or 0xFF000000.toInt()))
                        ) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Column {
                            Text(
                                ev.title,
                                maxLines = 1,
                                style = TextStyle(color = text, fontSize = 14.sp, fontWeight = weight),
                            )
                            Text(
                                whenLabel(ev, data.today),
                                maxLines = 1,
                                style = TextStyle(color = text, fontSize = 12.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Asks the widget to redraw, e.g. after you change photos or settings. */
suspend fun refreshWidgets(context: Context) {
    try {
        CalendarWidget().updateAll(context)
    } catch (_: Exception) {
    }
}
