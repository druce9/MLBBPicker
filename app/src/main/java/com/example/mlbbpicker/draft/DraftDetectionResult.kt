package com.example.mlbbpicker.draft

data class DraftDetectionResult(
    val bansDetected: Int,
    val allyPicked: Int,
    val enemyPicked: Int,
    val banSlots: List<Boolean>,
    val allySlots: List<Boolean>,
    val enemySlots: List<Boolean>,
    val isBanPhase: Boolean
)