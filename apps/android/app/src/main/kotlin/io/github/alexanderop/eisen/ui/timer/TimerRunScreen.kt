package io.github.alexanderop.eisen.ui.timer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.OUTCOME_LABELS
import io.github.alexanderop.eisen.domain.Wod
import io.github.alexanderop.eisen.domain.bestText
import io.github.alexanderop.eisen.domain.wodBest
import io.github.alexanderop.eisen.domain.elm.RoundCard
import io.github.alexanderop.eisen.domain.elm.earlierRuns
import io.github.alexanderop.eisen.domain.elm.PaceLine
import io.github.alexanderop.eisen.domain.elm.PaceTone
import io.github.alexanderop.eisen.domain.elm.paceLine
import io.github.alexanderop.eisen.domain.elm.resultGap
import io.github.alexanderop.eisen.domain.elm.roundCard
import io.github.alexanderop.eisen.domain.elm.runRest
import io.github.alexanderop.eisen.domain.WodRest
import io.github.alexanderop.eisen.domain.wodRound
import androidx.compose.ui.graphics.graphicsLayer
import io.github.alexanderop.eisen.domain.elm.wodButton
import io.github.alexanderop.eisen.domain.elm.wasWodRecord
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.TimerOutcome
import io.github.alexanderop.eisen.domain.TimerPhase
import io.github.alexanderop.eisen.domain.TimerRun
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.TimerView
import io.github.alexanderop.eisen.domain.activeTime
import io.github.alexanderop.eisen.domain.countsRoundsByTap
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.timerOutcome
import io.github.alexanderop.eisen.domain.timerSummary
import io.github.alexanderop.eisen.domain.timerView
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.TimerState
import io.github.alexanderop.eisen.domain.elm.nextText
import io.github.alexanderop.eisen.domain.elm.phaseName
import io.github.alexanderop.eisen.domain.elm.roundText
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.findExercise
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.ExerciseMotion
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** The height of a line of the round card with its picture: the 40dp tile and a little air. */
private val PictureRow = 44.dp

/** The End sheet ignores taps this long after it opens. */
private const val END_SHEET_GUARD_MS = 600L

/** How long "Hold to finish" has to be held. */
private const val HOLD_TO_FINISH_MS = 700

/** The colour of a phase, told apart from across the room. A paused run is grey. */
internal fun phaseColor(phase: TimerPhase, paused: Boolean): Color = when {
    paused -> EisenColors.MutedForeground
    phase == TimerPhase.READY -> EisenColors.Warn
    phase == TimerPhase.REST -> EisenColors.Rest
    else -> EisenColors.Primary
}

private fun ceilSeconds(millis: Long): Int = ceil(millis / 1000.0).toInt()

/** The interval timer over everything: the run, or its result. */
@Composable
fun TimerOverlay(state: TimerState, app: AppState, now: Long, dispatch: (Msg) -> Unit) {
    when (state) {
        is TimerState.Running -> {
            val wod = state.run.wod
            when {
                wod != null -> WodRunScreen(state, wod, app, now, dispatch)
                state.run.config.mode == TimerMode.BOXING -> BoxingRunScreen(state, now, dispatch)
                else -> TimerRunScreen(state, now, dispatch)
            }
        }
        is TimerState.Finished -> TimerResultScreen(state.session, state.wod, app.timerSessions, undo = state.undo != null, combos = state.combos, dispatch)
    }
}

/**
 * A running interval timer at [now], the model's clock, which ticks every second while it runs. The
 * colour says the phase, the ring follows it and the digits are big enough for two metres away.
 * The screen stays on: the phone lies on the floor.
 */
