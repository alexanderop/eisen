package io.github.alexanderop.eisen.feature.workout.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.EXTEND_SECONDS
import io.github.alexanderop.eisen.app.elm.RestState
import io.github.alexanderop.eisen.app.elm.restSecondsLeft
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.formatClock
import kotlin.math.floor

/** The last seconds of a rest change colour, so a glance from the bar is enough. */
private const val SOON_SECONDS = 10

/**
 * The running rest ([rest], null when none runs), as a band below the workout header. It floats over
 * the list, so starting a rest never moves the set buttons. Sound, vibration and the notification come
 * from `update`; this only shows the time at [now], the model's clock, which ticks every second while
 * a rest runs and the app shows. [onExtend] is "+30s", [onDismiss] the close button and a swipe.
 * [onOpen], when the rest screen was minimised, is a tap on the band that brings it back.
 */
@Composable
fun RestTimerBar(rest: RestState?, now: Long, onExtend: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    // Keeps the last rest on screen while the band slides away after it was dismissed.
    val shown = remember { mutableStateOf<RestState?>(null) }
    if (rest != null) shown.value = rest
    AnimatedVisibility(
        visible = rest != null,
        modifier = modifier,
        enter = slideInVertically { -it / 3 } + fadeIn(),
        exit = slideOutVertically { -it / 3 } + fadeOut(),
    ) {
        shown.value?.let { band ->
            // Each rest gets a fresh swipe state, so a new rest does not start swiped away.
            key(band.startedAt) { SwipeToDismiss(onDismiss) { RestBand(band, now, onExtend, onDismiss, onOpen) } }
        }
    }
}

/** Swiping the band sideways dismisses the rest, like its close button. */
@Composable
private fun SwipeToDismiss(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { distance -> distance * 0.4f })
    val dismiss by rememberUpdatedState(onDismiss)
    // The same callback every time, so the box calls it once.
    val onSwiped = remember { { _: SwipeToDismissBoxValue -> dismiss() } }
    SwipeToDismissBox(state = state, backgroundContent = {}, onDismiss = onSwiped) { content() }
}

@Composable
private fun RestBand(rest: RestState, now: Long, onExtend: () -> Unit, onDismiss: () -> Unit, onOpen: (() -> Unit)?) {
    // Counted from timestamps, so the band is right again the moment the app comes back.
    val over = now >= rest.endsAt
    val remaining = restSecondsLeft(rest, now)
    val overtime = maxOf(0, floor((now - rest.endsAt) / 1000.0).toInt())
    val total = rest.endsAt - rest.startedAt
    // The bar drains: what is left of it is what is left of the rest.
    fun leftAt(time: Long): Float = if (total <= 0) 0f else (1f - (time - rest.startedAt).toFloat() / total).coerceIn(0f, 1f)
    val drain = remember { Animatable(if (over) 1f else leftAt(now)) }
    // The model's clock moves a whole second at a time. In between, the bar glides to where it will be
    // at the next tick, so it drains smoothly. Pure presentation: the digits and colours follow [now].
    LaunchedEffect(rest, now) {
        if (over) {
            drain.animateTo(1f, tween(250, easing = LinearEasing))
        } else {
            val untilTick = Math.floorMod(rest.endsAt - now, 1000L).takeIf { it > 0 } ?: 1000L
            drain.animateTo(leftAt(now + untilTick), tween(untilTick.toInt(), easing = LinearEasing))
        }
    }
    val left = drain.value
    val stage = when {
        over -> EisenColors.Destructive
        remaining <= SOON_SECONDS -> EisenColors.Warn
        else -> EisenColors.Primary
    }
    val shake = shakeOffset(enabled = over)

    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = shake * density },
        color = EisenColors.Muted,
        shadowElevation = 12.dp,
    ) {
        Column {
            HorizontalDivider(color = EisenColors.Border)
            Box(if (onOpen != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open rest timer", onClick = onOpen) else Modifier) {
                // Tints the band itself, so the remaining time reads from across the room.
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(left).background(stage.copy(alpha = if (over) 0.28f else 0.16f)))
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (over) "+${formatClock(overtime)}" else formatClock(remaining),
                        modifier = Modifier.widthIn(min = 124.dp),
                        color = if (over || remaining <= SOON_SECONDS) stage else EisenColors.Foreground,
                        fontSize = 48.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).em,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Text(
                        if (over) "Rest is over. Start your next set." else rest.note,
                        modifier = Modifier.weight(1f),
                        color = EisenColors.MutedForeground,
                        fontSize = 13.sp,
                    )
                    if (!over) {
                        FilledTonalButton(
                            onClick = onExtend,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text("+${EXTEND_SECONDS}s", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = EisenColors.Card,
                            contentColor = EisenColors.MutedForeground,
                        ),
                    ) {
                        Icon(Icons.Close, contentDescription = "Dismiss timer")
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(10.dp).background(EisenColors.Border)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(left).background(stage))
            }
            HorizontalDivider(color = EisenColors.Border)
        }
    }
}

/**
 * A short shake every 1.6 seconds once the rest is over, so it catches the eye from across the room.
 * Off when the phone has animations turned off. In dp.
 */
@Composable
private fun shakeOffset(enabled: Boolean): Float {
    val context = LocalContext.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (!enabled || reduced) return 0f
    val transition = rememberInfiniteTransition(label = "shake")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1600
                0f at 0
                -5f at 80
                5f at 160
                -5f at 240
                5f at 320
                -5f at 400
                0f at 480
                0f at 1600
            },
        ),
        label = "shake",
    )
    return offset
}
