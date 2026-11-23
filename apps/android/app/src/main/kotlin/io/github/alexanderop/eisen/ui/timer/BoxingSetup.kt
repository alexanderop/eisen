package io.github.alexanderop.eisen.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.BOXING_COMBOS
import io.github.alexanderop.eisen.domain.BOXING_PACES
import io.github.alexanderop.eisen.domain.BOXING_SPLITS
import io.github.alexanderop.eisen.domain.BOXING_TEMPLATES
import io.github.alexanderop.eisen.domain.BOXING_THEMES
import io.github.alexanderop.eisen.domain.BoxingCombo
import io.github.alexanderop.eisen.domain.BoxingPlan
import io.github.alexanderop.eisen.domain.BoxingRound
import io.github.alexanderop.eisen.domain.BoxingStyle
import io.github.alexanderop.eisen.domain.BoxingTheme
import io.github.alexanderop.eisen.domain.MAX_BOXING_REST_SECONDS
import io.github.alexanderop.eisen.domain.MAX_BOXING_ROUNDS
import io.github.alexanderop.eisen.domain.MAX_BOXING_ROUND_SECONDS
import io.github.alexanderop.eisen.domain.MIN_BOXING_REST_SECONDS
import io.github.alexanderop.eisen.domain.MIN_BOXING_ROUND_SECONDS
import io.github.alexanderop.eisen.domain.TimerCalls
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.boxingPlan
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.paceOf
import io.github.alexanderop.eisen.domain.roundSummary
import io.github.alexanderop.eisen.domain.templatePlan
import io.github.alexanderop.eisen.domain.themeCombos
import io.github.alexanderop.eisen.domain.timerSummary
import io.github.alexanderop.eisen.domain.elm.Model
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.NUMBERS_KEY
import io.github.alexanderop.eisen.domain.elm.TimerSetup
import io.github.alexanderop.eisen.domain.elm.boxingCallsSummary
import io.github.alexanderop.eisen.domain.elm.callsText
import io.github.alexanderop.eisen.domain.elm.comboGroups
import io.github.alexanderop.eisen.domain.elm.comboTotal
import io.github.alexanderop.eisen.domain.elm.drillOf
import io.github.alexanderop.eisen.domain.elm.defaultPresetName
import io.github.alexanderop.eisen.domain.elm.pickText
import io.github.alexanderop.eisen.domain.elm.planTotal
import io.github.alexanderop.eisen.domain.elm.presetOf
import io.github.alexanderop.eisen.domain.elm.roundCalls
import io.github.alexanderop.eisen.domain.elm.roundDetail
import io.github.alexanderop.eisen.domain.elm.themeExample
import io.github.alexanderop.eisen.domain.elm.timerWheelSeconds
import io.github.alexanderop.eisen.domain.elm.toggledPick
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.ReorderableColumnItem
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.rememberColumnReorderState
import io.github.alexanderop.eisen.ui.rememberFullSheetState
import io.github.alexanderop.eisen.ui.rememberHaptics
import io.github.alexanderop.eisen.ui.reorderContainer
import io.github.alexanderop.eisen.ui.reorderHandle
import kotlin.math.ceil

/*
 * The boxing timer's card on the Timers page and its setup: the fight card of rounds, the templates,
 * the calls, and the round sheet with the combinations to pick. Every edit is a Msg; the open round
 * sheet is `TimerSetup.round`.
 */

/** A theme's colour, the same as in the PWA. */
internal val BoxingTheme.tint: Color get() = Color(color)

/** The darker half of a theme's colour: the power stretches of a split round. */
internal val BoxingTheme.shade: Color get() = lerp(tint, Color.Black, 0.42f)