@Composable
private fun TimerRunScreen(state: TimerState.Running, now: Long, dispatch: (Msg) -> Unit) {
    val run = state.run
    val paused = run.pausedAt != null
    val view = timerView(run.config, activeTime(run, now))
    val color by animateColorAsState(phaseColor(view.phase, paused), tween(400), label = "phase")
    val tapped = countsRoundsByTap(run.config.mode)
    val forTime = run.config.mode == TimerMode.FOR_TIME
    val round = roundText(run, view.round)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(EisenColors.Background)
            .keepScreenOn()
            // Takes every touch, so nothing below reacts.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        val compact = maxHeight < 600.dp
        val controls = if (compact) 56.dp else 64.dp
        // Where rounds are tapped, their button takes the lower third: it is hit with a sweaty thumb from above.
        val button = if (tapped) (maxHeight * if (compact) 0.19f else 0.26f).coerceIn(96.dp, 220.dp) else 0.dp
        val holdRow = if (forTime) 52.dp + 10.dp else 0.dp
        // The head, the phase word with its round, the lines below the ring, the controls and the gaps.
        val fixed = (if (compact) 150.dp else 210.dp) + (if (round != null) 30.dp else 0.dp) + controls + holdRow + (if (tapped) button + 28.dp else 0.dp)
        val ring = minOf(maxWidth * 0.84f, maxHeight - fixed).coerceAtLeast(130.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(
                    run.name,
                    Modifier.align(Alignment.Center).padding(horizontal = 96.dp).semantics { heading() },
                    color = EisenColors.MutedForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Small and out of the way of Pause: ending is never one stray tap.
                FilledTonalButton(
                    onClick = { dispatch(Msg.Timer.End) },
                    modifier = Modifier.align(Alignment.CenterEnd).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
                ) { Text("End", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val word = if (compact) 26.sp else 32.sp
                    Text(
                        if (paused) "PAUSED" else phaseName(view.phase).uppercase(),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = color,
                        fontSize = word,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.14.em,
                    )
                    if (round != null) {
                        Text(round, color = EisenColors.Foreground, fontSize = word * 0.6f, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
                    Dial(run, view, now, ring, color, paused)
                    Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
                    BelowRing(run, view, compact)
                }
            }
            if (tapped) {
                RoundCounter(run.taps, button, color, enabled = !paused && view.phase != TimerPhase.READY, dispatch)
                Spacer(Modifier.height(28.dp))
            }
            if (forTime) {
                HoldToFinish(enabled = view.phase != TimerPhase.READY) { dispatch(Msg.Timer.Finish) }
                Spacer(Modifier.height(10.dp))
            }
            PauseButton(paused, controls, dispatch)
        }
    }
    if (state.ending) EndSheet(dispatch)
}

/**
 * A run of a WOD: the timer as above, with the round card under the ring that says what to do now,
 * and one big button: rounds counted in an AMRAP, the next step of a For Time, whose last tap stops
 * the clock. The clock comes first: the button and the paddings shrink before the ring does.
 */
@Composable
private fun WodRunScreen(state: TimerState.Running, wod: Wod, app: AppState, now: Long, dispatch: (Msg) -> Unit) {
    val run = state.run
    val paused = run.pausedAt != null
    val view = timerView(run.config, activeTime(run, now))
    // A rest of a For Time runs by itself: the screen is the rest's, in its colour.
    val rest = runRest(run, view)
    val color by animateColorAsState(if (rest != null && !paused) EisenColors.Rest else phaseColor(view.phase, paused), tween(400), label = "phase")
    val forTime = run.config.mode == TimerMode.FOR_TIME
    val tapped = forTime || run.config.mode == TimerMode.AMRAP
    val card = roundCard(run, view, app)
    val pace = paceLine(run, wodBest(app.timerSessions, wod.id), view)
    // "WORK · 2/4": the round, read from two metres. A rest names the round that comes in its card.
    val round = wodRound(wod, run.taps, 1)
    val rounds = forTime && wod.rounds > 1 && view.phase != TimerPhase.READY && rest == null
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(EisenColors.Background)
            .keepScreenOn()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        val compact = maxHeight < 600.dp
        val controls = if (compact) 52.dp else 64.dp
        // The big button never shrinks below a thumb and keeps its distance from Pause: the ring gives way.
        val button = if (compact) 80.dp else (maxHeight * 0.14f).coerceIn(88.dp, 128.dp)
        val gap = if (compact) 8.dp else 12.dp
        // A short screen keeps the room for the clock: its round card goes without pictures.
        val pictures = maxHeight >= 700.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(
                    run.name,
                    Modifier.align(Alignment.Center).padding(horizontal = 96.dp).semantics { heading() },
                    color = EisenColors.MutedForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(
                    onClick = { dispatch(Msg.Timer.End) },
                    modifier = Modifier.align(Alignment.CenterEnd).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
                ) { Text("End", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
            PhaseWord(
                when {
                    paused -> "PAUSED"
                    rest != null -> "REST"
                    else -> phaseName(view.phase).uppercase()
                },
                round = if (rounds) "$round/${wod.rounds}" else null,
                color = color,
                size = if (compact) 22.sp else 28.sp,
            )
            // The ring and the time left stay together in the room the card and the buttons leave.
            // A For Time shows its cap in the ring, so it has no "Total left".
            val total = !forTime && (run.config.mode != TimerMode.AMRAP || view.phase == TimerPhase.READY) && view.totalLeft != null
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                val ring = minOf(maxWidth * 0.9f, maxHeight - if (total) 30.dp else 0.dp).coerceAtLeast(0.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Dial(run, view, now, ring, color, paused, minDigits = 64f, rest = rest)
                    if (total) BelowRing(run, view, compact, next = false)
                }
            }
            if (pace != null) {
                Spacer(Modifier.height(gap / 2))
                PaceRow(pace)
            }
            Spacer(Modifier.height(gap))
            if (card != null) WodRoundCard(card, color, compact, pictures = pictures, playing = !paused) { app.findExercise(it) }
            Spacer(Modifier.height(gap))
            if (tapped) {
                if (rest != null) SkipRest(button, enabled = !paused, dispatch)
                else WodButton(wod, run.taps, app, button, color, enabled = !paused && view.phase != TimerPhase.READY, dispatch)
                Spacer(Modifier.height(24.dp))
            }
            PauseButton(paused, controls, dispatch)
        }
    }
    if (state.ending) EndSheet(dispatch, wod = true)
}

/** Pause in the dark tonal style, so the big button above it is the one thing in colour; Resume is lime. */
@Composable
private fun PauseButton(paused: Boolean, height: Dp, dispatch: (Msg) -> Unit) {
    FilledTonalButton(
        onClick = { dispatch(if (paused) Msg.Timer.Resume else Msg.Timer.Pause) },
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).height(height),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (paused) EisenColors.Primary else EisenColors.Muted,
            contentColor = if (paused) EisenColors.OnPrimary else EisenColors.Foreground,
        ),
    ) { Text(if (paused) "Resume" else "Pause", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
}

/**
 * The phase word, and with several rounds the round beside it in the foreground colour: "WORK · 2/4".
 * It pulses once when the round moves on.
 */
@Composable
private fun PhaseWord(word: String, round: String?, color: Color, size: androidx.compose.ui.unit.TextUnit) {
    val pulse = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(round) }
    LaunchedEffect(round) {
        if (round != null && seen != null && round != seen) {
            pulse.animateTo(1.18f, tween(140))
            pulse.animateTo(1f, tween(260))
        }
        seen = round
    }
    Text(
        buildAnnotatedString {
            append(word)
            if (round != null) {
                append(" · ")
                withStyle(SpanStyle(color = EisenColors.Foreground)) { append(round) }
            }
        },
        Modifier
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (round != null) contentDescription = "$word, round ${round.replace("/", " of ")}"
            },
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.14.em,
        maxLines = 1,
    )
}

