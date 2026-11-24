package io.github.alexanderop.eisen.feature.workout.ui

import io.github.alexanderop.eisen.core.typedIncrementSnap
import io.github.alexanderop.eisen.core.typedWeightSnap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.app.state.Settings
import io.github.alexanderop.eisen.core.incrementStep
import io.github.alexanderop.eisen.core.isPlateLoaded
import io.github.alexanderop.eisen.core.stepFor
import io.github.alexanderop.eisen.core.unitDefaults
import io.github.alexanderop.eisen.core.ui.ADDED_WEIGHT_HINT
import io.github.alexanderop.eisen.core.ui.AddedWeightStepper
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.IntStepper
import io.github.alexanderop.eisen.core.ui.Stepper
import io.github.alexanderop.eisen.core.ui.fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState

/**
 * A bottom sheet with a title and a close button, in reach of the thumb.
 * [content] gets a `close` that slides the sheet away before it is removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val state = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = EisenColors.Card) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            FilledTonalIconButton(
                onClick = close,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.MutedForeground),
            ) {
                Icon(Icons.Close, contentDescription = "Close")
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content(close)
        }
    }
}

/**
 * Weight (or added weight), plates and increment of one exercise in the running workout, putting it off
 * and removing it. [increment] is the exercise's working increment, changed with [onIncrementChange].
 */
@Composable
internal fun EditExerciseSheet(
    log: ExerciseLog,
    settings: Settings,
    increment: Double,
    onWeightChange: (Double) -> Unit,
    onIncrementChange: (Double) -> Unit,
    canDefer: Boolean,
    onDefer: () -> Unit,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val unit = settings.unit
    WorkoutSheet(log.name, onDismiss) { close ->
        if (log.equipment == Equipment.BODYWEIGHT) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SheetRow("Added weight") { AddedWeightStepper(log.weight, onWeightChange, unit) }
                Text(ADDED_WEIGHT_HINT, color = EisenColors.MutedForeground, fontSize = 13.sp)
            }
        } else {
            val step = stepFor(log.equipment, unit)
            SheetRow("Weight") {
                Stepper(
                    value = log.weight,
                    onValueChange = onWeightChange,
                    step = step,
                    min = step,
                    label = "weight",
                    format = { fmt(it, unit) },
                    snap = typedWeightSnap(log.equipment, unit),
                )
            }
        }
        if (isPlateLoaded(log.equipment)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BarbellPlates(loadFor(log.weight, log.equipment, settings).plates, largest = unitDefaults(unit).plates[0])
                Text(platesText(log.weight, log.equipment, settings), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Text(
                    if (log.equipment == Equipment.MACHINE) "Plates only. The sled is not counted." else "Bar ${fmt(settings.bar, unit)}",
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = EisenColors.MutedForeground,
                    fontSize = 13.sp,
                )
            }
        }
        if (log.targetReps != null && log.equipment != Equipment.BODYWEIGHT) {
            val step = incrementStep(unit)
            SheetRow(if (log.timed) "Add after all holds" else "Add after all reps") {
                Stepper(
                    value = increment,
                    onValueChange = onIncrementChange,
                    step = step,
                    min = step,
                    label = "weight added after a full workout",
                    format = { "+${fmt(it, unit)}" },
                    snap = typedIncrementSnap(unit),
                )
            }
        }
        PrimaryButton("Done", onClick = close)
        if (canDefer) SecondaryButton("Do later", onClick = onDefer)
        if (canRemove) {
            // A new exercise must not inherit an armed button from the one shown before.
            androidx.compose.runtime.key(log.id) {
                ConfirmButton("Remove from this workout", "Tap again to remove", onConfirm = onRemove)
            }
        }
    }
}

/** Entering the reps of a max-reps set, which cannot be tapped down from a target. */
@Composable
internal fun RepsEntrySheet(
    title: String,
    reps: Int,
    onRepsChange: (Int) -> Unit,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    WorkoutSheet(title, onDismiss) {
        SheetRow("Reps") {
            IntStepper(value = reps, onValueChange = onRepsChange, label = "reps", onDone = { onSave(it) })
        }
        PrimaryButton("Save set") { onSave(reps) }
        SecondaryButton("Clear set") { onSave(null) }
    }
}

@Composable
private fun SheetRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = EisenColors.MutedForeground)
        control()
    }
}

private val ButtonShape = RoundedCornerShape(14.dp)

@Composable
internal fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().height(52.dp), shape = ButtonShape) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
internal fun SecondaryButton(text: String, modifier: Modifier = Modifier, contentColor: Color = EisenColors.Foreground, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = contentColor),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** A destructive action that needs two taps instead of a dialog. It disarms by itself after a few seconds. */
@Composable
private fun ConfirmButton(label: String, confirmLabel: String, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }
    SecondaryButton(if (armed) confirmLabel else label, contentColor = EisenColors.Destructive) {
        if (armed) {
            armed = false
            onConfirm()
        } else {
            armed = true
        }
    }
}
