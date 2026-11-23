package io.github.alexanderop.eisen.ui.workouts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.DEFAULT_HOLD_SECONDS
import io.github.alexanderop.eisen.domain.HOLD_STEP_SECONDS
import io.github.alexanderop.eisen.domain.MAX_HOLD_SECONDS
import io.github.alexanderop.eisen.domain.MAX_REPS
import io.github.alexanderop.eisen.domain.MIN_HOLD_SECONDS
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.targetText
import io.github.alexanderop.eisen.domain.MAX_SETS
import io.github.alexanderop.eisen.domain.Routine
import io.github.alexanderop.eisen.domain.Slot
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.elm.AddTarget
import io.github.alexanderop.eisen.domain.elm.Model
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.exerciseNamed
import io.github.alexanderop.eisen.domain.elm.exercises
import io.github.alexanderop.eisen.domain.elm.findExercise
import io.github.alexanderop.eisen.domain.elm.nextRoutine
import io.github.alexanderop.eisen.domain.elm.openRoutine
import io.github.alexanderop.eisen.domain.elm.progressOf
import io.github.alexanderop.eisen.domain.elm.settings
import io.github.alexanderop.eisen.domain.elm.workoutRunning
import io.github.alexanderop.eisen.domain.kindLabel
import io.github.alexanderop.eisen.ui.ColumnReorderState
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.ExerciseTile
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.IntStepper
import io.github.alexanderop.eisen.ui.ReorderableColumnItem
import io.github.alexanderop.eisen.ui.fmtLoad
import io.github.alexanderop.eisen.ui.keyboardPadding
import io.github.alexanderop.eisen.ui.longPressToReorder
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.picker.ButtonHeight
import io.github.alexanderop.eisen.ui.picker.ConfirmButton
import io.github.alexanderop.eisen.ui.picker.EisenCard
import io.github.alexanderop.eisen.ui.picker.ExercisePickerSheet
import io.github.alexanderop.eisen.ui.picker.PillBadge
import io.github.alexanderop.eisen.ui.rememberColumnReorderState
import io.github.alexanderop.eisen.ui.rememberHaptics
import io.github.alexanderop.eisen.ui.reorderContainer
import io.github.alexanderop.eisen.ui.reorderHandle
import kotlinx.coroutines.flow.drop

private val ArrowUp = pathIcon("ArrowUp", "m5 12 7-7 7 7", "M12 19V5")
private val ArrowDown = pathIcon("ArrowDown", "M12 5v14", "m19 12-7 7-7-7")

// Lucide's grip-vertical: six dots, drawn filled because circles have no path of their own.
private val Grip = pathIcon(
    "GripVertical",
    "M7 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" +
        "M7 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" +
        "M7 19a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM13 19a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
    filled = true,
)

private val OnDestructive = Color(0xFF2B0A0A)

/**
 * The workouts in the order they rotate in, and an editor for the one in `model.nav.routine`.
 * The back gesture closes the editor through the root (`Msg.Nav.Back`); the Undo of a removed
 * exercise comes from `update` and shows in the app's one SnackbarHost.
 */
@Composable
fun WorkoutsScreen(model: Model, dispatch: (Msg) -> Unit) {
    val editing = openRoutine(model)
    Box(Modifier.fillMaxSize()) {
        if (editing != null) {
            // A fresh scroll position and closed exercises for every workout opened.
            key(editing.id) {
                RoutineEditor(model, editing, dispatch)
            }
        } else {
            RoutineList(model, dispatch)
        }
    }
}

private fun summary(model: Model, routine: Routine): String {
    val names = routine.slots.mapNotNull { findExercise(model, it.exerciseId)?.name }
    return if (names.isNotEmpty()) names.joinToString(" · ") else "No exercises yet"
}

/** "Move up" and "Move down" for TalkBack, the same moves a drag makes. */
private fun moveActions(index: Int, count: Int, move: (from: Int, to: Int) -> Unit): List<CustomAccessibilityAction> = buildList {
    if (index > 0) add(CustomAccessibilityAction("Move up") { move(index, index - 1); true })
    if (index < count - 1) add(CustomAccessibilityAction("Move down") { move(index, index + 1); true })
}

