package com.salestracker.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * Receipt photos, kept inside the app and encrypted with the same hardware-backed key as the rest of the data.
 *
 * - The app never asks for access to the phone's photos or files. A photo arrives only when the user takes one
 *   with the camera app or picks one in Android's photo picker, and the app gets that one photo and nothing else.
 * - Each photo is re-drawn into a fresh JPEG. That shrinks it to a sensible size and drops everything else the
 *   original file carried (GPS location, phone model, time stamps and other metadata).
 * - The encrypted copies live in the app's private storage, are never backed up by Android, and are destroyed
 *   (along with the key) by "Erase all data". A decrypted photo only ever exists in memory while it is on screen.
 */
object ReceiptStore {
    /** Longest side of a stored photo, in pixels. Sharp enough to read small print on a till receipt. */
    const val MAX_SIDE = 2000
    private const val QUALITY = 85
    /** Photos per expense. */
    const val MAX_PER_EXPENSE = 6
    /** Refuse images so large they could only be a mistake or an attack on memory (e.g. 30000 × 30000). */
    private const val MAX_PIXELS = 120_000_000L

    private const val DIR = "receipts"
    private const val CAMERA_DIR = "camera"

    fun dir(context: Context): File = File(context.noBackupFilesDir, DIR)
    private fun file(context: Context, id: Long) = File(dir(context), "$id.qvr")

    /** Where the camera app writes a new photo (inside the app's private cache) before it is encrypted. */
    fun cameraFile(context: Context): File =
        File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }.let { File(it, "receipt.jpg") }

    /** Removes any photo the camera app left behind (e.g. if the app was closed mid-capture). */
    fun clearCamera(context: Context) {
        File(context.cacheDir, CAMERA_DIR).listFiles()?.forEach { wipe(it) }
    }

    fun exists(context: Context, id: Long): Boolean = file(context, id).exists()

    /** Encrypts and stores [jpeg] under [id]. Written to a temporary file first so a crash never leaves half a photo. */
    fun save(context: Context, id: Long, jpeg: ByteArray, cipher: DataCipher) {
        val d = dir(context).apply { mkdirs() }
        val tmp = File(d, "$id.tmp")
        tmp.writeBytes(SealedData.seal(jpeg, cipher))
        if (!tmp.renameTo(file(context, id))) {
            tmp.delete()
            throw java.io.IOException("could not store receipt")
        }
    }

    /** The decrypted JPEG, or null if this phone doesn't have the photo (or it can't be opened). */
    fun load(context: Context, id: Long, cipher: DataCipher): ByteArray? {
        val f = file(context, id)
        if (!f.exists()) return null
        return try { SealedData.open(f.readBytes(), cipher) } catch (e: Exception) { null }
    }

    fun delete(context: Context, ids: Collection<Long>) = ids.forEach { wipe(file(context, it)) }

    /** Deletes every stored photo that [keep] doesn't mention (after restoring a backup, say). */
    fun deleteAllExcept(context: Context, keep: Set<Long>) {
        dir(context).listFiles()?.forEach { f ->
            val id = f.name.substringBefore('.').toLongOrNull()
            if (id == null || id !in keep) wipe(f)
        }
    }

    fun deleteAll(context: Context) {
        dir(context).listFiles()?.forEach { wipe(it) }
        clearCamera(context)
    }

    /** Overwrites a file with zeros before deleting it (a best effort; the bytes were encrypted anyway). */
    private fun wipe(f: File) {
        try {
            if (f.isFile) f.outputStream().use { out -> out.write(ByteArray(f.length().toInt().coerceAtMost(16 * 1024 * 1024))) }
        } catch (e: Exception) {
            // Deleting is what matters.
        }
        f.delete()
    }

    /**
     * Turns a photo into the stored form: upright, at most [MAX_SIDE] pixels on its longest side,
     * re-encoded as a plain JPEG with no metadata. [open] is called more than once, so it must
     * return a fresh stream each time. Returns null if the file isn't a readable image.
     */
    fun prepare(open: () -> InputStream?): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // (Reading only the size always returns null, so success is judged by the size it finds.)
        val stream = open() ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return null

        // Decode at roughly the size needed (powers of two keep memory low), then scale exactly.
        var sample = 1
        while (max(w, h) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val orientation = try {
            open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) {
            null
        } ?: ExifInterface.ORIENTATION_NORMAL

        val m = Matrix()
        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) m.postScale(scale, scale)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        val upright = if (m.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)

        // Draw onto a plain opaque bitmap: JPEG has no transparency, and this guarantees nothing but pixels carries over.
        val out = Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(upright, 0f, 0f, null)
        }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        return ByteArrayOutputStream().use { bytes ->
            out.compress(Bitmap.CompressFormat.JPEG, QUALITY, bytes)
            out.recycle()
            bytes.toByteArray()
        }
    }

    /** Pixel size of a stored photo's JPEG (used by tests and the viewer). */
    fun size(jpeg: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        return o.outWidth to o.outHeight
    }
}
