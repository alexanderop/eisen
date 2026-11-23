package io.github.alexanderop.eisen.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.MOTION_BACKGROUND
import io.github.alexanderop.eisen.domain.MOTION_FRAMES
import io.github.alexanderop.eisen.domain.MOTION_FRAME_MS
import io.github.alexanderop.eisen.domain.MOTION_STILL_FRAME
import io.github.alexanderop.eisen.domain.motionOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private val strips = HashMap<String, ImageBitmap?>()

private val MotionShape = RoundedCornerShape(10.dp)

/** The strip of an exercise from the shared assets. Null when it has none or its file is not there. */
@Composable
private fun motionStrip(exerciseId: String?): ImageBitmap? {
    val name = exerciseId?.let(::motionOf) ?: return null
    val context = LocalContext.current
    return remember(name) {
        strips.getOrPut(name) {
            runCatching { context.assets.open("motions/$name.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()
                ?.takeIf { it.width == it.height * MOTION_FRAMES }
                // A tile is far smaller than a frame: mipmaps keep the shrunk figure from flickering.
                ?.apply { setHasMipMap(true) }
                ?.asImageBitmap()
        }
    }
}

/**
 * An exercise moving: its strip frame after frame, looping, for the screens of a WOD. Without a strip
 * it is the still [ExerciseTile]. It holds the middle frame when not [playing] or when the system's
 * animations are off, and does nothing off screen or in the background. Decorative: the name is next to it.
 */
@Composable
fun ExerciseMotion(exercise: Exercise?, size: Dp, modifier: Modifier = Modifier, playing: Boolean = true) {
    val strip = motionStrip(exercise?.id)
    if (strip == null) {
        ExerciseTile(exercise?.image, exercise?.name ?: "?", modifier, size)
        return
    }
    var visible by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(MOTION_STILL_FRAME) }
    val moving = playing && visible
    LaunchedEffect(moving) {
        if (!moving) {
            frame = MOTION_STILL_FRAME
            return@LaunchedEffect
        }
        val scale = currentCoroutineContext()[MotionDurationScale]
        val start = withFrameMillis { it }
        while (true) {
            if (scale != null && scale.scaleFactor == 0f) {
                frame = MOTION_STILL_FRAME
                snapshotFlow { scale.scaleFactor }.first { it != 0f }
            }
            // The frame clock stops while the app is in the background, and this waits for it.
            val elapsed = withFrameMillis { it } - start
            frame = ((elapsed / MOTION_FRAME_MS) % MOTION_FRAMES).toInt()
            delay(MOTION_FRAME_MS - elapsed % MOTION_FRAME_MS)
        }
    }
    Spacer(
        modifier
            .size(size)
            .onVisibilityChanged(minFractionVisible = 0.01f) { visible = it }
            .clip(MotionShape)
            .background(Color(MOTION_BACKGROUND))
            .border(1.dp, EisenColors.Border, MotionShape)
            .drawBehind {
                val side = strip.height
                drawImage(
                    strip,
                    srcOffset = IntOffset(frame * side, 0),
                    srcSize = IntSize(side, side),
                    dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            },
    )
}