@Composable
private fun RoutineList(model: Model, dispatch: (Msg) -> Unit) {
    val nextId = nextRoutine(model).id
    val routines = model.state.routines
    val scroll = rememberScrollState()
    val move = { from: Int, to: Int -> dispatch(Msg.Routines.Move(from, to)) }
    val reorder = rememberColumnReorderState(
        keys = routines.map { it.id },
        onMove = move,
        scrollState = scroll,
    )
    Column(
        Modifier.fillMaxSize().reorderContainer(reorder).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Workouts",
                modifier = Modifier.semantics { heading() },
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
            )
            Text(
                if (routines.size > 1) "Workouts rotate in this order. Tap one to change it, hold one to move it."
                else "Workouts rotate in this order. Tap one to change it.",
                color = EisenColors.MutedForeground,
                fontSize = 13.sp,
            )
        }
        routines.forEachIndexed { index, routine ->
            key(routine.id) {
                ReorderableColumnItem(reorder, key = routine.id, shape = MaterialTheme.shapes.large) {
                    // A hold picks the card up, so a tap still opens it and the page still scrolls.
                    EisenCard(
                        Modifier
                            .fillMaxWidth()
                            .longPressToReorder(reorder, routine.id, enabled = routines.size > 1)
                            .semantics { customActions = moveActions(index, routines.size, move) },
                        onClick = { dispatch(Msg.Nav.OpenRoutine(routine.id)) },
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(routine.name, modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                if (routine.id == nextId) PillBadge("Next")
                                Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.Primary)
                            }
                            Text(summary(model, routine), color = EisenColors.MutedForeground, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        FilledTonalButton(
            // Opens the new workout in the editor.
            onClick = { dispatch(Msg.Routines.Add()) },
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        ) {
            Text("New workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/**
 * "5 × 5 · 80kg · Barbell", "3 × max · BW + 10kg · Bodyweight" or "3 × 1:00 · Bodyweight".
 * Plain bodyweight has no load to show.
 */
private fun detail(slot: Slot, exercise: Exercise?, weight: Double, unit: WeightUnit): String {
    if (exercise == null) return ""
    val scheme = "${slot.sets} × ${targetText(slot.reps, exercise.timed)}"
    val kind = kindLabel(exercise.id, exercise.equipment)
    return if (exercise.equipment == Equipment.BODYWEIGHT && weight == 0.0) "$scheme · $kind"
    else "$scheme · ${fmtLoad(exercise.equipment, weight, unit)} · $kind"
}

@Composable
private fun RoutineEditor(model: Model, routine: Routine, dispatch: (Msg) -> Unit) {
    // Which sheet shows and which card is open are presentation: nothing else depends on them.
    var picking by rememberSaveable { mutableStateOf(false) }
    // The exercise whose sets and reps are being changed. One at a time keeps the list short.
    var openSlot by rememberSaveable { mutableStateOf<String?>(null) }
    // The name of an exercise just created in the picker. Its id comes from `update`, so the card
    // opens once the exercise shows up in this workout.
    var created by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val moveSlot = { from: Int, to: Int -> dispatch(Msg.Routines.MoveSlot(routine.id, from, to)) }
    // A slot is known by its exercise: a workout holds every exercise at most once.
    val reorder = rememberColumnReorderState(
        keys = routine.slots.map { it.exerciseId },
        onMove = moveSlot,
        scrollState = scroll,
    )

    // By exercise, not by index: `update` ignores a second remove of the same card. It offers Undo.
    fun remove(exerciseId: String) {
        if (openSlot == exerciseId) openSlot = null
        dispatch(Msg.Routines.RemoveSlot(routine.id, exerciseId))
    }

    val createdId = created?.let { name -> exerciseNamed(model, name)?.id }
    LaunchedEffect(createdId, routine.slots) {
        if (createdId != null && routine.slots.any { it.exerciseId == createdId }) {
            openSlot = createdId
            created = null
        }
    }

    // An empty name would leave a blank card. Leaving the editor counts as leaving the field.
    DisposableEffect(routine.id) {
        onDispose { dispatch(Msg.Routines.CommitName(routine.id)) }
    }

    Column(
        // keyboardPadding: a typed number of sets or reps near the bottom stays above the keyboard.
        // reorderContainer after it, so a drag scrolls at the edge of what the keyboard leaves visible.
        Modifier
            .fillMaxSize()
            .keyboardPadding()
            .reorderContainer(reorder)
            .verticalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(
            onClick = { dispatch(Msg.Nav.CloseRoutine) },
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 12.dp),
        ) {
            Icon(Icons.Back, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Workouts", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        OutlinedTextField(
            value = routine.name,
            onValueChange = { dispatch(Msg.Routines.Rename(routine.id, it)) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) dispatch(Msg.Routines.CommitName(routine.id)) },
            label = { Text("Name") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )

        if (routine.slots.isEmpty()) {
            EisenCard(Modifier.fillMaxWidth()) {
                Text("No exercises yet. Add the first one below.", modifier = Modifier.padding(16.dp), color = EisenColors.MutedForeground)
            }
        }

        routine.slots.forEachIndexed { index, slot ->
            key(slot.exerciseId) {
                ReorderableColumnItem(reorder, key = slot.exerciseId, shape = MaterialTheme.shapes.large) {
                    SwipeToRemove(enabled = !reorder.isDragging, onRemove = { remove(slot.exerciseId) }) {
                        SlotCard(
                            routine = routine,
                            index = index,
                            slot = slot,
                            exercise = findExercise(model, slot.exerciseId),
                            weight = progressOf(model, slot.exerciseId).weight,
                            unit = model.settings.unit,
                            onUpdate = { dispatch(Msg.Routines.UpdateSlot(routine.id, index, it)) },
                            onMove = moveSlot,
                            open = openSlot == slot.exerciseId,
                            reorder = reorder,
                            onToggle = { openSlot = if (openSlot == slot.exerciseId) null else slot.exerciseId },
                            onRemove = { remove(slot.exerciseId) },
                        )
                    }
                }
            }
        }

        Button(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Add exercise", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (workoutRunning(model)) {
            Text(
                "Changes apply from the next workout. The one in progress stays as it is.",
                modifier = Modifier.fillMaxWidth(),
                color = EisenColors.MutedForeground,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        val routines = model.state.routines
        if (routines.size > 1) {
            // The list's hold-and-drag without a gesture.
            val position = routines.indexOfFirst { it.id == routine.id }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Place in the rotation", color = EisenColors.MutedForeground, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoveButton("Move up", ArrowUp, enabled = position > 0, modifier = Modifier.weight(1f)) {
                        dispatch(Msg.Routines.Move(position, position - 1))
                    }
                    MoveButton("Move down", ArrowDown, enabled = position < routines.lastIndex, modifier = Modifier.weight(1f)) {
                        dispatch(Msg.Routines.Move(position, position + 1))
                    }
                }
            }
            ConfirmButton(
                label = "Delete workout",
                confirmLabel = "Tap again to delete",
                // Closes the editor too.
                onConfirm = { dispatch(Msg.Routines.Remove(routine.id)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (picking) {
        ExercisePickerSheet(
            exercises = exercises(model),
            exclude = routine.slots.map { it.exerciseId }.toSet(),
            onPick = { id ->
                dispatch(Msg.Routines.AddSlot(routine.id, id))
                openSlot = id
                picking = false
            },
            onDismiss = { picking = false },
            onCreate = { name, equipment, timed ->
                dispatch(Msg.Exercises.AddCustom(name, equipment, addTo = AddTarget.Routine(routine.id), timed = timed))
                created = name
                picking = false
            },
            onDelete = { id -> dispatch(Msg.Exercises.DeleteCustom(id)) },
        )
    }
}

/**
 * Swiping a slot to the left removes it; the snackbar offers Undo. It takes 40% of the width or
 * a fling, so a sloppy vertical scroll removes nothing. Off while a card is dragged.
 */
@Composable
private fun SwipeToRemove(enabled: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val haptics = rememberHaptics()
    // Not saved: a slot brought back by Undo must come back as a card, not as a swiped-away one.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    // A tick when the swipe passes the point where letting go removes, and again when it goes back.
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.drop(1).collect { haptics.perform(Haptic.STEP) }
    }
    val remove by rememberUpdatedState(onRemove)
    // The same callback every time: the box calls a new one again while the card is still swiped away.
    val onDismiss = remember(state) { { _: SwipeToDismissBoxValue -> remove() } }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            // Only while swiping: at rest the red would show at the card's round corners.
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Row(
                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(EisenColors.Destructive).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Trash, contentDescription = null, tint = OnDestructive)
                    Text("Remove", color = OnDestructive, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        gesturesEnabled = enabled,
        onDismiss = onDismiss,
    ) {
        content()
    }
}

@Composable
private fun SlotCard(
    routine: Routine,
    index: Int,
    slot: Slot,
    exercise: Exercise?,
    weight: Double,
    unit: WeightUnit,
    onUpdate: (Slot) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    open: Boolean,
    reorder: ColumnReorderState,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    fun update(next: Slot) = onUpdate(next)
    val canMove = routine.slots.size > 1
    val actions = moveActions(index, routine.slots.size, onMove) +
        CustomAccessibilityAction("Remove from workout") { onRemove(); true }

    EisenCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Change", onClick = onToggle)
                .semantics {
                    stateDescription = if (open) "Expanded" else "Collapsed"
                    customActions = actions
                }
                .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // On the leading edge, away from where a swipe to remove starts. A drag that starts here
            // moves the card at once; a tap counts as a tap on the row.
            Box(Modifier.size(48.dp).reorderHandle(reorder, slot.exerciseId, enabled = canMove), contentAlignment = Alignment.Center) {
                Icon(
                    Grip,
                    contentDescription = null,
                    tint = if (canMove) EisenColors.MutedForeground else EisenColors.Border,
                    modifier = Modifier.size(20.dp),
                )
            }
            ExerciseTile(exercise?.image, exercise?.name ?: "?")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise?.name ?: "Deleted exercise", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(detail(slot, exercise, weight, unit), color = EisenColors.MutedForeground, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Icon(if (open) Icons.ChevronUp else Icons.ChevronDown, contentDescription = null, tint = EisenColors.Primary)
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sets", modifier = Modifier.weight(1f), color = EisenColors.MutedForeground)
                    IntStepper(slot.sets, { update(slot.copy(sets = it)) }, label = "sets", min = 1, max = MAX_SETS)
                }
                // A timed exercise is held for seconds instead of done for reps.
                val timed = exercise?.timed == true
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (timed) "Hold" else "Reps", modifier = Modifier.weight(1f), color = EisenColors.MutedForeground)
                    val reps = slot.reps
                    when {
                        reps == null -> Text(if (timed) "As long as possible" else "As many as possible", fontWeight = FontWeight.Bold)
                        timed -> IntStepper(
                            reps,
                            { update(slot.copy(reps = it)) },
                            label = "seconds to hold",
                            step = HOLD_STEP_SECONDS,
                            min = MIN_HOLD_SECONDS,
                            max = MAX_HOLD_SECONDS,
                            format = ::holdText,
                            typedSuffix = "s",
                        )
                        else -> IntStepper(reps, { update(slot.copy(reps = it)) }, label = "reps", min = 1, max = MAX_REPS)
                    }
                }
                val max = slot.reps == null
                val fixed = if (timed) DEFAULT_HOLD_SECONDS else 5
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = max, role = Role.Checkbox) { update(slot.copy(reps = if (max) fixed else null)) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // The row is the toggle, so the box itself takes no clicks of its own.
                    Checkbox(
                        checked = max,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = EisenColors.Primary,
                            checkmarkColor = EisenColors.OnPrimary,
                            uncheckedColor = EisenColors.Border,
                        ),
                    )
                    Text(
                        if (timed) "Max hold instead of a fixed time" else "Max reps instead of a fixed number",
                        color = if (max) EisenColors.Foreground else EisenColors.MutedForeground,
                        fontSize = 14.sp,
                    )
                }
                ConfirmButton(
                    label = "Remove from workout",
                    confirmLabel = "Tap again to remove",
                    onConfirm = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    ghost = true,
                )
            }
        }
    }
}

@Composable
private fun MoveButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(ButtonHeight),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
