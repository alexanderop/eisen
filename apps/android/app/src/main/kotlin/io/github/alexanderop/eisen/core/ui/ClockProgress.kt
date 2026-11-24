package io.github.alexanderop.eisen.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The wave stays flat this close to either end: a wave on a sliver of bar reads as noise.
 */
private const val FLAT_AT_ENDS = 0.02f

/** How high the wave goes, of the room it has: a calm wave, the digits stay the loud thing. */
private const val WAVE = 0.6f

/** The wave of a running clock, [height] high, flat once it is paused or stopped, and at the very ends. */
private fun waveOf(running: Boolean, height: Float = WAVE): (Float) -> Float =
    if (!running) { _ -> 0f } else { progress -> if (progress <= FLAT_AT_ENDS || progress >= 1f - FLAT_AT_ENDS) 0f else height }

/**
 * Material's ring waves a quarter of its radius deep at full amplitude: fine at 48dp, a cog around
 * the digits of a timer. This keeps the wave a third of the stroke deep at any [size].
 */
private fun ringWave(size: Dp, thickness: Dp): Float = (thickness.value / 3f / (size.value / 2f * 0.25f)).coerceIn(0f, 1f)

/**
 * How far a clock has run (or how much of it is left) as a wavy bar: the wave moves while the
 * clock runs and lies flat when it is paused or stopped. [progress] is read off the model's clock
 * by the caller on every frame, it is never eased towards: the bar is where the clock is. TalkBack
 * reads [description] with the percentage.
 */
@Composable
fun ClockBar(
    progress: () -> Float,
    running: Boolean,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    trackColor: Color = EisenColors.Muted,
    thickness: Dp = 6.dp,
) {
    val px = with(LocalDensity.current) { thickness.toPx() }
    val stroke = remember(px) { Stroke(px, cap = StrokeCap.Round) }
    val amplitude = remember(running) { waveOf(running) }
    // With animations removed the wave stands still: its shape still says the clock runs.
    val speed = if (rememberReducedMotion()) 0.dp else thickness * 7
    LinearWavyProgressIndicator(
        progress = progress,
        // Room above and below the stroke for the wave, so it never clips.
        modifier = modifier.fillMaxWidth().height(thickness * 2.5f).semantics { contentDescription = description },
        color = color,
        trackColor = trackColor,
        stroke = stroke,
        trackStroke = stroke,
        gapSize = thickness * 0.75f,
        stopSize = 0.dp,
        amplitude = amplitude,
        wavelength = thickness * 7,
        waveSpeed = speed,
    )
}

/** [ClockBar] as a ring of [size] around the digits of a timer, drawn inside it, wave included. */
@Composable
fun ClockRing(
    progress: () -> Float,
    running: Boolean,
    color: Color,
    description: String,
    size: Dp,
    thickness: Dp,
    modifier: Modifier = Modifier,
    trackColor: Color = EisenColors.Muted,
) {
    val px = with(LocalDensity.current) { thickness.toPx() }
    val stroke = remember(px) { Stroke(px, cap = StrokeCap.Round) }
    val amplitude = remember(running, size, thickness) { waveOf(running, ringWave(size, thickness)) }
    val speed = if (rememberReducedMotion()) 0.dp else thickness * 1.5f
    CircularWavyProgressIndicator(
        progress = progress,
        modifier = modifier.size(size).semantics { contentDescription = description },
        color = color,
        trackColor = trackColor,
        stroke = stroke,
        trackStroke = stroke,
        gapSize = thickness * 0.5f,
        amplitude = amplitude,
        // Short waves: Material bends a polygon of 2πr / wavelength corners, and few corners read as edges.
        wavelength = thickness * 1.5f,
        waveSpeed = speed,
    )
}
