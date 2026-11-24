package io.github.alexanderop.eisen.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.core.ui.EisenColors
import kotlinx.coroutines.delay

/*
 * Small pieces the Home tab, the Workouts tab and the picker share. They live here because
 * these three screens are one part of the port. They could move to ui/Components.kt later.
 */

private const val DISARM_AFTER_MS = 4000L

/**
 * A destructive action that needs two taps instead of a dialog, like the PWA's ConfirmButton.
 * The first tap arms it and changes the label. It disarms by itself after a few seconds.
 * `ghost` is the quiet form, for a list row.
 */
@Composable
internal fun ConfirmButton(
    label: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    height: Dp = 52.dp,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(DISARM_AFTER_MS)
            armed = false
        }
    }
    val onClick = {
        if (armed) {
            armed = false
            onConfirm()
        } else {
            armed = true
        }
    }
    val text = @Composable {
        // Read out the new label, so a screen reader user knows a second tap is needed.
        Text(
            if (armed) confirmLabel else label,
            fontWeight = if (ghost) FontWeight.SemiBold else FontWeight.Bold,
            fontSize = if (height >= 52.dp) 16.sp else 15.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (ghost) {
        TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = maxOf(height, 48.dp)),
            colors = ButtonDefaults.textButtonColors(contentColor = EisenColors.Destructive),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { text() }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = height),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Destructive),
        ) { text() }
    }
}

/** The small uppercase label above a section. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = EisenColors.MutedForeground,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

/** A lime pill such as "Next" or "New record". */
@Composable
internal fun PillBadge(text: String, modifier: Modifier = Modifier) {
    Badge(modifier = modifier, containerColor = EisenColors.Primary, contentColor = EisenColors.OnPrimary) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private val CardBorder = BorderStroke(1.dp, EisenColors.Border)

/** A raised surface that groups one topic, with the PWA's border and corners. */
@Composable
internal fun EisenCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.outlinedCardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground)
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = CardBorder, content = content)
    } else {
        OutlinedCard(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = CardBorder, content = content)
    }
}

/** The PWA's main buttons are 52px high, sized for a thumb in a gym. */
internal val ButtonHeight = 52.dp
