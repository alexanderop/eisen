package io.github.alexanderop.eisen.feature.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.MAX_RIR
import io.github.alexanderop.eisen.app.elm.OrderedExercise
import io.github.alexanderop.eisen.app.state.Settings
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.core.isPlateLoaded
import io.github.alexanderop.eisen.feature.workout.lastLogged
import io.github.alexanderop.eisen.feature.workout.rirOf
import io.github.alexanderop.eisen.feature.workout.rirSuffix
import io.github.alexanderop.eisen.feature.workout.rirText
import io.github.alexanderop.eisen.feature.workout.setText
import io.github.alexanderop.eisen.feature.workout.targetText
import io.github.alexanderop.eisen.feature.workout.warmupSets
import io.github.alexanderop.eisen.feature.workout.warmupStep
import io.github.alexanderop.eisen.app.elm.warmupKey
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Haptic
import io.github.alexanderop.eisen.core.ui.Haptics
import io.github.alexanderop.eisen.core.ui.RepsPick
import io.github.alexanderop.eisen.core.ui.RepsPickerState
import io.github.alexanderop.eisen.core.ui.SetHold
import io.github.alexanderop.eisen.core.ui.pickableReps
import io.github.alexanderop.eisen.core.ui.SwipeHaptics
import io.github.alexanderop.eisen.core.ui.ExerciseTile
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.SetButton
import io.github.alexanderop.eisen.core.ui.SetState
import io.github.alexanderop.eisen.core.ui.exerciseBitmap
import io.github.alexanderop.eisen.core.ui.fmt
import io.github.alexanderop.eisen.core.ui.fmtLoad
import io.github.alexanderop.eisen.core.ui.formatShortDate
import io.github.alexanderop.eisen.core.ui.pathIcon

internal val CardShape = RoundedCornerShape(18.dp)

@Composable
private fun WorkoutCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
        border = BorderStroke(1.dp, EisenColors.Border),
    ) {
        content()
    }
}

/**
 * "Nov 12: 80kg · 5 5 5 4 3", so the lifter sees what there is to beat. Plain bodyweight shows only the
 * reps, a timed exercise its holds: "Nov 12: 1:00 1:00 45s". Rated sets add " · RIR 2 2 1 0".
 */
internal fun lastTime(history: List<Workout>, id: String): String? {
    val last = lastLogged(history, id) ?: return null
    val reps = last.log.sets.joinToString(" ") { it?.let { count -> setText(count, last.log.timed) } ?: "–" }
    val plain = last.log.equipment == Equipment.BODYWEIGHT && last.log.weight == 0.0
    val load = if (plain) "" else "${fmtLoad(last.log.equipment, last.log.weight, last.workout.unit)} · "
    // The ratings wrap as one piece, so a lone "–" never ends up on a line of its own.
    return "${formatShortDate(last.workout.startedAt)}: $load$reps${rirSuffix(last.log).replace(' ', '\u00A0')}"
}

private fun setState(log: ExerciseLog, reps: Int?): SetState = when {
    reps == null -> SetState.EMPTY
    log.targetReps != null && reps < log.targetReps -> SetState.MISSED
    else -> SetState.DONE
}

/**
 * One exercise: its sets, what it did last time and the plates to load.
 * A finished one folds up into a single row that opens on a tap. [image] is the exercise's picture.
 */
