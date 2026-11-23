package io.github.alexanderop.eisen.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.MAX_TIMER_ROUNDS
import io.github.alexanderop.eisen.domain.TIMER_MODES
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.TimerPreset
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.timerSeconds
import io.github.alexanderop.eisen.domain.timerSummary
import io.github.alexanderop.eisen.domain.elm.Model
import io.github.alexanderop.eisen.domain.elm.TimerPage
import io.github.alexanderop.eisen.domain.elm.openWod
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.PRESET_NAME_LENGTH
import io.github.alexanderop.eisen.domain.elm.TimerSetup
import io.github.alexanderop.eisen.domain.elm.callsSummary
import io.github.alexanderop.eisen.domain.elm.defaultPresetName
import io.github.alexanderop.eisen.domain.elm.presetOf
import io.github.alexanderop.eisen.domain.elm.setupChanged
import io.github.alexanderop.eisen.domain.elm.setupSaved
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import io.github.alexanderop.eisen.domain.elm.lastTimerConfig
import io.github.alexanderop.eisen.domain.elm.timerCapSeconds
import io.github.alexanderop.eisen.domain.elm.timerWheelSeconds
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.Wheel
import io.github.alexanderop.eisen.ui.formatClock
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.rememberHaptics
import kotlinx.coroutines.flow.drop

/** What each mode is, in one line on its card. */
fun modeLine(mode: TimerMode): String = when (mode) {
    TimerMode.TABATA -> "Work and rest in rounds"
    TimerMode.EMOM -> "Every minute on the minute"
    TimerMode.AMRAP -> "As many rounds as possible"
    TimerMode.FOR_TIME -> "As fast as you can"
}


internal val VoiceIcon = pathIcon(
    "Voice",
    "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z",
    "M16 9a5 5 0 0 1 0 6",
    "M19.364 18.364a9 9 0 0 0 0-12.728",
)
private val SaveIcon = pathIcon("Save", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z")

private val CardShape = RoundedCornerShape(18.dp)

/**
 * The Timer tab: the WODs and the timers on two pages, a WOD's detail, the builder, or the setup of a
 * timer. Which sheet or dialog is open stays here; the rest is in the model ([Model.timerSetup],
 * [Model.wods], [io.github.alexanderop.eisen.domain.elm.Navigation.timerPage]).
 */
@Composable
fun TimerScreen(model: Model, dispatch: (Msg) -> Unit) {
    val setup = model.timerSetup
    val draft = model.wods.draft
    val detail = openWod(model)
    val wods = model.nav.timerPage == TimerPage.WODS
    // Out here, so the list is where it was when the lifter comes back from a WOD's detail.
    val wodsList = androidx.compose.foundation.lazy.rememberLazyListState()
    when {
        setup != null -> TimerSetupScreen(model, setup, dispatch)
        wods && draft != null -> WodBuilder(model, draft, dispatch)
        wods && detail != null -> WodDetail(model, detail, dispatch)
        // The WODs page scrolls its heading away, so a short screen has room for its cards under the search.
        wods -> WodsPage(model, wodsList, dispatch) { TimerHeading(model, dispatch) }
        else -> Column(Modifier.fillMaxSize()) {
            TimerHeading(model, dispatch)
            TimerHome(model, dispatch)
        }
    }
}

/** "Timer" and the switch between its two pages. */
@Composable
private fun TimerHeading(model: Model, dispatch: (Msg) -> Unit) {
    Column {
        Text(
            "Timer",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).semantics { heading() },
        )
        PageSwitch(model.nav.timerPage) { dispatch(Msg.Nav.ShowTimerPage(it)) }
    }
}

/** "WODs" or "Timers", like the Log and Progress switch of the History tab. */
@Composable
private fun PageSwitch(page: TimerPage, onChange: (TimerPage) -> Unit) {
    val pages = listOf(TimerPage.WODS to "WODs", TimerPage.TIMERS to "Timers")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        pages.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = page == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, pages.size),
                icon = {},
                colors = segmentColors(),
                label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            )
        }
    }
}

@Composable
internal fun segmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = EisenColors.Primary,
    activeContentColor = EisenColors.OnPrimary,
    inactiveContainerColor = EisenColors.Card,
    inactiveContentColor = EisenColors.MutedForeground,
    activeBorderColor = EisenColors.Primary,
    inactiveBorderColor = EisenColors.Border,
)