/**
 * In a rest the big button lets it end early, in the quiet style: the clock counts the rest anyway.
 * "Back" stays beside it: the tap that started the rest may have been one too many.
 */
@Composable
private fun SkipRest(height: Dp, enabled: Boolean, dispatch: (Msg) -> Unit) {
    Row(Modifier.fillMaxWidth().widthIn(max = 520.dp).height(height), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = { dispatch(Msg.Timer.Untap) },
            modifier = Modifier.width(64.dp).height(minOf(height, 56.dp).coerceAtLeast(48.dp)),
            shape = RoundedCornerShape(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        ) {
            Text("Back", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false, modifier = Modifier.semantics { contentDescription = "Take back a step" })
        }
        FilledTonalButton(
            onClick = { dispatch(Msg.Timer.SkipRest) },
            enabled = enabled,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        ) { Text("Skip rest", fontWeight = FontWeight.Black, fontSize = 24.sp) }
    }
}

/**
 * What to do now, big and heavy enough to read from the floor. The heading, centred in the phase
 * colour, is the one count of the round. Ticking items off, the done ones are dimmed with a check and
 * the card scrolls to keep the current one in sight. More than four lines scroll inside the card.
 * With [pictures] each line has its exercise moving at its left; ticking items off, only the current one moves.
 */
