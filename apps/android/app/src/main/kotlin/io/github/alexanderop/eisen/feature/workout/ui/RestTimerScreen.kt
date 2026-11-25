package io.github.alexanderop.eisen.feature.workout.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.MAX_RIR
import io.github.alexanderop.eisen.app.state.Settings
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.ADJUST_SECONDS
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.RestScreen
import io.github.alexanderop.eisen.app.elm.RestState
import io.github.alexanderop.eisen.app.elm.ratesSet
import io.github.alexanderop.eisen.app.elm.restSecondsLeft
import io.github.alexanderop.eisen.core.formatNumber
import io.github.alexanderop.eisen.core.holdText
import io.github.alexanderop.eisen.core.loadText
import io.github.alexanderop.eisen.feature.workout.loadText
import io.github.alexanderop.eisen.feature.workout.repsChoices
import io.github.alexanderop.eisen.feature.workout.rirOf
import io.github.alexanderop.eisen.feature.workout.rirText
import io.github.alexanderop.eisen.feature.workout.weightChoices
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.ClockBar
import androidx.compose.material3.MaterialTheme
import io.github.alexanderop.eisen.core.ui.pressShapes
import io.github.alexanderop.eisen.core.ui.Haptic
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.NumberRow
import io.github.alexanderop.eisen.core.ui.pickableReps
import io.github.alexanderop.eisen.core.ui.Wheel
import io.github.alexanderop.eisen.core.ui.formatClock
import io.github.alexanderop.eisen.core.ui.rememberHaptics
import kotlin.math.floor
import kotlin.math.roundToInt

/** A number key of the rest screen: the whole of it takes the touch, its face is drawn [KEY_INSET] inside. */
private val KEY = 48.dp
private val KEY_INSET = 2.dp

/** The narrowest the weight wheel gets beside the number keys, and the room between the two. */
private val MIN_WEIGHT_BOX = 88.dp
private val LIFT_GAP = 10.dp
private val WHEEL_BOX_PADDING = 6.dp

/** The number keys beside the weight wheel: [columns] by [rows], of which [shown] rows show at once, [width] with the box around them. */
private class KeyGrid(val columns: Int, val rows: Int, val shown: Int, val width: Dp)

/** The last seconds of a rest turn the bar amber, like the rest bar. */
private const val SOON_SECONDS = 10

/** A drag this far down puts the screen away, or a flick this fast (px/s). */
private val MinimiseDistance = 120.dp
private const val MINIMISE_VELOCITY = 1_500f

/** Below this height (a 360×640 phone, or any phone on its side) everything but the digits gets smaller. */
private val COMPACT_HEIGHT = 700.dp

/**
 * The running [rest] over the whole workout screen, with huge digits, and what corrects the set that
 * started it ([screen], of [log]): the weight wheel of the exercise and the reps of that set, as number
 * keys for a set with a target and as a wheel for a max-reps set. Above them
 * the chips that rate the set's reps in reserve, when it can be rated. It shows the time at [now], the
 * model's clock, which ticks every second while a rest runs and the app shows. The cues, the alarms and
 * the end of the rest come from `update`. A swipe down, the chevron or the back gesture put it away to
 * the rest bar; "Next set" ends the rest.
 *
 * Without a [rest] it is the "set done" screen after the last set of an exercise: no clock, and "Done",
 * a swipe down, the chevron or the back gesture close it.
 *
 * [shown] is false while the screen leaves (its exit animation still draws it). How far it is pulled is
 * a [RestPull], changed only by [restPullAfter].
 */
