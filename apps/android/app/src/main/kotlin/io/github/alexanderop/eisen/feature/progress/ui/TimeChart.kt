package io.github.alexanderop.eisen.feature.progress.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import io.github.alexanderop.eisen.feature.progress.niceTicks
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Haptic
import io.github.alexanderop.eisen.core.ui.rememberHaptics
import kotlin.math.abs

data class ChartPoint(
    val time: Long,
    val value: Double,
    /** Reps were missed at this weight. */
    val missed: Boolean = false,
    /** The weight is lower than the time before. */
    val deload: Boolean = false,
    /** The best of the exercise so far. The parent decides which points to mark. */
    val record: Boolean = false,
)

data class ChartSeries(val id: String, val name: String, val color: Color, val points: List<ChartPoint>)

/** The web chart's viewBox. The chart keeps its shape at any width. */
private const val ASPECT = 328f / 196f
private val PAD_TOP = 12.dp
private val PAD_RIGHT = 14.dp
private val PAD_BOTTOM = 24.dp
private val PAD_LEFT = 44.dp
/** Past this many points, only the points that say something keep their marker. */
private const val DENSE = 45
/** Past this many points, markers shrink so neighbours do not run into each other. */
private const val CROWDED = 14

private val LabelStyle = TextStyle(color = EisenColors.MutedForeground, fontSize = 11.sp, fontFeatureSettings = "tnum")

/** Where times and values land inside the chart, for one size. */
private class Frame(size: Size, density: Density, private val times: List<Long>, ticks: List<Double>) {
    val left = with(density) { PAD_LEFT.toPx() }
    val right = size.width - with(density) { PAD_RIGHT.toPx() }
    val top = with(density) { PAD_TOP.toPx() }
    val bottom = size.height - with(density) { PAD_BOTTOM.toPx() }
    private val start = times.first()
    private val duration = times.last() - start
    private val low = ticks.first()
    private val span = ticks.last() - low

    fun x(time: Long): Float =
        if (duration == 0L) (left + right) / 2 else left + (time - start).toFloat() / duration * (right - left)

    fun y(value: Double): Float = top + (1 - ((value - low) / span).toFloat()) * (bottom - top)

    fun nearest(pointer: Float): Long = times.minBy { abs(x(it) - pointer) }
}

/** The time the crosshair is on, kept outside composition so a fast drag does not tick twice. */
private class Cursor(var time: Long?)

/**
 * Lines over time on one shared axis. A sideways drag or a tap moves a crosshair that snaps
 * to the nearest workout; the parent shows what is under it through [active].
 * [events] draws every point with its outcome, for a single exercise.
 */
