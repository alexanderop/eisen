package io.github.alexanderop.eisen.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.StateJson
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.sessionSummary
import io.github.alexanderop.eisen.domain.OUTCOME_LABELS
import io.github.alexanderop.eisen.domain.TimerOutcome
import io.github.alexanderop.eisen.domain.timerOutcome
import io.github.alexanderop.eisen.domain.elm.HistoryView
import io.github.alexanderop.eisen.ui.progress.ProgressScreen
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import io.github.alexanderop.eisen.domain.durationMinutes
import io.github.alexanderop.eisen.domain.elm.Model
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.HOLD_STEP_SECONDS
import io.github.alexanderop.eisen.domain.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.nextReps
import io.github.alexanderop.eisen.domain.setText
import io.github.alexanderop.eisen.domain.recordIds
import io.github.alexanderop.eisen.domain.stepFor
import io.github.alexanderop.eisen.domain.typedWeightSnap
import io.github.alexanderop.eisen.ui.AddedWeightStepper
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.IntStepper
import io.github.alexanderop.eisen.ui.SetButton
import io.github.alexanderop.eisen.ui.SetState
import io.github.alexanderop.eisen.ui.Stepper
import io.github.alexanderop.eisen.ui.fmt
import io.github.alexanderop.eisen.ui.fmtLoad
import io.github.alexanderop.eisen.ui.formatDate
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.rememberHaptics
import io.github.alexanderop.eisen.ui.share.ShareWorkoutSheet
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import io.github.alexanderop.eisen.ui.rememberFullSheetState

private val ShareIcon = pathIcon(
    "Share",
    "M15 5a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "M3 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "M15 19a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "M8.59 13.51 15.42 17.49",
    "M15.41 6.51 8.59 10.49",
)
private val MoreIcon = pathIcon(
    "More",
    "M11 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
    "M11 5a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
    "M11 19a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
)

/** The draft lives as JSON in the saved state, so turning the phone keeps the corrections made so far. */
private val DraftSaver = Saver<Workout?, String>(
    save = { draft -> draft?.let { StateJson.encodeToString(Workout.serializer(), it) } },
    restore = { runCatching { StateJson.decodeFromString(Workout.serializer(), it) }.getOrNull() },
)

private fun duration(workout: Workout): String = durationMinutes(workout)?.let { "$it min" } ?: ""

/**
 * The History tab: a switch between the log of what was done and the charts of the progress made.
 */
@Composable
fun HistoryScreen(model: Model, dispatch: (Msg) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "History",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).semantics { heading() },
        )
        ViewSwitch(model.nav.history) { dispatch(Msg.Nav.ShowHistory(it)) }
        when (model.nav.history) {
            HistoryView.LOG -> HistoryLog(model, dispatch)
            HistoryView.PROGRESS -> ProgressScreen(model, title = false)
        }
    }
}

@Composable
private fun ViewSwitch(view: HistoryView, onChange: (HistoryView) -> Unit) {
    val views = listOf(HistoryView.LOG to "Log", HistoryView.PROGRESS to "Progress")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        views.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = view == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, views.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = EisenColors.Primary,
                    activeContentColor = EisenColors.OnPrimary,
                    inactiveContainerColor = EisenColors.Card,
                    inactiveContentColor = EisenColors.MutedForeground,
                    activeBorderColor = EisenColors.Primary,
                    inactiveBorderColor = EisenColors.Border,
                ),
                label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            )
        }
    }
}

/** A line of the log: a finished workout or a finished interval timer, ordered by when they started. */
private sealed interface LogItem {
    val startedAt: Long
    val key: String

    class Lift(val workout: Workout, val records: Set<String>) : LogItem {
        override val startedAt get() = workout.startedAt
        override val key get() = "w:" + workout.id
    }

    class Interval(val session: TimerSession) : LogItem {
        override val startedAt get() = session.startedAt
        override val key get() = "t:" + session.id
    }
}

/**
 * The finished workouts and timers, newest first. Which card's menu, dialog or sheet is open and the
 * corrections typed into the edit sheet before "Save changes" stay here: nothing else depends on them.
 */