@Composable
internal fun RestTimerScreen(
    screen: RestScreen,
    rest: RestState?,
    log: ExerciseLog,
    settings: Settings,
    now: Long,
    dispatch: (Msg) -> Unit,
    modifier: Modifier = Modifier,
    shown: Boolean = true,
) {
    val minimiseAt = with(LocalDensity.current) { MinimiseDistance.toPx() }
    var pull by remember { mutableStateOf<RestPull>(RestPull.Still) }
    fun send(event: RestPullEvent) {
        val step = restPullAfter(pull, event)
        pull = step.pull
        if (step.minimise) dispatch(Msg.Rest.Minimise)
    }
    // Back over the workout while the exit still held it: in its place, and a drag starts from there.
    if (shown && pull is RestPull.Away) send(RestPullEvent.Shown)
    val returning = pull is RestPull.Returning
    LaunchedEffect(returning) {
        val from = (pull as? RestPull.Returning)?.offset ?: return@LaunchedEffect
        animate(from, 0f) { value, _ -> send(RestPullEvent.Frame(value)) }
        send(RestPullEvent.Settled)
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .offset { IntOffset(0, pull.offset.roundToInt()) }
            .background(EisenColors.Background)
            // Takes every touch, so nothing of the workout below reacts.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .draggable(
                rememberDraggableState { delta -> send(RestPullEvent.Drag(delta)) },
                Orientation.Vertical,
                onDragStopped = { velocity -> send(RestPullEvent.LetGo(velocity, minimiseAt, MINIMISE_VELOCITY)) },
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        val wide = maxWidth > maxHeight
        // A small phone or one on its side gets smaller text and lower wheel rows, so the digits keep their room.
        val rates = ratesSet(settings, log, screen.set)
        // The chips under the clock of a short phone leave the digits too little room, so lines that repeat go.
        val tight = rates && !wide && maxHeight < COMPACT_HEIGHT
        val parts = RestParts(screen, rest, log, settings, now, compact = wide || maxHeight < COMPACT_HEIGHT, tight, dispatch)
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    parts.Header()
                    if (rest != null) parts.Clock(rest, Modifier.weight(1f)) else parts.DoneMark(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    if (rates) parts.Reserve()
                    parts.Lift()
                    parts.Next()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                parts.Header()
                if (rest != null) parts.Clock(rest, Modifier.weight(1f)) else parts.DoneMark(Modifier.weight(1f))
                Spacer(Modifier.height(if (parts.compact) 8.dp else 12.dp))
                if (rates) parts.Reserve()
                parts.Lift()
                parts.Next()
            }
        }
    }
}

