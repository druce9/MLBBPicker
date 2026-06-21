package com.example.mlbbpicker.draft

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object DraftAnalyzer {

    fun analyze(bitmap: Bitmap): DraftDetectionResult {
        val banPhase = isBanPhase(bitmap)

        val banSlots = (DraftLayout.leftBanSlots + DraftLayout.rightBanSlots).map { rect ->
            val crop = crop(bitmap, rect)
            val filled = isBanSlotFilled(crop)
            crop.recycle()
            filled
        }

        val allySlots = if (banPhase) {
            List(5) { false }
        } else {
            DraftLayout.allyPickSlots.map { rect ->
                val crop = crop(bitmap, rect)
                val filled = isAllyPickSlotFilled(crop)
                crop.recycle()
                filled
            }
        }

        val enemySlots = if (banPhase) {
            List(5) { false }
        } else {
            DraftLayout.enemyPickSlots.map { rect ->
                val crop = crop(bitmap, rect)
                val filled = isEnemyPickSlotFilled(crop)
                crop.recycle()
                filled
            }
        }

        return DraftDetectionResult(
            bansDetected = banSlots.count { it },
            allyPicked = allySlots.count { it },
            enemyPicked = enemySlots.count { it },
            banSlots = banSlots,
            allySlots = allySlots,
            enemySlots = enemySlots,
            isBanPhase = banPhase
        )
    }

    private fun isBanPhase(bitmap: Bitmap): Boolean {
        val centerButtonRed = redRatio(bitmap, DraftLayout.banButtonCenter)
        val rightButtonRed = redRatio(bitmap, DraftLayout.banButtonRight)

        return centerButtonRed > 0.08 || rightButtonRed > 0.08
    }

    private fun isBanSlotFilled(slotBitmap: Bitmap): Boolean {
        val stats = computeStats(slotBitmap)

        return stats.edgeMean > 8.0 && stats.lumaStd > 30.0
    }

    private fun isAllyPickSlotFilled(slotBitmap: Bitmap): Boolean {
        val wide = computeStats(slotBitmap)

        val leftStrip = cropRelative(
            slotBitmap,
            0.00f,
            0.00f,
            0.38f,
            1.00f
        )

        val rightStrip = cropRelative(
            slotBitmap,
            0.69f,
            0.00f,
            1.00f,
            1.00f
        )

        val leftStats = computeStats(leftStrip)
        val rightStats = computeStats(rightStrip)

        leftStrip.recycle()
        rightStrip.recycle()

        val baseLooksLikePortrait =
            wide.edgeMean > 5.5 && wide.lumaStd > 28.0

        // Пустые союзные слоты часто светло-синие из-за подсветки/логотипа.
        val looksLikeEmptyBlueSlot =
            leftStats.blueRatio > 0.70 && leftStats.lumaMean > 90.0

        // Иногда пустой слот с логотипом игрока даёт много текстуры.
        // Этот фильтр отсекает такие "ложные портреты".
        val looksLikeEmptyLogoSlot =
            rightStats.edgeMean > 8.8 && rightStats.darkRatio > 0.45

        return baseLooksLikePortrait &&
                !looksLikeEmptyBlueSlot &&
                !looksLikeEmptyLogoSlot
    }

    private fun isEnemyPickSlotFilled(slotBitmap: Bitmap): Boolean {
        val stats = computeStats(slotBitmap)

        // Справа пустые красные слоты тоже яркие,
        // поэтому делаем порог по текстуре строже.
        return stats.edgeMean > 6.0 && stats.lumaStd > 30.0
    }

    private fun redRatio(bitmap: Bitmap, rect: NormRect): Double {
        val crop = crop(bitmap, rect)

        var redPixels = 0
        var total = 0

        val stepX = if (crop.width > 80) 2 else 1
        val stepY = if (crop.height > 80) 2 else 1

        for (y in 0 until crop.height step stepY) {
            for (x in 0 until crop.width step stepX) {
                val pixel = crop.getPixel(x, y)

                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (r > 120 && r > g * 1.25 && r > b * 1.25) {
                    redPixels++
                }

                total++
            }
        }

        crop.recycle()

        return if (total == 0) 0.0 else redPixels.toDouble() / total.toDouble()
    }

    private fun crop(bitmap: Bitmap, rect: NormRect): Bitmap {
        val left = (rect.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun cropRelative(
        bitmap: Bitmap,
        leftRatio: Float,
        topRatio: Float,
        rightRatio: Float,
        bottomRatio: Float
    ): Bitmap {
        val left = (bitmap.width * leftRatio).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * topRatio).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * rightRatio).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * bottomRatio).roundToInt().coerceIn(top + 1, bitmap.height)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private data class Stats(
        val lumaMean: Double,
        val lumaStd: Double,
        val edgeMean: Double,
        val blueRatio: Double,
        val darkRatio: Double
    )

    private fun computeStats(bitmap: Bitmap): Stats {
        var count = 0

        var lumaSum = 0.0
        var lumaSqSum = 0.0

        var edgeSum = 0.0
        var edgeCount = 0

        var bluePixels = 0
        var darkPixels = 0

        val step = 2

        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)

                val r = Color.red(pixel).toDouble()
                val g = Color.green(pixel).toDouble()
                val b = Color.blue(pixel).toDouble()

                val luma = 0.299 * r + 0.587 * g + 0.114 * b

                lumaSum += luma
                lumaSqSum += luma * luma
                count++

                if (b > 80.0 && b > r * 1.15 && b > g * 1.05) {
                    bluePixels++
                }

                if (luma < 35.0) {
                    darkPixels++
                }

                if (x + step < bitmap.width) {
                    edgeSum += abs(luma - luma(bitmap.getPixel(x + step, y)))
                    edgeCount++
                }

                if (y + step < bitmap.height) {
                    edgeSum += abs(luma - luma(bitmap.getPixel(x, y + step)))
                    edgeCount++
                }
            }
        }

        val lumaMean = if (count == 0) 0.0 else lumaSum / count
        val variance = if (count == 0) {
            0.0
        } else {
            (lumaSqSum / count) - lumaMean * lumaMean
        }

        return Stats(
            lumaMean = lumaMean,
            lumaStd = sqrt(variance.coerceAtLeast(0.0)),
            edgeMean = if (edgeCount == 0) 0.0 else edgeSum / edgeCount,
            blueRatio = if (count == 0) 0.0 else bluePixels.toDouble() / count.toDouble(),
            darkRatio = if (count == 0) 0.0 else darkPixels.toDouble() / count.toDouble()
        )
    }

    private fun luma(pixel: Int): Double {
        val r = Color.red(pixel).toDouble()
        val g = Color.green(pixel).toDouble()
        val b = Color.blue(pixel).toDouble()

        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}