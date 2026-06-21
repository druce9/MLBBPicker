package com.example.mlbbpicker.draft

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object DraftCropper {

    fun saveDraftCrops(bitmap: Bitmap, outputDir: File) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        saveSlotGroup(bitmap, DraftLayout.leftBanSlots, outputDir, "ban_left")
        saveSlotGroup(bitmap, DraftLayout.rightBanSlots, outputDir, "ban_right")
        saveSlotGroup(bitmap, DraftLayout.allyPickSlots, outputDir, "ally_pick")
        saveSlotGroup(bitmap, DraftLayout.enemyPickSlots, outputDir, "enemy_pick")
    }

    private fun saveSlotGroup(
        bitmap: Bitmap,
        slots: List<NormRect>,
        outputDir: File,
        prefix: String
    ) {
        slots.forEachIndexed { index, rect ->
            val cropped = crop(bitmap, rect)
            val file = File(outputDir, "${prefix}_${index + 1}.png")
            saveBitmap(cropped, file)
            cropped.recycle()
        }
    }

    private fun crop(bitmap: Bitmap, rect: NormRect): Bitmap {
        val left = (rect.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)

        val width = right - left
        val height = bottom - top

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}