/** The pieces of the rest screen, laid out in a column on a phone and in two columns on its side. */
private class RestParts(
    val screen: RestScreen,
    val rest: RestState?,
    val log: ExerciseLog,
    val settings: Settings,
    val now: Long,
    val compact: Boolean,
    /** Without the line over the digits (its "Rest" joins "of 1:30") and without "Up next", which the header says. */
    val tight: Boolean,
    val dispatch: (Msg) -> Unit,
) {
    private val rowHeight = if (compact) 32.dp else 48.dp
    private val over = rest != null && now >= rest.endsAt

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
                Icon(Icons.ChevronDown, contentDescription = if (rest == null) "Close" else "Minimise rest timer", tint = EisenColors.MutedForeground)
            }
        }
    }

    /** "Rest" and the note, the digits as large as the room allows, the bar and the ±15s buttons. */
    @Composable
    fun Clock(rest: RestState, modifier: Modifier) {
        val remaining = restSecondsLeft(rest, now)
        val overtime = maxOf(0, floor((now - rest.endsAt) / 1000.0).toInt())
        val total = ((rest.endsAt - rest.startedAt) / 1000).toInt()
        Column(modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 12.dp)) {
            if (!tight) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text(
                    buildAnnotatedString {
                        if (tight) {
                            withStyle(SpanStyle(color = if (over) EisenColors.Warn else EisenColors.Foreground, fontWeight = FontWeight.ExtraBold)) {
                                append(if (over) "Rest over " else "Rest ")
                            }
                        }
                        append("of ${formatClock(total)}")
                    },
                    Modifier.weight(1f),
                    color = EisenColors.MutedForeground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val height = if (compact) 36.dp else 40.dp
                SmallButton("−${ADJUST_SECONDS}s", "Rest ${ADJUST_SECONDS} seconds less", height, enabled = !over) { dispatch(Msg.Rest.Shorten) }
                Spacer(Modifier.size(8.dp))
                SmallButton("+${ADJUST_SECONDS}s", "Rest ${ADJUST_SECONDS} seconds longer", height, enabled = true) { dispatch(Msg.Rest.Lengthen) }
            }
        }
    }

    /** Where the clock would be: a lime check, when there is room for it. */
    @Composable
    fun DoneMark(modifier: Modifier) {
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (maxHeight >= 96.dp) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(EisenColors.Primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Check, contentDescription = null, tint = EisenColors.OnPrimary, modifier = Modifier.size(44.dp))
                }
            }
        }
    }

    /**
     * "Reps in reserve": five chips in a row, 0 to "4+". One tap rates the set, a tap on the chosen one
     * takes the rating back. On the "set done" screen a rating closes it a moment later (`update`).
     */
    @Composable
    fun Reserve() {
        val haptics = rememberHaptics()
        val chosen = rirOf(log, screen.set)
        Column(Modifier.fillMaxWidth().padding(bottom = if (compact) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            // The chips' group carries the label for TalkBack.
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Reps in reserve",
                    Modifier.clearAndSetSemantics { },
                    color = EisenColors.Foreground,
                    fontSize = if (compact) 17.sp else 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text("How many more could you have done?", color = EisenColors.MutedForeground, fontSize = if (compact) 13.sp else 15.sp)
            }
            Row(
                Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Reps in reserve" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (rir in 0..MAX_RIR) {
                    val selected = rir == chosen
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) EisenColors.Primary else EisenColors.Muted)
                            .selectable(selected, role = Role.RadioButton) {
                                // Chosen ticks on, the chosen one tapped again takes the rating back.
                                haptics.toggle(!selected)
                                dispatch(Msg.Workout(WorkoutEvent.RateSet(screen.exercise, screen.set, if (selected) null else rir)))
                            }
                            .semantics { contentDescription = if (rir == MAX_RIR) "$MAX_RIR or more reps in reserve" else "$rir reps in reserve" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            rirText(rir),
                            color = if (selected) EisenColors.OnPrimary else EisenColors.Foreground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }

    /** "What did you lift?": the weight wheel, the reps (number keys, a wheel for a max-reps set, or the time held) and what they make. */
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
            // A set with a target gets number keys instead of the reps wheel; they take the width they need.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val target = log.targetReps.takeIf { !log.timed }
                val numbers = remember(target, count) { target?.let { pickableReps(maxOf(it, count)) } }
                val keys = numbers?.let { keyGrid(it.size, maxWidth - LIFT_GAP) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LIFT_GAP)) {
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
                    } else if (numbers != null && keys != null) {
                        WheelBox("reps", compact, Modifier.width(keys.width)) {
                            RepsKeys(numbers, count, keys)
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

    /**
     * How the number keys of a set with a target sit beside the weight wheel in [room]: never taller than
     * the wheel (two rows of 48 dp on a short phone, three otherwise), as wide as half the room when they
     * need no more, and the weight wheel keeps [MIN_WEIGHT_BOX]. Rows that still do not fit scroll.
     */
    private fun keyGrid(count: Int, room: Dp): KeyGrid {
        val shown = maxOf(1, ((rowHeight * 3) / KEY).toInt())
        val fit = maxOf(1, ((room - MIN_WEIGHT_BOX - WHEEL_BOX_PADDING * 2) / KEY).toInt())
        val half = ((room / 2 - WHEEL_BOX_PADDING * 2) / KEY).toInt()
        val columns = maxOf((count + shown - 1) / shown, half).coerceAtMost(minOf(fit, count)).coerceAtLeast(1)
        val rows = (count + columns - 1) / columns
        val even = (count + rows - 1) / rows
        return KeyGrid(even, rows, minOf(rows, shown), KEY * even + WHEEL_BOX_PADDING * 2)
    }

    /**
     * The counts of a set with a target as number keys, the logged one lime: one tap corrects the set.
     * Rows that do not fit scroll, by whole rows, with the logged count in view.
     */
    @Composable
    private fun RepsKeys(numbers: List<Int>, count: Int, grid: KeyGrid) {
        val haptics = rememberHaptics()
        val scroll = rememberScrollState()
        val keyRow = with(LocalDensity.current) { KEY.roundToPx() }
        LaunchedEffect(grid.columns) { scroll.scrollTo(minOf(numbers.indexOf(count).coerceAtLeast(0) / grid.columns, grid.rows - grid.shown) * keyRow) }
        // A scroll comes to rest on a whole row, so no row of keys is left cut in half.
        LaunchedEffect(scroll, keyRow) {
            snapshotFlow { scroll.isScrollInProgress }.filter { !it }.collect {
                val row = ((scroll.value + keyRow / 2) / keyRow * keyRow).coerceAtMost(scroll.maxValue)
                if (row != scroll.value) scroll.animateScrollTo(row)
            }
        }
        NumberRow(
            numbers = numbers,
            selected = count,
            onPick = {
                if (it != count) {
                    haptics.perform(Haptic.TOGGLE_ON)
                    dispatch(Msg.Workout(WorkoutEvent.CorrectReps(screen.exercise, screen.set, it)))
                }
            },
            label = "Reps",
            modifier = Modifier.height(KEY * grid.shown).verticalScroll(scroll),
            columns = grid.columns,
            gap = 0.dp,
            inset = KEY_INSET,
        )
    }

    /** "Up next" and the button that ends the rest. "Exercise done" and the button that closes the "set done" screen. */
    @Composable
    fun Next() {
        val next = log.sets.indexOfFirst { it == null }
        val gap = if (compact) 6.dp else 10.dp
        Column(Modifier.fillMaxWidth().padding(top = gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (rest == null || (next != -1 && !tight)) {
                Text(
                    if (rest == null) "Exercise done" else "Up next: set ${next + 1} of ${log.sets.size}",
                    Modifier.fillMaxWidth(),
                    color = EisenColors.MutedForeground,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = { dispatch(if (rest == null) Msg.Rest.Minimise else Msg.Rest.Dismiss) },
                modifier = Modifier.fillMaxWidth().height(if (compact) 50.dp else 60.dp),
                shapes = pressShapes(16.dp),
            ) {
                Text(if (rest == null) "Done" else "Next set", fontSize = if (compact) 18.sp else 20.sp, fontWeight = FontWeight.ExtraBold)
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

/**
 * What is left of the rest, as a wavy bar that drains. Empty and flat once the rest is over. Its
 * value follows the clock: the glide between two ticks of the model's clock is a tween as long as the
 * time to the next tick, so the bar is where the clock is on every frame.
 */
@Composable
private fun Drain(rest: RestState, now: Long, over: Boolean, color: Color) {
    val total = rest.endsAt - rest.startedAt
    fun leftAt(time: Long): Float = if (total <= 0) 0f else (1f - (time - rest.startedAt).toFloat() / total).coerceIn(0f, 1f)
    val left = remember { Animatable(if (over) 0f else leftAt(now)) }
    val motion = MaterialTheme.motionScheme
    // The clock moves a whole second at a time. In between, the bar glides to where it will be at the
    // next tick, like the rest bar.
    LaunchedEffect(rest, now) {
        if (over) {
            left.animateTo(0f, motion.fastSpatialSpec())
        } else {
            val untilTick = Math.floorMod(rest.endsAt - now, 1000L).takeIf { it > 0 } ?: 1000L
            left.animateTo(leftAt(now + untilTick), tween(untilTick.toInt(), easing = LinearEasing))
        }
    }
    ClockBar(progress = { left.value }, running = !over, color = color, description = "Rest left", thickness = 8.dp)
}

@Composable
private fun WheelBox(header: String, compact: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(EisenColors.Background)
            .border(1.dp, EisenColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = WHEEL_BOX_PADDING, vertical = if (compact) 4.dp else 6.dp),
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
