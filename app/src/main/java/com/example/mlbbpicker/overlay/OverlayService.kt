package com.example.mlbbpicker.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mlbbpicker.capture.CaptureStatus

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var statusText: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val statusUpdater = object : Runnable {
        override fun run() {
            updateStatusText()
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        showOverlay()
        mainHandler.post(statusUpdater)
    }

    override fun onDestroy() {
        super.onDestroy()

        mainHandler.removeCallbacks(statusUpdater)

        overlayView?.let { view ->
            windowManager?.removeView(view)
        }

        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(220, 20, 20, 20))
            setPadding(24, 18, 24, 18)
        }

        val title = TextView(this).apply {
            text = "MLBB Assistant"
            setTextColor(Color.WHITE)
            textSize = 16f
        }

        val recommendation = TextView(this).apply {
            text = "Gold: Karrie / Melissa / Hanabi"
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        statusText = TextView(this).apply {
            text = "Capture: inactive"
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        val closeButton = Button(this).apply {
            text = "×"
            setOnClickListener {
                stopSelf()
            }
        }

        root.addView(title)
        root.addView(recommendation)
        root.addView(statusText)
        root.addView(closeButton)

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 250
        }

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(root, params)
                    true
                }

                else -> false
            }
        }

        overlayView = root
        windowManager?.addView(root, params)
    }

    private fun updateStatusText() {
        val error = CaptureStatus.lastError

        statusText?.text = when {
            error != null -> {
                "Capture error:\n$error"
            }

            else -> {
                buildString {
                    append(if (CaptureStatus.isActive) "Capture: active" else CaptureStatus.statusMessage)

                    if (CaptureStatus.frameCounter > 0) {
                        append("\nFrames: ${CaptureStatus.frameCounter}")
                    }

                    append("\nPhase: ${if (CaptureStatus.isBanPhase) "Ban" else "Pick/Other"}")
                    append("\nBans: ${CaptureStatus.bansDetected} detected")
                    append("\nAllies: ${CaptureStatus.allyPicked} picked")
                    append("\nEnemies: ${CaptureStatus.enemyPicked} picked")

                    append("\nB: ${CaptureStatus.banSlotsText}")
                    append("\nA: ${CaptureStatus.allySlotsText}")
                    append("\nE: ${CaptureStatus.enemySlotsText}")

                    append("\nSource: ${CaptureStatus.analysisSource}")
                }
            }
        }
    }
}