@Composable
private fun HistoryLog(model: Model, dispatch: (Msg) -> Unit) {
    val history = model.state.history
    val timers = model.state.timerSessions
    // Per workout, the exercises that set a record on that day.
    val records = remember(history) { history.indices.map { recordIds(history, it) } }
    val items = remember(history, records, timers) {
        (history.mapIndexed { index, workout -> LogItem.Lift(workout, records[index]) } + timers.map { LogItem.Interval(it) })
            .sortedByDescending { it.startedAt }
    }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var sharing by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()

    // A swipe deletes at once. The root's snackbar offers the way back instead of a dialog in the way.
    fun deleteWithUndo(workout: Workout) {
        if (history.none { it.id == workout.id }) return
        dispatch(Msg.History.Delete(workout.id, offerUndo = true))
        haptics.perform(Haptic.TAP)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (items.isEmpty()) {
            item(key = "empty") {
                EisenCard {
                    Text(
                        "Nothing logged yet",
                        color = EisenColors.MutedForeground,
                        fontSize = 16.sp,
                    )
                }
            }
        }
        items(items, key = { it.key }) { item ->
            when (item) {
                is LogItem.Lift -> {
                    val workout = item.workout
                    SwipeToDelete(workout.name, onDelete = { deleteWithUndo(workout) }, modifier = Modifier.animateItem()) {
                        WorkoutCard(
                            workout = workout,
                            records = item.records,
                            onShare = { sharing = workout.id },
                            onEdit = { draft = workout },
                            onDelete = { deleting = workout.id },
                        )
                    }
                }
                is LogItem.Interval -> {
                    val session = item.session
                    SwipeToDelete(session.name, onDelete = {
                        if (timers.any { it.id == session.id }) {
                            dispatch(Msg.Timer.DeleteSession(session.id))
                            haptics.perform(Haptic.TAP)
                        }
                    }, modifier = Modifier.animateItem()) {
                        TimerRow(session)
                    }
                }
            }
        }
    }

    history.firstOrNull { it.id == sharing }?.let { workout ->
        ShareWorkoutSheet(workout, history, onDismiss = { sharing = null })
    }

    draft?.let { current ->
        EditSheet(
            draft = current,
            onChange = { draft = it },
            onSave = {
                dispatch(Msg.History.Edit(current.id, current.exercises))
                draft = null
            },
            onClose = { draft = null },
        )
    }

    val doomed = history.firstOrNull { it.id == deleting }
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this workout?") },
            text = { Text("${doomed.name} on ${formatDate(doomed.startedAt)} is removed from your history. It cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        dispatch(Msg.History.Delete(doomed.id))
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = EisenColors.Destructive),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private val CardShape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
private val OnDestructive = Color(0xFF2B0A0A)

/**
 * Swiping a card to the left deletes the workout. It takes 40% of the width or a fling, so a
 * sloppy vertical scroll does not delete anything. TalkBack gets the same as an action.
 */
@Composable
private fun SwipeToDelete(name: String, onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val haptics = rememberHaptics()
    // Not saved: a workout brought back by Undo must come back as a card, not as a swiped-away one.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    // A tick when the swipe passes the point where letting go deletes, and again when it goes back.
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.drop(1).collect { haptics.perform(Haptic.STEP) }
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            // Only while swiping: at rest the red would show at the card's round corners.
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
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Delete $name") { onDelete(); true })
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { onDelete() },
    ) {
        content()
    }
}

@Composable
private fun EisenCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
        border = BorderStroke(1.dp, EisenColors.Border),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

private val StopwatchIcon = pathIcon("Stopwatch", "M10 2h4", "M12 14l3-3", "M4 14a8 8 0 1 0 16 0a8 8 0 1 0-16 0")

/** A finished interval timer: compact, no detail screen. */
@Composable
private fun TimerRow(session: TimerSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
        border = BorderStroke(1.dp, EisenColors.Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(EisenColors.Muted), contentAlignment = Alignment.Center) {
                Icon(StopwatchIcon, contentDescription = null, tint = EisenColors.Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(session.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Text("${formatDate(session.startedAt)} · ${sessionSummary(session)}", fontSize = 13.sp, color = EisenColors.MutedForeground)
            }
            val outcome = timerOutcome(session)
            if (outcome != TimerOutcome.COMPLETED) {
                Spacer(Modifier.width(8.dp))
                Text(OUTCOME_LABELS.getValue(outcome), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EisenColors.Warn)
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    records: Set<String>,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    EisenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workout.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text("${formatDate(workout.startedAt)} · ${duration(workout)}", fontSize = 13.sp, color = EisenColors.MutedForeground)
            }
            IconButton(onClick = onShare) { Icon(ShareIcon, contentDescription = "Share ${workout.name}") }
            IconButton(onClick = onEdit) { Icon(Icons.Pencil, contentDescription = "Edit ${workout.name}") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(MoreIcon, contentDescription = "More options for ${workout.name}") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = EisenColors.Destructive) },
                        leadingIcon = { Icon(Icons.Trash, contentDescription = null, tint = EisenColors.Destructive) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        for (log in workout.exercises) {
            HorizontalDivider(color = EisenColors.Border)
            LogRow(log, workout, record = log.id in records)
        }
    }
}

@Composable
private fun LogRow(log: ExerciseLog, workout: Workout, record: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(log.name, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
            if (record) {
                Spacer(Modifier.width(6.dp))
                Badge(containerColor = EisenColors.Primary, contentColor = EisenColors.OnPrimary) {
                    Text("Record", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            fmtLoad(log.equipment, log.weight, workout.unit),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.widthIn(min = 92.dp).clearAndSetSemantics {
                contentDescription = "Sets: " + log.sets.joinToString(", ") { it?.let { count -> setText(count, log.timed) } ?: "not logged" }
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            for (reps in log.sets) {
                val missed = reps == null || (log.targetReps != null && reps != log.targetReps)
                Text(
                    reps?.let { setText(it, log.timed) } ?: "–",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (missed) EisenColors.Warn else EisenColors.Primary,
                )
            }
        }
    }
}

private fun setState(log: ExerciseLog, reps: Int?): SetState = when {
    reps == null -> SetState.EMPTY
    log.targetReps != null && reps < log.targetReps -> SetState.MISSED
    else -> SetState.DONE
}

/** A copy of the workout being corrected. Nothing changes until it is saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(draft: Workout, onChange: (Workout) -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    fun editLog(index: Int, change: (ExerciseLog) -> ExerciseLog) =
        onChange(draft.copy(exercises = draft.exercises.mapIndexed { at, log -> if (at == index) change(log) else log }))
    fun setReps(index: Int, setIndex: Int, reps: Int?) =
        editLog(index) { log -> log.copy(sets = log.sets.mapIndexed { at, value -> if (at == setIndex) reps else value }) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Edit ${draft.name}",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { closeThen(onClose) }) { Icon(Icons.Close, contentDescription = "Close") }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "${formatDate(draft.startedAt)}. This corrects the log. Your next weights stay as they are.",
                fontSize = 13.sp,
                color = EisenColors.MutedForeground,
            )
            draft.exercises.forEachIndexed { index, log ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = EisenColors.Border)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (log.equipment == Equipment.BODYWEIGHT) {
                            Column(Modifier.weight(1f)) {
                                Text(log.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Added weight", fontSize = 13.sp, color = EisenColors.MutedForeground)
                            }
                            AddedWeightStepper(
                                value = log.weight,
                                onValueChange = { weight -> editLog(index) { it.copy(weight = weight) } },
                                unit = draft.unit,
                                label = "${log.name} added weight",
                            )
                        } else {
                            Text(log.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            val step = stepFor(log.equipment, draft.unit)
                            Stepper(
                                value = log.weight,
                                onValueChange = { weight -> editLog(index) { it.copy(weight = weight) } },
                                step = step,
                                min = step,
                                label = "${log.name} weight",
                                format = { fmt(it, draft.unit) },
                                snap = typedWeightSnap(log.equipment, draft.unit),
                            )
                        }
                    }
                    val target = log.targetReps
                    if (log.timed) {
                        // A hold is corrected in seconds, five at a time. 0 is a set that was not done.
                        log.sets.forEachIndexed { setIndex, seconds ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Set ${setIndex + 1}", color = EisenColors.MutedForeground, modifier = Modifier.weight(1f))
                                IntStepper(
                                    value = seconds ?: 0,
                                    onValueChange = { setReps(index, setIndex, if (it == 0) null else it) },
                                    label = "${log.name} set ${setIndex + 1} seconds held",
                                    step = HOLD_STEP_SECONDS,
                                    max = MAX_HELD_SECONDS,
                                    format = { if (it == 0) "–" else holdText(it) },
                                    snap = 1,
                                    typedSuffix = "s",
                                )
                            }
                        }
                    } else if (target != null) {
                        for (row in log.sets.indices.chunked(5)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (setIndex in row) {
                                    val reps = log.sets[setIndex]
                                    SetButton(
                                        text = (reps ?: target).toString(),
                                        state = setState(log, reps),
                                        onClick = {
                                            setReps(index, setIndex, nextReps(reps, target))
                                            haptics.perform(Haptic.TAP)
                                        },
                                        description = "${log.name} set ${setIndex + 1}: ${if (reps == null) "not logged" else "$reps reps"}",
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                // Keeps a short last row on the same grid as the full rows above it.
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        log.sets.forEachIndexed { setIndex, reps ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Set ${setIndex + 1}", color = EisenColors.MutedForeground, modifier = Modifier.weight(1f))
                                IntStepper(
                                    value = reps ?: 0,
                                    onValueChange = { setReps(index, setIndex, if (it == 0) null else it) },
                                    label = "${log.name} set ${setIndex + 1} reps",
                                    format = { if (it == 0) "–" else it.toString() },
                                )
                            }
                        }
                    }
                }
            }
            Button(onClick = { closeThen(onSave) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Save changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            FilledTonalButton(
                onClick = { closeThen(onClose) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
