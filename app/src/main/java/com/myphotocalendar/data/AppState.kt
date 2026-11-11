package com.myphotocalendar.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

enum class ShuffleFrequency(val label: String) {
    EACH_OPEN("Each open"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
}

/** All of the app's settings and photos, saved on the phone. */
class AppState(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val photoStore = PhotoStore(context)
    val calendar = CalendarRepo(context)

    /** Changes every time the app is opened; used by "Each open" shuffle. */
    private val sessionSeed = Random.nextLong()

    var fontColor by mutableStateOf(Color(prefs.getInt("fontColor", Color.White.toArgb())))
        private set
    var bold by mutableStateOf(prefs.getBoolean("bold", true))
        private set
    var textShadow by mutableStateOf(prefs.getBoolean("textShadow", true))
        private set
    var dim by mutableFloatStateOf(prefs.getFloat("dim", 0.35f))
        private set
    var shuffleFrequency by mutableStateOf(
        ShuffleFrequency.entries.firstOrNull { it.name == prefs.getString("shuffle", null) }
            ?: ShuffleFrequency.DAILY
    )
        private set

    /** The view the app opens in: the last one you used. */
    val startView: String = prefs.getString("view", "MONTH") ?: "MONTH"

    fun updateStartView(name: String) {
        prefs.edit().putString("view", name).apply()
    }

    /** Whether the week, day and schedule views show the month's photo. Off by default. */
    var photoOnWeek by mutableStateOf(prefs.getBoolean("photoOnWeek", false))
        private set
    var photoOnDay by mutableStateOf(prefs.getBoolean("photoOnDay", false))
        private set
    var photoOnSchedule by mutableStateOf(prefs.getBoolean("photoOnSchedule", false))
        private set

    fun updatePhotoOnWeek(on: Boolean) {
        photoOnWeek = on
        prefs.edit().putBoolean("photoOnWeek", on).apply()
    }

    fun updatePhotoOnDay(on: Boolean) {
        photoOnDay = on
        prefs.edit().putBoolean("photoOnDay", on).apply()
    }

    fun updatePhotoOnSchedule(on: Boolean) {
        photoOnSchedule = on
        prefs.edit().putBoolean("photoOnSchedule", on).apply()
    }

    /** Every photo you've added, oldest first. */
    val photos = mutableStateListOf<File>().apply { addAll(photoStore.list()) }

    /** Month number (1-12) -> file name of the photo pinned to that month, every year. */
    val pins = mutableStateMapOf<Int, String>().apply {
        for (m in 1..12) prefs.getString("pin_$m", null)?.let { put(m, it) }
    }

    /** Font for text over photos (an id from FontChoices) and text size (1.0 = normal). */
    var fontId by mutableStateOf(prefs.getString("font", "system") ?: "system")
        private set
    var textScale by mutableFloatStateOf(prefs.getFloat("textScale", 1.0f))
        private set

    fun updateFont(id: String) {
        fontId = id
        prefs.edit().putString("font", id).apply()
    }

    fun updateTextScale(value: Float) {
        textScale = value
        prefs.edit().putFloat("textScale", value).apply()
    }

    /** Applies a whole style template at once. */
    fun applyStyle(font: String, color: Color, scale: Float, bold: Boolean, shadow: Boolean) {
        updateFont(font)
        updateFontColor(color)
        updateTextScale(scale)
        updateBold(bold)
        updateTextShadow(shadow)
    }

    fun updateFontColor(color: Color) {
        fontColor = color
        prefs.edit().putInt("fontColor", color.toArgb()).apply()
    }

    fun updateBold(on: Boolean) {
        bold = on
        prefs.edit().putBoolean("bold", on).apply()
    }

    fun updateTextShadow(on: Boolean) {
        textShadow = on
        prefs.edit().putBoolean("textShadow", on).apply()
    }

    fun updateDim(value: Float) {
        dim = value
        prefs.edit().putFloat("dim", value).apply()
    }

    fun updateShuffleFrequency(value: ShuffleFrequency) {
        shuffleFrequency = value
        prefs.edit().putString("shuffle", value.name).apply()
    }

    fun pin(month: Int, file: File) {
        pins[month] = file.name
        prefs.edit().putString("pin_$month", file.name).apply()
    }

    fun unpin(month: Int) {
        pins.remove(month)
        prefs.edit().remove("pin_$month").apply()
    }

    fun addPhotos(files: List<File>) {
        val all = (photos + files).sortedBy { it.name }
        photos.clear()
        photos.addAll(all)
    }

    fun removePhoto(file: File) {
        photos.remove(file)
        pins.filterValues { it == file.name }.keys.toList().forEach { unpin(it) }
        photoStore.delete(file)
    }

    fun isPinned(month: Int): Boolean = pins.containsKey(month)

    /** The background for a month: its pinned photo, or a shuffled one from all your photos. */
    fun photoFor(month: YearMonth, today: LocalDate): File? {
        pins[month.monthValue]?.let { name ->
            photos.firstOrNull { it.name == name }?.let { return it }
        }
        if (photos.isEmpty()) return null
        val period = when (shuffleFrequency) {
            ShuffleFrequency.EACH_OPEN -> sessionSeed
            ShuffleFrequency.DAILY -> today.toEpochDay()
            ShuffleFrequency.WEEKLY -> Math.floorDiv(today.toEpochDay() + 4, 7L)
            ShuffleFrequency.MONTHLY -> 0L
        }
        val seed = period * 1_000_003L + month.year * 12L + month.monthValue
        return photos[Random(seed).nextInt(photos.size)]
    }
}
