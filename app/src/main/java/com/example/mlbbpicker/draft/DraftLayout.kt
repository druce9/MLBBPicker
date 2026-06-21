package com.example.mlbbpicker.draft

object DraftLayout {

    private fun square(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float
    ): NormRect {
        return NormRect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f
        )
    }

    // Верхние баны слева. Координаты под твои скрины 1536x691.
    val leftBanSlots = listOf(
        square(0.074f, 0.045f, 0.045f, 0.090f),
        square(0.120f, 0.045f, 0.045f, 0.090f),
        square(0.166f, 0.045f, 0.045f, 0.090f),
        square(0.212f, 0.045f, 0.045f, 0.090f),
        square(0.258f, 0.045f, 0.045f, 0.090f)
    )

    // Верхние баны справа.
    val rightBanSlots = listOf(
        square(0.742f, 0.045f, 0.045f, 0.090f),
        square(0.788f, 0.045f, 0.045f, 0.090f),
        square(0.834f, 0.045f, 0.045f, 0.090f),
        square(0.880f, 0.045f, 0.045f, 0.090f),
        square(0.926f, 0.045f, 0.045f, 0.090f)
    )

    // Левые пики. Узкая зона портрета/слота союзника.
    val allyPickSlots = listOf(
        NormRect(0.000f, 0.075f, 0.160f, 0.225f),
        NormRect(0.000f, 0.240f, 0.160f, 0.390f),
        NormRect(0.000f, 0.405f, 0.160f, 0.555f),
        NormRect(0.000f, 0.570f, 0.160f, 0.720f),
        NormRect(0.000f, 0.735f, 0.160f, 0.885f)
    )

    // Пики врагов — 5 вертикальных слотов справа.
    val enemyPickSlots = listOf(
        NormRect(0.815f, 0.075f, 0.980f, 0.225f),
        NormRect(0.815f, 0.240f, 0.980f, 0.390f),
        NormRect(0.815f, 0.405f, 0.980f, 0.555f),
        NormRect(0.815f, 0.570f, 0.980f, 0.720f),
        NormRect(0.815f, 0.735f, 0.980f, 0.885f)
    )

    // Красные кнопки бан-фазы:
    // 1) большая кнопка "Запрет" снизу по центру
    // 2) кнопка "Предложить запрет" справа снизу
    val banButtonCenter = NormRect(0.420f, 0.860f, 0.580f, 0.980f)
    val banButtonRight = NormRect(0.640f, 0.740f, 0.770f, 0.840f)
}