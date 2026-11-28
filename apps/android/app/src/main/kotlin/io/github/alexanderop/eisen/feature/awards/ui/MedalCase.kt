package io.github.alexanderop.eisen.feature.awards.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.rememberCoroutineScope
import io.github.alexanderop.eisen.core.ui.Icons
import kotlinx.coroutines.launch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.awards
import io.github.alexanderop.eisen.core.ui.EisenCard
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.formatShortDate
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState
import io.github.alexanderop.eisen.core.ui.rememberReducedMotion
import io.github.alexanderop.eisen.feature.awards.Awards
import io.github.alexanderop.eisen.feature.awards.Medal
import io.github.alexanderop.eisen.feature.awards.MedalGroup
import io.github.alexanderop.eisen.feature.awards.nextRankText

/** Medals to a row of the case. */
private const val PER_ROW = 3

/**
 * The third view of Progress: the level with its bar, then the medals by group, three to a row. A locked one is grey
 * with a lock and says how far it is. A tap opens a small sheet with how it was earned, or how to earn it.
 */
@Composable
fun MedalsView(model: Model) {
    val awards = remember(model.state, model.zone) { awards(model) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LevelCard(awards)
        for (group in MedalGroup.entries) {
            val medals = awards.medals.filter { it.group == group }
            if (medals.isEmpty()) continue
            Eyebrow(group.title, Modifier.padding(top = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (row in medals.chunked(PER_ROW)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (medal in row) {
                            MedalCell(medal, shine = medal.id in model.medalShine, Modifier.weight(1f)) { open = medal.id }
                        }
                        repeat(PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    awards.medals.firstOrNull { it.id == open }?.let { medal -> MedalSheet(medal, onDismiss = { open = null }) }
}

@Composable
private fun LevelCard(awards: Awards) {
    val level = awards.level
    EisenCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ShieldEmblem(level.level, awards.rank, 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Lv ${level.level} · ${awards.rank.label}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                XpBar(level.fraction, Modifier.semantics { contentDescription = "${level.into} of ${level.cost} XP to level ${level.level + 1}" })
                Text(
                    "${nextRankText(level.level)} · ${awards.earned.size} of ${awards.medals.size} medals",
                    color = EisenColors.MutedForeground,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/** What a medal says under its name: the day it was earned, or how far it is. */
private fun subOf(medal: Medal): String = medal.earnedAt?.let(::formatShortDate) ?: medal.progressText

@Composable
private fun MedalCell(medal: Medal, shine: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val state = when {
        shine -> "New medal"
        medal.earned -> "Earned"
        else -> "Locked"
    }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Show the medal", onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Shining(shine, 64.dp) { MedalArt(medal, 64.dp) }
        Text(medal.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 15.sp)
        Text(subOf(medal), fontSize = 11.sp, color = EisenColors.MutedForeground, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}

/** A lime glow that breathes behind a medal that is new. Still, at its brightest, with the system's animations removed. */
@Composable
internal fun Shining(shine: Boolean, size: Dp, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        if (shine) {
            val glow = if (rememberReducedMotion()) {
                1f
            } else {
                val breathing = rememberInfiniteTransition(label = "shine")
                breathing.animateFloat(0.2f, 1f, infiniteRepeatable(tween(1_200), RepeatMode.Reverse), label = "glow").value
            }
            Canvas(Modifier.size(size * 1.3f)) {
                drawCircle(Brush.radialGradient(listOf(EisenColors.Primary.copy(alpha = 0.55f * glow), Color.Transparent)))
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedalSheet(medal: Medal, onDismiss: () -> Unit) {
    val state = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = EisenColors.Card) {
        Box(Modifier.fillMaxWidth().padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
            FilledTonalIconButton(
                onClick = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.MutedForeground),
            ) {
                Icon(Icons.Close, contentDescription = "Close")
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedalArt(medal, 96.dp)
            Text(medal.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("${medal.group.title} · ${medal.tier.label}", color = EisenColors.MutedForeground, fontSize = 14.sp)
            Text(medal.how, textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            val earnedAt = medal.earnedAt
            if (earnedAt != null) {
                Text("Earned ${formatShortDate(earnedAt)}", color = EisenColors.Primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                XpBar(medal.progress, Modifier.padding(top = 6.dp))
                Text(medal.progressText, color = EisenColors.MutedForeground, fontSize = 14.sp)
            }
        }
    }
}
