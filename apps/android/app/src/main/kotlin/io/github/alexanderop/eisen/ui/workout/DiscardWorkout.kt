package io.github.alexanderop.eisen.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.Session
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.isStarted
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.fmt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToLong

/** How long "Workout discarded" offers Undo. Longer when accessibility settings ask for it. */
private const val UNDO_MS = 6_000L

/**
 * The parts of the workout flow that outlive the workout screen: the discard question, which the
 * workout screen and the home screen both ask, and the "Workout discarded" snackbar with Undo,
 * which shows on the home screen after the workout screen closed. Place it once, above both.
 * [onDiscarded] runs when a workout was discarded, [onRestored] after Undo brought it back.
 */
@Composable
fun WorkoutSessionHost(snackbar: SnackbarHostState, onDiscarded: () -> Unit, onRestored: () -> Unit) {
    val effects = rememberWorkoutEffects()
    val session = Store.session
    if (session is Session.ConfirmingDiscard) {
        DiscardWorkoutSheet(
            workout = session.workout,
            onDiscard = { effects.send(WorkoutEvent.Confirm) },
            onKeep = { effects.send(WorkoutEvent.Cancel) },
        )
    }

    val discarded = (session as? Session.Discarded)?.workout?.id
    val accessibility = LocalAccessibilityManager.current
    val closed by rememberUpdatedState(onDiscarded)
    val restored by rememberUpdatedState(onRestored)
    LaunchedEffect(discarded) {
        if (discarded == null) return@LaunchedEffect
        closed()
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(UNDO_MS, containsText = true, containsControls = true) ?: UNDO_MS
        // Indefinite plus a timeout: the snackbar's own durations are 4 or 10 seconds.
        val result = withTimeoutOrNull(timeout) {
            snackbar.showSnackbar("Workout discarded", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        }
        // Starting a new workout meanwhile cancels this effect: that start already forgot the old one.
        if (result == SnackbarResult.ActionPerformed) {
            effects.send(WorkoutEvent.Undo)
            restored()
        } else {
            effects.send(WorkoutEvent.Forget)
        }
    }
}

/** "7 sets logged · 18 min" for the workout so far. */
private fun summary(workout: Workout, now: Long): String {
    val sets = workout.exercises.sumOf { log -> log.sets.count { it != null } }
    val minutes = max(1, ((now - workout.startedAt) / 60000.0).roundToLong())
    return "$sets ${if (sets == 1) "set" else "sets"} logged · $minutes min"
}

/** What discarding loses, and the two ways out. Swiping it away keeps the workout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscardWorkoutSheet(workout: Workout, onDiscard: () -> Unit, onKeep: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slides the sheet away before the session moves on, so it does not vanish in one frame.
    fun close(then: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { then() }
    }
    val now = remember(workout.id) { System.currentTimeMillis() }
    val logged = workout.exercises.filter(::isStarted)
    ModalBottomSheet(onDismissRequest = onKeep, sheetState = state, containerColor = EisenColors.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Discard this workout?", Modifier.semantics { heading() }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(summary(workout, now), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Column {
                logged.forEachIndexed { index, log ->
                    if (index > 0) HorizontalDivider(color = EisenColors.Border)
                    LoggedRow(log, workout)
                }
            }
            Text(
                "Nothing is saved to your history and your weights do not change.",
                color = EisenColors.MutedForeground,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { close(onDiscard) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EisenColors.Destructive, contentColor = EisenColors.Background),
            ) {
                Icon(Icons.Trash, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Discard workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            SecondaryButton("Keep going") { close(onKeep) }
        }
    }
}

/** An exercise with logged sets, like a row of a workout in History. */
@Composable
private fun LoggedRow(log: ExerciseLog, workout: Workout) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(log.name, Modifier.weight(1f), fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            if (log.equipment == Equipment.BODYWEIGHT) "BW" else fmt(log.weight, workout.unit),
            Modifier.widthIn(min = 64.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
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
