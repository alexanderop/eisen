package io.github.alexanderop.eisen.core.ui

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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

/**
 * Expressive motion: things that move or change size spring and settle, colours and fades follow
 * the effects springs. Screens read it as `MaterialTheme.motionScheme`. With the system's animations
 * removed ([rememberReducedMotion]) every one of them is a snap: the end state on the next frame.
 */
@Composable
fun EisenTheme(content: @Composable () -> Unit) {
    val motion = if (rememberReducedMotion()) InstantMotion else MotionScheme.expressive()
    MaterialTheme(colorScheme = Scheme, motionScheme = motion, shapes = EisenShapes, content = content)
}

/**
 * The system's "Remove animations" (animator duration scale 0). Compose's own springs and tweens do
 * not follow that setting, so the theme and the wavy bars ask for it. Read when the screen first draws.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** The motion scheme with animations removed: everything lands at once. */
private object InstantMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/**
 * A button's [corner] and the squarer shape it morphs to while pressed, the press feedback of
 * Material 3 Expressive. For the buttons a lifter hits with a sweaty thumb: Start, Pause, Done.
 */
fun pressShapes(corner: Dp): ButtonShapes = ButtonShapes(RoundedCornerShape(corner), RoundedCornerShape(corner * PRESSED_CORNER))

private const val PRESSED_CORNER = 0.4f

/**
 * [pressShapes] for a tap area that is not a Material button (the big round counter): the shape to
 * clip it to, squarer while [press] is pressed. Drawing only, the tap itself is not delayed.
 */
@Composable
fun pressedShape(press: InteractionSource, corner: Dp): Shape {
    val pressed by press.collectIsPressedAsState()
    val now by animateDpAsState(if (pressed) corner * PRESSED_CORNER else corner, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    return RoundedCornerShape(now.coerceAtLeast(0.dp))
}
