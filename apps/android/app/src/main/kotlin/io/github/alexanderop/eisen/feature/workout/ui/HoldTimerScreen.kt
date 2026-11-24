package io.github.alexanderop.eisen.feature.workout.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.HOLD_LEAD_IN_SECONDS
import io.github.alexanderop.eisen.core.holdText
import io.github.alexanderop.eisen.app.elm.HoldPhase
import io.github.alexanderop.eisen.app.elm.HoldTimer
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.holdEndsAt
import io.github.alexanderop.eisen.app.elm.holdPhase
import io.github.alexanderop.eisen.app.elm.holdSecondsShown
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.formatClock

private const val MINUTE_MS = 60_000L

/**
 * The hold timer of a timed set, over the whole workout screen. It shows [hold] at [now], the model's
 * clock, which ticks every second while the timer is open: "Get ready" counts down the lead-in (a tap
 * on the ring skips it), then the hold counts down from its target with a ring that empties, or counts
 * up when the set is held as long as possible. Logging, the cues and the end of the timer come from
 * `update`. The screen stays on while it shows: the phone lies on the floor during a plank.
 */
@Composable
internal fun HoldTimerScreen(hold: HoldTimer, log: ExerciseLog, now: Long, dispatch: (Msg) -> Unit, modifier: Modifier = Modifier) {
    val phase = holdPhase(hold, now)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(EisenColors.Background)
            .keepScreenOn()
            // Takes every touch, so nothing of the workout below reacts.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
    ) {
        val wide = maxWidth > maxHeight
        if (wide) {
            val ring = minOf(maxHeight, maxWidth * 0.5f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Dial(hold, phase, now, ring, dispatch) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Title(hold, log, phase)
                    Buttons(hold, phase, dispatch)
                }
            }
        } else {
            val ring = minOf(maxWidth, maxHeight - 300.dp).coerceAtLeast(180.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Title(hold, log, phase)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Dial(hold, phase, now, ring, dispatch) }
                Buttons(hold, phase, dispatch)
            }
        }
    }
}

@Composable
private fun Title(hold: HoldTimer, log: ExerciseLog, phase: HoldPhase) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            log.name,
            Modifier.semantics { heading() },
            color = EisenColors.Foreground,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Set ${hold.set + 1} of ${log.sets.size}", color = EisenColors.MutedForeground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (phase == HoldPhase.LEAD_IN) "GET READY" else "HOLD",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (phase == HoldPhase.LEAD_IN) EisenColors.Warn else EisenColors.Primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.12.em,
        )
    }
}

/** How full the ring is at [time]: what is left of the lead-in or the countdown, or the minute held so far. */
private fun ringAt(hold: HoldTimer, phase: HoldPhase, time: Long): Float {
    val fraction = when {
        phase == HoldPhase.LEAD_IN -> (hold.startsAt - time).toFloat() / (HOLD_LEAD_IN_SECONDS * 1000L)
        hold.target != null -> (holdEndsAt(hold) - time).toFloat() / (hold.target * 1000L)
        else -> Math.floorMod(time - hold.startsAt, MINUTE_MS).toFloat() / MINUTE_MS
    }
    return fraction.coerceIn(0f, 1f)
}

/** The ring with the time in it. During the lead-in a tap on it starts the hold. */
@Composable
private fun Dial(hold: HoldTimer, phase: HoldPhase, now: Long, size: Dp, dispatch: (Msg) -> Unit) {
    val color = if (phase == HoldPhase.LEAD_IN) EisenColors.Warn else EisenColors.Primary
    val ring = remember(hold.startsAt, phase) { Animatable(ringAt(hold, phase, now)) }
    // The clock moves a whole second at a time. In between, the ring glides to where it will be at the
    // next tick, like the rest bar. Pure presentation: the digits follow [now].
    LaunchedEffect(hold.startsAt, phase, now) {
        val untilTick = Math.floorMod(hold.startsAt - now, 1000L).takeIf { it > 0 } ?: 1000L
        val next = ringAt(hold, phase, now + untilTick)
        if (next < ring.value && hold.target == null && phase == HoldPhase.HOLD) ring.snapTo(0f)
        ring.animateTo(next, tween(untilTick.toInt(), easing = LinearEasing))
    }
    val seconds = holdSecondsShown(hold, now)
    val digits = if (phase == HoldPhase.LEAD_IN) seconds.toString() else formatClock(seconds)
    val caption = when {
        phase == HoldPhase.LEAD_IN -> "Tap to start now"
        hold.target != null -> "of ${holdText(hold.target)}"
        else -> "Max hold"
    }
    val tap = if (phase == HoldPhase.LEAD_IN) {
        Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Start the hold now") { dispatch(Msg.Hold.SkipLeadIn) }
    } else {
        Modifier
    }
    Box(Modifier.size(size).then(tap), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = this.size.minDimension * 0.07f
            val inset = width / 2
            val arc = Size(this.size.width - width, this.size.height - width)
            drawCircle(EisenColors.Muted, radius = (this.size.minDimension - width) / 2, style = Stroke(width))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * ring.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                digits,
                color = EisenColors.Foreground,
                fontSize = (size.value * if (phase == HoldPhase.LEAD_IN) 0.42f else 0.27f).sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
            Text(caption, color = EisenColors.MutedForeground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Buttons(hold: HoldTimer, phase: HoldPhase, dispatch: (Msg) -> Unit) {
    Row(Modifier.fillMaxWidth().widthIn(max = 480.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Cancel", Modifier.weight(1f), contentColor = EisenColors.MutedForeground) { dispatch(Msg.Hold.Cancel) }
        when {
            // Before the hold there is only Cancel and the tap on the ring.
            phase == HoldPhase.LEAD_IN -> Unit
            // Stopping a countdown early logs what was held, like a set with missed reps.
            hold.target != null -> SecondaryButton("Stop", Modifier.weight(1f), contentColor = EisenColors.Warn) { dispatch(Msg.Hold.Stop) }
            else -> PrimaryButton("Done", Modifier.weight(1f)) { dispatch(Msg.Hold.Stop) }
        }
    }
}
