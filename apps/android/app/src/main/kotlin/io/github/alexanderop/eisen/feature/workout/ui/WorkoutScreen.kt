package io.github.alexanderop.eisen.feature.workout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.lastReps
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.app.elm.FinishReason
import io.github.alexanderop.eisen.app.elm.OrderedExercise
import io.github.alexanderop.eisen.app.elm.Session
import io.github.alexanderop.eisen.app.state.Settings
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.canDefer
import io.github.alexanderop.eisen.app.elm.AddTarget
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.RestScreen
import io.github.alexanderop.eisen.app.elm.RestState
import io.github.alexanderop.eisen.app.elm.exercises
import io.github.alexanderop.eisen.app.elm.findExercise
import io.github.alexanderop.eisen.app.elm.progressOf
import io.github.alexanderop.eisen.app.elm.settings
import io.github.alexanderop.eisen.core.isPlateLoaded
import io.github.alexanderop.eisen.app.elm.order
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.ReorderableItem
import io.github.alexanderop.eisen.core.ui.longPressToReorder
import io.github.alexanderop.eisen.core.ui.pathIcon
import io.github.alexanderop.eisen.core.ui.rememberLazyReorderState

private enum class Mode(val label: String) { WORKOUT("Workout"), WARMUP("Warmup") }

private val ChevronLeft = pathIcon("ChevronLeft", "m15 18-6-6 6-6")
private val MoreVertical = pathIcon(
    "MoreVertical",
    "M12 7a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
    "M12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
    "M12 21a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
    filled = true,
)

