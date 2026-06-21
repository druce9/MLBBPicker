package com.example.mlbbpicker.capture

object CaptureStatus {
    @Volatile
    var isActive: Boolean = false

    @Volatile
    var frameCounter: Int = 0

    @Volatile
    var captureWidth: Int = 0

    @Volatile
    var captureHeight: Int = 0

    @Volatile
    var statusMessage: String = "Capture: inactive"

    @Volatile
    var lastSavedPath: String? = null

    @Volatile
    var bansDetected: Int = 0

    @Volatile
    var allyPicked: Int = 0

    @Volatile
    var enemyPicked: Int = 0

    @Volatile
    var analysisSource: String = "No analysis"

    @Volatile
    var lastError: String? = null

    @Volatile
    var banSlotsText: String = "0000000000"

    @Volatile
    var allySlotsText: String = "00000"

    @Volatile
    var enemySlotsText: String = "00000"

    @Volatile
    var isBanPhase: Boolean = false
}