private val ChevronLeft = pathIcon("ChevronLeft", "m15 18-6-6 6-6")
private val PlayIcon = pathIcon("Play", "M7 4.5v15l12-7.5z", filled = true)
private val CopyIcon = pathIcon("Copy", "M8 8h12v12H8z", "M4 16V4h12")
private val UndoIcon = pathIcon("Undo", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")

// Lucide's grip-vertical, as in the routine editor and the WOD builder.
private val Grip = pathIcon(
    "GripVertical",
    "M7 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" +
        "M7 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" +
        "M7 19a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 19a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
    filled = true,
)

private val RowShape = RoundedCornerShape(14.dp)
private val TileShape = RoundedCornerShape(16.dp)

/** What the setup shows as an example of the calls: a real combination. */
private val EXAMPLE: BoxingCombo = BOXING_COMBOS.first { it.id == "jab-cross-hook" }

internal fun shown(combo: BoxingCombo, style: BoxingStyle): String = if (style == BoxingStyle.NUMBERS) combo.numbers else combo.names

/** A label over a part of the setup. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            Modifier.weight(1f).semantics { heading() },
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.1.em,
            color = EisenColors.MutedForeground,
        )
        trailing()
    }
}

/** One dot per round in its theme's colour: the plan at a glance on the mode card. */
@Composable
private fun PlanDots(plan: BoxingPlan, size: Dp = 12.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(size / 2), verticalAlignment = Alignment.CenterVertically) {
        for (round in plan.rounds) Box(Modifier.size(size).clip(CircleShape).background(round.theme.tint))
    }
}

// The Timers page

