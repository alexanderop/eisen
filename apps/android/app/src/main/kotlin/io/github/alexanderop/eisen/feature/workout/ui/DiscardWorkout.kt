package io.github.alexanderop.eisen.feature.workout.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.app.elm.Session
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.feature.workout.setText
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.isStarted
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.fmtLoad
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToLong
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState

/**
 * The discard question, which the workout screen and the home screen both ask. Place it once,
 * above both. It shows while [session] asks it. [now] is the model's clock, read again by `update`
 * when the question is asked. The "Workout discarded" snackbar with Undo comes from `update` and
 * shows in the app's one SnackbarHost.
 */
@Composable
fun WorkoutSessionHost(session: Session, now: Long, dispatch: (Msg) -> Unit) {
    if (session is Session.ConfirmingDiscard) {
        DiscardWorkoutSheet(
            workout = session.workout,
            now = now,
            onDiscard = { dispatch(Msg.Workout(WorkoutEvent.Confirm)) },
            onKeep = { dispatch(Msg.Workout(WorkoutEvent.Cancel)) },
        )
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
private fun DiscardWorkoutSheet(workout: Workout, now: Long, onDiscard: () -> Unit, onKeep: () -> Unit) {
    val state = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    // Slides the sheet away before the session moves on, so it does not vanish in one frame.
    fun close(then: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { then() }
    }
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
            fmtLoad(log.equipment, log.weight, workout.unit),
            Modifier.widthIn(min = 64.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
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
