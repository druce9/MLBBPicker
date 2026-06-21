package com.example.mlbbpicker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mlbbpicker.capture.ScreenCaptureService
import com.example.mlbbpicker.overlay.OverlayService
import android.graphics.BitmapFactory
import android.widget.Toast
import com.example.mlbbpicker.capture.CaptureStatus
import com.example.mlbbpicker.draft.DraftAnalyzer

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val overlayPermissionGranted = mutableStateOf(false)
    private val notificationPermissionGranted = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        refreshPermissions()

        setContent {
            val screenCaptureLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val data = result.data

                if (result.resultCode == Activity.RESULT_OK && data != null) {
                    startScreenCapture(result.resultCode, data)
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                refreshPermissions()
            }

            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    analyzeTestScreenshot(uri)
                }
            }

            MaterialTheme {
                MainScreen(
                    hasOverlayPermission = overlayPermissionGranted.value,
                    hasNotificationPermission = notificationPermissionGranted.value,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onStartOverlay = { startOverlay() },
                    onStopOverlay = { stopOverlay() },
                    onStartScreenCapture = {
                        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                        screenCaptureLauncher.launch(captureIntent)
                    },
                    onStopScreenCapture = { stopScreenCapture() },
                    onAnalyzeTestScreenshot = {
                        imagePickerLauncher.launch("image/*")
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        overlayPermissionGranted.value = Settings.canDrawOverlays(this)

        notificationPermissionGranted.value =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun analyzeTestScreenshot(uri: Uri) {
        Thread {
            try {
                val bitmap = contentResolver.openInputStream(uri).use { input ->
                    BitmapFactory.decodeStream(input)
                }

                if (bitmap == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val result = DraftAnalyzer.analyze(bitmap)

                CaptureStatus.bansDetected = result.bansDetected
                CaptureStatus.allyPicked = result.allyPicked
                CaptureStatus.enemyPicked = result.enemyPicked
                CaptureStatus.banSlotsText = result.banSlots.toSlotText()
                CaptureStatus.allySlotsText = result.allySlots.toSlotText()
                CaptureStatus.enemySlotsText = result.enemySlots.toSlotText()
                CaptureStatus.isBanPhase = result.isBanPhase
                CaptureStatus.analysisSource = "Test screenshot"
                CaptureStatus.statusMessage = "Test screenshot analyzed"
                CaptureStatus.lastError = null
                CaptureStatus.captureWidth = bitmap.width
                CaptureStatus.captureHeight = bitmap.height

                bitmap.recycle()

                runOnUiThread {
                    Toast.makeText(this, "Скриншот проанализирован", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                CaptureStatus.lastError = e.javaClass.simpleName + ": " + e.message

                runOnUiThread {
                    Toast.makeText(this, "Ошибка анализа: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun startOverlay() {
        startService(Intent(this, OverlayService::class.java))
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopScreenCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
    }
}
private fun List<Boolean>.toSlotText(): String {
    return joinToString(separator = "") { if (it) "1" else "0" }
}
@Composable
private fun MainScreen(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onStartScreenCapture: () -> Unit,
    onStopScreenCapture: () -> Unit,
    onAnalyzeTestScreenshot: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MLBB Draft Assistant",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (hasOverlayPermission) {
                "Overlay permission: granted"
            } else {
                "Overlay permission: not granted"
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (hasNotificationPermission) {
                "Notification permission: granted"
            } else {
                "Notification permission: not granted"
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRequestOverlayPermission) {
            Text("Разрешить overlay")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onRequestNotificationPermission,
            enabled = !hasNotificationPermission
        ) {
            Text("Разрешить уведомления")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onStartOverlay,
            enabled = hasOverlayPermission
        ) {
            Text("Запустить overlay")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onStopOverlay) {
            Text("Остановить overlay")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartScreenCapture,
            enabled = hasNotificationPermission
        ) {
            Text("Запустить захват экрана")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onStopScreenCapture) {
            Text("Остановить захват экрана")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onAnalyzeTestScreenshot) {
            Text("Анализировать скриншот")
        }
    }
}
