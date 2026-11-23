package io.github.alexanderop.eisen.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.Settings
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.elm.ADJUST_SECONDS
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.RestScreen
import io.github.alexanderop.eisen.domain.elm.RestState
import io.github.alexanderop.eisen.domain.elm.restSecondsLeft
import io.github.alexanderop.eisen.domain.formatNumber
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.loadText
import io.github.alexanderop.eisen.domain.repsChoices
import io.github.alexanderop.eisen.domain.weightChoices
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.Wheel
import io.github.alexanderop.eisen.ui.formatClock
import kotlin.math.floor
import kotlin.math.roundToInt

/** The last seconds of a rest turn the bar amber, like the rest bar. */
private const val SOON_SECONDS = 10

/** A drag this far down puts the screen away, or a flick this fast (px/s). */
private val MinimiseDistance = 120.dp
private const val MINIMISE_VELOCITY = 1_500f

/** Below this height (a 360×640 phone, or any phone on its side) everything but the digits gets smaller. */
private val COMPACT_HEIGHT = 700.dp

/**
 * The running [rest] over the whole workout screen, with huge digits, and two wheels to correct the set
 * that started it ([screen], of [log]): the weight of the exercise and the reps of that set. It shows
 * the time at [now], the model's clock, which ticks every second while a rest runs and the app shows.
 * The cues, the alarms and the end of the rest come from `update`. A swipe down, the chevron or the
 * back gesture put it away to the rest bar; "Next set" ends the rest.
 */
@Composable
internal fun RestTimerScreen(
    screen: RestScreen,
    rest: RestState,
    log: ExerciseLog,
    settings: Settings,
    now: Long,
    dispatch: (Msg) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minimiseAt = with(LocalDensity.current) { MinimiseDistance.toPx() }
    // How far the screen is pulled down. Pure presentation: letting go far enough minimises it.
    var pulled by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .offset { IntOffset(0, pulled.roundToInt()) }
            .background(EisenColors.Background)
            // Takes every touch, so nothing of the workout below reacts.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .draggable(
                rememberDraggableState { delta -> pulled = (pulled + delta).coerceAtLeast(0f) },
                Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (pulled > minimiseAt || velocity > MINIMISE_VELOCITY) dispatch(Msg.Rest.Minimise)
                    else animate(pulled, 0f) { value, _ -> pulled = value }
                },
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        val over = now >= rest.endsAt
        val wide = maxWidth > maxHeight
        // A small phone or one on its side gets smaller text and lower wheel rows, so the digits keep their room.
        val parts = RestParts(screen, rest, log, settings, now, over, compact = wide || maxHeight < COMPACT_HEIGHT, dispatch)
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    parts.Header()
                    parts.Clock(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    parts.Lift()
                    parts.Next()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                parts.Header()
                parts.Clock(Modifier.weight(1f))
                Spacer(Modifier.height(if (parts.compact) 8.dp else 12.dp))
                parts.Lift()
                parts.Next()
            }
        }
    }
}

