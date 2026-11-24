package io.github.alexanderop.eisen.feature.timer.boxing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_MOVES
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingMove
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingPlan
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStyle
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.feature.timer.boxing.MAX_OWN_COMBOS
import io.github.alexanderop.eisen.feature.timer.boxing.MAX_OWN_MOVES
import io.github.alexanderop.eisen.feature.timer.boxing.MIN_OWN_MOVES
import io.github.alexanderop.eisen.feature.timer.boxing.OwnCombo
import io.github.alexanderop.eisen.feature.timer.boxing.boxingMove
import io.github.alexanderop.eisen.feature.timer.boxing.ownCombo
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Haptic
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.pathIcon
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState
import io.github.alexanderop.eisen.core.ui.rememberHaptics

/*
 * The lifter's own combinations: their section on the boxing setup and the builder that puts one
 * together from the moves. What the builder holds before "Save" is presentation: it is sent with
 * `Msg.Timer.SaveOwn`.
 */

private val PlayIcon = pathIcon("Play", "M7 4.5v15l12-7.5z", filled = true)
private val PlusIcon = pathIcon("Plus", "M12 5v14", "M5 12h14")
private val OwnShape = RoundedCornerShape(14.dp)

private fun other(style: BoxingStyle) = if (style == BoxingStyle.NUMBERS) BoxingStyle.NAMES else BoxingStyle.NUMBERS

/** A move as the chosen style shows it: "1", "3B", "Slip" or "Jab", "Hook to the body". */
private fun shownMove(move: BoxingMove, style: BoxingStyle): String = if (style == BoxingStyle.NUMBERS) move.number else move.name

/**
 * "My combos" on the setup: each combination of the lifter's own in the chosen style, the other style
 * small, a play button, a tap to edit it; "New combination" under them, up to twelve.
 */