@Composable
private fun WodRoundCard(card: RoundCard, color: Color, compact: Boolean, pictures: Boolean, playing: Boolean, find: (String) -> Exercise?) {
    val size = if (compact) 20.sp else 25.sp
    val line = if (compact) 25.sp else 31.sp
    val big = if (compact) 23.sp else 28.sp
    val bigLine = if (compact) 29.sp else 34.sp
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val current = card.current
    val row = with(density) { if (pictures) maxOf(line.toDp(), PictureRow) else line.toDp() }
    val bigRow = with(density) { if (pictures) maxOf(bigLine.toDp(), PictureRow) else bigLine.toDp() }
    // The line before the current one stays in sight above it, so the lifter sees where they came from.
    // A rest's card of the coming round starts at its top.
    LaunchedEffect(current, row, card.upNext) {
        if (current != null) scroll.animateScrollTo(with(density) { (row.toPx() * (current - 1).coerceAtLeast(0)).toInt() })
        else if (card.upNext) scroll.animateScrollTo(0)
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .clip(shape)
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, shape)
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        card.heading?.let {
            Text(
                it,
                Modifier.fillMaxWidth(),
                color = if (card.upNext) EisenColors.Rest else color,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth()) {
            // A short screen keeps the room for the clock: three lines in sight, the rest scrolls.
            Column(Modifier.heightIn(max = row * (if (compact) 2 else 3) + bigRow).verticalScroll(scroll)) {
                card.lines.forEachIndexed { index, text ->
                    val done = current != null && index < current
                    val now = current == index
                    Row(Modifier.heightIn(min = if (pictures) PictureRow else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (current != null) {
                            Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                                if (done) Icon(Icons.Check, contentDescription = "Done", tint = EisenColors.Primary, modifier = Modifier.size(20.dp))
                                if (now) Box(Modifier.size(width = 5.dp, height = 22.dp).clip(RoundedCornerShape(3.dp)).background(color))
                            }
                        }
                        if (pictures) {
                            ExerciseMotion(card.exercises.getOrNull(index)?.let(find), size = 40.dp, playing = playing && (current == null || now))
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text,
                            color = when {
                                card.upNext -> EisenColors.Rest
                                done -> EisenColors.MutedForeground
                                else -> EisenColors.Foreground
                            },
                            fontSize = if (now) big else size,
                            lineHeight = if (now) bigLine else line,
                            fontWeight = if (done) FontWeight.Bold else FontWeight.Black,
                            // Ticking off, the lines stay one each, but the one to do now is read whole.
                            maxLines = if (current != null && !now) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // More lines than fit: an arrow says there is more to scroll to.
            if (scroll.canScrollForward) {
                Icon(
                    Icons.ChevronDown,
                    contentDescription = null,
                    tint = EisenColors.Primary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).background(EisenColors.Muted, CircleShape).padding(4.dp),
                )
            }
        }
    }
}

/**
 * The colours of the distance to the best: lime ahead, amber behind, plain on pace.
 * The first run, with nothing to beat, is muted.
 */
private fun paceColor(tone: PaceTone): Color = when (tone) {
    PaceTone.AHEAD -> EisenColors.Primary
    PaceTone.BEHIND -> EisenColors.Warn
    PaceTone.LEVEL -> EisenColors.Foreground
}

/**
 * "Best 12:40" (muted) with the live distance to it in a chip: "▲ 12s ahead", "▼ 8s behind", "On pace",
 * so the colour is not the only signal. Where the chip does not fit beside the best, it goes on its own
 * line under it. Not a live region: it changes every second.
 */
@Composable
private fun PaceRow(pace: PaceLine) {
    val style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")
    val gap = pace.gap
    val tone = paceColor(pace.tone)
    val arrow = when (pace.tone) {
        PaceTone.AHEAD -> "\u25B2 "
        PaceTone.BEHIND -> "\u25BC "
        PaceTone.LEVEL -> ""
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val oneLine = gap == null || with(density) {
            val text = measurer.measure(pace.best, style).size.width + measurer.measure(arrow + gap, style).size.width
            text.toDp() + 10.dp + 28.dp <= maxWidth
        }
        val best: @Composable () -> Unit = { Text(pace.best, style = style, color = EisenColors.MutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        val chip: @Composable () -> Unit = {
            if (gap != null) {
                Box(
                    Modifier
                        .background(if (pace.tone == PaceTone.LEVEL) EisenColors.Muted else tone.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 3.dp)
                        .semantics { contentDescription = gap },
                ) { Text(arrow + gap, style = style, color = tone, maxLines = 1) }
            }
        }
        if (oneLine) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            best()
            chip()
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            best()
            chip()
        }
    }
}

/**
 * The one big button of a WOD's run, in the phase colour, with a small button beside it that takes a
 * tap back. An AMRAP counts its rounds ("−1" beside it). A For Time moves on item by item: "Next"
 * with the item to do now, "Done" with the very last, whose tap stops the clock.
 */
@Composable
private fun WodButton(wod: Wod, taps: Int, app: AppState, height: Dp, color: Color, enabled: Boolean, dispatch: (Msg) -> Unit) {
    val amrap = wod.timer.mode == TimerMode.AMRAP
    val (title, label, spoken) = if (amrap) {
        Triple(taps.toString(), "rounds done", "$taps ${if (taps == 1) "round" else "rounds"} done. Tap to count a round.")
    } else {
        val next = wodButton(wod, taps, app)
        Triple(next.big, next.small, next.spoken)
    }
    Row(Modifier.fillMaxWidth().widthIn(max = 520.dp).height(height), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Taking a tap back sits on the left, away from the thumb that taps the big button.
        FilledTonalButton(
            onClick = { dispatch(Msg.Timer.Untap) },
            enabled = taps > 0,
            modifier = Modifier.width(64.dp).height(minOf(height, 56.dp).coerceAtLeast(48.dp)),
            shape = RoundedCornerShape(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        ) {
            Text(
                if (amrap) "−1" else "Back",
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (amrap) 18.sp else 15.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { contentDescription = if (amrap) "Take back a round" else "Take back a step" },
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(if (enabled) color else EisenColors.Muted)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (amrap) "Count a round" else title) { dispatch(Msg.Timer.Tap) }
                .semantics { contentDescription = spoken }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            val ink = if (enabled) EisenColors.OnPrimary else EisenColors.MutedForeground
            if (amrap) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = ink,
                    fontSize = (height.value * 0.5f).coerceAtMost(56f).sp,
                    lineHeight = (height.value * 0.56f).coerceAtMost(62f).sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.width(12.dp))
                Text(label, color = ink.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // What the tap ticks off, and under it what comes next.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = ink, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(label, color = ink.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * How full the ring is at [time]: what is left of the phase. A For Time with a cap counts up, so its
 * ring grows towards the cap; an open one fills once a minute.
 */
private fun ringAt(run: TimerRun, time: Long): Float {
    val view = timerView(run.config, activeTime(run, time))
    // In a rest the ring empties over the rest.
    runRest(run, view)?.let { rest ->
        val left = run.splits.last() + rest.total - view.elapsed / 1000.0
        return (left / rest.total).toFloat().coerceIn(0f, 1f)
    }
    val left = view.phaseLeft
    val total = view.phaseTotal
    val fraction = when {
        view.phase == TimerPhase.READY && left != null && total != null -> left.toFloat() / total
        run.config.mode == TimerMode.FOR_TIME && run.config.work > 0 -> view.elapsed.toFloat() / (run.config.work * 1000L)
        left != null && total != null && total > 0 -> left.toFloat() / total
        else -> (view.elapsed % 60_000L) / 60_000f
    }
    return fraction.coerceIn(0f, 1f)
}

@Composable
internal fun Dial(run: TimerRun, view: TimerView, now: Long, size: Dp, color: Color, paused: Boolean, minDigits: Float = 0f, rest: WodRest? = null) {
    val anchor = run.startedAt + run.paused
    val resting = rest != null
    val ring = remember(run.id, view.phase, view.round, resting) { Animatable(ringAt(run, now)) }
    // The clock moves a second at a time. In between the ring glides to where it will be at the next
    // tick, like the hold timer. Pure presentation: the digits follow [now].
    LaunchedEffect(run.id, view.phase, view.round, now, paused, resting) {
        if (paused) {
            ring.snapTo(ringAt(run, now))
            return@LaunchedEffect
        }
        val untilTick = Math.floorMod(anchor - now, 1000L).takeIf { it > 0 } ?: 1000L
        val next = ringAt(run, now + untilTick)
        if (kotlin.math.abs(next - ring.value) > 0.5f) ring.snapTo(next) else ring.animateTo(next, tween(untilTick.toInt(), easing = LinearEasing))
    }
    // Paused, the digits pulse but stay readable: never below 60 %.
    val blink = rememberInfiniteTransition(label = "paused")
    val dim by blink.animateFloat(1f, 0.6f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dim")

    val left = view.phaseLeft
    val forTime = run.config.mode == TimerMode.FOR_TIME
    val clock = formatClock((view.elapsed / 1000).toInt())
    val digits = when {
        view.phase == TimerPhase.READY -> ceilSeconds(left ?: 0).toString()
        rest != null -> formatClock(rest.left)
        forTime -> clock
        else -> formatClock(ceilSeconds(left ?: 0))
    }
    val cap = "Cap ${holdText(run.config.work)}".takeIf { run.config.work > 0 }
    val caption = when {
        // The rest has the big digits; the WOD's own clock goes on underneath.
        rest != null -> listOfNotNull(clock, cap).joinToString(" · ")
        view.phase != TimerPhase.READY && forTime -> cap
        else -> null
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // A soft glow of the phase colour behind the ring, so the phase reads from across the room.
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), radius = this.size.minDimension * 0.62f),
                radius = this.size.minDimension * 0.62f,
            )
            val width = this.size.minDimension * 0.075f
            val inset = width / 2
            drawCircle(EisenColors.Muted, radius = (this.size.minDimension - width) / 2, style = Stroke(width))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * ring.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - width, this.size.height - width),
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                digits,
                Modifier.alpha(if (paused) dim else 1f),
                color = EisenColors.Foreground,
                fontSize = maxOf(minDigits, size.value * if (digits.length <= 2) 0.44f else if (digits.length <= 4) 0.3f else 0.25f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.03).em,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
            // On a small ring the line under the digits stays inside it.
            if (caption != null) Text(caption, color = EisenColors.MutedForeground, fontSize = if (size < 220.dp) 13.sp else 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun BelowRing(run: TimerRun, view: TimerView, compact: Boolean, next: Boolean = true) {
    // In AMRAP the ring already counts the whole timer down.
    val total = view.totalLeft.takeIf { run.config.mode != TimerMode.AMRAP || view.phase == TimerPhase.READY }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total != null) {
            Text(
                "Total left ${formatClock(ceilSeconds(total))}",
                color = EisenColors.Foreground,
                fontSize = if (compact) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        nextText(run, view)?.takeIf { next }?.let {
            Text(it, color = EisenColors.MutedForeground, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * AMRAP and For Time: the count is the big thing, read from two metres. The whole block is the "+1"
 * (in the phase colour), with a small "−1" beside it for a round counted by mistake.
 */
@Composable
private fun RoundCounter(taps: Int, height: Dp, color: Color, enabled: Boolean, dispatch: (Msg) -> Unit) {
    val compact = height < 140.dp
    Row(Modifier.fillMaxWidth().widthIn(max = 520.dp).height(height), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(if (enabled) color else EisenColors.Muted)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Count a round") { dispatch(Msg.Timer.Tap) }
                .semantics { contentDescription = "$taps ${if (taps == 1) "round" else "rounds"}. Tap to count a round." },
            contentAlignment = Alignment.Center,
        ) {
            val ink = if (enabled) EisenColors.OnPrimary else EisenColors.MutedForeground
            if (height < 110.dp) {
                // Low on a small phone: the count beside its words instead of above them.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        taps.toString(),
                        color = ink,
                        fontSize = (height.value * 0.5f).sp,
                        lineHeight = (height.value * 0.54f).sp,
                        fontWeight = FontWeight.Black,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (taps == 1) "round" else "rounds", color = ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("+1 · tap", color = ink.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    taps.toString(),
                    color = ink,
                    fontSize = (height.value * if (compact) 0.5f else 0.46f).sp,
                    lineHeight = (height.value * if (compact) 0.52f else 0.48f).sp,
                    fontWeight = FontWeight.Black,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(if (taps == 1) "round" else "rounds", color = ink, fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("+1 · tap", color = ink.copy(alpha = 0.7f), fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        FilledTonalButton(
            onClick = { dispatch(Msg.Timer.Untap) },
            enabled = taps > 0,
            modifier = Modifier.width(64.dp).height(56.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        ) {
            Text("−1", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, softWrap = false, modifier = Modifier.semantics { contentDescription = "Take back a round" })
        }
    }
}

/**
 * For Time's "Finish": it ends the run as completed, so it has to be held for a moment and fills
 * while it is. A short tap only says so. TalkBack's double tap finishes at once: there is no way to hold there.
 */
@Composable
private fun HoldToFinish(enabled: Boolean, onFinish: () -> Unit) {
    val fill = remember { Animatable(0f) }
    var hint by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinish)
    val scope = rememberCoroutineScope()
    LaunchedEffect(hint) {
        if (hint) {
            delay(1800)
            hint = false
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .height(52.dp)
            .clip(shape)
            .background(EisenColors.Muted)
            .border(1.dp, if (enabled) EisenColors.Primary.copy(alpha = 0.5f) else EisenColors.Border, shape)
            .semantics {
                role = Role.Button
                if (enabled) onClick(label = "Finish") { finish(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    val held = scope.launch { fill.animateTo(1f, tween(HOLD_TO_FINISH_MS, easing = LinearEasing)) }
                    val released = tryAwaitRelease()
                    if (fill.value >= 1f) {
                        finish()
                    } else {
                        held.cancel()
                        if (released) hint = true
                        scope.launch { fill.animateTo(0f, tween(150)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(fill.value).background(EisenColors.Primary.copy(alpha = 0.35f)))
        Text(
            if (hint) "Keep holding to finish" else "Hold to finish",
            color = if (enabled) EisenColors.Primary else EisenColors.MutedForeground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
        )
    }
}

/** "End timer?", or "End WOD?": going on is the big answer, ending takes a deliberate second tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EndSheet(dispatch: (Msg) -> Unit, wod: Boolean = false) {
    // The thumb that tapped End may tap again where an answer now is, or on the scrim: the sheet waits a moment.
    var ready by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { ready || it != SheetValue.Hidden })
    LaunchedEffect(Unit) {
        delay(END_SHEET_GUARD_MS)
        ready = true
    }
    fun answer(msg: Msg) {
        if (ready) dispatch(msg)
    }
    ModalBottomSheet(onDismissRequest = { dispatch(Msg.Timer.KeepGoing) }, sheetState = sheet, containerColor = EisenColors.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (wod) "End WOD?" else "End timer?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
            Text("Save what you did so far to your history, or discard it.", fontSize = 15.sp, color = EisenColors.MutedForeground)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { answer(Msg.Timer.KeepGoing) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Keep going", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            FilledTonalButton(
                onClick = { answer(Msg.Timer.SaveAndEnd) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
            ) { Text("Save and end", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            TextButton(
                onClick = { answer(Msg.Timer.Discard) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = EisenColors.Destructive),
            ) { Text("Discard", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        }
    }
}

/** The colour of how a timer ended: lime when completed, amber when stopped early or at its cap. */
internal fun outcomeColor(outcome: TimerOutcome): Color = if (outcome == TimerOutcome.COMPLETED) EisenColors.Primary else EisenColors.Warn

/**
 * After the timer ran out, "Finish" or "Save and end". Where rounds are tapped they are the result,
 * and can be corrected here: a round missed or counted twice changes the saved session.
 */
@Composable
private fun TimerResultScreen(session: TimerSession, wod: Wod?, sessions: List<TimerSession>, undo: Boolean, combos: Int?, dispatch: (Msg) -> Unit) {
    // A For Time WOD is about its time: that is the big number, the rounds are a line below it.
    val wodForTime = wod != null && session.config.mode == TimerMode.FOR_TIME
    val tapped = countsRoundsByTap(session.config.mode) && !wodForTime
    val wodId = session.wodId
    val best = wodId?.let { wodBest(earlierRuns(sessions, session), it) }
    val against = resultGap(session, best)
    val record = wodId != null && wasWodRecord(sessions, session)
    val outcome = timerOutcome(session)
    val accent = outcomeColor(outcome)
    val rounds = "${session.rounds} ${if (session.rounds == 1) "round" else "rounds"}"
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(EisenColors.Background)
            .keepScreenOn()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
    ) {
        val hero = if (maxHeight < 560.dp) 80.sp else 104.sp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The last tap may have been one too many: it can be taken back while the result shows. Up
            // here, away from where the big button was, so a second tap of the same thumb lands on nothing.
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (undo) {
                    TextButton(
                        onClick = { dispatch(Msg.Timer.UndoFinish) },
                        modifier = Modifier.align(Alignment.CenterStart).heightIn(min = 48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    ) { Text("Not finished? Undo", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // A name as the lifter wrote it: "AMRAP 30s" is not "AMRAP 30S".
                Text(
                    session.name,
                    color = EisenColors.Foreground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                // The timer described once: "Tabata · 3 × 5s / 5s", a WOD by its format: "21-15-9 · For Time".
                val summary = timerSummary(session.config)
                Text(
                    when {
                        wod != null -> io.github.alexanderop.eisen.domain.wodFormat(wod).takeIf { it != session.name } ?: timerSummary(session.config)
                        session.name == session.config.mode.title -> summary
                        else -> "${session.config.mode.title} · $summary"
                    },
                    color = EisenColors.MutedForeground,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(16.dp))
                if (tapped) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Adjust("−", "One round less", enabled = session.rounds > 0) { dispatch(Msg.Timer.CorrectRounds(-1)) }
                        Text(
                            session.rounds.toString(),
                            Modifier.widthIn(min = 96.dp).semantics { heading() },
                            color = EisenColors.Foreground,
                            fontSize = hero,
                            lineHeight = hero,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Adjust("+", "One round more", enabled = true) { dispatch(Msg.Timer.CorrectRounds(1)) }
                    }
                    Text(if (session.rounds == 1) "round" else "rounds", color = EisenColors.Foreground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(formatClock(session.seconds), color = EisenColors.MutedForeground, fontSize = 26.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
                } else {
                    Text(
                        formatClock(session.seconds),
                        Modifier.semantics { heading() },
                        color = EisenColors.Foreground,
                        fontSize = hero,
                        lineHeight = hero,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.03).em,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                    when {
                        !wodForTime -> Text(rounds, color = EisenColors.Foreground, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        wod!!.rounds > 1 -> Text("${session.rounds} of ${wod.rounds} rounds", color = EisenColors.Foreground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    if (combos != null) BoxingResultLines(session.config, combos)
                }
                if (against != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        against.text,
                        color = if (against.better) EisenColors.Primary else EisenColors.MutedForeground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                }
                // The best before this run, so a record shows what it beat.
                if (best != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Previous best ${bestText(best)}", color = EisenColors.MutedForeground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.background(accent.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(OUTCOME_LABELS.getValue(outcome), color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    if (record) {
                        Box(Modifier.background(EisenColors.Primary, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("New record", color = EisenColors.OnPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { dispatch(Msg.Timer.Again) },
                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
                ) { Text("Again", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                Button(
                    onClick = { dispatch(Msg.Timer.Done) },
                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            }
            // A run that was a test or went wrong is not a result: it leaves the history, with Undo.
            TextButton(
                onClick = { dispatch(Msg.Timer.DontCount) },
                modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = EisenColors.MutedForeground),
            ) { Text("Don't count this run", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun Adjust(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp).semantics { contentDescription = description },
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
    ) { Text(symbol, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
}
