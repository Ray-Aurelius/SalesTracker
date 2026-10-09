package com.salestracker.app.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private class TestCipher(private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()) : DataCipher {
    override fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key)
        return c.iv to c.doFinal(plain)
    }
    override fun decrypt(iv: ByteArray, encrypted: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return c.doFinal(encrypted)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptStoreTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @After fun clean() = ReceiptStore.deleteAll(app)

    /** A wide JPEG on disk, tagged with a GPS location, a phone model and "rotate 90°" like a real camera photo. */
    private fun cameraPhoto(w: Int = 3000, h: Int = 1500): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(Color.WHITE); drawRect(0f, 0f, w / 2f, h / 2f, android.graphics.Paint().apply { color = Color.RED }) }
        val f = File(app.cacheDir, "photo-${System.nanoTime()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(f.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "40/1,44/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "104/1,59/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
            setAttribute(ExifInterface.TAG_MAKE, "PhoneMaker")
            setAttribute(ExifInterface.TAG_MODEL, "Model X")
            saveAttributes()
        }
        return f
    }

    @Test
    fun photosAreShrunkTurnedUprightAndStrippedOfLocation() {
        val f = cameraPhoto()
        // Sanity check: the original really does carry a location.
        assertTrue(ExifInterface(f.absolutePath).getLatLong(FloatArray(2)))

        val out = ReceiptStore.prepare { f.inputStream() }!!
        val (w, h) = ReceiptStore.size(out)
        // 3000×1500 shrinks to 2000×1000, then the 90° turn makes it 1000×2000.
        assertEquals(1000, w)
        assertEquals(2000, h)

        val exif = ExifInterface(ByteArrayInputStream(out))
        assertFalse(exif.getLatLong(FloatArray(2)))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
        // Already upright, so no viewer turns it again.
        assertTrue(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) in
            setOf(ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED))
    }

    @Test
    fun smallPhotosAreNotEnlarged() {
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val f = File(app.cacheDir, "small.jpg").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        assertEquals(800 to 600, ReceiptStore.size(ReceiptStore.prepare { f.inputStream() }!!))
    }

    @Test
    fun filesThatAreNotImagesAreRefused() {
        assertNull(ReceiptStore.prepare { ByteArrayInputStream("not a picture at all".toByteArray()) })
        assertNull(ReceiptStore.prepare { null })
    }

    @Test
    fun storedPhotosAreEncryptedAndOnlyOurKeyOpensThem() {
        val cipher = TestCipher()
        val jpeg = ReceiptStore.prepare { cameraPhoto(1200, 900).inputStream() }!!
        ReceiptStore.save(app, 42L, jpeg, cipher)

        val onDisk = File(ReceiptStore.dir(app), "42.qvr").readBytes()
        // Nothing on disk looks like a JPEG: no JPEG start marker anywhere near the beginning.
        assertFalse(onDisk.copyOfRange(0, 64).toList().windowed(3).any { it == listOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) })
        assertTrue(SealedData.isSealed(onDisk))

        assertArrayEquals(jpeg, ReceiptStore.load(app, 42L, cipher))
        assertNull(ReceiptStore.load(app, 42L, TestCipher())) // another key can't open it
        assertTrue(ReceiptStore.exists(app, 42L))

        ReceiptStore.delete(app, listOf(42L))
        assertFalse(ReceiptStore.exists(app, 42L))
        assertNull(ReceiptStore.load(app, 42L, cipher))
    }

    @Test
    fun restoringKeepsOnlyPhotosTheDataStillMentions() {
        val cipher = TestCipher()
        val jpeg = byteArrayOf(1, 2, 3)
        listOf(1L, 2L, 3L).forEach { ReceiptStore.save(app, it, jpeg, cipher) }
        ReceiptStore.deleteAllExcept(app, setOf(2L))
        assertFalse(ReceiptStore.exists(app, 1L))
        assertTrue(ReceiptStore.exists(app, 2L))
        assertFalse(ReceiptStore.exists(app, 3L))
    }

    @Test
    fun cameraLeftoversAreCleared() {
        val f = ReceiptStore.cameraFile(app).apply { writeBytes(byteArrayOf(9, 9, 9)) }
        ReceiptStore.clearCamera(app)
        assertFalse(f.exists())
    }

    @Test
    fun expensesKeepTheirReceiptListThroughSaving() {
        val e = Expense(id = 7, timestamp = 1_700_000_000_000, kind = ExpenseKind.EXPENSE, amount = 12.5, receipts = listOf(101L, 102L))
        assertEquals(listOf(101L, 102L), Expense.fromJson(e.toJson()).receipts)
        // Data saved before receipts existed opens with none.
        val old = e.toJson().apply { remove("receipts") }
        assertEquals(emptyList<Long>(), Expense.fromJson(old).receipts)
    }
}
