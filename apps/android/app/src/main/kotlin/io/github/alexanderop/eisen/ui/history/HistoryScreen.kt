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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberModalBottomSheetState
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
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.StateJson
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.durationMinutes
import io.github.alexanderop.eisen.domain.nextReps
import io.github.alexanderop.eisen.domain.recordIds
import io.github.alexanderop.eisen.domain.stepFor
import io.github.alexanderop.eisen.domain.typedWeightSnap
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.IntStepper
import io.github.alexanderop.eisen.ui.SetButton
import io.github.alexanderop.eisen.ui.SetState
import io.github.alexanderop.eisen.ui.Stepper
import io.github.alexanderop.eisen.ui.fmt
import io.github.alexanderop.eisen.ui.formatDate
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.rememberHaptics
import io.github.alexanderop.eisen.ui.share.ShareWorkoutSheet
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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

@Composable
fun HistoryScreen() {
    val history = Store.state.history
    // Per workout, the exercises that set a record on that day.
    val records = remember(history) { history.indices.map { recordIds(history, it) } }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var sharing by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // A swipe deletes at once. The snackbar offers the way back instead of a dialog in the way.
    fun deleteWithUndo(workout: Workout) {
        val index = Store.state.history.indexOfFirst { it.id == workout.id }
        if (index == -1) return
        Store.deleteWorkout(workout.id)
        haptics.perform(Haptic.TAP)
        scope.launch {
            // A second delete replaces the first snackbar, so only the newest one can be undone.
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Workout deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) Store.restoreWorkout(workout, index)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                Text(
                    "History",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (history.isEmpty()) {
                item(key = "empty") {
                    EisenCard {
                        Text(
                            "No workouts yet. Finish your first one and it shows up here.",
                            color = EisenColors.MutedForeground,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
            itemsIndexed(history, key = { _, workout -> workout.id }) { position, workout ->
                SwipeToDelete(workout.name, onDelete = { deleteWithUndo(workout) }, modifier = Modifier.animateItem()) {
                    WorkoutCard(
                        workout = workout,
                        records = records.getOrElse(position) { emptySet() },
                        onShare = { sharing = workout.id },
                        onEdit = { draft = workout },
                        onDelete = { deleting = workout.id },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp)) { data ->
            Snackbar(
                data,
                containerColor = EisenColors.Border,
                contentColor = EisenColors.Foreground,
                actionColor = EisenColors.Primary,
            )
        }
    }

    history.firstOrNull { it.id == sharing }?.let { workout ->
        ShareWorkoutSheet(workout, onDismiss = { sharing = null })
    }

    draft?.let { current ->
        EditSheet(
            draft = current,
            onChange = { draft = it },
            onSave = {
                Store.updateWorkout(current.id, current.exercises)
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
                        Store.deleteWorkout(doomed.id)
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
            if (log.equipment == Equipment.BODYWEIGHT) "BW" else fmt(log.weight, workout.unit),
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
                contentDescription = "Sets: " + log.sets.joinToString(", ") { it?.toString() ?: "not logged" }
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            for (reps in log.sets) {
                val missed = reps == null || (log.targetReps != null && reps != log.targetReps)
                Text(
                    reps?.toString() ?: "–",
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                        Text(log.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (log.equipment != Equipment.BODYWEIGHT) {
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
                    if (target != null) {
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
