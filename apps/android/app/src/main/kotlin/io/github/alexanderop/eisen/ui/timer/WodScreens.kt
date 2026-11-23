package io.github.alexanderop.eisen.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import io.github.alexanderop.eisen.domain.wodName
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import io.github.alexanderop.eisen.domain.elm.WOD_EXERCISES
import io.github.alexanderop.eisen.ui.picker.WOD_FILTER
import io.github.alexanderop.eisen.domain.elm.maxHint
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.FindExercise
import io.github.alexanderop.eisen.domain.MAX_TIMER_ROUNDS
import io.github.alexanderop.eisen.domain.MAX_WOD_ITEMS
import io.github.alexanderop.eisen.domain.MAX_WOD_ROUNDS
import io.github.alexanderop.eisen.domain.SCHEMES
import io.github.alexanderop.eisen.domain.TIMER_MODES
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Wod
import io.github.alexanderop.eisen.domain.WodItem
import io.github.alexanderop.eisen.domain.WodUnit
import io.github.alexanderop.eisen.domain.bestText
import io.github.alexanderop.eisen.domain.distanceText
import io.github.alexanderop.eisen.domain.formatNumber
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.isLibraryWod
import io.github.alexanderop.eisen.domain.loadFrom
import io.github.alexanderop.eisen.domain.loadIn
import io.github.alexanderop.eisen.domain.timerSummary
import io.github.alexanderop.eisen.domain.wod
import io.github.alexanderop.eisen.domain.wodBest
import io.github.alexanderop.eisen.domain.wodFormat
import io.github.alexanderop.eisen.domain.wodImages
import io.github.alexanderop.eisen.domain.wodLines
import io.github.alexanderop.eisen.domain.elm.AddTarget
import io.github.alexanderop.eisen.domain.elm.Model
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.WodDraft
import io.github.alexanderop.eisen.domain.elm.WodFilter
import io.github.alexanderop.eisen.domain.elm.benchmarks
import io.github.alexanderop.eisen.domain.elm.callsSummary
import io.github.alexanderop.eisen.domain.elm.draftEditsOwn
import io.github.alexanderop.eisen.domain.elm.exercises
import io.github.alexanderop.eisen.domain.elm.lastWodRun
import io.github.alexanderop.eisen.domain.elm.parseScheme
import io.github.alexanderop.eisen.domain.elm.timerCapSeconds
import io.github.alexanderop.eisen.domain.elm.timerWheelSeconds
import io.github.alexanderop.eisen.domain.elm.withMax
import io.github.alexanderop.eisen.domain.elm.withUnit
import io.github.alexanderop.eisen.domain.elm.wodAmounts
import io.github.alexanderop.eisen.domain.elm.wodFind
import io.github.alexanderop.eisen.domain.elm.wodItemSummary
import io.github.alexanderop.eisen.domain.elm.wodLoads
import io.github.alexanderop.eisen.domain.elm.wodRows
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.ExerciseMotion
import io.github.alexanderop.eisen.ui.ExerciseTile
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.ReorderableColumnItem
import io.github.alexanderop.eisen.ui.Wheel
import io.github.alexanderop.eisen.ui.formatShortDate
import io.github.alexanderop.eisen.ui.keyboardPadding
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.picker.ExercisePickerSheet
import io.github.alexanderop.eisen.ui.rememberColumnReorderState
import io.github.alexanderop.eisen.ui.rememberFullSheetState
import io.github.alexanderop.eisen.ui.reorderContainer
import io.github.alexanderop.eisen.ui.reorderHandle
import kotlinx.coroutines.launch

/*
 * The WODs page of the Timer tab, a WOD's detail and the builder. What they show comes from the
 * model; which sheet is open stays here.
 */

private val CardShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(14.dp)

// Lucide's grip-vertical, as in the routine editor.
private val Grip = pathIcon(
    "GripVertical",
    "M7 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" +
        "M7 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" +
        "M7 19a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 19a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
    filled = true,
)

private val CopyIcon = pathIcon("Copy", "M8 8h12v12H8z", "M4 16V4h12")

/** A row of chips that scrolls out to the screen's edges, past the padding of the page it sits in. */
private fun Modifier.bleed(by: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp).semantics { heading() }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = EisenColors.Muted,
    labelColor = EisenColors.MutedForeground,
    selectedContainerColor = EisenColors.Primary,
    selectedLabelColor = EisenColors.OnPrimary,
)

