package io.github.alexanderop.eisen.ui.workout

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
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.OrderedExercise
import io.github.alexanderop.eisen.domain.isPlateLoaded
import io.github.alexanderop.eisen.domain.lastLogged
import io.github.alexanderop.eisen.domain.warmupSets
import io.github.alexanderop.eisen.domain.warmupStep
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.ExerciseTile
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.SetButton
import io.github.alexanderop.eisen.ui.SetState
import io.github.alexanderop.eisen.ui.emptyWeightFor
import io.github.alexanderop.eisen.ui.exerciseBitmap
import io.github.alexanderop.eisen.ui.fmt
import io.github.alexanderop.eisen.ui.fmtLoad
import io.github.alexanderop.eisen.ui.formatShortDate
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.platesText

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

/** "Nov 12: 80kg · 5 5 5 4 3", so the lifter sees what there is to beat. Plain bodyweight shows only the reps. */
internal fun lastTime(id: String): String? {
    val last = lastLogged(Store.state.history, id) ?: return null
    val reps = last.log.sets.joinToString(" ") { it?.toString() ?: "–" }
    val plain = last.log.equipment == Equipment.BODYWEIGHT && last.log.weight == 0.0
    val load = if (plain) "" else "${fmtLoad(last.log.equipment, last.log.weight, last.workout.unit)} · "
    return "${formatShortDate(last.workout.startedAt)}: $load$reps"
}

private fun setState(log: ExerciseLog, reps: Int?): SetState = when {
    reps == null -> SetState.EMPTY
    log.targetReps != null && reps < log.targetReps -> SetState.MISSED
    else -> SetState.DONE
}

/**
 * One exercise: its sets, what it did last time and the plates to load.
 * A finished one folds up into a single row that opens on a tap.
 */
@Composable
internal fun ExerciseCard(
    item: OrderedExercise,
    first: Boolean,
    expanded: Boolean,
    lastTime: String?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTap: (setIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    val log = item.log
    val image = Store.findExercise(log.id)?.image
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
                Text(log.sets.joinToString(" · ") { it?.toString() ?: "" }, color = EisenColors.Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
                            append("${log.sets.size}×${log.targetReps ?: "max"} ")
                            withStyle(SpanStyle(color = EisenColors.Foreground, fontWeight = FontWeight.Bold)) {
                                append(fmtLoad(log.equipment, log.weight))
                            }
                        },
                        color = EisenColors.MutedForeground,
                        fontSize = 15.sp,
                    )
                    Icon(Icons.ChevronRight, contentDescription = null, tint = EisenColors.Primary, modifier = Modifier.size(20.dp))
                }
                SetsGrid(log, onTap)
                if (lastTime != null) {
                    Text("Last time, $lastTime", color = EisenColors.Foreground, fontSize = 13.sp)
                }
                if (log.targetReps == null) {
                    Text("As many reps as you can. Tap a set to enter them.", color = EisenColors.MutedForeground, fontSize = 13.sp)
                } else if (isPlateLoaded(log.equipment)) {
                    Text(platesText(log.weight, log.equipment), color = EisenColors.MutedForeground, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Five sets to a row; an exercise with more sets wraps into a second row. */
@Composable
private fun SetsGrid(log: ExerciseLog, onTap: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        log.sets.indices.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (j in row) {
                    val reps = log.sets[j]
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SetButton(
                            text = (reps ?: log.targetReps)?.toString() ?: "+",
                            state = setState(log, reps),
                            onClick = { onTap(j) },
                            description = "${log.name} set ${j + 1}: ${if (reps == null) "not logged" else "$reps reps"}",
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

/** The ramp up to the work weight of one exercise, ticked off set by set. */
@Composable
internal fun WarmupCard(log: ExerciseLog, modifier: Modifier = Modifier) {
    val sets = warmupSets(log.weight, emptyWeightFor(log.equipment), warmupStep(log.equipment, Store.state.settings.unit))
    WorkoutCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.name, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Work set ${fmt(log.weight)}", color = EisenColors.MutedForeground)
            }
            if (sets.isEmpty()) {
                Text("No warmup needed at this weight.", color = EisenColors.MutedForeground)
            } else {
                Column {
                    sets.forEachIndexed { k, set ->
                        val key = "${log.id}:$k"
                        val done = key in Store.warmupDone
                        val decoration = if (done) TextDecoration.LineThrough else null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = done,
                                    role = Role.Checkbox,
                                    onValueChange = { Store.warmupDone = if (done) Store.warmupDone - key else Store.warmupDone + key },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(32.dp)) { Check(done) }
                            Text(
                                "${set.sets}×${set.reps}",
                                Modifier.width(44.dp),
                                color = if (done) EisenColors.MutedForeground else EisenColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textDecoration = decoration,
                            )
                            Text(
                                fmt(set.weight),
                                Modifier.width(72.dp),
                                color = if (done) EisenColors.MutedForeground else EisenColors.Foreground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textDecoration = decoration,
                            )
                            Text(platesText(set.weight, log.equipment), Modifier.weight(1f), color = EisenColors.MutedForeground, fontSize = 13.sp)
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