@Composable
fun TimeChart(
    series: List<ChartSeries>,
    active: Long?,
    onActiveChange: (Long?) -> Unit,
    format: (Double) -> String,
    formatTime: (Long) -> String,
    label: String,
    modifier: Modifier = Modifier,
    events: Boolean = false,
) {
    val times = remember(series) { series.flatMap { line -> line.points.map { it.time } }.distinct().sorted() }
    val ticks = remember(series) {
        val values = series.flatMap { line -> line.points.map { it.value } }
        if (values.isEmpty()) emptyList() else niceTicks(values.min(), values.max())
    }
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val drawn = remember { Animatable(0f) }
    LaunchedEffect(series) {
        drawn.snapTo(0f)
        drawn.animateTo(1f, tween(durationMillis = 700, easing = EaseOut))
    }

    val cursor = remember { Cursor(active) }
    SideEffect { cursor.time = active }
    val latestTimes by rememberUpdatedState(times)
    val latestTicks by rememberUpdatedState(ticks)
    val moveTo by rememberUpdatedState { time: Long ->
        if (time != cursor.time) {
            cursor.time = time
            onActiveChange(time)
            haptics.perform(Haptic.STEP)
        }
    }

    if (times.isEmpty()) return

    fun step(by: Int): Boolean {
        val index = cursor.time?.let { times.indexOf(it) }?.takeIf { it >= 0 } ?: times.size
        moveTo(times[(index + by).coerceIn(0, times.lastIndex)])
        return true
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(ASPECT)
            .semantics {
                contentDescription = label
                active?.let { stateDescription = formatTime(it) }
                customActions = listOf(
                    CustomAccessibilityAction("Previous workout") { step(-1) },
                    CustomAccessibilityAction("Next workout") { step(1) },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    moveTo(Frame(size.toSize(), this, latestTimes, latestTicks).nearest(offset.x))
                }
            }
            // Only a sideways drag scrubs. An up or down drag still scrolls the page.
            .pointerInput(Unit) {
                fun pick(x: Float) = moveTo(Frame(size.toSize(), this, latestTimes, latestTicks).nearest(x))
                detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ ->
                    change.consume()
                    pick(change.position.x)
                }
            },
    ) {
        val frame = Frame(size, this, times, ticks)

        for (tick in ticks) {
            val y = frame.y(tick)
            drawLine(EisenColors.Border, Offset(frame.left, y), Offset(frame.right, y), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(format(tick), LabelStyle)
            drawText(text, topLeft = Offset(frame.left - 8.dp.toPx() - text.size.width, y - text.size.height / 2f))
        }
        val baseline = size.height - 4.dp.toPx()
        val first = measurer.measure(formatTime(times.first()), LabelStyle)
        drawText(first, topLeft = Offset(frame.left, baseline - first.firstBaseline))
        if (times.size > 1) {
            val last = measurer.measure(formatTime(times.last()), LabelStyle)
            drawText(last, topLeft = Offset(frame.right - last.size.width, baseline - last.firstBaseline))
        }

        if (active != null && active in times) {
            val x = frame.x(active)
            drawLine(EisenColors.MutedForeground, Offset(x, frame.top), Offset(x, frame.bottom), strokeWidth = 1.dp.toPx())
        }

        for (line in series) drawSeries(line, frame, events, drawn.value)

        if (active != null) {
            for (line in series) {
                val point = line.points.firstOrNull { it.time == active } ?: continue
                drawCircle(line.color, 7.dp.toPx(), Offset(frame.x(point.time), frame.y(point.value)), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.drawSeries(line: ChartSeries, frame: Frame, events: Boolean, drawn: Float) {
    if (line.points.isEmpty()) return
    val offsets = line.points.map { Offset(frame.x(it.time), frame.y(it.value)) }
    val path = Path().apply {
        moveTo(offsets[0].x, offsets[0].y)
        for (offset in offsets.drop(1)) lineTo(offset.x, offset.y)
    }

    if (events && offsets.size > 1) {
        val area = Path().apply {
            moveTo(offsets.first().x, frame.bottom)
            for (offset in offsets) lineTo(offset.x, offset.y)
            lineTo(offsets.last().x, frame.bottom)
            close()
        }
        val wash = Brush.verticalGradient(
            listOf(line.color.copy(alpha = 0.2f), line.color.copy(alpha = 0f)),
            startY = offsets.minOf { it.y },
            endY = frame.bottom,
        )
        drawPath(area, wash, alpha = drawn)
    }

    val visible = if (drawn >= 1f) {
        path
    } else {
        val measure = PathMeasure().apply { setPath(path, false) }
        Path().also { measure.getSegment(0f, measure.length * drawn, it, true) }
    }
    drawPath(visible, line.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    val lastIndex = line.points.lastIndex
    val dense = line.points.size > DENSE
    val radius = (if (line.points.size > CROWDED) 3.dp else 4.5.dp).toPx()
    val ring = 2.dp.toPx()
    line.points.forEachIndexed { index, point ->
        val marked = if (events) {
            !dense || point.missed || point.deload || point.record || index == lastIndex
        } else {
            index == lastIndex
        }
        if (!marked) return@forEachIndexed
        val center = offsets[index]
        if (point.record) {
            drawCircle(line.color, radius + 4.5.dp.toPx(), center, alpha = 0.7f, style = Stroke(1.5.dp.toPx()))
        }
        // A ring in the card colour keeps a marker readable where it sits on a line.
        // A missed workout turns hollow and amber, so the shape says it too, not only the colour.
        val fill = if (point.missed) EisenColors.Card else line.color
        val edge = if (point.missed) EisenColors.Warn else EisenColors.Card
        if (point.deload) {
            val half = radius + 2.5.dp.toPx()
            val inset = 1.dp.toPx()
            val triangle = Path().apply {
                moveTo(center.x - half, center.y - half + inset)
                lineTo(center.x + half, center.y - half + inset)
                lineTo(center.x, center.y + half)
                close()
            }
            drawPath(triangle, fill)
            drawPath(triangle, edge, style = Stroke(ring, join = StrokeJoin.Miter))
        } else {
            drawCircle(fill, radius, center)
            drawCircle(edge, radius, center, style = Stroke(ring))
        }
    }
}