/** The pieces of the rest screen, laid out in a column on a phone and in two columns on its side. */
private class RestParts(
    val screen: RestScreen,
    val rest: RestState,
    val log: ExerciseLog,
    val settings: Settings,
    val now: Long,
    val over: Boolean,
    val compact: Boolean,
    val dispatch: (Msg) -> Unit,
) {
    private val rowHeight = if (compact) 32.dp else 48.dp

    @Composable
    fun Header() {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    log.name,
                    Modifier.semantics { heading() },
                    color = EisenColors.Foreground,
                    fontSize = if (compact) 22.sp else 28.sp,
                    lineHeight = if (compact) 26.sp else 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Set ${screen.set + 1} of ${log.sets.size} done",
                    color = EisenColors.MutedForeground,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = { dispatch(Msg.Rest.Minimise) }) {
                Icon(Icons.ChevronDown, contentDescription = "Minimise rest timer", tint = EisenColors.MutedForeground)
            }
        }
    }

    /** "Rest" and the note, the digits as large as the room allows, the bar and the ±15s buttons. */
    @Composable
    fun Clock(modifier: Modifier) {
        val remaining = restSecondsLeft(rest, now)
        val overtime = maxOf(0, floor((now - rest.endsAt) / 1000.0).toInt())
        val total = ((rest.endsAt - rest.startedAt) / 1000).toInt()
        Column(modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (over) "Rest over" else "Rest",
                    color = if (over) EisenColors.Warn else EisenColors.Foreground,
                    fontSize = if (compact) 17.sp else 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    if (over) "Start your next set." else rest.note,
                    Modifier.weight(1f),
                    color = EisenColors.MutedForeground,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Digits(
                if (over) "+${formatClock(overtime)}" else formatClock(remaining),
                color = if (over) EisenColors.Warn else EisenColors.Foreground,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Drain(rest, now, over, color = if (!over && remaining <= SOON_SECONDS) EisenColors.Warn else EisenColors.Primary)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("of ${formatClock(total)}", Modifier.weight(1f), color = EisenColors.MutedForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val height = if (compact) 36.dp else 40.dp
                SmallButton("−${ADJUST_SECONDS}s", "Rest ${ADJUST_SECONDS} seconds less", height, enabled = !over) { dispatch(Msg.Rest.Shorten) }
                Spacer(Modifier.size(8.dp))
                SmallButton("+${ADJUST_SECONDS}s", "Rest ${ADJUST_SECONDS} seconds longer", height, enabled = true) { dispatch(Msg.Rest.Lengthen) }
            }
        }
    }

    /** "What did you lift?": the weight wheel, the reps wheel (or the time held) and what they make. */
    @Composable
    fun Lift() {
        val unit = settings.unit
        val bodyweight = log.equipment == Equipment.BODYWEIGHT
        val count = log.sets.getOrNull(screen.set) ?: 0
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(EisenColors.Card)
                .border(BorderStroke(1.dp, EisenColors.Border), CardShape)
                .padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            Text("What did you lift?", color = EisenColors.Foreground, fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val weights = remember(log.equipment, unit, settings.bar, log.weight) { weightChoices(log.equipment, unit, settings.bar, log.weight) }
                WheelBox(if (bodyweight) "+${unit.label}" else unit.label, compact, Modifier.weight(1f)) {
                    Wheel(
                        labels = remember(weights) { weights.map { if (bodyweight && it == 0.0) "BW" else formatNumber(it) } },
                        selected = weights.indexOf(log.weight).coerceAtLeast(0),
                        onSettle = { dispatch(Msg.Workout(WorkoutEvent.SetWeight(screen.exercise, weights[it]))) },
                        label = if (bodyweight) "Added weight" else "Weight",
                        spoken = { spokenWeight(weights[it], bodyweight, unit) },
                        rowHeight = rowHeight,
                    )
                }
                if (log.timed) {
                    WheelBox("held", compact, Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(rowHeight * 3), contentAlignment = Alignment.Center) {
                            Text(holdText(count), color = EisenColors.Foreground, fontSize = (rowHeight.value * 0.66f).sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    val choices = repsChoices(log.targetReps, count)
                    WheelBox("reps", compact, Modifier.weight(1f)) {
                        Wheel(
                            labels = remember(choices) { choices.map { it.toString() } },
                            selected = count,
                            onSettle = { dispatch(Msg.Workout(WorkoutEvent.CorrectReps(screen.exercise, screen.set, it))) },
                            label = "Reps",
                            spoken = { if (it == 1) "1 rep" else "$it reps" },
                            rowHeight = rowHeight,
                        )
                    }
                }
            }
            val load = loadText(log, unit)
            Text(
                if (log.timed) "$load · ${holdText(count)}" else "$load × $count",
                Modifier.fillMaxWidth(),
                color = EisenColors.Foreground,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
    }

    /** "Up next" and the button that ends the rest. */
    @Composable
    fun Next() {
        val next = log.sets.indexOfFirst { it == null }
        val gap = if (compact) 6.dp else 10.dp
        Column(Modifier.fillMaxWidth().padding(top = gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (next != -1) {
                Text(
                    "Up next: set ${next + 1} of ${log.sets.size}",
                    Modifier.fillMaxWidth(),
                    color = EisenColors.MutedForeground,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = { dispatch(Msg.Rest.Dismiss) },
                modifier = Modifier.fillMaxWidth().height(if (compact) 50.dp else 60.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Next set", fontSize = if (compact) 18.sp else 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun spokenWeight(weight: Double, bodyweight: Boolean, unit: WeightUnit): String {
    val units = if (unit == WeightUnit.KG) "kilograms" else "pounds"
    return when {
        !bodyweight -> "${formatNumber(weight)} $units"
        weight == 0.0 -> "Bodyweight only"
        else -> "Bodyweight plus ${formatNumber(weight)} $units"
    }
}

/**
 * The time in the heaviest digits that fit: as wide as the screen, as tall as the room left. The size
 * is worked out from the text with every digit as 0, so it only changes when the text gets longer.
 */
@Composable
private fun Digits(text: String, color: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(
        fontWeight = FontWeight.Black,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.03).em,
        lineHeight = 1.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val template = text.map { if (it.isDigit()) '0' else it }.joinToString("")
        val probe = remember(template) { measurer.measure(template, style.copy(fontSize = 100.sp)).size }
        val byWidth = 100f * constraints.maxWidth / probe.width.coerceAtLeast(1)
        val byHeight = 100f * constraints.maxHeight / probe.height.coerceAtLeast(1)
        val size = (minOf(byWidth, byHeight) * 0.97f).coerceAtLeast(24f)
        Text(text, color = color, style = style.copy(fontSize = size.sp), maxLines = 1, softWrap = false)
    }
}

/** What is left of the rest, as a lime bar that drains. Empty once the rest is over. */
@Composable
private fun Drain(rest: RestState, now: Long, over: Boolean, color: Color) {
    val total = rest.endsAt - rest.startedAt
    fun leftAt(time: Long): Float = if (total <= 0) 0f else (1f - (time - rest.startedAt).toFloat() / total).coerceIn(0f, 1f)
    val left = remember { Animatable(if (over) 0f else leftAt(now)) }
    // The clock moves a whole second at a time. In between, the bar glides to where it will be at the
    // next tick, like the rest bar.
    LaunchedEffect(rest, now) {
        if (over) {
            left.animateTo(0f, tween(250, easing = LinearEasing))
        } else {
            val untilTick = Math.floorMod(rest.endsAt - now, 1000L).takeIf { it > 0 } ?: 1000L
            left.animateTo(leftAt(now + untilTick), tween(untilTick.toInt(), easing = LinearEasing))
        }
    }
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(EisenColors.Muted)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(left.value).clip(RoundedCornerShape(5.dp)).background(color))
    }
}

@Composable
private fun WheelBox(header: String, compact: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(EisenColors.Background)
            .border(1.dp, EisenColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(header, color = EisenColors.MutedForeground, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SmallButton(text: String, description: String, height: Dp, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(height).widthIn(min = 64.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
