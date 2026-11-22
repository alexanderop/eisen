package io.github.alexanderop.eisen.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An icon from SVG path data on a 24×24 grid. `filled` paths are filled, the others are
 * drawn as 2px round strokes, which is how the Lucide icons of the PWA are made.
 */
fun pathIcon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (data in paths) {
            val nodes = PathParser().parsePathString(data).toNodes()
            if (filled) addPath(nodes, fill = SolidColor(Color.Black))
            else addPath(
                nodes,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        }
    }.build()

/** Icons used on more than one screen. A screen keeps its own next to its code. */
object Icons {
    val Home = pathIcon("Home", "M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z", filled = true)
    val Workouts = pathIcon("Workouts", "M2 10h2v4H2zM5 7h3v10H5zM9 11h6v2H9zM16 7h3v10h-3zM20 10h2v4h-2z", filled = true)
    val History = pathIcon("History", "M5 4h14v3H5zM5 10.5h14v3H5zM5 17h14v3H5z", filled = true)
    val Progress = pathIcon("Progress", "M4 20V10h3.5v10zM10.25 20V4h3.5v16zM16.5 20v-7H20v7z", filled = true)
    val Settings = pathIcon("Settings", "M4 6h9v2H4zM17 6h3v2h-3zM13 4.5h3v5h-3zM4 16h3v2H4zM11 16h9v2h-9zM7 14.5h3v5H7z", filled = true)
    val Minus = pathIcon("Minus", "M5 12h14")
    val Plus = pathIcon("Plus", "M5 12h14", "M12 5v14")
    val Close = pathIcon("Close", "M18 6 6 18", "m6 6 12 12")
    val Back = pathIcon("Back", "m12 19-7-7 7-7", "M19 12H5")
    val ChevronRight = pathIcon("ChevronRight", "m9 18 6-6-6-6")
    val ChevronUp = pathIcon("ChevronUp", "m18 15-6-6-6 6")
    val ChevronDown = pathIcon("ChevronDown", "m6 9 6 6 6-6")
    val Check = pathIcon("Check", "M20 6 9 17l-5-5")
    val Trash = pathIcon("Trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
    val Pencil = pathIcon("Pencil", "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4")
}

private val images = HashMap<String, ImageBitmap?>()

/** The picture of an exercise from the shared assets. Null when it has none. */
@Composable
fun exerciseBitmap(image: String?): ImageBitmap? {
    if (image == null) return null
    val context = LocalContext.current
    return remember(image) {
        images.getOrPut(image) {
            runCatching { context.assets.open("exercises/$image.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
}

/**
 * A small square picture of an exercise for lists. The images are dark equipment on a
 * transparent background, so they sit on a pale tile. Without an image it shows the first letter.
 */
@Composable
fun ExerciseTile(image: String?, name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val bitmap = exerciseBitmap(image)
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(if (bitmap != null) EisenColors.Tile else EisenColors.Muted),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(3.dp))
        } else {
            Text(name.take(1).uppercase(), color = EisenColors.MutedForeground, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        }
    }
}

/** A wide picture of an exercise as the header of a card. Draws nothing without an image. */
@Composable
fun ExerciseBanner(image: String?, modifier: Modifier = Modifier) {
    val bitmap = exerciseBitmap(image) ?: return
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 7f).clip(RoundedCornerShape(12.dp)).background(EisenColors.Tile),
        contentAlignment = Alignment.Center,
    ) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(8.dp))
    }
}

/** Changes a number in fixed steps with thumb-sized buttons: weights, sets, reps, seconds. */
@Composable
fun Stepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    label: String,
    modifier: Modifier = Modifier,
    min: Double = 0.0,
    max: Double = Double.POSITIVE_INFINITY,
    format: (Double) -> String = { io.github.alexanderop.eisen.domain.formatNumber(it) },
) {
    val haptics = rememberHaptics()
    fun change(direction: Int) {
        val next = (value + direction * step).coerceIn(min, max)
        if (next == value) return
        onValueChange(next)
        haptics.perform(Haptic.STEP)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(onClick = { change(-1) }, enabled = value > min, modifier = Modifier.size(52.dp)) {
            androidx.compose.material3.Icon(Icons.Minus, contentDescription = "Decrease $label")
        }
        Text(
            format(value),
            modifier = Modifier.widthIn(min = 84.dp).semantics { contentDescription = "$label ${format(value)}" },
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
        FilledTonalIconButton(onClick = { change(1) }, enabled = value < max, modifier = Modifier.size(52.dp)) {
            androidx.compose.material3.Icon(Icons.Plus, contentDescription = "Increase $label")
        }
    }
}

/** A stepper for whole numbers: sets, reps, seconds. */
@Composable
fun IntStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    format: (Int) -> String = { it.toString() },
) = Stepper(
    value = value.toDouble(),
    onValueChange = { onValueChange(it.toInt()) },
    step = step.toDouble(),
    label = label,
    modifier = modifier,
    min = min.toDouble(),
    max = max.toDouble(),
    format = { format(it.toInt()) },
)

enum class SetState { EMPTY, DONE, MISSED }

/** One set of an exercise. Lime when all reps were done, an amber ring when reps were missed. */
@Composable
fun SetButton(text: String, state: SetState, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = text) {
    val background by animateColorAsState(
        when (state) {
            SetState.DONE -> EisenColors.Primary
            SetState.MISSED -> Color.Transparent
            SetState.EMPTY -> EisenColors.Muted
        },
        label = "set",
    )
    val content = when (state) {
        SetState.DONE -> EisenColors.OnPrimary
        SetState.MISSED -> EisenColors.Warn
        SetState.EMPTY -> EisenColors.MutedForeground
    }
    Box(
        modifier
            .widthIn(max = 68.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .then(if (state == SetState.MISSED) Modifier.border(2.dp, EisenColors.Warn, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = 20.sp, style = MaterialTheme.typography.titleLarge)
    }
}
