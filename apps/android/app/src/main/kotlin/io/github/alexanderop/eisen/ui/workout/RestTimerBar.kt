package io.github.alexanderop.eisen.ui.workout

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.rest.EXTEND_SECONDS
import io.github.alexanderop.eisen.rest.Rest
import io.github.alexanderop.eisen.rest.RestState
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.formatClock
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor

/** The last seconds of a rest change colour, so a glance from the bar is enough. */
private const val SOON_SECONDS = 10

/**
 * The running rest, as a band below the workout header. It floats over the list,
 * so starting a rest never moves the set buttons. Sound, vibration and the
 * notification belong to [Rest]; this only shows the time.
 */
@Composable
fun RestTimerBar(modifier: Modifier = Modifier) {
    val current = Rest.state
    // Keeps the last rest on screen while the band slides away after it was dismissed.
    val shown = remember { mutableStateOf<RestState?>(null) }
    if (current != null) shown.value = current
    AnimatedVisibility(
        visible = current != null,
        modifier = modifier,
        enter = slideInVertically { -it / 3 } + fadeIn(),
        exit = slideOutVertically { -it / 3 } + fadeOut(),
    ) {
        shown.value?.let { RestBand(it) }
    }
}

@Composable
private fun RestBand(rest: RestState) {
    // Counted from timestamps, so the band is right again the moment the app comes back.
    val now by produceState(System.currentTimeMillis(), rest) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    val over = now >= rest.endsAt
    val remaining = maxOf(0, ceil((rest.endsAt - now) / 1000.0).toInt())
    val overtime = maxOf(0, floor((now - rest.endsAt) / 1000.0).toInt())
    val total = rest.endsAt - rest.startedAt
    val fraction = if (total <= 0) 1f else ((now - rest.startedAt).toFloat() / total).coerceIn(0f, 1f)
    // The bar drains: what is left of it is what is left of the rest.
    val left by animateFloatAsState(if (over) 1f else 1f - fraction, tween(250, easing = LinearEasing), label = "left")
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
            Box {
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
                            onClick = { Rest.extend() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text("+${EXTEND_SECONDS}s", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    FilledTonalIconButton(
                        onClick = { Rest.stop() },
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