// The list

/** A draft from before the process ended, "New WOD", the lifter's own and the benchmarks with their filter. */
@Composable
internal fun WodsPage(model: Model, dispatch: (Msg) -> Unit) {
    val find = wodFind(model)
    val unit = model.state.settings.unit
    val sessions = model.state.timerSessions
    val own = model.state.wods
    val library = benchmarks(model)
    val list = rememberLazyListState()
    // A tap on the Timer tab while it shows goes back to the top, once per tap.
    var top by rememberSaveable { mutableIntStateOf(model.nav.timerTop) }
    LaunchedEffect(model.nav.timerTop) {
        if (model.nav.timerTop != top) {
            top = model.nav.timerTop
            list.animateScrollToItem(0)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        model.wods.pending?.let { pending ->
            item(key = "pending") { PendingDraft(wodName(pending.wod), dispatch) }
        }
        item(key = "new") {
            FilledTonalButton(
                onClick = { dispatch(Msg.Wods.New) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
            ) {
                Icon(Icons.Plus, contentDescription = null, tint = EisenColors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("New WOD", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
        if (own.isNotEmpty()) {
            item(key = "own") { SectionTitle("My WODs") }
            items(own, key = { "own:" + it.id }) { wod ->
                WodCard(wod, find, unit, wodBest(sessions, wod.id), onOpen = { dispatch(Msg.Wods.Open(wod.id)) }, onStart = { dispatch(Msg.Wods.Start(wod.id)) })
            }
        }
        item(key = "benchmarks") { SectionTitle("Benchmarks") }
        item(key = "filters") {
            LazyRow(Modifier.bleed(16.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WodFilter.entries, key = { it.name }) { filter ->
                    FilterChip(
                        selected = model.wods.filter == filter,
                        onClick = { dispatch(Msg.Wods.Filter(filter)) },
                        label = { Text(filter.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
                        modifier = Modifier.height(40.dp),
                        shape = CircleShape,
                        colors = chipColors(),
                        border = null,
                    )
                }
            }
        }
        items(library, key = { it.id }) { wod ->
            WodCard(wod, find, unit, wodBest(sessions, wod.id), onOpen = { dispatch(Msg.Wods.Open(wod.id)) }, onStart = { dispatch(Msg.Wods.Start(wod.id)) })
        }
    }
}

/** The draft the builder had when the process ended: nothing typed is lost. */
@Composable
private fun PendingDraft(name: String, dispatch: (Msg) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(EisenColors.Card).border(1.dp, EisenColors.Primary.copy(alpha = 0.6f), CardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Continue editing $name", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { dispatch(Msg.Wods.ContinueDraft) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RowShape,
            ) { Text("Continue", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            FilledTonalButton(
                onClick = { dispatch(Msg.Wods.DiscardDraft) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RowShape,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Destructive),
            ) { Text("Discard", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

/**
 * A WOD as a card: its first movement's picture, name, format and movements, and its best run. The
 * whole card opens the detail, the play button starts it at once. Without [onOpen] it is the
 * builder's preview, without [onStart] it has no play button.
 */
@Composable
internal fun WodCard(wod: Wod, find: FindExercise, unit: WeightUnit, best: TimerSession?, onOpen: (() -> Unit)?, onStart: (() -> Unit)?) {
    val colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground)
    val border = BorderStroke(1.dp, EisenColors.Border)
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val first = wod.items.firstNotNullOfOrNull { find(it.exerciseId) }
            ExerciseTile(first?.image, first?.name ?: "?", size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(wodName(wod), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (wodFormat(wod) != wodName(wod)) {
                    Text(wodFormat(wod), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EisenColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    wodLines(wod, find, unit).joinToString(" · ").ifEmpty { "No movements yet" },
                    fontSize = 13.sp,
                    color = EisenColors.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (best != null) Text("Best ${bestText(best)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EisenColors.Foreground)
            }
            if (onStart != null) {
                Spacer(Modifier.width(8.dp))
                // Straight into the lead-in; the rest of the card opens the detail.
                FilledIconButton(
                    onClick = onStart,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = EisenColors.Primary, contentColor = EisenColors.OnPrimary),
                ) { Icon(StartIcon, contentDescription = "Start ${wodName(wod)}", modifier = Modifier.size(20.dp)) }
            }
        }
    }
    if (onOpen != null) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = CardShape, colors = colors, border = border) { content() }
    } else {
        Card(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }, shape = CardShape, colors = colors, border = border) { content() }
    }
}

// The detail

/** One column of the Best and Last block: the number big, its day under it. "Nov 12" reads at a glance. */
@Composable
private fun RunColumn(label: String, session: TimerSession, strong: Boolean, modifier: Modifier) {
    Column(modifier.clip(RowShape).background(EisenColors.Card).border(1.dp, EisenColors.Border, RowShape).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = EisenColors.MutedForeground)
        Text(bestText(session), fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (strong) EisenColors.Primary else EisenColors.Foreground)
        Text(formatShortDate(session.startedAt), fontSize = 14.sp, color = EisenColors.MutedForeground)
    }
}

@Composable
internal fun WodDetail(model: Model, wod: Wod, dispatch: (Msg) -> Unit) {
    val find = wodFind(model)
    val unit = model.state.settings.unit
    val sessions = model.state.timerSessions
    val calls = model.state.settings.timerCalls
    val best = wodBest(sessions, wod.id)
    val last = lastWodRun(sessions, wod.id)
    var voiceOpen by rememberSaveable { mutableStateOf(false) }
    // The row whose movement shows large.
    var shown by rememberSaveable { mutableStateOf<Int?>(null) }
    val rows = wodRows(wod, find, unit)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dispatch(Msg.Wods.Close) }) { Icon(Icons.Back, contentDescription = "Back to the WODs") }
            Text(
                wodName(wod),
                Modifier.weight(1f).semantics { heading() },
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The format once: under a name, or as the title of an unnamed WOD.
            Column {
                if (wodFormat(wod) != wodName(wod)) {
                    Text(wodFormat(wod), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, color = EisenColors.Primary)
                }
                Text(timerSummary(wod.timer), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground)
            }
            Column(
                Modifier.fillMaxWidth().clip(CardShape).background(EisenColors.Card).border(1.dp, EisenColors.Border, CardShape),
            ) {
                rows.forEachIndexed { index, (exercise, line) ->
                    if (index > 0) HorizontalDivider(color = EisenColors.Border, modifier = Modifier.padding(start = 84.dp))
                    // The whole row opens the movement large, so TalkBack reads its line as the button's name.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Show the movement") { shown = index }
                            .heightIn(min = 76.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseMotion(exercise, size = 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(line, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
                    }
                }
                if (rows.isEmpty()) Text("No movements", Modifier.padding(16.dp), color = EisenColors.MutedForeground)
            }
            if (best != null || last != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (best != null) RunColumn("Best", best, strong = true, modifier = Modifier.weight(1f))
                    if (last != null) RunColumn("Last", last, strong = false, modifier = Modifier.weight(1f))
                }
            }
            SetupRow(VoiceIcon, "Voice", callsSummary(calls)) { voiceOpen = true }
            if (isLibraryWod(wod.id)) {
                SecondaryButton("Edit a copy", CopyIcon, Modifier.fillMaxWidth()) { dispatch(Msg.Wods.Copy(wod.id)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Edit", Icons.Pencil, Modifier.weight(1f)) { dispatch(Msg.Wods.Edit(wod.id)) }
                    // Gone at once, with Undo in the snackbar, like a saved timer.
                    SecondaryButton("Delete", Icons.Trash, Modifier.weight(1f), destructive = true) { dispatch(Msg.Wods.Delete(wod.id)) }
                }
            }
        }
        Button(
            onClick = { dispatch(Msg.Wods.Start(wod.id)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(StartIcon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Start", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
    if (voiceOpen) VoiceSheet(calls, dispatch, onClose = { voiceOpen = false })
    val open = shown
    val row = open?.let { rows.getOrNull(it) }
    if (row != null) MotionSheet(row.first, row.second, onClose = { shown = null })
    else if (open != null) SideEffect { shown = null }
}

/** One movement of a WOD large: the figure doing it, its name and its line ("21 × Thruster 43kg"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotionSheet(exercise: Exercise, line: String, onClose: () -> Unit) {
    val sheet = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = EisenColors.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExerciseMotion(exercise, size = 240.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(exercise.name, Modifier.semantics { heading() }, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                // Under a scheme the line is the name alone.
                if (line != exercise.name) {
                    Text(line, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground, textAlign = TextAlign.Center)
                }
            }
            Button(
                onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RowShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = EisenColors.Muted,
            contentColor = if (destructive) EisenColors.Destructive else EisenColors.Foreground,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// The builder

/** The number rows of each mode, and the wheel sheet each one opens. */
private enum class Number { ROUNDS, CAP, TIME, EVERY, WORK, REST, INTERVALS }

private fun schemeLabel(scheme: List<Int>): String = when (scheme) {
    SCHEMES[1] -> "10 down to 1"
    SCHEMES[3] -> "1 up to 10"
    else -> scheme.joinToString("-")
}

@Composable
internal fun WodBuilder(model: Model, draft: WodDraft, dispatch: (Msg) -> Unit) {
    val wod = draft.wod
    val find = wodFind(model)
    val unit = model.state.settings.unit
    val mode = wod.timer.mode
    var picking by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var number by rememberSaveable { mutableStateOf<Number?>(null) }
    var custom by rememberSaveable { mutableStateOf(wod.scheme.isNotEmpty() && wod.scheme !in SCHEMES) }
    // The picker's search and chip stay while the builder is open.
    val pickerQuery = rememberSaveable { mutableStateOf("") }
    val pickerFilter = rememberSaveable { mutableStateOf(WOD_FILTER) }
    var customText by rememberSaveable { mutableStateOf(if (custom) wod.scheme.joinToString("-") else "") }
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    // One exercise can be in a WOD twice, so the items are known by their place.
    val reorder = rememberColumnReorderState(
        keys = wod.items.indices.toList(),
        onMove = { from, to ->
            dispatch(Msg.Wods.MoveItem(from, to))
            editing = null
        },
        scrollState = scroll,
    )
    fun timer(change: (TimerConfig) -> TimerConfig) = dispatch(Msg.Wods.SetTimer(change(wod.timer)))

    Column(Modifier.fillMaxSize().keyboardPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dispatch(Msg.Wods.Leave) }) { Icon(Icons.Back, contentDescription = "Leave the builder") }
            Text(
                if (draftEditsOwn(model, draft)) "Edit WOD" else "New WOD",
                Modifier.weight(1f).semantics { heading() },
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().reorderContainer(reorder).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = wod.name,
                onValueChange = { dispatch(Msg.Wods.Rename(it)) },
                placeholder = { Text("Name your WOD") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(TimerMode.FOR_TIME, TimerMode.AMRAP, TimerMode.EMOM, TimerMode.TABATA)
                modes.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { dispatch(Msg.Wods.SetMode(option)) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        icon = {},
                        colors = segmentColors(),
                        label = { Text(option.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false) },
                    )
                }
            }
            when (mode) {
                TimerMode.FOR_TIME -> {
                    val schemed = wod.scheme.isNotEmpty()
                    if (schemed) ValueRow("Scheme", wod.scheme.joinToString("-"), enabled = false) {}
                    else ValueRow("Rounds", wod.rounds.toString()) { number = Number.ROUNDS }
                    Text("Reps per round", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground, modifier = Modifier.padding(bottom = 0.dp))
                    LazyRow(Modifier.bleed(16.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            SchemeChip("Same each round", !schemed && !custom) {
                                custom = false
                                dispatch(Msg.Wods.SetScheme(emptyList()))
                            }
                        }
                        items(SCHEMES) { scheme ->
                            SchemeChip(schemeLabel(scheme), !custom && wod.scheme == scheme) {
                                custom = false
                                dispatch(Msg.Wods.SetScheme(scheme))
                            }
                        }
                        item {
                            SchemeChip("Custom", custom) {
                                custom = true
                                dispatch(Msg.Wods.SetScheme(parseScheme(customText)))
                            }
                        }
                    }
                    if (custom) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = {
                                customText = it.take(48)
                                dispatch(Msg.Wods.SetScheme(parseScheme(customText)))
                            },
                            label = { Text("Reps per round") },
                            placeholder = { Text("30-20-10") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ValueRow("Time cap", if (wod.timer.work == 0) "Off" else holdText(wod.timer.work)) { number = Number.CAP }
                }
                TimerMode.AMRAP -> ValueRow("Time", holdText(wod.timer.work)) { number = Number.TIME }
                TimerMode.EMOM -> {
                    ValueRow("Every", holdText(wod.timer.work)) { number = Number.EVERY }
                    ValueRow("Rounds", wod.timer.rounds.toString()) { number = Number.INTERVALS }
                }
                TimerMode.TABATA -> {
                    ValueRow("Work", holdText(wod.timer.work)) { number = Number.WORK }
                    ValueRow("Rest", holdText(wod.timer.rest)) { number = Number.REST }
                    ValueRow("Rounds", wod.timer.rounds.toString()) { number = Number.INTERVALS }
                }
            }

            SectionTitle("Movements")
            wod.items.forEachIndexed { index, item ->
                key(index) {
                    ReorderableColumnItem(reorder, key = index, shape = RowShape) {
                        // The drag without a gesture, for TalkBack, like the routine editor.
                        val moves = buildList {
                            if (index > 0) add(CustomAccessibilityAction("Move up") { dispatch(Msg.Wods.MoveItem(index, index - 1)); true })
                            if (index < wod.items.lastIndex) add(CustomAccessibilityAction("Move down") { dispatch(Msg.Wods.MoveItem(index, index + 1)); true })
                        }
                        ItemRow(
                            item = item,
                            exercise = find(item.exerciseId),
                            summary = wodItemSummary(item, wod, unit),
                            handle = Modifier.reorderHandle(reorder, index, enabled = wod.items.size > 1),
                            canMove = wod.items.size > 1,
                            moves = moves,
                        ) { editing = index }
                    }
                }
            }
            AddMovement(enabled = wod.items.size < MAX_WOD_ITEMS) { picking = true }

            SectionTitle("Preview")
            WodCard(wod(wod), find, unit, wodBest(model.state.timerSessions, wod.id), onOpen = null, onStart = null)
        }
        Button(
            onClick = { dispatch(Msg.Wods.Save) },
            enabled = wod.items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (wod.items.isEmpty()) "Add a movement to save" else "Save", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    when (number) {
        Number.ROUNDS -> WheelSheet("Rounds", (1..MAX_WOD_ROUNDS).toList(), wod.rounds, { it.toString() }, { "$it rounds" }, onClose = { number = null }) {
            dispatch(Msg.Wods.SetRounds(it))
        }
        Number.CAP -> WheelSheet("Time cap", timerCapSeconds(wod.timer.work), wod.timer.work, { if (it == 0) "Off" else holdText(it) }, { spokenSeconds(it, "Off") }, onClose = { number = null }) { v ->
            timer { it.copy(work = v) }
        }
        Number.TIME -> WheelSheet("Time", timerWheelSeconds(30, 3600, wod.timer.work), wod.timer.work, ::holdText, { spokenSeconds(it, null) }, onClose = { number = null }) { v ->
            timer { it.copy(work = v) }
        }
        Number.EVERY -> WheelSheet("Every", timerWheelSeconds(10, 600, wod.timer.work), wod.timer.work, ::holdText, { spokenSeconds(it, null) }, onClose = { number = null }) { v ->
            timer { it.copy(work = v) }
        }
        Number.WORK -> WheelSheet("Work", timerWheelSeconds(5, 600, wod.timer.work), wod.timer.work, ::holdText, { spokenSeconds(it, null) }, onClose = { number = null }) { v ->
            timer { it.copy(work = v) }
        }
        Number.REST -> WheelSheet("Rest", timerWheelSeconds(5, 300, wod.timer.rest), wod.timer.rest, ::holdText, { spokenSeconds(it, null) }, onClose = { number = null }) { v ->
            timer { it.copy(rest = v) }
        }
        Number.INTERVALS -> WheelSheet("Rounds", (1..MAX_TIMER_ROUNDS).toList(), wod.timer.rounds, { it.toString() }, { "$it rounds" }, onClose = { number = null }) { v ->
            timer { it.copy(rounds = v) }
        }
        null -> Unit
    }

    val open = editing
    val item = open?.let { wod.items.getOrNull(it) }
    if (open != null && item != null) {
        ItemSheet(
            item = item,
            exercise = find(item.exerciseId),
            byScheme = wod.scheme.isNotEmpty() && item.unit == WodUnit.REPS,
            tabata = mode == TimerMode.TABATA,
            unit = unit,
            onChange = { dispatch(Msg.Wods.UpdateItem(open, it)) },
            onRemove = {
                dispatch(Msg.Wods.RemoveItem(open, item.exerciseId))
                editing = null
            },
            onClose = { editing = null },
        )
    } else if (open != null) {
        // Removed meanwhile, or the WOD was already full.
        SideEffect { editing = null }
    }

    if (picking) {
        // Several at once, in the order they were ticked. Their rows are there to tap afterwards.
        ExercisePickerSheet(
            exercises = exercises(model),
            exclude = emptySet(),
            onPick = { id -> dispatch(Msg.Wods.AddItem(id)) },
            onDismiss = { picking = false },
            onCreate = { name, equipment, timed ->
                dispatch(Msg.Exercises.AddCustom(name, equipment, addTo = AddTarget.Wod, timed = timed))
                picking = false
            },
            onDelete = { id -> dispatch(Msg.Exercises.DeleteCustom(id)) },
            multi = true,
            onPickMany = { ids ->
                dispatch(Msg.Wods.AddItems(ids))
                picking = false
            },
            wodIds = WOD_EXERCISES,
            query = pickerQuery,
            filter = pickerFilter,
        )
    }

    if (draft.asking) {
        AlertDialog(
            onDismissRequest = { dispatch(Msg.Wods.KeepEditing) },
            title = { Text("Discard changes?") },
            text = { Text("What you changed in this WOD is not saved.") },
            confirmButton = {
                TextButton(
                    onClick = { dispatch(Msg.Wods.Discard) },
                    colors = ButtonDefaults.textButtonColors(contentColor = EisenColors.Destructive),
                ) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { dispatch(Msg.Wods.KeepEditing) }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun SchemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        modifier = Modifier.height(40.dp),
        shape = CircleShape,
        colors = chipColors(),
        border = null,
    )
}

/** A number of the WOD: its name, its value, and a tap opens its wheel. */
@Composable
private fun ValueRow(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, RowShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Change $label", onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = EisenColors.MutedForeground)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (enabled) EisenColors.Foreground else EisenColors.Primary)
        if (enabled) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ItemRow(item: WodItem, exercise: Exercise?, summary: String, handle: Modifier, canMove: Boolean, moves: List<CustomAccessibilityAction>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, RowShape)
            .clickable(role = Role.Button, onClickLabel = "Change", onClick = onClick)
            .semantics { customActions = moves }
            .heightIn(min = 68.dp)
            .padding(end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A drag that starts here moves the row at once; a tap counts as a tap on the row.
        Box(Modifier.size(48.dp).then(handle), contentAlignment = Alignment.Center) {
            Icon(Grip, contentDescription = null, tint = if (canMove) EisenColors.MutedForeground else EisenColors.Border, modifier = Modifier.size(20.dp))
        }
        ExerciseTile(exercise?.image, exercise?.name ?: "?")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(exercise?.name ?: "Deleted exercise", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, fontSize = 14.sp, color = if (item.amount == 0) EisenColors.Primary else EisenColors.MutedForeground)
        }
        Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddMovement(enabled: Boolean, onClick: () -> Unit) {
    val tone = if (enabled) EisenColors.Primary else EisenColors.Border
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RowShape)
            .drawBehind {
                val width = 1.5.dp.toPx()
                drawRoundRect(
                    color = tone,
                    topLeft = androidx.compose.ui.geometry.Offset(width / 2, width / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Plus, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (enabled) "Add movement" else "$MAX_WOD_ITEMS movements at most", color = tone, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

/** One number of the WOD on a wheel. It applies as the wheel settles; "Done" closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WheelSheet(
    title: String,
    choices: List<Int>,
    value: Int,
    label: (Int) -> String,
    spoken: (Int) -> String,
    onClose: () -> Unit,
    onChange: (Int) -> Unit,
) {
    val sheet = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = EisenColors.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .align(Alignment.CenterHorizontally)
                    .clip(RowShape)
                    .background(EisenColors.Background)
                    .border(1.dp, EisenColors.Border, RowShape)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Wheel(
                    labels = remember(choices) { choices.map(label) },
                    selected = choices.indexOf(value).coerceAtLeast(0),
                    onSettle = { onChange(choices[it]) },
                    label = title,
                    spoken = { spoken(choices[it]) },
                    rowHeight = 56.dp,
                )
            }
            Button(
                onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        }
    }
}

private fun amountLabel(unit: WodUnit, amount: Int): String = when (unit) {
    WodUnit.REPS -> amount.toString()
    WodUnit.SECONDS -> holdText(amount)
    WodUnit.METERS -> distanceText(amount)
}

private val UNIT_LABELS = listOf(WodUnit.REPS to "Reps", WodUnit.SECONDS to "Seconds", WodUnit.METERS to "Metres")

/**
 * One movement of the WOD: how much, counted how, and with what load. Every change applies at once.
 * [byScheme]: a scheme gives the reps of every round, so there is no amount to choose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(
    item: WodItem,
    exercise: Exercise?,
    byScheme: Boolean,
    tabata: Boolean,
    unit: WeightUnit,
    onChange: (WodItem) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    // The load the sheet opened with stays on the wheel, so it can always be turned back to.
    val opened = remember { item.load }
    val sheet = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { then() }
    }
    val loaded = exercise != null && exercise.equipment != Equipment.BODYWEIGHT
    // Max only exists in a Tabata: as often as possible in each round.
    val max = tabata && item.amount == 0
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = EisenColors.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseMotion(exercise, size = 96.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    exercise?.name ?: "Deleted exercise",
                    Modifier.weight(1f).semantics { heading() },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((option, label) in UNIT_LABELS) {
                    FilterChip(
                        selected = item.unit == option,
                        onClick = { onChange(withUnit(item, option)) },
                        label = { Text(label, Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = chipColors(),
                        border = null,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(if (loaded) 1f else 0.6f).align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val amountHeader = when (item.unit) {
                    WodUnit.REPS -> "Reps"
                    WodUnit.SECONDS -> "Time"
                    WodUnit.METERS -> "Distance"
                }
                WheelBox(amountHeader, Modifier.weight(1f)) {
                    if (max || byScheme) {
                        Box(Modifier.fillMaxWidth().height(48.dp * 3), contentAlignment = Alignment.Center) {
                            Text(if (byScheme) "By scheme" else "Max", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = EisenColors.Primary)
                        }
                    } else {
                        key(item.unit) {
                            val choices = wodAmounts(item.unit, item.amount)
                            Wheel(
                                labels = remember(choices) { choices.map { amountLabel(item.unit, it) } },
                                selected = choices.indexOf(item.amount).coerceAtLeast(0),
                                onSettle = { onChange(item.copy(amount = choices[it])) },
                                label = amountHeader,
                                spoken = { amountLabel(item.unit, choices[it]) },
                                rowHeight = 48.dp,
                            )
                        }
                    }
                }
                if (loaded) {
                    WheelBox("Load (${unit.label})", Modifier.weight(1f)) {
                        val choices = remember(unit) { wodLoads(exercise!!.equipment, unit, opened) }
                        val shown = loadIn(item.load, unit)
                        Wheel(
                            labels = remember(choices) { choices.map { if (it == 0.0) "None" else formatNumber(it) + unit.label } },
                            selected = choices.indexOf(shown).coerceAtLeast(0),
                            onSettle = { onChange(item.copy(load = loadFrom(choices[it], unit))) },
                            label = "Load",
                            spoken = { if (choices[it] == 0.0) "No load" else "${formatNumber(choices[it])} ${unit.label}" },
                            rowHeight = 48.dp,
                        )
                    }
                }
            }
            if (tabata) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RowShape)
                        .toggleable(value = max, role = Role.Switch) { onChange(withMax(item, it)) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Max", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(maxHint(item.unit), fontSize = 13.sp, color = EisenColors.MutedForeground)
                    }
                    Switch(checked = max, onCheckedChange = null)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { close(onRemove) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Destructive),
                ) {
                    Icon(Icons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Button(
                    onClick = { close(onClose) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Done", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            }
        }
    }
}