@Composable
internal fun OwnCombos(plan: BoxingPlan, onPlay: (String) -> Unit, onEdit: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (plan.own.isEmpty()) {
            Text("Put your own combinations together. They get their own theme.", fontSize = 14.sp, color = EisenColors.MutedForeground)
        }
        for (own in plan.own) {
            val combo = ownCombo(own)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(OwnShape)
                    .background(EisenColors.Card)
                    .border(1.dp, EisenColors.Border, OwnShape)
                    .clickable(role = Role.Button, onClickLabel = "Edit") { onEdit(own.id) }
                    .heightIn(min = 60.dp)
                    .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 6.dp, height = 36.dp).clip(RoundedCornerShape(3.dp)).background(BoxingTheme.OWN.tint))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(shown(combo, plan.style), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(shown(combo, other(plan.style)), fontSize = 13.sp, color = EisenColors.MutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onPlay(own.id) }) {
                    Icon(PlayIcon, contentDescription = "Hear ${combo.names}", tint = EisenColors.Foreground, modifier = Modifier.size(18.dp))
                }
            }
        }
        val full = plan.own.size >= MAX_OWN_COMBOS
        OutlinedButton(
            onClick = { onEdit("") },
            enabled = !full,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = OwnShape,
            border = BorderStroke(1.dp, EisenColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EisenColors.Foreground, disabledContentColor = EisenColors.MutedForeground),
        ) {
            Icon(PlusIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (full) "12 is the most" else "New combination", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/**
 * Puts a combination together: the moves so far large on top, a tap on one takes it out; the pad of
 * the fourteen moves under it, the six head punches as big keys. "Listen" plays it the way the coach
 * will, "Save" needs two moves. [editing] is the combination edited, null for a new one; [onDelete]
 * is there only when editing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ComboBuilder(
    editing: OwnCombo?,
    style: BoxingStyle,
    onListen: (List<String>) -> Unit,
    onSave: (List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val sheetState = rememberFullSheetState()
    val haptics = rememberHaptics()
    // The moves as one string, so the sheet keeps them over a rotation.
    var draft by rememberSaveable(editing?.id) { mutableStateOf(editing?.moves?.joinToString(",").orEmpty()) }
    val moves = draft.split(",").filter { it.isNotEmpty() }
    fun set(next: List<String>) {
        draft = next.joinToString(",")
    }
    val full = moves.size >= MAX_OWN_MOVES
    val tint = BoxingTheme.OWN.tint
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = EisenColors.Card) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (editing == null) "New combination" else "Edit combination",
                    Modifier.weight(1f).semantics { heading() },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                TextButton(onClick = { set(emptyList()) }, enabled = moves.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Clear", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) { Icon(Icons.Close, contentDescription = "Close") }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The combination so far, big and in order. A tap on a move takes it out.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp)
                        .clip(OwnShape)
                        .background(EisenColors.Muted)
                        .padding(10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (moves.isEmpty()) {
                        Text(
                            "Tap the moves below, in order",
                            Modifier.fillMaxWidth(),
                            fontSize = 16.sp,
                            color = EisenColors.MutedForeground,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            moves.forEachIndexed { at, id ->
                                val move = boxingMove(id)
                                Box(
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tint)
                                        .clickable(role = Role.Button, onClickLabel = "Take out") {
                                            set(moves.filterIndexed { index, _ -> index != at })
                                            haptics.perform(Haptic.TAP)
                                        }
                                        .clearAndSetSemantics { contentDescription = "${at + 1}: ${move.name}. Tap to take it out" }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(shownMove(move, style), color = EisenColors.OnPrimary, fontSize = if (style == BoxingStyle.NUMBERS) 26.sp else 18.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            moves.joinToString(if (style == BoxingStyle.NUMBERS) " · " else " – ") { shownMove(boxingMove(it), other(style)) },
                            fontSize = 13.sp,
                            color = EisenColors.MutedForeground,
                        )
                    }
                }
                Text(
                    if (full) "Six moves is the most" else "${moves.size} of $MAX_OWN_MOVES moves",
                    fontSize = 13.sp,
                    color = if (full) EisenColors.Foreground else EisenColors.MutedForeground,
                    fontWeight = if (full) FontWeight.Bold else FontWeight.Normal,
                )
                fun add(move: BoxingMove) {
                    set(moves + move.id)
                    haptics.perform(Haptic.TAP)
                }
                // The six head punches: the number large, the name small.
                for (row in BOXING_MOVES.take(6).chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (move in row) MoveKey(move.number, move.name, !full, 68.dp, 28) { add(move) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (move in BOXING_MOVES.slice(6..9)) MoveKey(move.number, move.name.removeSuffix(" to the body") + " to the body", !full, 68.dp, 22, underLines = 2) { add(move) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (move in BOXING_MOVES.drop(10)) MoveKey(move.name, null, !full, 52.dp, 16) { add(move) }
                }
                Text("Your own combinations are said piece by piece.", fontSize = 13.sp, color = EisenColors.MutedForeground)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    FilledTonalButton(
                        onClick = onDelete,
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Destructive),
                    ) { Text("Delete", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                FilledTonalButton(
                    onClick = { onListen(moves) },
                    enabled = moves.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
                ) {
                    Icon(PlayIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Listen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Button(
                    onClick = { onSave(moves) },
                    enabled = moves.size >= MIN_OWN_MOVES,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Save", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            }
        }
    }
}

/** One key of the pad: its label large, a smaller line under it. */
@Composable
private fun RowScope.MoveKey(label: String, under: String?, enabled: Boolean, height: androidx.compose.ui.unit.Dp, size: Int, underLines: Int = 1, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(EisenColors.Muted)
            .border(1.dp, EisenColors.Border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = size.sp, lineHeight = (size + 2).sp, fontWeight = FontWeight.Black, color = EisenColors.Foreground, maxLines = 1, textAlign = TextAlign.Center)
        if (under != null) Text(under, fontSize = 12.sp, lineHeight = 14.sp, color = EisenColors.MutedForeground, maxLines = underLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
