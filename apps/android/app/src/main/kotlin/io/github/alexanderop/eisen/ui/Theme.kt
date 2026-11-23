package io.github.alexanderop.eisen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The palette of the PWA (`apps/web/src/style.css`). The app is dark only. */
object EisenColors {
    val Background = Color(0xFF0B0C0E)
    val Foreground = Color(0xFFF4F5F7)
    val Card = Color(0xFF15171B)
    /** Lime: the one loud colour. */
    val Primary = Color(0xFFC8F031)
    val OnPrimary = Color(0xFF11140A)
    val Muted = Color(0xFF1F2228)
    val MutedForeground = Color(0xFF8D939F)
    val Border = Color(0xFF2B2F37)
    val Destructive = Color(0xFFFF6464)
    /** Missed reps and deloads, and the "Get ready" of the interval timers. */
    val Warn = Color(0xFFFFB020)
    /** The rest of an interval timer: calm, and told apart from work across the room. */
    val Rest = Color(0xFF5BC8FF)
    /** Pale ground for the exercise images, which are dark on transparent. */
    val Tile = Color(0xFFECEEF1)
}

private val Scheme = darkColorScheme(
    primary = EisenColors.Primary,
    onPrimary = EisenColors.OnPrimary,
    primaryContainer = Color(0xFF3A4610),
    onPrimaryContainer = Color(0xFFE4F99A),
    secondary = EisenColors.Primary,
    onSecondary = EisenColors.OnPrimary,
    secondaryContainer = Color(0xFF2E3713),
    onSecondaryContainer = Color(0xFFE4F99A),
    tertiary = EisenColors.Warn,
    onTertiary = Color(0xFF2A1C00),
    background = EisenColors.Background,
    onBackground = EisenColors.Foreground,
    surface = EisenColors.Background,
    onSurface = EisenColors.Foreground,
    surfaceVariant = EisenColors.Muted,
    onSurfaceVariant = EisenColors.MutedForeground,
    surfaceContainerLowest = EisenColors.Background,
    surfaceContainerLow = EisenColors.Card,
    surfaceContainer = EisenColors.Card,
    surfaceContainerHigh = EisenColors.Muted,
    surfaceContainerHighest = EisenColors.Muted,
    surfaceBright = EisenColors.Muted,
    surfaceDim = EisenColors.Background,
    outline = EisenColors.Border,
    outlineVariant = EisenColors.Border,
    error = EisenColors.Destructive,
    onError = Color(0xFF2B0A0A),
    errorContainer = Color(0xFF4A1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    // Snackbars: a raised dark surface with a lime action, not the default pale one.
    inverseSurface = EisenColors.Border,
    inverseOnSurface = EisenColors.Foreground,
    inversePrimary = EisenColors.Primary,
    scrim = Color.Black,
)

private val EisenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EisenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = EisenShapes, content = content)
}
