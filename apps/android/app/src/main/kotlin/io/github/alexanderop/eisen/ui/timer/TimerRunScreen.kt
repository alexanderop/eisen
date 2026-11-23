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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.OUTCOME_LABELS
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
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** How long "Hold to finish" has to be held. */
private const val HOLD_TO_FINISH_MS = 700

/** The colour of a phase, told apart from across the room. A paused run is grey. */
private fun phaseColor(phase: TimerPhase, paused: Boolean): Color = when {
    paused -> EisenColors.MutedForeground
    phase == TimerPhase.READY -> EisenColors.Warn
    phase == TimerPhase.REST -> EisenColors.Rest
    else -> EisenColors.Primary
}

private fun ceilSeconds(millis: Long): Int = ceil(millis / 1000.0).toInt()

/** The interval timer over everything: the run, or its result. */
@Composable
fun TimerOverlay(state: TimerState, now: Long, dispatch: (Msg) -> Unit) {
    when (state) {
        is TimerState.Running -> TimerRunScreen(state, now, dispatch)
        is TimerState.Finished -> TimerResultScreen(state.session, dispatch)
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
            Button(
                onClick = { dispatch(if (paused) Msg.Timer.Resume else Msg.Timer.Pause) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).height(controls),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (paused) EisenColors.Primary else EisenColors.Foreground, contentColor = EisenColors.OnPrimary),
            ) { Text(if (paused) "Resume" else "Pause", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
        }
    }
    if (state.ending) EndSheet(dispatch)
}

/**
 * How full the ring is at [time]: what is left of the phase. A For Time with a cap counts up, so its
 * ring grows towards the cap; an open one fills once a minute.
 */
private fun ringAt(run: TimerRun, time: Long): Float {
    val view = timerView(run.config, activeTime(run, time))
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
private fun Dial(run: TimerRun, view: TimerView, now: Long, size: Dp, color: Color, paused: Boolean) {
    val anchor = run.startedAt + run.paused
    val ring = remember(run.id, view.phase, view.round) { Animatable(ringAt(run, now)) }
    // The clock moves a second at a time. In between the ring glides to where it will be at the next
    // tick, like the hold timer. Pure presentation: the digits follow [now].
    LaunchedEffect(run.id, view.phase, view.round, now, paused) {
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
    val digits = when {
        view.phase == TimerPhase.READY -> ceilSeconds(left ?: 0).toString()
        forTime -> formatClock((view.elapsed / 1000).toInt())
        else -> formatClock(ceilSeconds(left ?: 0))
    }
    val caption = if (view.phase != TimerPhase.READY && forTime && run.config.work > 0) "Cap ${holdText(run.config.work)}" else null
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
                fontSize = (size.value * if (digits.length <= 2) 0.44f else if (digits.length <= 4) 0.3f else 0.25f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.03).em,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
            if (caption != null) Text(caption, color = EisenColors.MutedForeground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BelowRing(run: TimerRun, view: TimerView, compact: Boolean) {
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
        nextText(run, view)?.let {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

/** "End timer?": going on is the big answer, ending takes a deliberate second tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndSheet(dispatch: (Msg) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { dispatch(Msg.Timer.KeepGoing) }, sheetState = sheet, containerColor = EisenColors.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("End timer?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
            Text("Save what you did so far to your history, or discard it.", fontSize = 15.sp, color = EisenColors.MutedForeground)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { dispatch(Msg.Timer.KeepGoing) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Keep going", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            FilledTonalButton(
                onClick = { dispatch(Msg.Timer.SaveAndEnd) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
            ) { Text("Save and end", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            TextButton(
                onClick = { dispatch(Msg.Timer.Discard) },
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
private fun TimerResultScreen(session: TimerSession, dispatch: (Msg) -> Unit) {
    val tapped = countsRoundsByTap(session.config.mode)
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
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    session.name.uppercase(),
                    color = EisenColors.MutedForeground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.14.em,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                // The timer described once: "Tabata · 3 × 5s / 5s".
                val summary = timerSummary(session.config)
                Text(
                    if (session.name == session.config.mode.title) summary else "${session.config.mode.title} · $summary",
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
                    Text(rounds, color = EisenColors.Foreground, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.background(accent.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(OUTCOME_LABELS.getValue(outcome), color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
