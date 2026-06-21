package com.example.mlbbpicker.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import com.example.mlbbpicker.draft.DraftCropper
import com.example.mlbbpicker.draft.DraftAnalyzer

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIFICATION_CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var handlerThread: HandlerThread
    private lateinit var captureHandler: Handler

    private var frameCounter = 0

    override fun onCreate() {
        super.onCreate()

        CaptureStatus.statusMessage = "ScreenCaptureService: onCreate"
        CaptureStatus.lastError = null

        handlerThread = HandlerThread("ScreenCaptureThread")
        handlerThread.start()
        captureHandler = Handler(handlerThread.looper)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForegroundService()

        CaptureStatus.isActive = true
        CaptureStatus.statusMessage = "ScreenCaptureService: onStartCommand"
        CaptureStatus.lastError = null

        val resultCode = intent?.getIntExtra(
            EXTRA_RESULT_CODE,
            Activity.RESULT_CANCELED
        ) ?: Activity.RESULT_CANCELED

        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            CaptureStatus.isActive = false
            CaptureStatus.statusMessage =
                "Invalid projection data\nresultCode=$resultCode\ndata=${resultData != null}"

            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startProjection(resultCode, resultData)
        } catch (e: Exception) {
            CaptureStatus.isActive = false
            CaptureStatus.lastError = e.javaClass.simpleName + ": " + e.message
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        CaptureStatus.isActive = false
        CaptureStatus.statusMessage = "Capture: inactive"

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        handlerThread.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForegroundService() {
        val notification = buildNotification("Screen capture: active")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        if (mediaProjection != null) return

        CaptureStatus.statusMessage = "startProjection called"

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        CaptureStatus.statusMessage = "MediaProjection created"

        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    CaptureStatus.isActive = false
                    CaptureStatus.statusMessage = "MediaProjection stopped"
                    stopSelf()
                }
            },
            captureHandler
        )

        val captureConfig = getLandscapeCaptureConfig()
        val width = captureConfig.width
        val height = captureConfig.height
        val density = captureConfig.densityDpi

        CaptureStatus.captureWidth = width
        CaptureStatus.captureHeight = height

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener(
            { reader ->
                val image = reader.acquireLatestImage()

                if (image != null) {
                    try {
                        frameCounter++
                        CaptureStatus.frameCounter = frameCounter
                        CaptureStatus.isActive = true
                        CaptureStatus.statusMessage = "Capture: active"

                        if (frameCounter % 120 == 0) {
                            saveLatestFrame(image)
                            updateNotification("Screen capture: active, frames: $frameCounter")
                        }
                    } catch (e: Exception) {
                        CaptureStatus.lastError = e.javaClass.simpleName + ": " + e.message
                    } finally {
                        image.close()
                    }
                }
            },
            captureHandler
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "MLBB_Draft_Capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler
        )

        CaptureStatus.statusMessage = "VirtualDisplay created"
    }

    private data class CaptureConfig(
        val width: Int,
        val height: Int,
        val densityDpi: Int
    )

    private fun getLandscapeCaptureConfig(): CaptureConfig {
        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)

        val rawWidth = metrics.widthPixels.coerceAtLeast(1)
        val rawHeight = metrics.heightPixels.coerceAtLeast(1)

        return CaptureConfig(
            width = maxOf(rawWidth, rawHeight),
            height = minOf(rawWidth, rawHeight),
            densityDpi = metrics.densityDpi
        )
    }

    private fun saveLatestFrame(image: Image) {
        val bitmap = normalizeForDraft(imageToBitmap(image))
        CaptureStatus.captureWidth = bitmap.width
        CaptureStatus.captureHeight = bitmap.height

        val picturesDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            ?: filesDir

        val latestFile = java.io.File(picturesDir, "latest_capture.png")

        java.io.FileOutputStream(latestFile).use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }

        val cropsDir = java.io.File(picturesDir, "draft_crops")
        com.example.mlbbpicker.draft.DraftCropper.saveDraftCrops(bitmap, cropsDir)

        val result = DraftAnalyzer.analyze(bitmap)
        CaptureStatus.bansDetected = result.bansDetected
        CaptureStatus.allyPicked = result.allyPicked
        CaptureStatus.enemyPicked = result.enemyPicked
        CaptureStatus.banSlotsText = result.banSlots.toSlotText()
        CaptureStatus.allySlotsText = result.allySlots.toSlotText()
        CaptureStatus.enemySlotsText = result.enemySlots.toSlotText()
        CaptureStatus.isBanPhase = result.isBanPhase
        CaptureStatus.analysisSource = "Live capture"

        bitmap.recycle()

        CaptureStatus.lastSavedPath = latestFile.absolutePath
    }

    private fun normalizeForDraft(bitmap: Bitmap): Bitmap {
        if (bitmap.width >= bitmap.height) {
            return bitmap
        }

        val matrix = Matrix().apply {
            postRotate(90f)
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        ).also {
            bitmap.recycle()
        }
    }

    private fun List<Boolean>.toSlotText(): String {
        return joinToString(separator = "") { if (it) "1" else "0" }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmapWidth = image.width + rowPadding / pixelStride

        val bitmap = Bitmap.createBitmap(
            bitmapWidth,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            image.width,
            image.height
        ).also {
            bitmap.recycle()
        }
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("MLBB Draft Assistant")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
