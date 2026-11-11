package com.myphotocalendar.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    // ---------------- Text style: shared, or a month's own ----------------

    private var sharedLook by mutableStateOf(
        TextLook(
            fontId = prefs.getString("font", "system") ?: "system",
            color = prefs.getInt("fontColor", Color.White.toArgb()),
            scale = prefs.getFloat("textScale", 1.0f),
            bold = prefs.getBoolean("bold", true),
            shadow = prefs.getBoolean("textShadow", true),
            dim = prefs.getFloat("dim", 0.35f),
        )
    )

    /** Months (1-12) that have their own text style instead of the shared one. */
    val monthLooks = mutableStateMapOf<Int, TextLook>().apply {
        for (m in 1..12) TextLook.decode(prefs.getString("look_$m", null))?.let { put(m, it) }
    }

    /** The month currently on screen; decides which style is used. */
    var displayMonth by mutableIntStateOf(LocalDate.now().monthValue)

    private val look: TextLook get() = monthLooks[displayMonth] ?: sharedLook

    val fontColor: Color get() = Color(look.color)
    val bold: Boolean get() = look.bold
    val textShadow: Boolean get() = look.shadow
    val dim: Float get() = look.dim
    val fontId: String get() = look.fontId
    val textScale: Float get() = look.scale

    fun hasOwnLook(month: Int) = monthLooks.containsKey(month)

    /** Gives [month] its own copy of the current style, to change separately. */
    fun giveOwnLook(month: Int) {
        if (!monthLooks.containsKey(month)) {
            monthLooks[month] = sharedLook
            prefs.edit().putString("look_$month", sharedLook.encode()).apply()
        }
    }

    /** Puts [month] back on the shared style. */
    fun removeOwnLook(month: Int) {
        monthLooks.remove(month)
        prefs.edit().remove("look_$month").apply()
    }

    /** Changes the style for the month on screen if it has its own, otherwise the shared one. */
    private fun changeLook(change: (TextLook) -> TextLook) {
        val m = displayMonth
        val own = monthLooks[m]
        if (own != null) {
            val next = change(own)
            monthLooks[m] = next
            prefs.edit().putString("look_$m", next.encode()).apply()
        } else {
            val next = change(sharedLook)
            sharedLook = next
            prefs.edit()
                .putString("font", next.fontId)
                .putInt("fontColor", next.color)
                .putFloat("textScale", next.scale)
                .putBoolean("bold", next.bold)
                .putBoolean("textShadow", next.shadow)
                .putFloat("dim", next.dim)
                .apply()
        }
    }

    fun updateFont(id: String) = changeLook { it.copy(fontId = id) }
    fun updateTextScale(value: Float) = changeLook { it.copy(scale = value) }
    fun updateFontColor(color: Color) = changeLook { it.copy(color = color.toArgb()) }
    fun updateBold(on: Boolean) = changeLook { it.copy(bold = on) }
    fun updateTextShadow(on: Boolean) = changeLook { it.copy(shadow = on) }
    fun updateDim(value: Float) = changeLook { it.copy(dim = value) }

    /** Applies a whole style template at once. */
    fun applyStyle(font: String, color: Color, scale: Float, bold: Boolean, shadow: Boolean) =
        changeLook { it.copy(fontId = font, color = color.toArgb(), scale = scale, bold = bold, shadow = shadow) }

    // ---------------- Photo position and zoom ----------------

    /** Photo file name -> how it's moved and zoomed. */
    private val crops = mutableStateMapOf<String, PhotoCrop>().apply {
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("crop_") && value is String) {
                PhotoCrop.decode(value)?.let { put(key.removePrefix("crop_"), it) }
            }
        }
    }

    fun cropFor(file: File): PhotoCrop = crops[file.name] ?: PhotoCrop()

    fun updateCrop(file: File, crop: PhotoCrop) {
        crops[file.name] = crop
        prefs.edit().putString("crop_${file.name}", crop.encode()).apply()
    }
    var shuffleFrequency by mutableStateOf(
        ShuffleFrequency.entries.firstOrNull { it.name == prefs.getString("shuffle", null) }
            ?: ShuffleFrequency.DAILY
    )
        private set

    /** Whether this app shows its own reminder notifications. */
    var notify by mutableStateOf(prefs.getBoolean("notify", true))
        private set

    fun updateNotify(on: Boolean) {
        notify = on
        prefs.edit().putBoolean("notify", on).apply()
    }

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
        crops.remove(file.name)
        prefs.edit().remove("crop_${file.name}").apply()
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

/** One text style: font, color, size, bold, shadow and how much the photo is dimmed. */
data class TextLook(
    val fontId: String,
    val color: Int,
    val scale: Float,
    val bold: Boolean,
    val shadow: Boolean,
    val dim: Float,
) {
    fun encode() = listOf(fontId, color, scale, bold, shadow, dim).joinToString("|")

    companion object {
        fun decode(text: String?): TextLook? {
            val p = text?.split("|") ?: return null
            if (p.size != 6) return null
            return try {
                TextLook(p[0], p[1].toInt(), p[2].toFloat(), p[3].toBoolean(), p[4].toBoolean(), p[5].toFloat())
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * How a photo is framed: [zoom] 1 = fills the screen, and the point of the photo
 * at the center of the screen, as fractions of its width ([cx]) and height ([cy]).
 */
data class PhotoCrop(val zoom: Float = 1f, val cx: Float = 0.5f, val cy: Float = 0.5f) {
    fun encode() = "$zoom,$cx,$cy"

    companion object {
        fun decode(text: String): PhotoCrop? {
            val p = text.split(",")
            if (p.size != 3) return null
            return try {
                PhotoCrop(p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
            } catch (e: Exception) {
                null
            }
        }
    }
}
