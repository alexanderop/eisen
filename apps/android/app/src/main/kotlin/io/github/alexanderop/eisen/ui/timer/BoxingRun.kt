package io.github.alexanderop.eisen.ui.timer

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.BoxingCombo
import io.github.alexanderop.eisen.domain.BoxingFocus
import io.github.alexanderop.eisen.domain.BoxingRound
import io.github.alexanderop.eisen.domain.BoxingStyle
import io.github.alexanderop.eisen.domain.BoxingTheme
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerPhase
import io.github.alexanderop.eisen.domain.activeTime
import io.github.alexanderop.eisen.domain.boxingView
import io.github.alexanderop.eisen.domain.timerView
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.TimerState
import io.github.alexanderop.eisen.domain.elm.callWindow
import io.github.alexanderop.eisen.domain.elm.combosText
import io.github.alexanderop.eisen.domain.elm.roundDetail
import io.github.alexanderop.eisen.domain.elm.runCues
import io.github.alexanderop.eisen.domain.elm.upNext
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.formatClock
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** How long a new call lights the combination area up. */
private const val FLASH_MS = 250L

/**
 * A boxing run, built around the combination: boxed with gloves on and read from two or three metres.
 * A slim header with End, the round and its theme, the time left in a band, and the combination the
 * coach called last filling the rest. Pause, the one big button, at the bottom. What shows comes from
 * the run's own cues: it is what was said.
 */