@Composable
private fun TimerHome(model: Model, dispatch: (Msg) -> Unit) {
    val presets = model.state.timerPresets
    val haptics = rememberHaptics()
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    // A tap on the Timer tab while it shows goes back to the top, once per tap.
    var top by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(model.nav.timerTop) }
    LaunchedEffect(model.nav.timerTop) {
        if (model.nav.timerTop != top) {
            top = model.nav.timerTop
            list.animateScrollToItem(0)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "modes") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in TIMER_MODES.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (mode in row) {
                            ModeCard(mode, timerSummary(lastTimerConfig(model, mode)), Modifier.weight(1f)) { dispatch(Msg.Timer.Open(mode)) }
                        }
                    }
                }
            }
        }
        item(key = "saved") {
            Text(
                "Saved timers",
                Modifier.padding(top = 12.dp).semantics { heading() },
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (presets.isEmpty()) {
            item(key = "empty") {
                Text("Save a timer to start it again in one tap.", color = EisenColors.MutedForeground, fontSize = 15.sp)
            }
        }
        items(presets, key = { it.id }) { preset ->
            SwipeToDelete(preset.name, onDelete = {
                dispatch(Msg.Timer.DeletePreset(preset.id))
                haptics.perform(Haptic.TAP)
            }, modifier = Modifier.animateItem()) {
                PresetRow(preset, onOpen = { dispatch(Msg.Timer.OpenPreset(preset.id)) }, onStart = { dispatch(Msg.Timer.StartPreset(preset.id)) })
            }
        }
    }
}

@Composable
private fun ModeCard(mode: TimerMode, summary: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 148.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
        border = BorderStroke(1.dp, EisenColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 148.dp).padding(16.dp)) {
            Box(Modifier.size(width = 28.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(EisenColors.Primary))
            Spacer(Modifier.height(12.dp))
            Text(mode.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(modeLine(mode), fontSize = 14.sp, lineHeight = 18.sp, color = EisenColors.MutedForeground, minLines = 2, maxLines = 2)
            Spacer(Modifier.height(14.dp))
            Text(summary, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EisenColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PresetRow(preset: TimerPreset, onOpen: () -> Unit, onStart: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
        border = BorderStroke(1.dp, EisenColors.Border),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 32.dp).clip(RoundedCornerShape(2.dp)).background(EisenColors.Primary))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(preset.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${preset.config.mode.title} · ${timerSummary(preset.config)}",
                    fontSize = 14.sp,
                    color = EisenColors.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Straight into the lead-in; the rest of the row opens the setup.
            FilledIconButton(
                onClick = onStart,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = EisenColors.Primary, contentColor = EisenColors.OnPrimary),
            ) { Icon(StartIcon, contentDescription = "Start ${preset.name}", modifier = Modifier.size(20.dp)) }
        }
    }
}

internal val StartIcon = pathIcon("Play", "M7 4.5v15l12-7.5z", filled = true)

private val OnDestructive = Color(0xFF2B0A0A)

/** Swiping a row to the left deletes it, like a workout in the history. TalkBack gets the same as an action. */
@Composable
internal fun SwipeToDelete(name: String, onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val haptics = rememberHaptics()
    // Not saved: a row brought back by Undo must come back as a row, not as a swiped-away one.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.drop(1).collect { haptics.perform(Haptic.STEP) }
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Row(
                    Modifier.fillMaxSize().clip(CardShape).background(EisenColors.Destructive).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Trash, contentDescription = null, tint = OnDestructive)
                    Text("Delete", color = OnDestructive, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
        modifier = modifier.semantics { customActions = listOf(CustomAccessibilityAction("Delete $name") { onDelete(); true }) },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { onDelete() },
    ) {
        content()
    }
}

// The setup

/** "Total 15s", "Total 3:50", or "No limit" for a For Time without a cap. */
private fun totalText(config: TimerConfig): String = timerSeconds(config)?.let { "Total ${holdText(it)}" } ?: "No limit"