@Composable
internal fun ExerciseCard(
    item: OrderedExercise,
    first: Boolean,
    expanded: Boolean,
    image: String?,
    settings: Settings,
    lastTime: String?,
    /** Per set, what it got last time. A max-reps set offers it on its button. */
    lastSets: List<Int?>,
    /** Rated sets show their reps in reserve on the button. */
    showRir: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTap: (setIndex: Int) -> Unit,
    /** What holding a set does: the numbers to pick from, the reps question, or a tap. */
    holds: SetHolds,
    /** Shows how holding a set works, until the lifter has picked reps once. */
    hint: Boolean,
    modifier: Modifier = Modifier,
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    val log = item.log
    WorkoutCard(modifier) {
        if (item.done) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExerciseTile(image, log.name, size = 40.dp)
                Text(log.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(log.sets.joinToString(" · ") { it?.let { count -> setText(count, log.timed) } ?: "" }, color = EisenColors.Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Icon(if (expanded) Icons.ChevronUp else Icons.ChevronDown, contentDescription = null, tint = EisenColors.Primary)
            }
        } else if (first) {
            Banner(image)
        }
        if (!item.done || expanded) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = if (item.done) 0.dp else 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClickLabel = "Edit ${log.name}", onClick = onEdit)
                        // Moving and putting off the exercise without a gesture, for TalkBack.
                        .semantics { if (actions.isNotEmpty()) customActions = actions },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!item.done && !first) ExerciseTile(image, log.name, size = 40.dp)
                    Text(if (item.done) "Sets" else log.name, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        buildAnnotatedString {
                            append("${log.sets.size}×${targetText(log.targetReps, log.timed)} ")
                            withStyle(SpanStyle(color = EisenColors.Foreground, fontWeight = FontWeight.Bold)) {
                                append(fmtLoad(log.equipment, log.weight, settings.unit))
                            }
                        },
                        color = EisenColors.MutedForeground,
                        fontSize = 15.sp,
                    )
                    Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.Primary, modifier = Modifier.size(20.dp))
                }
                SetsGrid(log, lastSets, showRir, onTap, holds)
                if (hint) {
                    Text(HOLD_HINT, color = EisenColors.MutedForeground, fontSize = 13.sp)
                }
                if (lastTime != null) {
                    Text("Last time, $lastTime", color = EisenColors.Foreground, fontSize = 13.sp)
                }
                if (log.timed) {
                    Text(
                        if (log.targetReps == null) "Hold as long as you can. Tap a set to start the timer." else "Tap a set to start the timer.",
                        color = EisenColors.MutedForeground,
                        fontSize = 13.sp,
                    )
                } else if (log.targetReps == null) {
                    Text(
                        if (lastSets.any { it != null }) "As many reps as you can. Tap a set to log last time's reps, tap it again to change them."
                        else "As many reps as you can. Tap a set to enter them.",
                        color = EisenColors.MutedForeground, fontSize = 13.sp)
                } else if (isPlateLoaded(log.equipment)) {
                    Text(platesText(log.weight, log.equipment, settings), color = EisenColors.MutedForeground, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * What a set button shows: the count logged, or the target in muted colour. A max hold shows "max",
 * a max set what it got last time ([last]) or "+" the first time.
 */
private fun setButtonText(log: ExerciseLog, reps: Int?, last: Int?): String = when {
    reps != null -> setText(reps, log.timed)
    log.targetReps != null -> setText(log.targetReps, log.timed)
    log.timed -> "max"
    else -> last?.toString() ?: "+"
}

/** How TalkBack reads a set: "5 reps", "5 reps, 2 in reserve". */
internal fun setDescription(log: ExerciseLog, reps: Int?, rir: Int? = null): String = when {
    reps == null -> "not logged"
    log.timed -> "held ${setText(reps, true)}"
    rir != null -> "$reps reps, ${if (rir >= MAX_RIR) "$MAX_RIR or more" else rir} in reserve"
    else -> "$reps reps"
}

/** The line under the sets that teaches the hold. It goes once the lifter has picked reps by holding a set. */
internal const val HOLD_HINT = "Tap: all reps. Hold: pick the reps."

/**
 * What holding a set of an exercise does. A set with a target opens its numbers in [picker]; picking one
 * sends [onPick] (set, reps). A max-reps set asks for its count ([onEnter]). A timed set has no numbers:
 * holding it is a tap.
 */
internal class SetHolds(
    val picker: RepsPickerState,
    val haptics: Haptics,
    val onPick: (set: Int, reps: Int) -> Unit,
    val onEnter: (set: Int) -> Unit,
)

private fun holdOf(log: ExerciseLog, set: Int, holds: SetHolds, onTap: (Int) -> Unit): SetHold {
    val target = log.targetReps
    return when {
        log.timed -> SetHold(label = null, action = null, start = { onTap(set); false })
        target == null -> SetHold(
            label = "Enter the reps",
            action = SET_REPS,
            start = {
                holds.haptics.perform(Haptic.THRESHOLD)
                holds.onEnter(set)
                false
            },
        )
        else -> SetHold(
            label = "Pick the reps",
            action = SET_REPS,
            start = { anchor ->
                val logged = log.sets.getOrNull(set)
                holds.picker.open(
                    RepsPick(pickableReps(target), logged, anchor) { reps ->
                        // A logged set is only corrected, which the program does without a feel of its own.
                        if (logged != null && reps != logged) holds.haptics.perform(Haptic.TOGGLE_ON)
                        holds.onPick(set, reps)
                    },
                )
                holds.haptics.perform(Haptic.THRESHOLD)
                true
            },
            slide = { at -> if (holds.picker.slide(at)) holds.haptics.perform(Haptic.STEP) },
            lift = { at, slid -> holds.picker.lift(at, slid) },
        )
    }
}

/** The custom action TalkBack offers on a set: what a long press does, without the gesture. */
internal const val SET_REPS = "Set reps"

/** Five sets to a row; an exercise with more sets wraps into a second row. */
@Composable
private fun SetsGrid(log: ExerciseLog, lastSets: List<Int?>, showRir: Boolean, onTap: (Int) -> Unit, holds: SetHolds) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        log.sets.indices.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (j in row) {
                    val reps = log.sets[j]
                    val rir = rirOf(log, j)?.takeIf { showRir && reps != null }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SetButton(
                            text = setButtonText(log, reps, lastSets.getOrNull(j)),
                            state = setState(log, reps),
                            onClick = { onTap(j) },
                            description = "${log.name} set ${j + 1}: ${setDescription(log, reps, rir)}",
                            badge = rir?.let(::rirText),
                            hold = holdOf(log, j, holds, onTap),
                        )
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A wide picture of the exercise across the top of the card. Nothing without an image. */
@Composable
private fun Banner(image: String?) {
    val bitmap = exerciseBitmap(image) ?: return
    Box(Modifier.fillMaxWidth().height(168.dp).background(EisenColors.Tile), contentAlignment = Alignment.Center) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(160.dp))
    }
}

/** The ramp up to the work weight of one exercise, ticked off set by set. [done] holds the ticked [warmupKey]s. */
@Composable
internal fun WarmupCard(
    log: ExerciseLog,
    settings: Settings,
    done: Set<String>,
    onToggle: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = settings.unit
    val sets = warmupSets(log.weight, emptyWeightFor(log.equipment, settings), warmupStep(log.equipment, unit))
    WorkoutCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.name, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Work set ${fmt(log.weight, unit)}", color = EisenColors.MutedForeground)
            }
            if (sets.isEmpty()) {
                Text("No warmup needed at this weight.", color = EisenColors.MutedForeground)
            } else {
                Column {
                    sets.forEachIndexed { k, set ->
                        val key = warmupKey(log.id, k)
                        val ticked = key in done
                        val decoration = if (ticked) TextDecoration.LineThrough else null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = ticked,
                                    role = Role.Checkbox,
                                    onValueChange = { onToggle(key) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(32.dp)) { Check(ticked) }
                            Text(
                                "${set.sets}×${set.reps}",
                                Modifier.width(44.dp),
                                color = if (ticked) EisenColors.MutedForeground else EisenColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textDecoration = decoration,
                            )
                            Text(
                                fmt(set.weight, unit),
                                Modifier.width(72.dp),
                                color = if (ticked) EisenColors.MutedForeground else EisenColors.Foreground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textDecoration = decoration,
                            )
                            Text(platesText(set.weight, log.equipment, settings), Modifier.weight(1f), color = EisenColors.MutedForeground, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Check(done: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(if (done) Modifier.background(EisenColors.Primary) else Modifier.border(2.dp, EisenColors.Border, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Check, contentDescription = null, tint = EisenColors.OnPrimary, modifier = Modifier.size(14.dp))
    }
}

private val Clock = pathIcon("Clock", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z", "M12 6v6l4 2")

/**
 * Swiping an open exercise to the left puts it off, like its "Do later" button. The card springs
 * back and moves behind the other open ones. Only sideways drags start it, so scrolling and
 * tapping a set never do.
 */
@Composable
internal fun DeferSwipe(enabled: Boolean, onDefer: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { distance -> distance * 0.35f })
    SwipeHaptics(state)
    val scope = rememberCoroutineScope()
    val defer by rememberUpdatedState(onDefer)
    // The same callback every time: the box calls a new one again while the card springs back.
    val onDismiss = remember(state) {
        { _: SwipeToDismissBoxValue ->
            defer()
            scope.launch { state.reset() }
            Unit
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        gesturesEnabled = enabled,
        onDismiss = onDismiss,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .clip(CardShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    Icon(Clock, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Do later", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) {
        content()
    }
}
