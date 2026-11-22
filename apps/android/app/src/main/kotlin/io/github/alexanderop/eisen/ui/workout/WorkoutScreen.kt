package io.github.alexanderop.eisen.ui.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Outcome
import io.github.alexanderop.eisen.domain.isComplete
import io.github.alexanderop.eisen.domain.isPlateLoaded
import io.github.alexanderop.eisen.domain.outcome
import io.github.alexanderop.eisen.rest.Rest
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.picker.ExercisePickerSheet
import io.github.alexanderop.eisen.ui.rememberHaptics
import kotlinx.coroutines.delay

private enum class Mode(val label: String) { WORKOUT("Workout"), WARMUP("Warmup") }

/** The max-reps set being entered. */
private data class Entry(val exerciseIndex: Int, val setIndex: Int, val reps: Int)

/**
 * The exercise tapped last stays where it is for a moment: the tap after a full set is
 * how missed reps are entered, and the card must not jump away under the thumb.
 */
private const val SETTLE_MS = 2500L

private val ChevronLeft = pathIcon("ChevronLeft", "m15 18-6-6 6-6")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(onClose: () -> Unit) {
    val workout = Store.state.active ?: return
    val settings = Store.state.settings
    val haptics = rememberHaptics()
    val startRest = rememberRestStarter()

    var mode by rememberSaveable { mutableStateOf(Mode.WORKOUT) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var entry by remember { mutableStateOf<Entry?>(null) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var settling by remember { mutableStateOf<String?>(null) }
    var settleCount by remember { mutableIntStateOf(0) }

    // The lifter looks at the phone between sets without touching it; it must not go dark.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(settleCount) {
        if (settling == null) return@LaunchedEffect
        delay(SETTLE_MS)
        settling = null
    }

    fun touch(id: String) {
        settling = id
        settleCount++
    }

    // Finished exercises sink to the bottom and fold up, so the top of the screen is what is left to do.
    val items = workout.exercises.mapIndexed { index, log ->
        ExerciseItem(log, index, done = log.id != settling && log.sets.all { it != null })
    }
    val ordered = items.filterNot { it.done } + items.filter { it.done }
    val doneCount = ordered.count { it.done }
    val nothingLogged = workout.exercises.all { outcome(it) == Outcome.SKIPPED }

    fun onTap(exerciseIndex: Int, setIndex: Int) {
        val log = Store.state.active?.exercises?.getOrNull(exerciseIndex) ?: return
        val target = log.targetReps
        if (target == null) {
            entry = Entry(exerciseIndex, setIndex, log.sets[setIndex] ?: Store.suggestedReps(log.id, setIndex))
            return
        }
        val reps = Store.tapSet(exerciseIndex, setIndex)
        touch(log.id)
        // Read again after the tap: outcome and completeness depend on the set just logged.
        val active = Store.state.active ?: return
        val updated = active.exercises[exerciseIndex]
        if (reps != null && reps < target) haptics.perform(Haptic.WARNING)
        else haptics.perform(if (outcome(updated) == Outcome.SUCCESS) Haptic.SUCCESS else Haptic.TAP)
        if (reps == null || isComplete(active)) {
            Rest.stop()
            return
        }
        val done = reps == target
        startRest(
            if (done) settings.restDone else settings.restMissed,
            if (done) "All reps done." else "Missed reps. Rest longer.",
        )
    }

    fun saveEntry(reps: Int?) {
        val current = entry ?: return
        Store.logReps(current.exerciseIndex, current.setIndex, reps)
        val active = Store.state.active ?: return
        touch(active.exercises[current.exerciseIndex].id)
        entry = null
        haptics.perform(Haptic.TAP)
        if (reps == null || isComplete(active)) Rest.stop()
        else startRest(settings.restDone, "Max set logged.")
    }

    fun finish() {
        haptics.perform(Haptic.DONE)
        Rest.stop()
        Store.finishWorkout()
        onClose()
    }

    fun discard() {
        Rest.stop()
        Store.discardWorkout()
        onClose()
    }

    fun onFinish() {
        if (isComplete(workout)) finish() else confirming = true
    }

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
                TextButton(onClick = ::onFinish) {
                    Text("Finish", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EisenColors.Background),
        )

        // The rest timer hangs below the header, at the top of the screen and away from the thumb.
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            val bottom = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
            LazyColumn(
                Modifier.fillMaxSize(),
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
                            ExerciseCard(
                                item = row,
                                first = position == 0,
                                expanded = row.log.id in expanded,
                                lastTime = lastTime(row.log.id),
                                onToggle = { expanded = if (row.log.id in expanded) expanded - row.log.id else expanded + row.log.id },
                                onEdit = { editing = row.index },
                                onTap = { set -> onTap(row.index, set) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    item(key = "add") {
                        SecondaryButton("Add exercise", Modifier.animateItem()) { picking = true }
                    }
                    if (doneCount == ordered.size) {
                        item(key = "finish") {
                            PrimaryButton("Finish workout", Modifier.animateItem(), onClick = ::onFinish)
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
                Store.addActiveExercise(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    val editIndex = editing
    val edited = editIndex?.let { workout.exercises.getOrNull(it) }
    if (editIndex != null && edited != null) {
        EditExerciseSheet(
            index = editIndex,
            log = edited,
            canRemove = workout.exercises.size > 1,
            onRemove = {
                Store.removeActiveExercise(editIndex)
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

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
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
                if (nothingLogged) {
                    TextButton(onClick = ::discard) { Text("Discard workout", color = EisenColors.Destructive, fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = ::finish) { Text("Finish workout", fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Keep going", color = EisenColors.Foreground) }
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

private fun LazyListScope.warmupItems(logs: List<io.github.alexanderop.eisen.domain.ExerciseLog>) {
    if (logs.isEmpty()) {
        item(key = "warmup-hint") { Hint("Warmup sets are worked out for barbell and plate machine exercises.") }
    }
    for (log in logs) {
        item(key = "warmup-${log.id}") { WarmupCard(log) }
    }
}

/**
 * Starts a rest. The first time, Android 13 and newer asks to allow notifications, so the rest
 * counts down on the lock screen. The rest starts whatever the answer is.
 */
@Composable
private fun rememberRestStarter(): (Int, String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val current = Rest.state ?: return@rememberLauncherForActivityResult
        // The rest started before the answer came, so it has no notification yet. Re-arming it
        // with nothing added shows one. Skipped for short or ending rests, whose cues it would change.
        if (granted && current.endsAt - current.startedAt >= 20_000 && current.endsAt - System.currentTimeMillis() > 11_000) {
            Rest.extend(0)
        }
    }
    return remember(context, launcher) {
        { seconds, note ->
            if (shouldAskForNotifications(context)) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED_KEY, true).apply()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Rest.start(seconds, note)
        }
    }
}

private const val PREFS = "workout_screen"
private const val ASKED_KEY = "askedNotifications"

private fun shouldAskForNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED_KEY, false)