/** The boxing card over the whole width of the Timers page, with the plan as dots. */
@Composable
internal fun BoxingModeCard(config: TimerConfig, onClick: () -> Unit) {
    val plan = boxingPlan(config.boxing, config.rounds)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
        border = BorderStroke(1.dp, EisenColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(Modifier.size(width = 28.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(EisenColors.Primary))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(TimerMode.BOXING.title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(timerSummary(config), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EisenColors.Primary, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(modeLine(TimerMode.BOXING), fontSize = 14.sp, lineHeight = 18.sp, color = EisenColors.MutedForeground)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.clearAndSetSemantics { contentDescription = plan.rounds.joinToString(", ") { it.theme.title } }) { PlanDots(plan) }
        }
    }
}

// The timeline, on the result screen

/**
 * The whole workout to scale: a segment per round in its theme's colour, the rests as thin dark gaps
 * between them. A round split into speed and power shows its stretches as lighter and darker stripes.
 */
@Composable
internal fun BoxingTimeline(config: TimerConfig, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    val plan = boxingPlan(config.boxing, config.rounds)
    Row(modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        plan.rounds.forEachIndexed { index, round ->
            Box(
                Modifier
                    .weight(config.work.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .semantics { contentDescription = "Round ${index + 1}: ${roundSummary(round)}" },
            ) { Stripes(round, config.work, vertical = true) }
            if (index < plan.rounds.lastIndex) {
                Box(Modifier.weight(config.rest.toFloat()).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(EisenColors.Border))
                }
            }
        }
    }
}

/**
 * A round's colour, with its speed and power stretches as lighter and darker stripes: across the
 * time of a round on the timeline ([vertical] cuts), in bands on a round's tile.
 */
@Composable
private fun Stripes(round: BoxingRound, work: Int, vertical: Boolean, modifier: Modifier = Modifier) {
    val tint = round.theme.tint
    val shade = round.theme.shade
    Canvas(modifier.fillMaxSize()) {
        drawRect(tint)
        if (round.split <= 0) return@Canvas
        val stretches = if (vertical) ceil(work / round.split.toDouble()).toInt() else 4
        val length = if (vertical) size.width else size.height
        for (stretch in 1 until stretches step 2) {
            val from = if (vertical) length * stretch * round.split / work else length * stretch / stretches
            val to = if (vertical) minOf(length, length * (stretch + 1) * round.split / work) else length * (stretch + 1) / stretches
            if (vertical) drawRect(shade, topLeft = Offset(from, 0f), size = Size(to - from, size.height))
            else drawRect(shade, topLeft = Offset(0f, from), size = Size(size.width, to - from))
        }
    }
}

// The setup

@Composable
internal fun BoxingSetupScreen(model: Model, setup: TimerSetup, dispatch: (Msg) -> Unit) {
    val config = setup.config
    val plan = boxingPlan(config.boxing, config.rounds)
    val calls = model.state.settings.timerCalls
    var voiceOpen by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    val preset = presetOf(model, setup)
    val haptics = rememberHaptics()
    fun edit(change: (TimerConfig) -> TimerConfig) = dispatch(Msg.Timer.Edit(change(config)))
    val scroll = rememberScrollState()
    // A round is known by its place: the plan can hold the same round twice.
    val reorder = rememberColumnReorderState(
        keys = plan.rounds.indices.toList(),
        onMove = { from, to -> dispatch(Msg.Timer.MoveRound(from, to)) },
        scrollState = scroll,
    )

    Column(Modifier.fillMaxSize()) {
        SetupHeader(TimerMode.BOXING, dispatch)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val row = if (maxHeight < 420.dp) 44.dp else 52.dp
            Column(
                Modifier.fillMaxSize().reorderContainer(reorder).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (preset != null) {
                    OutlinedTextField(
                        value = setup.name,
                        onValueChange = { dispatch(Msg.Timer.EditName(it)) },
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundsWheel(config.rounds, row, Modifier.weight(1f), max = MAX_BOXING_ROUNDS) { v -> edit { it.copy(rounds = v) } }
                    SecondsWheel("Round", config.work, timerWheelSeconds(MIN_BOXING_ROUND_SECONDS, MAX_BOXING_ROUND_SECONDS, config.work), row, Modifier.weight(1f)) { v -> edit { it.copy(work = v) } }
                    SecondsWheel("Rest", config.rest, timerWheelSeconds(MIN_BOXING_REST_SECONDS, MAX_BOXING_REST_SECONDS, config.rest), row, Modifier.weight(1f)) { v -> edit { it.copy(rest = v) } }
                }

                SectionLabel("Templates")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BOXING_TEMPLATES.forEachIndexed { index, template ->
                            val applied = templatePlan(template.rounds, config.rounds, plan.style) == plan
                            TemplateChip(template.name, template.hint, applied) {
                                dispatch(Msg.Timer.ApplyTemplate(index))
                                haptics.perform(Haptic.TAP)
                            }
                        }
                    }
                    // The Undo of a template stands beside the chips, not in a snackbar over Start.
                    if (setup.beforeTemplate != null) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { dispatch(Msg.Timer.UndoTemplate) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Primary, contentColor = EisenColors.OnPrimary),
                        ) {
                            Icon(UndoIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Undo", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    }
                }

                // The fight card: the plan drawn once, as its rounds.
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(planTotal(config), Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    if (config.rounds > 1) {
                        Box(Modifier.size(width = 18.dp, height = 2.dp).background(EisenColors.MutedForeground))
                        Spacer(Modifier.width(6.dp))
                        Text("rest ${holdText(config.rest)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plan.rounds.forEachIndexed { index, round ->
                        key(index) {
                            ReorderableColumnItem(reorder, key = index, shape = TileShape) {
                                RoundTile(
                                    index = index,
                                    round = round,
                                    work = config.work,
                                    handle = Modifier.reorderHandle(reorder, index, enabled = plan.rounds.size > 1),
                                    canMove = plan.rounds.size > 1,
                                    moves = buildList {
                                        if (index > 0) add(CustomAccessibilityAction("Move earlier") { dispatch(Msg.Timer.MoveRound(index, index - 1)); true })
                                        if (index < plan.rounds.lastIndex) add(CustomAccessibilityAction("Move later") { dispatch(Msg.Timer.MoveRound(index, index + 1)); true })
                                    },
                                ) { dispatch(Msg.Timer.OpenRound(index)) }
                            }
                        }
                    }
                }

                SectionLabel("Calls")
                CallsChoice(plan.style) {
                    dispatch(Msg.Timer.SetStyle(it))
                    haptics.perform(Haptic.TAP)
                }

                SetupRow(VoiceIcon, "Voice", boxingCallsSummary(calls)) { voiceOpen = true }
                PresetRows(model, setup, dispatch) { naming = true }
            }
        }
        Button(
            onClick = { dispatch(Msg.Timer.Start) },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Start", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    if (setup.round != null) RoundSheet(setup, calls, dispatch)
    if (voiceOpen) VoiceSheet(calls, dispatch, onClose = { voiceOpen = false }, boxing = true)
    if (naming) {
        NameDialog(
            initial = defaultPresetName(TimerMode.BOXING, model.state.timerPresets),
            onSave = {
                dispatch(Msg.Timer.SavePreset(it))
                naming = false
            },
            onClose = { naming = false },
        )
    }
}

/** A template by its name. TalkBack hears its hint too. */
@Composable
private fun TemplateChip(name: String, hint: String, applied: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (applied) EisenColors.Primary.copy(alpha = 0.12f) else EisenColors.Card)
            .border(if (applied) 2.dp else 1.dp, if (applied) EisenColors.Primary else EisenColors.Border, shape)
            .clickable(role = Role.Button, onClickLabel = "Apply") { onClick() }
            .clearAndSetSemantics { contentDescription = "$name template. $hint" }
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = EisenColors.Foreground, maxLines = 1)
    }
}