/**
 * The running workout. The exercise being worked on rises to the top, finished ones sink to the
 * bottom and fold up, so the top of the screen is what to do now. The exercise tapped last holds
 * its place for a moment ([Model.settling], decided by `update`); a manual order wins over the
 * rising. See `order` in `app/elm/WorkoutMachine.kt`.
 *
 * Kept here because it is presentation only: the Workout/Warmup switch, which exercise's edit sheet,
 * the picker and the menu are open, which finished cards are unfolded, and the scroll position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(model: Model, dispatch: (Msg) -> Unit) {
    val session = model.session
    // Confirmations show on top of the running workout. A discarded one is closed by `update`.
    if (session !is Session.WithRun || session is Session.Discarded) return
    val run = session.run
    val workout = run.workout
    val settings = model.settings

    var mode by rememberSaveable { mutableStateOf(Mode.WORKOUT) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()

    // The lifter looks at the phone between sets without touching it; it must not go dark.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    fun send(event: WorkoutEvent) = dispatch(Msg.Workout(event))

    val settling = model.settling?.id
    val ordered = order(run, settling)
    val doneCount = ordered.count { it.done }
    val openIds = ordered.filterNot { it.done }.map { it.log.id }
    val quiet = followSettled(listState, settling, openIds.firstOrNull())

    /** Settles the exercise tapped last without the list following it: the lifter is placing cards. */
    fun quietly(msg: Msg) {
        if (settling != null) quiet.value = true
        dispatch(msg)
    }

    fun defer(index: Int) {
        send(WorkoutEvent.Defer(index))
        editing = null
    }

    fun actionsFor(row: OrderedExercise, position: Int): List<CustomAccessibilityAction> = buildList {
        if (row.done) return@buildList
        if (position > 0) add(CustomAccessibilityAction("Move up") { quietly(Msg.WorkoutScreen.MoveBy(row.log.id, -1)); true })
        if (position < openIds.size - 1) add(CustomAccessibilityAction("Move down") { quietly(Msg.WorkoutScreen.MoveBy(row.log.id, 1)); true })
        if (canDefer(ordered, row)) add(CustomAccessibilityAction("Do later") { defer(row.index); true })
    }

    val reorder = rememberLazyReorderState(
        listState,
        keys = openIds,
        onMove = { from, to -> send(WorkoutEvent.Move(from, to)) },
        // Commits the card that is about to move, so the order under the finger holds still.
        onDragStart = { quietly(Msg.WorkoutScreen.SettleNow) },
    )

    val hold = model.hold
    val holding = hold?.let { workout.exercises.getOrNull(it.exercise) }
    val restScreen = model.restScreen
    val rest = model.rest
    // The "set done" screen after the last set of an exercise has no rest.
    val resting = restScreen?.takeIf { !it.minimised && (rest != null || it.setDone) && hold == null }?.let { workout.exercises.getOrNull(it.exercise) }

    Box(Modifier.fillMaxSize()) {
        // Under an open hold timer or rest screen the workout is out of reach, for TalkBack too.
        val covered = holding != null || resting != null
        Column(Modifier.fillMaxSize().background(EisenColors.Background).then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)) {
            CenterAlignedTopAppBar(
                title = { Text(workout.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = { dispatch(Msg.Nav.CloseWorkout) }) {
                        Icon(ChevronLeft, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Back", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    TextButton(onClick = { send(WorkoutEvent.RequestFinish) }) {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EisenColors.Background),
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
                                            image = findExercise(model, row.log.id)?.image,
                                            settings = settings,
                                            lastTime = lastTime(model.state.history, row.log.id),
                                            showRir = settings.trackRir,
                                            lastSets = row.log.sets.indices.map { lastReps(model.state.history, row.log.id, it) },
                                            onToggle = { expanded = if (row.log.id in expanded) expanded - row.log.id else expanded + row.log.id },
                                            onEdit = { editing = row.index },
                                            onTap = { set -> send(WorkoutEvent.TapSet(row.index, set)) },
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
                                PrimaryButton("Finish workout", Modifier.animateItem(), onClick = { send(WorkoutEvent.RequestFinish) })
                            }
                        }
                        item(key = "hint") {
                            Hint("Tap a set when you finish it. Tap again for every rep you missed.", Modifier.animateItem())
                        }
                    } else {
                        warmupItems(
                            workout.exercises.filter { isPlateLoaded(it.equipment) },
                            settings = settings,
                            done = model.warmupDone,
                            onToggle = { dispatch(Msg.WorkoutScreen.ToggleWarmup(it)) },
                        )
                    }
                }
                RestTimerBar(
                    rest = model.rest,
                    now = model.now,
                    onExtend = { dispatch(Msg.Rest.Extend) },
                    onDismiss = { dispatch(Msg.Rest.Dismiss) },
                    modifier = Modifier.align(Alignment.TopCenter),
                    onOpen = if (restScreen?.minimised == true) ({ dispatch(Msg.Rest.Open) }) else null,
                )
            }
        }
        RestScreenOverlay(if (restScreen != null && resting != null) Triple(restScreen, rest, resting) else null, model, dispatch)
        if (hold != null && holding != null) HoldTimerScreen(hold, holding, model.now, dispatch)
    }

    if (picking) {
        ExercisePickerSheet(
            exercises = exercises(model),
            exclude = workout.exercises.map { it.id }.toSet(),
            onPick = {
                send(WorkoutEvent.AddExercise(it))
                picking = false
            },
            onDismiss = { picking = false },
            onCreate = { name, equipment, timed ->
                dispatch(Msg.Exercises.AddCustom(name, equipment, addTo = AddTarget.Workout, timed = timed))
                picking = false
            },
            onDelete = { dispatch(Msg.Exercises.DeleteCustom(it)) },
        )
    }

    val editIndex = editing
    val edited = editIndex?.let { index -> ordered.firstOrNull { it.index == index } }
    if (editIndex != null && edited != null) {
        EditExerciseSheet(
            log = edited.log,
            settings = settings,
            increment = progressOf(model, edited.log.id).increment,
            onWeightChange = { send(WorkoutEvent.SetWeight(editIndex, it)) },
            onIncrementChange = { dispatch(Msg.Settings.SetIncrement(edited.log.id, it)) },
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

    model.repsQuestion?.let { question ->
        val log = workout.exercises.getOrNull(question.exercise) ?: return@let
        // The count being chosen is the lifter's draft until "Save set"; a new question starts over.
        var reps by remember(question) { mutableIntStateOf(question.reps) }
        RepsEntrySheet(
            title = "${log.name} · Set ${question.set + 1}",
            reps = reps,
            onRepsChange = { reps = it },
            onSave = { send(WorkoutEvent.LogReps(question.exercise, question.set, it)) },
            onDismiss = { dispatch(Msg.WorkoutScreen.CloseRepsEntry) },
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
                TextButton(onClick = { send(WorkoutEvent.Confirm) }) {
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

/**
 * The rest screen slides up over the workout when a rest starts and down to the bar when it is put
 * away. [shown] is the screen, its rest and its exercise, null when it is closed or minimised. The last
 * one stays on screen while it slides away.
 */
@Composable
private fun RestScreenOverlay(shown: Triple<RestScreen, RestState?, ExerciseLog>?, model: Model, dispatch: (Msg) -> Unit) {
    val last = remember { mutableStateOf(shown) }
    if (shown != null) last.value = shown
    AnimatedVisibility(
        visible = shown != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        last.value?.let { (screen, rest, log) ->
            RestTimerScreen(screen, rest, log, model.settings, model.now, dispatch)
        }
    }
}

/**
 * When the exercise that settled rose to the top, the list scrolls up after it. Returns a flag to set
 * before a settle the lifter caused by moving cards (a drag, TalkBack's move): then the list stays.
 * Scrolling is presentation; which card rises is decided by `update`.
 */
@Composable
private fun followSettled(listState: LazyListState, settling: String?, top: String?): MutableState<Boolean> {
    val quiet = remember { mutableStateOf(false) }
    val seen = remember { mutableStateOf(settling to top) }
    LaunchedEffect(settling, top) {
        val (before, beforeTop) = seen.value
        seen.value = settling to top
        if (before == null || before == settling) return@LaunchedEffect
        val follow = !quiet.value && top == before && beforeTop != before
        quiet.value = false
        if (follow) {
            // Waits for the list to show the new order before it scrolls.
            withFrameNanos { }
            listState.animateScrollToItem(0)
        }
    }
    return quiet
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

private fun LazyListScope.warmupItems(logs: List<ExerciseLog>, settings: Settings, done: Set<String>, onToggle: (key: String) -> Unit) {
    if (logs.isEmpty()) {
        item(key = "warmup-hint") { Hint("Warmup sets are worked out for barbell and plate machine exercises.") }
    }
    for (log in logs) {
        item(key = "warmup-${log.id}") { WarmupCard(log, settings, done, onToggle) }
    }
}
