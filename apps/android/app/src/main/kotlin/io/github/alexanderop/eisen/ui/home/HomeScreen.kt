package io.github.alexanderop.eisen.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Change
import io.github.alexanderop.eisen.domain.ChangeKind
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Outcome
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.durationMinutes
import io.github.alexanderop.eisen.domain.outcome
import io.github.alexanderop.eisen.domain.recordIds
import io.github.alexanderop.eisen.domain.totalReps
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.ExerciseTile
import io.github.alexanderop.eisen.ui.fmt
import io.github.alexanderop.eisen.ui.formatDate
import io.github.alexanderop.eisen.ui.formatShortDate
import io.github.alexanderop.eisen.ui.picker.ButtonHeight
import io.github.alexanderop.eisen.ui.picker.EisenCard
import io.github.alexanderop.eisen.ui.picker.Eyebrow
import io.github.alexanderop.eisen.ui.picker.PillBadge
import io.github.alexanderop.eisen.ui.share.ShareWorkoutSheet
import io.github.alexanderop.eisen.ui.workout.rememberWorkoutEffects

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** One exercise of the workout on the card. Weight is null for bodyweight exercises. */
private class ExerciseRow(val id: String, val name: String, val image: String?, val sets: Int, val reps: Int?, val weight: Double?)

/** The running workout as it was started, or the next one as it is set up right now. */
private fun rows(): List<ExerciseRow> {
    val active = Store.state.active
    if (active != null) {
        return active.exercises.map { log ->
            ExerciseRow(
                log.id,
                log.name,
                Store.findExercise(log.id)?.image,
                log.sets.size,
                log.targetReps,
                if (log.equipment == Equipment.BODYWEIGHT) null else log.weight,
            )
        }
    }
    return Store.nextRoutine.slots.mapNotNull { slot ->
        val exercise = Store.findExercise(slot.exerciseId) ?: return@mapNotNull null
        ExerciseRow(
            exercise.id,
            exercise.name,
            exercise.image,
            slot.sets,
            slot.reps,
            if (exercise.equipment == Equipment.BODYWEIGHT) null else Store.progressOf(exercise.id).weight,
        )
    }
}

@Composable
fun HomeScreen(onOpenWorkout: () -> Unit) {
    val history = Store.state.history
    val lastWorkout = history.firstOrNull()
    val changes = Store.lastChanges

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Eyebrow(formatDate(System.currentTimeMillis()))
            Text(
                "Eisen 5×5",
                modifier = Modifier.semantics { heading() },
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
            )
        }
        if (changes != null) SavedCard(changes, lastWorkout)
        NextCard(onOpenWorkout)
        if (lastWorkout != null) {
            val now = System.currentTimeMillis()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(history.size.toString(), "workouts", Modifier.weight(1f))
                StatCard(history.count { now - it.startedAt < WEEK_MS }.toString(), "last 7 days", Modifier.weight(1f))
                StatCard(formatShortDate(lastWorkout.startedAt), "last workout", Modifier.weight(1f))
            }
        }
    }
}

private fun changeText(kind: ChangeKind) = when (kind) {
    ChangeKind.UP -> "Up"
    ChangeKind.REPEAT -> "Repeat"
    ChangeKind.DELOAD -> "Deload"
}

/** Records the workout that was just saved set, as "Hack Squat 85kg". */
private fun records(workout: Workout): List<String> {
    val ids = recordIds(Store.state.history, 0)
    return workout.exercises.filter { it.id in ids }.map { log ->
        if (log.equipment == Equipment.BODYWEIGHT) "${log.name} ${totalReps(log)} reps" else "${log.name} ${fmt(log.weight, workout.unit)}"
    }
}

