package com.myphotocalendar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Keeps the app's own copies of the photos you add, so backgrounds load fast
 * and keep working offline. Photos are resized so the longest side is at most
 * MAX_SIDE pixels, which is still sharper than the phone's screen.
 */
class PhotoStore(private val context: Context) {

    private val dir = File(context.filesDir, "photos").apply { mkdirs() }

    fun list(): List<File> =
        dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    /** Copies one picked photo into the app. Returns null if it couldn't be read. */
    fun import(uri: Uri): File? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1)
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val name = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        file
    } catch (e: Exception) {
        null
    }

    fun delete(file: File) {
        file.delete()
    }

    companion object {
        private const val MAX_SIDE = 3200
    }
}
