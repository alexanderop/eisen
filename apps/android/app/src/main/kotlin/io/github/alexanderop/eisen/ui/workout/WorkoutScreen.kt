package io.github.alexanderop.eisen.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Effect
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.FinishReason
import io.github.alexanderop.eisen.domain.OrderedExercise
import io.github.alexanderop.eisen.domain.Session
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.canDefer
import io.github.alexanderop.eisen.domain.isPlateLoaded
import io.github.alexanderop.eisen.domain.order
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.ReorderableItem
import io.github.alexanderop.eisen.ui.longPressToReorder
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.picker.ExercisePickerSheet
import io.github.alexanderop.eisen.ui.rememberLazyReorderState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Mode(val label: String) { WORKOUT("Workout"), WARMUP("Warmup") }

/** The max-reps set being entered. */
private data class Entry(val exerciseIndex: Int, val setIndex: Int, val reps: Int)

/**
 * The exercise being worked on rises to the top, finished ones sink to the bottom and fold up,
 * so the top of the screen is what to do now. Starting with the third exercise because the
 * first one's machine is taken needs no sorting by hand.
 * The exercise tapped last stays where it is for a moment: the tap after a full set is
 * how missed reps are entered, and the card must not jump away under the thumb.
 *
 * A manual order wins over all of this: once the lifter dragged a card (or used Move up/down),
 * the open exercises keep that order until the workout ends and the one worked on no longer
 * rises. Finished ones still sink. The rule itself lives in `order` in `domain/WorkoutMachine.kt`.
 */
private const val SETTLE_MS = 2500L

