package com.zebrasoftware.dude.data

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The photo of the parked car, kept in the app's private storage on this device.
 *
 * The camera app writes a new photo to a separate "pending" file first, so the old
 * photo is only replaced once a new one has actually been taken.
 */
internal class CarPhotoStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "photos")
    private val pending = File(directory, "car-pending.jpg")
    private val authority = "${appContext.packageName}.fileprovider"

    val file = File(directory, "car.jpg")

    fun exists(): Boolean = file.isFile && file.length() > 0

    /** A content URI the camera app can write the new photo to. */
    fun pendingUri(): Uri {
        directory.mkdirs()
        pending.delete()
        return FileProvider.getUriForFile(appContext, authority, pending)
    }

    /** Keeps the photo just taken. Returns false if the camera didn't write one. */
    fun commitPending(): Boolean {
        if (!pending.isFile || pending.length() == 0L) {
            pending.delete()
            return false
        }
        file.delete()
        return pending.renameTo(file)
    }

    fun discardPending() {
        pending.delete()
    }

    fun delete() {
        file.delete()
        pending.delete()
    }
}

/**
 * Loads [file] scaled down so its longest side is at most [maxSize] pixels.
 * ImageDecoder applies the photo's rotation, so it shows the right way up.
 */
internal suspend fun loadPhoto(file: File, maxSize: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    if (!file.isFile) return@withContext null
    runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSize) {
                val scale = maxSize.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }.asImageBitmap()
    }.getOrNull()
}