/**
 * One round of the fight card: a wide block in its theme's colour with its number (in stripes when it
 * is split into speed and power), its theme large and what it does, and the handle to drag it.
 */
@Composable
private fun RoundTile(
    index: Int,
    round: BoxingRound,
    work: Int,
    handle: Modifier,
    canMove: Boolean,
    moves: List<CustomAccessibilityAction>,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(TileShape)
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, TileShape)
            .clickable(role = Role.Button, onClickLabel = "Edit round ${index + 1}", onClick = onClick)
            .semantics(mergeDescendants = true) { customActions = moves }
            .heightIn(min = 76.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Stripes(round, work, vertical = false)
            Text("${index + 1}", color = EisenColors.OnPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Text(round.theme.title, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, color = EisenColors.Foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(roundDetail(round), fontSize = 14.sp, color = EisenColors.MutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (canMove) {
            Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Grip, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(20.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** "Names" or "Numbers", with a real combination as it would be shown, and the key of the numbers. */
@Composable
private fun CallsChoice(style: BoxingStyle, onChange: (BoxingStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val options = listOf(BoxingStyle.NAMES to "Names", BoxingStyle.NUMBERS to "Numbers")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = style == option,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                    colors = segmentColors(),
                    label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RowShape).background(EisenColors.Card).border(1.dp, EisenColors.Border, RowShape).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(shown(EXAMPLE, style), fontSize = 26.sp, fontWeight = FontWeight.Black, color = EisenColors.Foreground, textAlign = TextAlign.Center, maxLines = 1)
            Text(
                if (style == BoxingStyle.NUMBERS) "the coach says “One, two, three”" else "the coach says “Jab, cross, hook”",
                fontSize = 13.sp,
                color = EisenColors.MutedForeground,
            )
        }
        // "B to the body" stays on one line.
        Text(NUMBERS_KEY.replace("B to the body", "B\u00A0to\u00A0the\u00A0body"), Modifier.fillMaxWidth(), fontSize = 13.sp, color = EisenColors.MutedForeground, textAlign = TextAlign.Center)
    }
}

// The round sheet

/**
 * One round: its theme, which of its combinations, the pace, speed and power, and what can be done
 * with the round. It keeps its height whatever the theme, so nothing jumps under the thumb; "Done"
 * stays at its bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundSheet(setup: TimerSetup, calls: TimerCalls, dispatch: (Msg) -> Unit) {
    val index = setup.round ?: return
    val config = setup.config
    val plan = boxingPlan(config.boxing, config.rounds)
    val round = plan.rounds.getOrNull(index) ?: return
    val haptics = rememberHaptics()
    val sheetState = rememberFullSheetState()
    var picking by rememberSaveable { mutableStateOf(false) }
    fun change(next: BoxingRound) {
        dispatch(Msg.Timer.EditRound(index, next))
        haptics.perform(Haptic.STEP)
    }
    val free = round.theme == BoxingTheme.FREESTYLE
    ModalBottomSheet(onDismissRequest = { dispatch(Msg.Timer.CloseRound) }, sheetState = sheetState, containerColor = EisenColors.Card) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dispatch(Msg.Timer.OpenRound(index - 1)) }, enabled = index > 0) {
                    Icon(ChevronLeft, contentDescription = "Previous round")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Round ${index + 1}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
                    Text("of ${config.rounds}", fontSize = 13.sp, color = EisenColors.MutedForeground)
                }
                IconButton(onClick = { dispatch(Msg.Timer.OpenRound(index + 1)) }, enabled = index < config.rounds - 1) {
                    Icon(Icons.ChevronRight, contentDescription = "Next round")
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeGrid(round.theme, plan.style) { change(round.copy(theme = it)) }
                Text(
                    if (free) round.theme.hint else "${round.theme.hint} · ${comboTotal(round.theme)}",
                    fontSize = 14.sp,
                    color = EisenColors.MutedForeground,
                )

                if (free) {
                    // In place of the coach's settings, one calm block of about their height.
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 300.dp).clip(RowShape).background(EisenColors.Muted).padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No calls in a freestyle round. You box, the clock runs.",
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = EisenColors.MutedForeground,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    SheetRow(pickText(round), "Choose the combinations of this round") { picking = true }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pace", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        val pace = paceOf(round.every)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (option in BOXING_PACES) {
                                Chip(
                                    option.name,
                                    selected = option.name == pace,
                                    tint = round.theme.tint,
                                    modifier = Modifier.weight(1f),
                                    description = "${option.name}: a call every ${option.every} seconds",
                                ) { change(round.copy(every = option.every)) }
                            }
                        }
                        Text(
                            callsText(roundCalls(config, calls, index), config.work, drillOf(round)),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            fontSize = 14.sp,
                            color = EisenColors.MutedForeground,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Speed and power", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (split in BOXING_SPLITS) {
                                Chip(if (split == 0) "Off" else "${split}s", round.split == split, round.theme.tint, Modifier.weight(1f)) { change(round.copy(split = split)) }
                            }
                        }
                        Text(
                            if (round.split > 0) "Speed and power take turns. The coach calls faster in speed and leaves room in power."
                            else "One pace all round.",
                            fontSize = 14.sp,
                            color = EisenColors.MutedForeground,
                            minLines = 2,
                        )
                    }

                    ListenButton(setup.heard?.let { id -> BOXING_COMBOS.firstOrNull { it.id == id } }?.let { shown(it, plan.style) }, round.theme.tint) {
                        dispatch(Msg.Timer.Listen(index))
                    }
                }

                Text("This round", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Move earlier", enabled = index > 0, Modifier.weight(1f)) { dispatch(Msg.Timer.MoveRound(index, index - 1)) }
                    ActionButton("Move later", enabled = index < config.rounds - 1, Modifier.weight(1f)) { dispatch(Msg.Timer.MoveRound(index, index + 1)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Duplicate", enabled = config.rounds < MAX_BOXING_ROUNDS, Modifier.weight(1f)) { dispatch(Msg.Timer.DuplicateRound(index)) }
                    ActionButton("Remove", enabled = config.rounds > 1, Modifier.weight(1f), destructive = true) { dispatch(Msg.Timer.RemoveRound(index)) }
                }
                OutlinedButton(
                    onClick = { dispatch(Msg.Timer.CopyToAll(index)) },
                    enabled = !setup.copied && config.rounds > 1,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EisenColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EisenColors.Foreground, disabledContentColor = if (setup.copied) EisenColors.Primary else EisenColors.MutedForeground),
                ) {
                    Icon(if (setup.copied) Icons.Check else CopyIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (setup.copied) "Copied to all ${config.rounds} rounds" else "Copy to all rounds",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            Button(
                onClick = { dispatch(Msg.Timer.CloseRound) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        }
    }
    if (picking && !free) PickSheet(round, plan.style, onPick = { change(round.copy(pick = it)) }, onPlay = { dispatch(Msg.Timer.PlayCombo(it)) }) { picking = false }
}

/** A row that opens something: "Combinations: all 7". */
@Composable
private fun SheetRow(label: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(EisenColors.Muted)
            .clickable(role = Role.Button, onClickLabel = hint, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(18.dp))
    }
}

/** "Listen", with the combination that played last under it. */
@Composable
private fun ListenButton(heard: String?, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(EisenColors.Muted)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(PlayIcon, contentDescription = null, tint = EisenColors.OnPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Listen", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                heard ?: "Hear a call of this round",
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                fontSize = 14.sp,
                color = if (heard != null) EisenColors.Foreground else EisenColors.MutedForeground,
                fontWeight = if (heard != null) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, modifier: Modifier, destructive: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = EisenColors.Muted,
            contentColor = if (destructive) EisenColors.Destructive else EisenColors.Foreground,
        ),
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1) }
}

/** The nine themes, three by three: the colour down the left edge, the name and an example. */
@Composable
private fun ThemeGrid(chosen: BoxingTheme, style: BoxingStyle, onChoose: (BoxingTheme) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in BOXING_THEMES.chunked(3)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (theme in row) {
                    val selected = theme == chosen
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 80.dp)
                            .clip(shape)
                            .background(if (selected) theme.tint.copy(alpha = 0.16f) else EisenColors.Muted)
                            .border(if (selected) 2.dp else 1.dp, if (selected) theme.tint else EisenColors.Border, shape)
                            .selectable(selected = selected, role = Role.RadioButton) { onChoose(theme) }
                            .semantics(mergeDescendants = true) { contentDescription = "${theme.title}. ${theme.hint}" },
                    ) {
                        Box(Modifier.width(8.dp).fillMaxHeight().background(theme.tint))
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(theme.title, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) theme.tint else EisenColors.Foreground, maxLines = 2)
                            Spacer(Modifier.height(3.dp))
                            Text(themeExample(theme, style), fontSize = 12.sp, lineHeight = 15.sp, color = EisenColors.MutedForeground, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, tint: Color, modifier: Modifier, description: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) tint else EisenColors.Muted)
            .border(1.dp, if (selected) tint else EisenColors.Border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) EisenColors.OnPrimary else EisenColors.Foreground, maxLines = 1)
    }
}