private val ChevronLeft = pathIcon("ChevronLeft", "m15 18-6-6 6-6")
private val MoreVertical = pathIcon(
    "MoreVertical",
    "M12 7a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
    "M12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
    "M12 21a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
    filled = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(onClose: () -> Unit) {
    val session = Store.session
    // Confirmations show on top of the running workout. A discarded one is closed by the host.
    if (session !is Session.WithRun || session is Session.Discarded) return
    val run = session.run
    val workout = run.workout
    val effects = rememberWorkoutEffects()

    var mode by rememberSaveable { mutableStateOf(Mode.WORKOUT) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<Entry?>(null) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var settling by remember { mutableStateOf<String?>(null) }
    var settleCount by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The lifter looks at the phone between sets without touching it; it must not go dark.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    fun send(event: WorkoutEvent): List<Effect> =
        effects.send(event) { ask -> entry = Entry(ask.exercise, ask.set, ask.reps) }

    fun firstOpen(): String? = (Store.session as? Session.WithRun)?.run?.let { order(it, null).firstOrNull()?.log?.id }

    /** Lets the exercise tapped last take its place. [scrollUp] follows it when it rose to the top. */
    fun settle(scrollUp: Boolean = true) {
        val id = settling ?: return
        settling = null
        val before = firstOpen()
        send(WorkoutEvent.Rise(id))
        if (scrollUp && firstOpen() == id && before != id) {
            scope.launch {
                // Waits for the list to show the new order before it scrolls.
                withFrameNanos { }
                listState.animateScrollToItem(0)
            }
        }
    }

    LaunchedEffect(settleCount) {
        if (settling == null) return@LaunchedEffect
        delay(SETTLE_MS)
        settle()
    }

    fun touch(id: String) {
        if (settling != id) settle()
        settling = id
        settleCount++
    }

    val ordered = order(run, settling)
    val doneCount = ordered.count { it.done }
    val openIds = ordered.filterNot { it.done }.map { it.log.id }

    fun onTap(exerciseIndex: Int, setIndex: Int) {
        val log = workout.exercises.getOrNull(exerciseIndex) ?: return
        send(WorkoutEvent.TapSet(exerciseIndex, setIndex))
        // A max-reps set opens the entry instead; the card moves once the count is saved.
        if (log.targetReps != null) touch(log.id)
    }

    fun saveEntry(reps: Int?) {
        val current = entry ?: return
        val id = workout.exercises.getOrNull(current.exerciseIndex)?.id
        send(WorkoutEvent.LogReps(current.exerciseIndex, current.setIndex, reps))
        if (id != null) touch(id)
        entry = null
    }

    fun finishWith(event: WorkoutEvent) {
        if (send(event).any { it is Effect.Saved }) onClose()
    }

    fun defer(index: Int) {
        send(WorkoutEvent.Defer(index))
        editing = null
    }

    /** Moves an open exercise one place up (-1) or down (1), for TalkBack. */
    fun moveBy(id: String, step: Int) {
        settle(scrollUp = false)
        val current = (Store.session as? Session.WithRun)?.run ?: return
        val from = order(current, null).filterNot { it.done }.indexOfFirst { it.log.id == id }
        if (from != -1) send(WorkoutEvent.Move(from, from + step))
    }

    fun actionsFor(row: OrderedExercise, position: Int): List<CustomAccessibilityAction> = buildList {
        if (row.done) return@buildList
        if (position > 0) add(CustomAccessibilityAction("Move up") { moveBy(row.log.id, -1); true })
        if (position < openIds.size - 1) add(CustomAccessibilityAction("Move down") { moveBy(row.log.id, 1); true })
        if (canDefer(ordered, row)) add(CustomAccessibilityAction("Do later") { defer(row.index); true })
    }

    val reorder = rememberLazyReorderState(
        listState,
        keys = openIds,
        onMove = { from, to -> send(WorkoutEvent.Move(from, to)) },
        // Commits the card that is about to move, so the order under the finger holds still.
        onDragStart = { settle(scrollUp = false) },
    )

    Column(Modifier.fillMaxSize().background(EisenColors.Background)) {
        CenterAlignedTopAppBar(
            title = { Text(workout.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
            navigationIcon = {
                TextButton(onClick = onClose) {
                    Icon(ChevronLeft, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Back", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            actions = {
                TextButton(onClick = { finishWith(WorkoutEvent.RequestFinish) }) {
                    Text("Finish", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(MoreVertical, contentDescription = "More options", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = EisenColors.Muted,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Discard workout", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                menuOpen = false
                                send(WorkoutEvent.RequestDiscard)
                            },
                            leadingIcon = { Icon(Icons.Trash, contentDescription = null) },
                            colors = MenuDefaults.itemColors(textColor = EisenColors.Destructive, leadingIconColor = EisenColors.Destructive),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EisenColors.Background),
        )

        // The rest timer hangs below the header, at the top of the screen and away from the thumb.
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            val bottom = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom + 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "mode") {
                    ModeSwitch(mode, onChange = { mode = it })
                }
                if (mode == Mode.WORKOUT) {
                    ordered.forEachIndexed { position, row ->
                        if (row.done && position == ordered.size - doneCount) {
                            item(key = "done-title") {
                                Text(
                                    "DONE · $doneCount OF ${ordered.size}",
                                    Modifier.animateItem().padding(top = 8.dp),
                                    color = EisenColors.MutedForeground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                )
                            }
                        }
                        item(key = row.log.id) {
                            ReorderableItem(reorder, key = row.log.id, shape = CardShape) {
                                DeferSwipe(
                                    enabled = canDefer(ordered, row) && !reorder.isDragging,
                                    onDefer = { defer(row.index) },
                                ) {
                                    ExerciseCard(
                                        item = row,
                                        first = position == 0,
                                        expanded = row.log.id in expanded,
                                        lastTime = lastTime(row.log.id),
                                        onToggle = { expanded = if (row.log.id in expanded) expanded - row.log.id else expanded + row.log.id },
                                        onEdit = { editing = row.index },
                                        onTap = { set -> onTap(row.index, set) },
                                        modifier = Modifier.longPressToReorder(reorder, row.log.id, enabled = !row.done && openIds.size > 1),
                                        actions = actionsFor(row, position),
                                    )
                                }
                            }
                        }
                    }
                    item(key = "add") {
                        SecondaryButton("Add exercise", Modifier.animateItem()) { picking = true }
                    }
                    if (doneCount == ordered.size) {
                        item(key = "finish") {
                            PrimaryButton("Finish workout", Modifier.animateItem(), onClick = { finishWith(WorkoutEvent.RequestFinish) })
                        }
                    }
                    item(key = "hint") {
                        Hint("Tap a set when you finish it. Tap again for every rep you missed.", Modifier.animateItem())
                    }
                } else {
                    warmupItems(workout.exercises.filter { isPlateLoaded(it.equipment) })
                }
            }
            RestTimerBar(Modifier.align(Alignment.TopCenter))
        }
    }

    if (picking) {
        ExercisePickerSheet(
            exclude = workout.exercises.map { it.id }.toSet(),
            onPick = {
                send(WorkoutEvent.AddExercise(it))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    val editIndex = editing
    val edited = editIndex?.let { index -> ordered.firstOrNull { it.index == index } }
    if (editIndex != null && edited != null) {
        EditExerciseSheet(
            log = edited.log,
            onWeightChange = { send(WorkoutEvent.SetWeight(editIndex, it)) },
            canDefer = canDefer(ordered, edited),
            onDefer = { defer(editIndex) },
            canRemove = workout.exercises.size > 1,
            onRemove = {
                send(WorkoutEvent.RemoveExercise(editIndex))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    entry?.let { current ->
        val log = workout.exercises.getOrNull(current.exerciseIndex) ?: return@let
        RepsEntrySheet(
            title = "${log.name} · Set ${current.setIndex + 1}",
            reps = current.reps,
            onRepsChange = { entry = current.copy(reps = it) },
            onSave = ::saveEntry,
            onDismiss = { entry = null },
        )
    }

    if (session is Session.ConfirmingFinish) {
        val nothingLogged = session.reason == FinishReason.NOTHING_LOGGED
        AlertDialog(
            onDismissRequest = { send(WorkoutEvent.Cancel) },
            containerColor = EisenColors.Card,
            title = { Text(if (nothingLogged) "Nothing logged" else "Finish early?") },
            text = {
                Text(
                    if (nothingLogged) "This workout has no sets. It will not be saved."
                    else "Some sets are not logged. Exercises you started keep their weight next time. Exercises you did not start are skipped.",
                    color = EisenColors.MutedForeground,
                )
            },
            confirmButton = {
                TextButton(onClick = { finishWith(WorkoutEvent.Confirm) }) {
                    if (nothingLogged) Text("Discard workout", color = EisenColors.Destructive, fontWeight = FontWeight.Bold)
                    else Text("Finish workout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { send(WorkoutEvent.Cancel) }) { Text("Keep going", color = EisenColors.Foreground) }
            },
        )
    }
}

@Composable
private fun ModeSwitch(mode: Mode, onChange: (Mode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Mode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, Mode.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = EisenColors.Muted,
                    activeContentColor = EisenColors.Foreground,
                    activeBorderColor = EisenColors.Border,
                    inactiveContainerColor = EisenColors.Card,
                    inactiveContentColor = EisenColors.MutedForeground,
                    inactiveBorderColor = EisenColors.Border,
                ),
            ) {
                Text(option.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth(), color = EisenColors.MutedForeground, fontSize = 13.sp, textAlign = TextAlign.Center)
}

private fun LazyListScope.warmupItems(logs: List<ExerciseLog>) {
    if (logs.isEmpty()) {
        item(key = "warmup-hint") { Hint("Warmup sets are worked out for barbell and plate machine exercises.") }
    }
    for (log in logs) {
        item(key = "warmup-${log.id}") { WarmupCard(log) }
    }
}
