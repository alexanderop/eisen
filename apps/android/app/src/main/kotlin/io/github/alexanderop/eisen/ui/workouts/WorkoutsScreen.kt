package io.github.alexanderop.eisen.ui.workouts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.MAX_REPS
import io.github.alexanderop.eisen.domain.MAX_SETS
import io.github.alexanderop.eisen.domain.Routine
import io.github.alexanderop.eisen.domain.Slot
import io.github.alexanderop.eisen.domain.kindLabel
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.ExerciseTile
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.IntStepper
import io.github.alexanderop.eisen.ui.fmt
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.picker.ButtonHeight
import io.github.alexanderop.eisen.ui.picker.ConfirmButton
import io.github.alexanderop.eisen.ui.picker.EisenCard
import io.github.alexanderop.eisen.ui.picker.ExercisePickerSheet
import io.github.alexanderop.eisen.ui.picker.PillBadge

private val ArrowUp = pathIcon("ArrowUp", "m5 12 7-7 7 7", "M12 19V5")
private val ArrowDown = pathIcon("ArrowDown", "M12 5v14", "m19 12-7 7-7-7")

private const val NAME_LENGTH = 30

/** The workouts in the order they rotate in, and an editor for one of them. */
@Composable
fun WorkoutsScreen() {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = Store.state.routines.firstOrNull { it.id == editingId }

    if (editing != null) {
        // Back closes the editor first, like the "Workouts" button at the top.
        BackHandler { editingId = null }
        // A fresh scroll position and closed exercises for every workout opened.
        key(editing.id) {
            RoutineEditor(editing, onClose = { editingId = null })
        }
    } else {
        RoutineList(onOpen = { editingId = it })
    }
}

private fun summary(routine: Routine): String {
    val names = routine.slots.mapNotNull { Store.findExercise(it.exerciseId)?.name }
    return if (names.isNotEmpty()) names.joinToString(" · ") else "No exercises yet"
}

@Composable
private fun RoutineList(onOpen: (String) -> Unit) {
    val nextId = Store.nextRoutine.id
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
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
            Text("Workouts rotate in this order. Tap one to change it.", color = EisenColors.MutedForeground, fontSize = 13.sp)
        }
        for (routine in Store.state.routines) {
            EisenCard(Modifier.fillMaxWidth(), onClick = { onOpen(routine.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(routine.name, modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (routine.id == nextId) PillBadge("Next")
                        Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.Primary)
                    }
                    Text(summary(routine), color = EisenColors.MutedForeground, fontSize = 13.sp)
                }
            }
        }
        FilledTonalButton(
            onClick = { onOpen(Store.addRoutine().id) },
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        ) {
            Text("New workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** "5 × 5 · 80kg · Barbell". Bodyweight exercises have no weight to show. */
private fun detail(slot: Slot): String {
    val exercise = Store.findExercise(slot.exerciseId) ?: return ""
    val scheme = "${slot.sets} × ${slot.reps ?: "max"}"
    val kind = kindLabel(exercise.id, exercise.equipment)
    return if (exercise.equipment == Equipment.BODYWEIGHT) "$scheme · $kind"
    else "$scheme · ${fmt(Store.progressOf(exercise.id).weight)} · $kind"
}

@Composable
private fun RoutineEditor(routine: Routine, onClose: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // The exercise whose sets, reps and order are being changed. One at a time keeps the list short.
    var openSlot by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    // An empty name would leave a blank card. Leaving the editor counts as leaving the field.
    DisposableEffect(routine.id) {
        onDispose { fixName(routine.id) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(
            onClick = onClose,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 12.dp),
        ) {
            Icon(Icons.Back, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Workouts", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        OutlinedTextField(
            value = routine.name,
            onValueChange = { Store.renameRoutine(routine.id, it.take(NAME_LENGTH)) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) fixName(routine.id) },
            label = { Text("Name") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )

        if (routine.slots.isEmpty()) {
            EisenCard(Modifier.fillMaxWidth()) {
                Text("No exercises yet. Add the first one below.", modifier = Modifier.padding(16.dp), color = EisenColors.MutedForeground)
            }
        }

        routine.slots.forEachIndexed { index, slot ->
            key(slot.exerciseId) {
                SlotCard(
                    routine = routine,
                    index = index,
                    slot = slot,
                    open = openSlot == slot.exerciseId,
                    onToggle = { openSlot = if (openSlot == slot.exerciseId) null else slot.exerciseId },
                    onRemove = {
                        Store.removeSlot(routine.id, index)
                        openSlot = null
                    },
                )
            }
        }

        Button(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Add exercise", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (Store.state.active != null) {
            Text(
                "Changes apply from the next workout. The one in progress stays as it is.",
                modifier = Modifier.fillMaxWidth(),
                color = EisenColors.MutedForeground,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (Store.state.routines.size > 1) {
            ConfirmButton(
                label = "Delete workout",
                confirmLabel = "Tap again to delete",
                onConfirm = {
                    Store.removeRoutine(routine.id)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (picking) {
        ExercisePickerSheet(
            exclude = routine.slots.map { it.exerciseId }.toSet(),
            onPick = { id ->
                Store.addSlot(routine.id, id)
                openSlot = id
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private fun fixName(routineId: String) {
    val routine = Store.state.routines.firstOrNull { it.id == routineId } ?: return
    if (routine.name.isBlank()) Store.renameRoutine(routineId, "Workout")
}

@Composable
private fun SlotCard(routine: Routine, index: Int, slot: Slot, open: Boolean, onToggle: () -> Unit, onRemove: () -> Unit) {
    val exercise = Store.findExercise(slot.exerciseId)
    fun update(next: Slot) = Store.updateSlot(routine.id, index, next)

    EisenCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Change", onClick = onToggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExerciseTile(exercise?.image, exercise?.name ?: "?")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise?.name ?: "Deleted exercise", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(detail(slot), color = EisenColors.MutedForeground, fontSize = 13.sp)
            }
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
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Reps", modifier = Modifier.weight(1f), color = EisenColors.MutedForeground)
                    val reps = slot.reps
                    if (reps != null) {
                        IntStepper(reps, { update(slot.copy(reps = it)) }, label = "reps", min = 1, max = MAX_REPS)
                    } else {
                        Text("As many as possible", fontWeight = FontWeight.Bold)
                    }
                }
                val max = slot.reps == null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = max, role = Role.Checkbox) { update(slot.copy(reps = if (max) 5 else null)) },
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
                        "Max reps instead of a fixed number",
                        color = if (max) EisenColors.Foreground else EisenColors.MutedForeground,
                        fontSize = 14.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoveButton("Move up", ArrowUp, enabled = index > 0, modifier = Modifier.weight(1f)) {
                        Store.moveSlot(routine.id, index, -1)
                    }
                    MoveButton("Move down", ArrowDown, enabled = index < routine.slots.lastIndex, modifier = Modifier.weight(1f)) {
                        Store.moveSlot(routine.id, index, 1)
                    }
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
private fun MoveButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