@Composable
private fun TimerSetupScreen(model: Model, setup: TimerSetup, dispatch: (Msg) -> Unit) {
    val config = setup.config
    val mode = config.mode
    val calls = model.state.settings.timerCalls
    var voiceOpen by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    val preset = presetOf(model, setup)
    fun edit(change: (TimerConfig) -> TimerConfig) = dispatch(Msg.Timer.Edit(change(config)))

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dispatch(Msg.Timer.CloseSetup) }) { Icon(Icons.Back, contentDescription = "Back to the timers") }
            Column(Modifier.weight(1f)) {
                Text(mode.title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.semantics { heading() })
                Text(modeLine(mode), fontSize = 14.sp, color = EisenColors.MutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // A 360×640 phone gets smaller wheel rows, so Start never leaves the screen.
            val row = if (maxHeight < 420.dp) 44.dp else 52.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (preset != null) {
                    // The saved timer's name, renamed by "Update preset" together with the numbers.
                    OutlinedTextField(
                        value = setup.name,
                        onValueChange = { dispatch(Msg.Timer.EditName(it)) },
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // One wheel alone stays wheel-sized in the middle instead of stretching across the screen.
                val single = mode == TimerMode.AMRAP || mode == TimerMode.FOR_TIME
                Row(
                    Modifier.fillMaxWidth(if (single) 0.6f else 1f).align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (mode) {
                        TimerMode.TABATA -> {
                            SecondsWheel("Work", config.work, timerWheelSeconds(5, 600, config.work), row, Modifier.weight(1f)) { v -> edit { it.copy(work = v) } }
                            SecondsWheel("Rest", config.rest, timerWheelSeconds(5, 300, config.rest), row, Modifier.weight(1f)) { v -> edit { it.copy(rest = v) } }
                            RoundsWheel(config.rounds, row, Modifier.weight(1f)) { v -> edit { it.copy(rounds = v) } }
                        }
                        TimerMode.EMOM -> {
                            SecondsWheel("Every", config.work, timerWheelSeconds(10, 600, config.work), row, Modifier.weight(1f)) { v -> edit { it.copy(work = v) } }
                            RoundsWheel(config.rounds, row, Modifier.weight(1f)) { v -> edit { it.copy(rounds = v) } }
                        }
                        TimerMode.AMRAP ->
                            SecondsWheel("Time", config.work, timerWheelSeconds(30, 3600, config.work), row, Modifier.weight(1f)) { v -> edit { it.copy(work = v) } }
                        TimerMode.FOR_TIME ->
                            SecondsWheel("Time cap", config.work, timerCapSeconds(config.work), row, Modifier.weight(1f), off = "Off") { v -> edit { it.copy(work = v) } }
                    }
                }
                Text(
                    totalText(config),
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = EisenColors.Foreground,
                )
                SetupRow(VoiceIcon, "Voice", callsSummary(calls)) { voiceOpen = true }
                if (setupChanged(model, setup)) {
                    SetupRow(SaveIcon, "Update preset", "Save the name and these numbers to ${preset?.name}") { dispatch(Msg.Timer.UpdatePreset) }
                }
                // Confirmed in place: no snackbar over the Start button.
                if (setupSaved(model, setup) && !setupChanged(model, setup)) {
                    SetupRow(SaveIcon, "Saved", "In your saved timers", enabled = false) {}
                } else {
                    SetupRow(SaveIcon, "Save as preset", "Start it again in one tap") { naming = true }
                }
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

    if (voiceOpen) VoiceSheet(calls, dispatch, onClose = { voiceOpen = false })
    if (naming) {
        NameDialog(
            initial = defaultPresetName(mode, model.state.timerPresets),
            onSave = {
                dispatch(Msg.Timer.SavePreset(it))
                naming = false
            },
            onClose = { naming = false },
        )
    }
}

@Composable
internal fun SetupRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tone = if (enabled) EisenColors.Foreground else EisenColors.MutedForeground
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tone)
            Text(value, fontSize = 13.sp, color = EisenColors.MutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (enabled) Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun WheelBox(header: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(EisenColors.Card)
            .border(1.dp, EisenColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(header, color = EisenColors.MutedForeground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SecondsWheel(
    header: String,
    value: Int,
    choices: List<Int>,
    row: Dp,
    modifier: Modifier,
    off: String? = null,
    onChange: (Int) -> Unit,
) {
    WheelBox(header, modifier) {
        Wheel(
            labels = remember(choices) { choices.map { if (it == 0 && off != null) off else holdText(it) } },
            selected = choices.indexOf(value).coerceAtLeast(0),
            onSettle = { onChange(choices[it]) },
            label = header,
            spoken = { index -> spokenSeconds(choices[index], off) },
            rowHeight = row,
        )
    }
}

@Composable
private fun RoundsWheel(value: Int, row: Dp, modifier: Modifier, onChange: (Int) -> Unit) {
    val choices = remember { (1..MAX_TIMER_ROUNDS).toList() }
    WheelBox("Rounds", modifier) {
        Wheel(
            labels = remember { choices.map { it.toString() } },
            selected = (value - 1).coerceIn(0, choices.lastIndex),
            onSettle = { onChange(choices[it]) },
            label = "Rounds",
            spoken = { if (it == 0) "1 round" else "${it + 1} rounds" },
            rowHeight = row,
        )
    }
}

internal fun spokenSeconds(seconds: Int, off: String?): String = when {
    seconds == 0 && off != null -> off
    seconds < 60 -> "$seconds seconds"
    seconds % 60 == 0 -> if (seconds == 60) "1 minute" else "${seconds / 60} minutes"
    else -> "${seconds / 60} minutes ${seconds % 60} seconds"
}

@Composable
private fun NameDialog(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Save as preset") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.text.length <= PRESET_NAME_LENGTH) text = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
