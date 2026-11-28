package io.github.alexanderop.eisen.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

private val ConfettiColors = listOf(EisenColors.Primary, Color(0xFFFFF1A8), EisenColors.Rest, Color(0xFFF0C08A), EisenColors.Foreground)

private const val PIECES = 40
private const val FALL_MS = 1_600
private const val STAGGER_MS = 50

/**
 * A short burst of confetti falling over the whole of [modifier]'s area, each time [burst] goes up; nothing at 0. It
 * takes no touches and draws nothing once it has fallen. With the system's animations removed it does not play at all.
 */
@Composable
fun ConfettiBurst(burst: Int, modifier: Modifier = Modifier) {
    if (burst == 0 || rememberReducedMotion()) return
    val fall = remember(burst) { Animatable(0f) }
    LaunchedEffect(burst) {
        fall.animateTo(1f, tween(FALL_MS + STAGGER_MS * 9, easing = LinearEasing))
    }
    Canvas(modifier) {
        val total = (FALL_MS + STAGGER_MS * 9).toFloat()
        val elapsed = fall.value * total
        if (fall.value >= 1f) return@Canvas
        val piece = Size(7.dp.toPx(), 12.dp.toPx())
        for (i in 0 until PIECES) {
            val local = ((elapsed - (i % 10) * STAGGER_MS) / FALL_MS).coerceIn(0f, 1f)
            if (local <= 0f) continue
            // Ease in: slow at the top, faster as it falls, as the prototype's CSS does.
            val t = local * local
            val x = size.width * ((i * 37) % 100) / 100f
            val y = -piece.height * 2 + (size.height + piece.height * 3) * t
            val turn = i * 23f + 540f * t
            rotate(turn, pivot = Offset(x + piece.width / 2, y + piece.height / 2)) {
                drawRoundRect(
                    ConfettiColors[i % ConfettiColors.size].copy(alpha = 1f - 0.8f * t),
                    topLeft = Offset(x, y),
                    size = piece,
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}