@Composable
internal fun BoxingRunScreen(state: TimerState.Running, now: Long, dispatch: (Msg) -> Unit) {
    val run = state.run
    val paused = run.pausedAt != null
    val active = activeTime(run, now)
    val view = timerView(run.config, active)
    val cues = remember(run.id, run.seed, run.config, run.calls) { runCues(run) }
    val box = boxingView(run.config, cues, active)
    val window = callWindow(run.config, cues, active)
    val theme = box.round.theme
    val style = run.config.boxing?.style ?: BoxingStyle.NAMES
    val rounds = run.config.rounds
    // Paused keeps the colours of the phase: the dimmed screen and the word say it is paused.
    val color = when (view.phase) {
        TimerPhase.READY -> EisenColors.Warn
        TimerPhase.REST -> EisenColors.Rest
        else -> theme.tint
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(EisenColors.Background)
            .keepScreenOn()
            // Takes every touch, so nothing below reacts.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        val compact = maxHeight < 620.dp
        val dim = if (paused) 0.6f else 1f
        Column(Modifier.fillMaxSize()) {
            // A slim header: the name, and End small at the top right, out of the way of the gloves.
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    run.name,
                    Modifier.weight(1f).alpha(dim).semantics { heading() },
                    color = EisenColors.MutedForeground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(
                    onClick = { dispatch(Msg.Timer.End) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
                ) { Text("End", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().alpha(dim)) {
                PhaseLine(view.phase, view.round, rounds, theme, color, compact)
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                TimeBand(run.config, view.phase, view.phaseLeft, view.phaseTotal, color, compact)
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                if (view.phase == TimerPhase.WORK && box.focus != null) {
                    FocusBand(box.focus, theme.tint, compact)
                    Spacer(Modifier.height(8.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (view.phase) {
                        TimerPhase.WORK -> CallArea(box.combo, box.go, box.goes, theme, style, window?.from, window?.until, active, now, run.startedAt + run.paused, paused, compact)
                        TimerPhase.DONE -> Unit
                        else -> UpNextArea(if (view.phase == TimerPhase.REST) view.round + 1 else 1, box.round, compact)
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            FilledTonalButton(
                onClick = { dispatch(if (paused) Msg.Timer.Resume else Msg.Timer.Pause) },
                modifier = Modifier.fillMaxWidth().height(if (compact) 72.dp else 80.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (paused) EisenColors.Primary else EisenColors.Muted,
                    contentColor = if (paused) EisenColors.OnPrimary else EisenColors.Foreground,
                ),
            ) { Text(if (paused) "Resume" else "Pause", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp) }
        }
        // Paused: one big word over the combination, at full strength over the dimmed screen.
        if (paused) {
            Box(Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                Text(
                    "PAUSED",
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(EisenColors.Background.copy(alpha = 0.85f))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = EisenColors.Foreground,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.12.em,
                )
            }
        }
    }
    if (state.ending) EndSheet(dispatch)
}

/** "ROUND 2/6" in the round's colour with its theme beside it; "GET READY" and "REST" between rounds. */
@Composable
private fun PhaseLine(phase: TimerPhase, round: Int, rounds: Int, theme: BoxingTheme, color: Color, compact: Boolean) {
    val word = when (phase) {
        TimerPhase.READY -> "GET READY"
        TimerPhase.REST -> "REST"
        TimerPhase.DONE -> "DONE"
        TimerPhase.WORK -> "ROUND $round/$rounds"
    }
    val after = when (phase) {
        TimerPhase.WORK -> theme.title
        TimerPhase.REST -> "after round $round"
        else -> null
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Black, letterSpacing = 0.1.em)) { append(word) }
            if (after != null) {
                append("  ")
                withStyle(SpanStyle(color = EisenColors.Foreground, fontWeight = FontWeight.ExtraBold)) { append(after) }
            }
        },
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            if (phase == TimerPhase.WORK) contentDescription = "Round $round of $rounds, ${theme.title}"
        },
        fontSize = if (compact) 20.sp else 24.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The time left of the phase in digits, and under them a thin bar of the colour that empties with it. */
@Composable
private fun TimeBand(config: TimerConfig, phase: TimerPhase, left: Long?, total: Long?, color: Color, compact: Boolean) {
    val seconds = ceil((left ?: 0) / 1000.0).toInt()
    val fraction = if (left != null && total != null && total > 0) (left.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatClock(seconds),
                color = EisenColors.Foreground,
                fontSize = if (compact) 52.sp else 64.sp,
                lineHeight = if (compact) 56.sp else 68.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.02).em,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                when (phase) {
                    TimerPhase.READY -> "to the first round"
                    TimerPhase.REST -> "rest left"
                    else -> "left in the round"
                },
                Modifier.padding(bottom = if (compact) 8.dp else 10.dp),
                color = EisenColors.MutedForeground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(EisenColors.Muted)) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

/**
 * "SPEED" on a band of the round's colour, "POWER" on a band of the foreground colour, both with dark
 * type, the same in both apps. It slides in and pulses once when the stretch changes.
 */
@Composable
private fun FocusBand(focus: BoxingFocus, tint: Color, compact: Boolean) {
    val shift = remember { Animatable(0f) }
    var seen by remember { mutableStateOf(focus) }
    LaunchedEffect(focus) {
        if (focus != seen) {
            shift.snapTo(1f)
            shift.animateTo(0f, tween(280))
        }
        seen = focus
    }
    val speed = focus == BoxingFocus.SPEED
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 44.dp else 52.dp)
            .graphicsLayer {
                translationX = shift.value * size.width * 0.15f
                scaleX = 1f + shift.value * 0.06f
                scaleY = 1f + shift.value * 0.06f
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (speed) tint else EisenColors.Foreground)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (speed) "SPEED" else "POWER",
            color = EisenColors.OnPrimary,
            fontSize = if (compact) 24.sp else 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.24.em,
        )
    }
}

/** The system's "Remove animations": a new call then only flashes, it does not pop. */
@Composable
private fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * The combination called last, as large as fits: the chosen style big, the other small under it. A
 * new call, and the coach's "Again!", light the whole area up in the round's colour for a moment and
 * the text pops; a drilled combination shows its goes as pips under it. A thin bar at the bottom fills
 * until the next call. Before the first call the theme waits there; a freestyle
 * round says it is the lifter's pace.
 */
@Composable
private fun CallArea(
    combo: BoxingCombo?,
    go: Int,
    goes: Int,
    theme: BoxingTheme,
    style: BoxingStyle,
    from: Long?,
    until: Long?,
    active: Long,
    now: Long,
    anchor: Long,
    paused: Boolean,
    compact: Boolean,
) {
    val tint = theme.tint
    val reduced = reducedMotion()
    val pop = remember { Animatable(1f) }
    var flash by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(from) }
    LaunchedEffect(from) {
        if (from == null || from == seen) {
            seen = from
            return@LaunchedEffect
        }
        seen = from
        flash = true
        if (!reduced) {
            pop.snapTo(1.12f)
            pop.animateTo(1f, tween(240))
        }
        // A plain delay: the flash stays when the system's animations are off.
        delay(FLASH_MS)
        flash = false
    }
    // The bar moves between the half-second ticks of the model's clock.
    val bar = remember(from) { Animatable(0f) }
    LaunchedEffect(from, until, now, paused) {
        if (from == null || until == null || until <= from) return@LaunchedEffect
        fun at(time: Long) = ((time - from).toFloat() / (until - from)).coerceIn(0f, 1f)
        if (paused) {
            bar.snapTo(at(active))
            return@LaunchedEffect
        }
        val step = Math.floorMod(anchor - now, 500L).takeIf { it > 0 } ?: 500L
        bar.snapTo(maxOf(bar.value, at(active)))
        bar.animateTo(at(active + step), tween(step.toInt(), easing = LinearEasing))
    }
    val shape = RoundedCornerShape(24.dp)
    val other = if (style == BoxingStyle.NUMBERS) BoxingStyle.NAMES else BoxingStyle.NUMBERS
    val (big, small, bigColor) = when {
        combo != null -> Triple(shown(combo, style), shown(combo, other), if (flash) EisenColors.OnPrimary else EisenColors.Foreground)
        theme == BoxingTheme.FREESTYLE -> Triple("Freestyle", "your pace, no calls", tint)
        else -> Triple(theme.title, "listen for the call", tint)
    }
    val numbers = combo != null && style == BoxingStyle.NUMBERS
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (flash) tint else EisenColors.Card)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = combo?.names ?: big
            },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 10.dp, bottom = 18.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FitText(
                // Numbers are set with room around their dashes; names keep each "·" at the end of a line.
                if (numbers) big.replace(" – ", "  –  ") else big.replace(" · ", "\u00A0· "),
                color = bigColor,
                max = when {
                    numbers -> if (compact) 92.sp else 120.sp
                    combo != null -> if (compact) 64.sp else 84.sp
                    else -> if (compact) 48.sp else 60.sp
                },
                min = if (combo != null) 40.sp else 28.sp,
                // Names of the longest combinations need up to four lines to stay at least 40sp at 360 wide.
                maxLines = if (numbers) 2 else 4,
                letterSpacing = if (numbers) 0.04.em else (-0.01).em,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            if (combo != null && goes > 1) {
                Goes(go, goes, if (flash) EisenColors.OnPrimary else tint, compact)
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            }
            Text(
                small,
                color = if (flash) EisenColors.OnPrimary.copy(alpha = 0.8f) else EisenColors.MutedForeground,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (combo != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.dp).background(EisenColors.Muted))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(bar.value).height(6.dp).background(if (flash) EisenColors.OnPrimary else tint))
        }
    }
}