// The combinations of a round

/**
 * The combinations of the round's theme, each with a check mark, in both styles, and a play button.
 * None checked means all. "None" starts from an empty list to check a few; it changes nothing until
 * one is checked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickSheet(round: BoxingRound, style: BoxingStyle, onPick: (List<String>) -> Unit, onPlay: (String) -> Unit, onClose: () -> Unit) {
    val sheetState = rememberFullSheetState()
    var fromNone by rememberSaveable(round.theme) { mutableStateOf(false) }
    val all = themeCombos(round.theme).size
    fun checked(id: String) = if (round.pick.isEmpty()) !fromNone else id in round.pick
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = EisenColors.Card) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(round.theme.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = round.theme.tint, modifier = Modifier.semantics { heading() })
                    Text(
                        if (round.pick.isEmpty()) "All $all are called" else "${round.pick.size} of $all are called",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        fontSize = 14.sp,
                        color = EisenColors.MutedForeground,
                    )
                }
                TextButton(onClick = { fromNone = false; onPick(emptyList()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("All", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { fromNone = true; onPick(emptyList()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("None", fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                for ((theme, combos) in comboGroups(round.theme)) {
                    if (round.theme == BoxingTheme.MIX) {
                        Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(theme.tint))
                            Spacer(Modifier.width(8.dp))
                            Text(theme.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = EisenColors.MutedForeground, modifier = Modifier.semantics { heading() })
                        }
                    }
                    for (combo in combos) {
                        val on = checked(combo.id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = on, role = Role.Checkbox) {
                                    val next = if (round.pick.isEmpty() && fromNone) listOf(combo.id) else toggledPick(round, combo.id)
                                    fromNone = false
                                    onPick(next)
                                }
                                .heightIn(min = 60.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (on) round.theme.tint else Color.Transparent)
                                    .border(2.dp, if (on) round.theme.tint else EisenColors.MutedForeground, RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(Icons.Check, contentDescription = null, tint = EisenColors.OnPrimary, modifier = Modifier.size(18.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shown(combo, style), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(shown(combo, if (style == BoxingStyle.NUMBERS) BoxingStyle.NAMES else BoxingStyle.NUMBERS), fontSize = 13.sp, color = EisenColors.MutedForeground)
                            }
                            IconButton(onClick = { onPlay(combo.id) }) {
                                Icon(PlayIcon, contentDescription = "Hear ${combo.names}", tint = EisenColors.Foreground, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        }
    }
}