@Composable
private fun SavedCard(changes: List<Change>, workout: Workout?) {
    EisenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Workout saved", modifier = Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = { Store.lastChanges = null }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Dismiss", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            if (workout != null) {
                val sets = workout.exercises.flatMap { it.sets }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A saved workout always has an end time. The minimum of one minute matches the PWA.
                    Text("${durationMinutes(workout) ?: 1} min", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("·", fontSize = 20.sp, color = EisenColors.MutedForeground)
                    Text("${sets.count { it != null }} of ${sets.size} sets", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                val records = records(workout)
                if (records.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillBadge("New record")
                        Text(records.joinToString(", "), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (changes.isEmpty()) {
                Text("No weight changes this time.", color = EisenColors.MutedForeground)
            } else {
                Column {
                    changes.forEachIndexed { index, change ->
                        if (index > 0) HorizontalDivider(color = EisenColors.Border)
                        ChangeRow(change)
                    }
                }
            }
            if (workout != null) {
                val skipped = workout.exercises.filter { outcome(it) == Outcome.SKIPPED }.map { it.name }
                if (skipped.isNotEmpty()) {
                    Text("Skipped: ${skipped.joinToString(", ")}", color = EisenColors.MutedForeground, fontSize = 13.sp)
                }
                ShareButton(workout)
            }
        }
    }
}

@Composable
private fun ChangeRow(change: Change) {
    val color = when (change.kind) {
        ChangeKind.UP -> EisenColors.Primary
        ChangeKind.DELOAD -> EisenColors.Warn
        ChangeKind.REPEAT -> Color.Unspecified
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(change.name, modifier = Modifier.weight(1f))
        Text(changeText(change.kind), color = EisenColors.MutedForeground)
        Text(fmt(change.to), modifier = Modifier.width(76.dp), color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

/** Opens the sheet that shares the workout as a picture, a link or Markdown. */
@Composable
private fun ShareButton(workout: Workout) {
    var sharing by rememberSaveable { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { sharing = true },
        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
    ) {
        Text("Share workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    if (sharing) ShareWorkoutSheet(workout, onDismiss = { sharing = false })
}

@Composable
private fun NextCard(onOpenWorkout: () -> Unit) {
    val active = Store.state.active
    val routines = Store.state.routines
    val next = Store.nextRoutine
    val rows = rows()

    EisenCard(Modifier.fillMaxWidth()) {
        // Children pad themselves, so the chip row can scroll to the card's edges.
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val inset = Modifier.padding(horizontal = 16.dp)
            Eyebrow(if (active != null) "In progress" else "Next up", inset)
            if (active == null && routines.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(routines, key = { it.id }) { routine ->
                        FilterChip(
                            selected = routine.id == next.id,
                            onClick = { Store.selectRoutine(routine.id) },
                            label = { Text(routine.name, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.height(40.dp),
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = EisenColors.Muted,
                                labelColor = EisenColors.MutedForeground,
                                selectedContainerColor = EisenColors.Primary,
                                selectedLabelColor = EisenColors.OnPrimary,
                            ),
                            border = null,
                        )
                    }
                }
            }
            Text(
                active?.name ?: next.name,
                modifier = inset.semantics { heading() },
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (rows.isEmpty()) {
                Text(
                    "This workout has no exercises yet. Add some in the Workouts tab.",
                    modifier = inset.padding(vertical = 12.dp),
                    color = EisenColors.MutedForeground,
                )
            } else {
                Column(inset) {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = EisenColors.Border)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ExerciseTile(row.image, row.name, size = 40.dp)
                            Text(row.name, modifier = Modifier.weight(1f))
                            Text("${row.sets}×${row.reps ?: "max"}", color = EisenColors.MutedForeground)
                            Text(
                                row.weight?.let { fmt(it) } ?: "BW",
                                modifier = Modifier.width(76.dp),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
            val button = inset.fillMaxWidth().height(ButtonHeight)
            if (active != null) {
                Button(onClick = onOpenWorkout, modifier = button, shape = MaterialTheme.shapes.medium) {
                    Text("Resume workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                // The same question and Undo as the workout screen's menu, see WorkoutSessionHost.
                val effects = rememberWorkoutEffects()
                TextButton(
                    onClick = { effects.send(WorkoutEvent.RequestDiscard) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = EisenColors.Destructive),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("Discard workout", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            } else {
                // A workout whose exercises were all deleted cannot start. The button stays put, like the PWA.
                Button(
                    onClick = { if (Store.startWorkout()) onOpenWorkout() },
                    enabled = rows.isNotEmpty(),
                    modifier = button,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Start workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    EisenCard(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, fontSize = 12.sp, color = EisenColors.MutedForeground, maxLines = 1)
        }
    }
}