/** A drilled combination's goes: a pip each, filled for those done and the one now, and "2 of 3" beside them. */
@Composable
private fun Goes(go: Int, goes: Int, color: Color, compact: Boolean) {
    val pip = if (compact) 24.dp else 30.dp
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Go $go of $goes" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        for (at in 1..goes) {
            val done = at <= go
            Box(
                Modifier
                    .size(pip)
                    .clip(CircleShape)
                    .background(if (done) color else Color.Transparent)
                    .border(3.dp, color, CircleShape),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("$go of $goes", color = color, fontSize = if (compact) 24.sp else 30.sp, fontWeight = FontWeight.Black, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

/** During the lead-in and a rest: "Up next · Round 3" and the round's theme large in its colour. */
@Composable
private fun UpNextArea(number: Int, round: BoxingRound, compact: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(EisenColors.Card)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(upNext(number).uppercase(), color = EisenColors.MutedForeground, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.em)
        Spacer(Modifier.height(8.dp))
        FitText(round.theme.title, round.theme.tint, max = if (compact) 52.sp else 68.sp, min = 28.sp, maxLines = 2, modifier = Modifier.fillMaxWidth().heightIn(max = if (compact) 130.dp else 170.dp))
        Spacer(Modifier.height(6.dp))
        Text(roundDetail(round), color = EisenColors.MutedForeground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/**
 * [text] at the largest size from [max] down to [min] that fits the space in [maxLines] lines without
 * breaking a word, centred.
 */
@Composable
private fun FitText(text: String, color: Color, max: TextUnit, min: TextUnit, maxLines: Int, modifier: Modifier, letterSpacing: TextUnit = (-0.01).em) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        fun style(size: Float) = TextStyle(
            fontSize = size.sp,
            lineHeight = (size * 1.06f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = letterSpacing,
            textAlign = TextAlign.Center,
        )
        val size = remember(text, width, height, max, min, maxLines) {
            var size = max.value
            while (size > min.value) {
                val layout = measurer.measure(text, style(size), maxLines = maxLines, constraints = Constraints(maxWidth = width))
                val wholeWords = (0 until layout.lineCount - 1).all { line ->
                    val end = layout.getLineEnd(line)
                    end >= text.length || text[end - 1] == ' ' || text[end] == ' '
                }
                if (!layout.hasVisualOverflow && layout.size.height <= height && wholeWords) break
                size -= 2f
            }
            maxOf(size, min.value)
        }
        Text(text, color = color, style = style(size), maxLines = maxLines)
    }
}

// The result

/** "166 combinations called" and the plan's timeline, under the rounds of a boxing result. */
@Composable
internal fun BoxingResultLines(config: TimerConfig, combos: Int) {
    Spacer(Modifier.height(4.dp))
    Text(combosText(combos), color = EisenColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    BoxingTimeline(config, Modifier.widthIn(max = 420.dp).padding(horizontal = 8.dp), height = 22.dp)
}
