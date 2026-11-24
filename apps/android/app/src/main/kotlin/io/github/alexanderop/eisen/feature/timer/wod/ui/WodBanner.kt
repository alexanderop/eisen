package io.github.alexanderop.eisen.feature.timer.wod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.core.MOTION_BACKGROUND
import io.github.alexanderop.eisen.feature.timer.wod.Wod
import io.github.alexanderop.eisen.app.elm.MAX_BANNER_FIGURES
import io.github.alexanderop.eisen.app.elm.bannerMotions
import io.github.alexanderop.eisen.app.elm.bannerSlots
import io.github.alexanderop.eisen.core.motionOf
import io.github.alexanderop.eisen.core.ui.ExerciseTile
import io.github.alexanderop.eisen.core.ui.drawFrame
import io.github.alexanderop.eisen.core.ui.motionFrame
import io.github.alexanderop.eisen.core.ui.stripNamed

/**
 * A WOD's picture: a slot for each of its movements ([bannerSlots]), centred and evenly spaced on the
 * strips' dark ground: the figure, square, as high as [height] or narrower when four do not fit, or
 * for a movement without one its still picture as a small tile. Still on the middle frame unless
 * [playing], and then with the motion tile's rules. Nothing is drawn for a WOD without a single
 * figure: check [bannerMotions] first. Decorative: the movements are named beside it.
 */
@Composable
fun WodBanner(wod: Wod, height: Dp, find: (String) -> Exercise?, modifier: Modifier = Modifier, playing: Boolean = false) {
    if (bannerMotions(wod).isEmpty()) return
    val slots = bannerSlots(wod).map { id -> id to motionOf(id)?.let { stripNamed(it) } }
    var visible by remember { mutableStateOf(false) }
    val frame by motionFrame(playing && visible)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Color(MOTION_BACKGROUND))
            .onVisibilityChanged(minFractionVisible = 0.01f) { visible = it }
            .clearAndSetSemantics { },
    ) {
        val side = minOf(maxHeight, maxWidth / MAX_BANNER_FIGURES)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            for ((id, strip) in slots) {
                if (strip != null) Spacer(Modifier.size(side).drawBehind { drawFrame(strip, frame) })
                else Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    val exercise = find(id)
                    ExerciseTile(exercise?.image, exercise?.name ?: "?", size = minOf(side, 40.dp))
                }
            }
        }
    }
}
