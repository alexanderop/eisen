package io.github.alexanderop.eisen.feature.awards.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.awards
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.rememberReducedMotion
import io.github.alexanderop.eisen.feature.awards.SavedAwards
import io.github.alexanderop.eisen.feature.awards.rankOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The level on Today: a small shield and "Lv 12" next to the gear. A tap opens Progress on the medal case. Its tap
 * area is 48 dp high, the chip itself smaller.
 */
@Composable
fun LevelChip(model: Model, dispatch: (Msg) -> Unit) {
    val awards = remember(model.state, model.zone) { awards(model) }
    val level = awards.level.level
    val open = { dispatch(Msg.Nav.OpenMedals) }
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = open)
            .clearAndSetSemantics {
                contentDescription = "Level $level, ${awards.rank.label}. Medals"
                role = Role.Button
                onClick { open(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(CircleShape).background(EisenColors.Muted).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ShieldEmblem(level, awards.rank, 26.dp)
            Text("Lv $level", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** The most medals the saved card flips in; more are counted under them. */
private const val REVEALED = 3

/** When the first medal flips in, and how far apart the next ones follow. */
private const val FIRST_MS = 350L
private const val NEXT_MS = 650L
private const val FLIP_MS = 600
private const val BAR_MS = 1_200

/**
 * What the saved workout earned, on its card: the medals that are new flip in one by one ("You earned"), then the XP it
 * brought and the bar of the level. Without new medals only the XP line. With the system's animations removed all of
 * it is there at once.
 */
@Composable
fun SavedAwardsReveal(model: Model) {
    val saved = model.lastAwards ?: return
    val still = rememberReducedMotion()
    val shown = saved.medals.take(REVEALED)
    val flips = remember(saved) { shown.map { Animatable(if (still) 0f else 90f) } }
    val bar = remember(saved) { Animatable(startOf(saved, still)) }
    LaunchedEffect(saved) {
        if (still) return@LaunchedEffect
        flips.forEachIndexed { index, flip ->
            launch {
                delay(FIRST_MS + index * NEXT_MS)
                flip.animateTo(0f, tween(FLIP_MS, easing = FastOutSlowInEasing))
            }
        }
        delay(FIRST_MS + shown.size * NEXT_MS)
        bar.animateTo(saved.after.fraction, tween(BAR_MS, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (shown.isNotEmpty()) {
            Text("You earned", Modifier.fillMaxWidth(), color = EisenColors.MutedForeground, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                shown.forEachIndexed { index, medal ->
                    val angle = flips[index].value
                    Column(
                        Modifier.weight(1f, fill = false).graphicsLayer {
                            rotationY = angle
                            alpha = 1f - angle / 90f * 0.92f
                            cameraDistance = 12f * density
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Shining(shine = true, size = 76.dp) { MedalArt(medal, 76.dp) }
                        Text(medal.name, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 16.sp)
                        Text(
                            if (medal.id in saved.records) "New record" else medal.group.title,
                            color = EisenColors.MutedForeground,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            val more = saved.medals.size - shown.size
            if (more > 0) {
                Text("and ${counted(more, "more medal", "more medals")}", Modifier.fillMaxWidth(), color = EisenColors.MutedForeground, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("+${saved.xp} XP", fontWeight = FontWeight.ExtraBold, color = EisenColors.Primary)
            XpBar(bar.value, Modifier.weight(1f))
            Text("Lv ${saved.after.level}", color = EisenColors.MutedForeground, fontWeight = FontWeight.SemiBold)
        }
        if (saved.levelUp) {
            Text("Level up! Now ${rankOf(saved.after.level).label}, level ${saved.after.level}.", color = EisenColors.Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Where the bar starts: at the old level's fill, or empty once the workout took it into a new level. Full still. */
private fun startOf(saved: SavedAwards, still: Boolean): Float = when {
    still -> saved.after.fraction
    saved.levelUp -> 0f
    else -> saved.before.fraction
}
