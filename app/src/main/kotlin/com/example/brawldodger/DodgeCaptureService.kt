// 3. FULL UPDATED: DodgeCaptureService.kt (Brawl-tuned colors + auto-aim)
package com.example.brawldodger  // Your package

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import kotlin.math.abs

class DodgeCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var prevProjCount = 0
    private var prevBitmap: Bitmap? = null
    private var lastDodgeTime = 0L
    private var lastAimTime = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        createImageReader()
        createVirtualDisplay()
        startForeground(1, createNotification())
        return START_STICKY
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Brawl Dodger")
        .setContentText(if (DodgeAccessibilityService.instance?.isAutoAim == true) "Dodge + Aim ACTIVE" else "Dodge ACTIVE")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Brawl Dodger", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createImageReader() {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            processImage(image)
            image.close()
        }, Handler(Looper.getMainLooper()))
    }

    private fun createVirtualDisplay() {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getMetrics(metrics)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "BrawlDodger",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun processImage(image: Image) {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        var bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)

        // AUTO-DODGE: Brawl projectiles (yellow/orange/white spikes)
        val leftProj = countProjectile(bitmap, 0f, 0.4f)
        val rightProj = countProjectile(bitmap, 0.6f, 1f)
        val totalProj = leftProj + rightProj
        val now = System.currentTimeMillis()
        if (totalProj > 20 && totalProj > prevProjCount * 1.4f && now - lastDodgeTime > 250) {
            val dir = if (leftProj > rightProj) "right" else "left"
            DodgeAccessibilityService.instance?.dispatchDodge(dir)
            lastDodgeTime = now
        }
        prevProjCount = totalProj

        // AUTO-AIM: Moving enemy blob
        if (DodgeAccessibilityService.instance?.isAutoAim == true && prevBitmap != null && now - lastAimTime > 300) {
            val centroid = computeMovingCentroid(bitmap, prevBitmap!!)
            if (centroid != null) {
                DodgeAccessibilityService.instance?.dispatchAim(centroid.first, centroid.second)
                lastAimTime = now
            }
        }

        prevBitmap?.recycle()
        prevBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()
    }

    private fun countProjectile(bitmap: Bitmap, fracLeft: Float, fracRight: Float): Int {
        var count = 0
        val w = bitmap.width
        val h = bitmap.height
        val xStart = (w * fracLeft).toInt()
        val xEnd = (w * fracRight).toInt()
        val yStart = (h * 0.25f).toInt()  // Mid-player area
        val yEnd = (h * 0.75f).toInt()
        val step = 5
        for (x in xStart until xEnd step step) {
            for (y in yStart until yEnd step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Brawl projectiles: bright yellow/orange/white
                if (r > 140 && g > 100 && b < 130 && (r + g + b > 400)) count++
            }
        }
        return count
    }

    private fun computeMovingCentroid(curr: Bitmap, prev: Bitmap): Pair<Float, Float>? {
        var totalDiff = 0f
        var sumX = 0f
        var sumY = 0f
        val w = curr.width
        val h = curr.height
        val step = 8  // Perf for diff
        val diffThresh = 45
        for (x in 0 until w step step) {
            val normX = x.toFloat() / w
            if (normX < 0.2f || normX > 0.9f) continue  // Center screen enemies
            for (y in 0 until h step step) {
                val normY = y.toFloat() / h
                if (normY < 0.15f || normY > 0.75f) continue  // Avoid HUD/ground
                val diff = colorDiff(curr.getPixel(x, y), prev.getPixel(x, y))
                if (diff > diffThresh) {
                    totalDiff += 1f
                    sumX += x
                    sumY += y
                }
            }
        }
        return if (totalDiff > 80) {  // Large blob = enemy (not tiny proj)
            Pair(sumX / totalDiff / w, sumY / totalDiff / h)
        } else null
    }

    private fun colorDiff(p1: Int, p2: Int): Int {
        val r1 = Color.red(p1)
        val g1 = Color.green(p1)
        val b1 = Color.blue(p1)
        val r2 = Color.red(p2)
        val g2 = Color.green(p2)
        val b2 = Color.blue(p2)
        return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        prevBitmap?.recycle()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "brawl_dodger_channel"
    }
}
