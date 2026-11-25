package io.github.alexanderop.eisen.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.BuildConfig
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.feature.workout.ExerciseProgress
import io.github.alexanderop.eisen.feature.workout.RIR_BOOST_FROM
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.backupFileName
import io.github.alexanderop.eisen.app.elm.findExercise
import io.github.alexanderop.eisen.app.elm.progressOf
import io.github.alexanderop.eisen.app.elm.settings
import io.github.alexanderop.eisen.app.elm.workoutRunning
import io.github.alexanderop.eisen.core.incrementStep
import io.github.alexanderop.eisen.core.stepFor
import io.github.alexanderop.eisen.core.typedIncrementSnap
import io.github.alexanderop.eisen.core.typedWeightSnap
import io.github.alexanderop.eisen.core.unitDefaults
import io.github.alexanderop.eisen.feature.health.ui.HealthConnectRows
import io.github.alexanderop.eisen.core.ui.ADDED_WEIGHT_HINT
import io.github.alexanderop.eisen.core.ui.AddedWeightStepper
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.ExerciseTile
import io.github.alexanderop.eisen.core.ui.Haptic
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.IntStepper
import io.github.alexanderop.eisen.core.ui.Stepper
import io.github.alexanderop.eisen.core.ui.fmt
import io.github.alexanderop.eisen.core.ui.fmtLoad
import io.github.alexanderop.eisen.core.ui.formatClock
import io.github.alexanderop.eisen.core.ui.pathIcon
import io.github.alexanderop.eisen.core.ui.rememberHaptics
import io.github.alexanderop.eisen.feature.timer.ui.TimerCallsControls
import kotlinx.coroutines.launch
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState

// The Lucide icons of the PWA's settings. Circles and rectangles are written as paths.
private val ScaleIcon = pathIcon(
    "Scale",
    "M12 3v18",
    "m19 8 3 8a5 5 0 0 1-6 0zV7",
    "M3 7h1a17 17 0 0 0 8-2 17 17 0 0 0 8 2h1",
    "m5 8 3 8a5 5 0 0 1-6 0zV7",
    "M7 21h10",
)
private val WeightIcon = pathIcon(
    "Weight",
    "M9 5a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "M6.5 8a2 2 0 0 0-1.905 1.46L2.1 18.5A2 2 0 0 0 4 21h16a2 2 0 0 0 1.925-2.54L19.4 9.5A2 2 0 0 0 17.48 8Z",
)
private val TimerIcon = pathIcon("Timer", "M10 2h4", "M12 14l3-3", "M4 14a8 8 0 1 0 16 0a8 8 0 1 0-16 0")
private val HourglassIcon = pathIcon(
    "Hourglass",
    "M5 22h14",
    "M5 2h14",
    "M17 22v-4.172a2 2 0 0 0-.586-1.414L12 12l-4.414 4.414A2 2 0 0 0 7 17.828V22",
    "M7 2v4.172a2 2 0 0 0 .586 1.414L12 12l4.414-4.414A2 2 0 0 0 17 6.172V2",
)
private val GaugeIcon = pathIcon("Gauge", "m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0")
private val VolumeIcon = pathIcon(
    "Volume",
    "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z",
    "M16 9a5 5 0 0 1 0 6",
    "M19.364 18.364a9 9 0 0 0 0-12.728",
)
private val VibrateIcon = pathIcon(
    "Vibrate",
    "m2 8 2 2-2 2 2 2-2 2",
    "m22 8-2 2 2 2-2 2 2 2",
    "M9 5h6a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z",
)
private val DownloadIcon = pathIcon("Download", "M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5")
private val UploadIcon = pathIcon("Upload", "M12 3v12", "m17 8-5-5-5 5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
private val ShareIcon = pathIcon(
    "Share",
    "M15 5a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "M3 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "M15 19a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "M8.59 13.51 15.42 17.49",
    "M15.41 6.51 8.59 10.49",
)
private val TrashIcon = pathIcon(
    "Trash",
    "M10 11v6",
    "M14 11v6",
    "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
    "M3 6h18",
    "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
)
private val UpdateIcon = pathIcon("Update", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0", "m16 12-4-4-4 4", "M12 16V8")
private val ExternalIcon = pathIcon("External", "M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")
private val TagIcon = pathIcon(
    "Tag",
    "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
    "M7 7.5a.5 .5 0 1 0 1 0a.5 .5 0 1 0-1 0",
)
private val CommitIcon = pathIcon("Commit", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M3 12h6", "M15 12h6")

private const val RELEASES_URL = "https://github.com/alexanderop/eisen/releases"

private val UNITS = listOf(WeightUnit.KG to "Kilograms", WeightUnit.LB to "Pounds")

/** Bars come in odd weights (a 33lb women's bar, a 7kg technique bar), so a typed one is kept finer than the plates. */
private const val BAR_SNAP = 0.5

/** A rest is typed in seconds, because a number keyboard has no ":". */
private const val REST_TYPED_IN = " sec"
private const val REST_SNAP = 5

/** An hour. Only there so a slip of the thumb (9000 instead of 90) does not set a rest of hours. */
private const val MAX_REST = 3600

/** What the open sheet changes. Kept over a rotation as a short text, see [EditingSaver]. */
private sealed interface Editing {
    data object WeightUnit : Editing
    data object Bar : Editing
    data object RestDone : Editing
    data object RestMissed : Editing

    /** The working weight (or the added weight) of exercise [id]. */
    data class Exercise(val id: String) : Editing
}

private const val EXERCISE_KEY = "exercise:"

/** [Editing] as `unit`, `bar`, `restDone`, `restMissed` or `exercise:<id>`, which saved state can hold. */
private val EditingSaver = Saver<Editing?, String>(
    save = { editing ->
        when (editing) {
            null -> null
            Editing.WeightUnit -> "unit"
            Editing.Bar -> "bar"
            Editing.RestDone -> "restDone"
            Editing.RestMissed -> "restMissed"
            is Editing.Exercise -> EXERCISE_KEY + editing.id
        }
    },
    restore = { key ->
        when {
            key == "unit" -> Editing.WeightUnit
            key == "bar" -> Editing.Bar
            key == "restDone" -> Editing.RestDone
            key == "restMissed" -> Editing.RestMissed
            key.startsWith(EXERCISE_KEY) -> Editing.Exercise(key.removePrefix(EXERCISE_KEY))
            else -> null
        }
    },
)

/** Every exercise that is part of a workout, once. Bodyweight ones are here for their added weight. */
private fun workoutExercises(model: Model): List<Exercise> =
    model.state.routines.flatMap { routine -> routine.slots.map { it.exerciseId } }.distinct()
        .mapNotNull { findExercise(model, it) }

/**
 * The settings list. Every change is a Msg; `update` decides what follows (the sound preview, the unit
 * lock during a workout, stopping the rest on a restore). Which sheet or dialog is open stays here:
 * opening one changes nothing else. The file pickers stay here too, as Android needs an activity for
 * them, but they only hand the picked file on: the runtime reads and writes it.
 */
@Composable
fun SettingsScreen(model: Model, dispatch: (Msg) -> Unit) {
    val settings = model.settings
    val uriHandler = LocalUriHandler.current
    val haptics = rememberHaptics()
    var editing by rememberSaveable(stateSaver = EditingSaver) { mutableStateOf<Editing?>(null) }
    var erasing by rememberSaveable { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) dispatch(Msg.Data.WriteBackup(uri.toString()))
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dispatch(Msg.Data.Restore(uri.toString()))
    }

    fun toggleSound() {
        // Switching it on plays the cue once: `update` asks for that.
        dispatch(Msg.Settings.SetSound(!settings.sound))
        haptics.toggle(!settings.sound)
    }

    fun toggleRestFullScreen() {
        dispatch(Msg.Settings.SetRestFullScreen(!settings.restFullScreen))
        haptics.toggle(!settings.restFullScreen)
    }

    fun toggleTrackRir() {
        dispatch(Msg.Settings.SetTrackRir(!settings.trackRir))
        haptics.toggle(!settings.trackRir)
    }

    fun toggleHaptics() {
        dispatch(Msg.Settings.SetHaptics(!settings.haptics))
        haptics.toggle(!settings.haptics)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Settings",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp).semantics { heading() },
        )

        Section("Weights", first = true) {
            SettingRow(
                icon = ScaleIcon,
                label = "Unit",
                description = if (workoutRunning(model)) "Finish the current workout to change it." else null,
                value = UNITS.first { it.first == settings.unit }.second,
                enabled = !workoutRunning(model),
                onClick = { editing = Editing.WeightUnit },
            )
            SettingRow(icon = WeightIcon, label = "Bar weight", value = fmt(settings.bar, settings.unit), onClick = { editing = Editing.Bar })
        }

        Section("Working weights") {
            val exercises = workoutExercises(model)
            if (exercises.isEmpty()) Note("No exercises in your workouts.")
            for (exercise in exercises) {
                SettingRow(
                    leading = { ExerciseTile(exercise.image, exercise.name, size = 36.dp) },
                    label = exercise.name,
                    value = fmtLoad(exercise.equipment, progressOf(model, exercise.id).weight, settings.unit),
                    onClick = { editing = Editing.Exercise(exercise.id) },
                )
            }
        }

        Section("Rest timer") {
            SettingRow(icon = TimerIcon, label = "After a full set", value = formatClock(settings.restDone), onClick = { editing = Editing.RestDone })
            SettingRow(
                icon = HourglassIcon,
                label = "After missed reps",
                value = formatClock(settings.restMissed),
                onClick = { editing = Editing.RestMissed },
            )
            SwitchRow(icon = HourglassIcon, label = "Full-screen rest timer", checked = settings.restFullScreen, onToggle = ::toggleRestFullScreen)
            SwitchRow(
                icon = GaugeIcon,
                label = "Track reps in reserve",
                description = "Rate a set with one tap after it. $RIR_BOOST_FROM or more left on your last set doubles the next weight jump.",
                checked = settings.trackRir,
                onToggle = ::toggleTrackRir,
            )
            SwitchRow(icon = VolumeIcon, label = "Sound when rest ends", checked = settings.sound, onToggle = ::toggleSound)
            SwitchRow(icon = VibrateIcon, label = "Vibrate on taps", checked = settings.haptics, onToggle = ::toggleHaptics)
        }

        // One setting for all interval timers, the same controls as the "Voice" sheet of a timer's setup.
        Section("Timer voice") {
            TimerCallsControls(settings.timerCalls, dispatch)
        }

        Section("Your data") {
            // The PWA downloads one file. A phone can either keep it in a folder the lifter picks
            // (Downloads, Drive) or hand it straight to another app, so both are offered.
            SettingRow(
                icon = DownloadIcon,
                label = "Save backup",
                description = "Everything is stored on this device only.",
                trailing = Trailing.NONE,
                onClick = { save.launch(backupFileName(model.now)) },
            )
            SettingRow(
                icon = ShareIcon,
                label = "Send backup",
                description = "Email it or keep it in another app.",
                trailing = Trailing.NONE,
                onClick = { dispatch(Msg.Data.SendBackup) },
            )
            SettingRow(
                icon = UploadIcon,
                label = "Restore backup",
                trailing = Trailing.NONE,
                // Some file managers do not call a .json file JSON, so any file can be picked and is checked after.
                onClick = { restore.launch(arrayOf("application/json", "*/*")) },
            )
            HealthConnectRows(model.health, dispatch)
            SettingRow(
                icon = TrashIcon,
                label = "Erase all data",
                tone = EisenColors.Destructive,
                trailing = Trailing.NONE,
                onClick = { erasing = true },
            )
            model.dataNotice?.let { Note(it, live = true) }
        }

        Section("About") {
            // An APK has no service worker to update itself. New versions are published as releases.
            SettingRow(
                icon = UpdateIcon,
                label = "Check for updates",
                description = "Opens GitHub, where new versions of the app are downloaded.",
                trailing = Trailing.EXTERNAL,
                onClick = { runCatching { uriHandler.openUri(RELEASES_URL) } },
            )
            SettingRow(icon = TagIcon, label = "Version", value = BuildConfig.VERSION_NAME, trailing = Trailing.NONE)
            SettingRow(icon = CommitIcon, label = "Build", value = BuildConfig.COMMIT, trailing = Trailing.NONE)
        }

        Band()
        Text(
            "Eisen 5×5",
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = EisenColors.MutedForeground,
        )
    }

    when (val target = editing) {
        null -> Unit
        Editing.WeightUnit -> UnitSheet(
            current = settings.unit,
            onPick = { unit ->
                dispatch(Msg.Settings.SetUnit(unit))
                haptics.perform(Haptic.TOGGLE_ON)
            },
            onClose = { editing = null },
        )
        Editing.Bar -> NumberSheet(
            title = "Bar weight",
            hint = "The empty bar. Plates and warmups are worked out from it.",
            onClose = { editing = null },
        ) {
            Stepper(
                value = settings.bar,
                onValueChange = { bar -> dispatch(Msg.Settings.SetBar(bar)) },
                step = unitDefaults(settings.unit).step,
                min = 0.0,
                label = "bar weight",
                format = { fmt(it, settings.unit) },
                snap = BAR_SNAP,
                large = true,
            )
        }
        Editing.RestDone -> NumberSheet(
            title = "Rest after a full set",
            hint = "The timer starts when you log a set with all reps.",
            onClose = { editing = null },
        ) {
            IntStepper(
                value = settings.restDone,
                onValueChange = { seconds -> dispatch(Msg.Settings.SetRestDone(seconds)) },
                step = 15,
                min = 15,
                max = MAX_REST,
                label = "rest after a full set",
                format = ::formatClock,
                snap = REST_SNAP,
                large = true,
                typedSuffix = REST_TYPED_IN,
            )
        }
        Editing.RestMissed -> NumberSheet(
            title = "Rest after missed reps",
            hint = "A longer rest gives the next set a fair chance.",
            onClose = { editing = null },
        ) {
            IntStepper(
                value = settings.restMissed,
                onValueChange = { seconds -> dispatch(Msg.Settings.SetRestMissed(seconds)) },
                step = 30,
                min = 30,
                max = MAX_REST,
                label = "rest after missed reps",
                format = ::formatClock,
                snap = REST_SNAP,
                large = true,
                typedSuffix = REST_TYPED_IN,
            )
        }
        is Editing.Exercise -> {
            val exercise = findExercise(model, target.id)
            if (exercise == null) {
                // A restored backup can drop the exercise while its sheet is open.
                LaunchedEffect(target) { editing = null }
            } else if (exercise.equipment == Equipment.BODYWEIGHT) {
                AddedWeightSheet(exercise, progressOf(model, exercise.id), settings.unit, dispatch, onClose = { editing = null })
            } else {
                WorkingWeightSheet(exercise, progressOf(model, exercise.id), settings.unit, dispatch, onClose = { editing = null })
            }
        }
    }

    if (erasing) {
        AlertDialog(
            onDismissRequest = { erasing = false },
            title = { Text("Erase all data?") },
            text = {
                Text(
                    "This deletes every workout, your weights and your settings from this device. It cannot be undone. " +
                        "Save a backup first if you might want them back.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // `update` stops the rest too and puts "All data erased." in the model.
                        dispatch(Msg.Data.Reset)
                        erasing = false
                        editing = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = EisenColors.Destructive),
                ) { Text("Erase everything") }
            },
            dismissButton = { TextButton(onClick = { erasing = false }) { Text("Cancel") } },
        )
    }
}

/** The 6dp strip of card colour the PWA puts between sections. */
@Composable
private fun Band() {
    Box(Modifier.fillMaxWidth().height(6.dp).background(EisenColors.Card))
}

@Composable
private fun Section(title: String, first: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if (!first) Band()
    Column(Modifier.padding(top = if (first) 0.dp else 10.dp, bottom = 10.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp).semantics { heading() },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = EisenColors.MutedForeground,
        )
        content()
    }
}

@Composable
private fun Note(text: String, live: Boolean = false) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        fontSize = 13.sp,
        color = EisenColors.MutedForeground,
    )
}

private enum class Trailing { CHEVRON, EXTERNAL, NONE }

/** One line of the settings list: an icon, a label and what the setting is right now. The whole line is the tap target. */
@Composable
private fun SettingRow(
    label: String,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    description: String? = null,
    value: String? = null,
    tone: Color = EisenColors.Foreground,
    trailing: Trailing = Trailing.CHEVRON,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val action = if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    ListItem(
        headlineContent = { Text(label, fontSize = 16.sp) },
        modifier = action.heightIn(min = 56.dp).alpha(if (enabled) 1f else 0.5f),
        supportingContent = description?.let { { Text(it, fontSize = 13.sp) } },
        leadingContent = leading ?: icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(24.dp)) } },
        trailingContent = if (value == null && trailing == Trailing.NONE) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value != null) {
                        Text(value, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = EisenColors.MutedForeground)
                    }
                    when (trailing) {
                        Trailing.CHEVRON -> {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Trailing.EXTERNAL -> Icon(ExternalIcon, contentDescription = "Opens in the browser", modifier = Modifier.size(18.dp))
                        Trailing.NONE -> Unit
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = tone,
            leadingIconColor = tone,
            supportingColor = EisenColors.MutedForeground,
            trailingIconColor = EisenColors.MutedForeground,
        ),
    )
}

/** The row itself is the switch, so there is no small control to aim for. */
@Composable
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit, description: String? = null) {
    ListItem(
        headlineContent = { Text(label, fontSize = 16.sp) },
        supportingContent = description?.let { { Text(it, fontSize = 13.sp, color = EisenColors.MutedForeground) } },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }).heightIn(min = 56.dp),
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, leadingIconColor = EisenColors.Foreground),
    )
}

/** A bottom sheet with a title and a close button. `close` slides it away before [onClose] runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = close) { Icon(Icons.Close, contentDescription = "Close") }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content(close) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth(), fontSize = 13.sp, color = EisenColors.MutedForeground, textAlign = TextAlign.Center)
}

@Composable
private fun DoneButton(onClick: () -> Unit) {
    Button(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun UnitSheet(current: WeightUnit, onPick: (WeightUnit) -> Unit, onClose: () -> Unit) {
    Sheet("Unit", onClose) { close ->
        Column(Modifier.selectableGroup()) {
            UNITS.forEachIndexed { index, (unit, label) ->
                if (index > 0) HorizontalDivider(color = EisenColors.Border)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = unit == current, role = Role.RadioButton, onClick = {
                            onPick(unit)
                            close()
                        })
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    RadioButton(selected = unit == current, onClick = null)
                }
            }
        }
        Hint("Your weights are converted and rounded to what the plates can load.")
    }
}

@Composable
private fun NumberSheet(title: String, hint: String, onClose: () -> Unit, stepper: @Composable () -> Unit) {
    Sheet(title, onClose) { close ->
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) { stepper() }
        Hint(hint)
        DoneButton(close)
    }
}

/** The weight added to a bodyweight exercise. It never goes up by itself, so there is no increment to set. */
@Composable
private fun AddedWeightSheet(exercise: Exercise, progress: ExerciseProgress, unit: WeightUnit, dispatch: (Msg) -> Unit, onClose: () -> Unit) {
    Sheet(exercise.name, onClose) { close ->
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Added weight", fontSize = 13.sp, color = EisenColors.MutedForeground)
            AddedWeightStepper(
                value = progress.weight,
                onValueChange = { dispatch(Msg.Workout(WorkoutEvent.SetWorkingWeight(exercise.id, it))) },
                unit = unit,
                label = "${exercise.name} added weight",
                large = true,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        Hint(ADDED_WEIGHT_HINT)
        DoneButton(close)
    }
}

/** The weight the next workout starts with, and how much is added after a workout with all reps. */
@Composable
private fun WorkingWeightSheet(exercise: Exercise, progress: ExerciseProgress, unit: WeightUnit, dispatch: (Msg) -> Unit, onClose: () -> Unit) {
    val step = stepFor(exercise.equipment, unit)
    val increment = incrementStep(unit)
    Sheet(exercise.name, onClose) { close ->
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            Stepper(
                value = progress.weight,
                onValueChange = { dispatch(Msg.Workout(WorkoutEvent.SetWorkingWeight(exercise.id, it))) },
                step = step,
                min = step,
                label = "${exercise.name} weight",
                format = { fmt(it, unit) },
                snap = typedWeightSnap(exercise.equipment, unit),
                large = true,
            )
        }
        Hint("The weight your next workout starts with. Changing it clears missed attempts.")
        HorizontalDivider(color = EisenColors.Border)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (exercise.timed) "Add after all holds" else "Add after all reps", color = EisenColors.MutedForeground, modifier = Modifier.weight(1f))
            Stepper(
                value = progress.increment,
                onValueChange = { dispatch(Msg.Settings.SetIncrement(exercise.id, it)) },
                step = increment,
                min = increment,
                label = "weight added after a full workout",
                format = { "+${fmt(it, unit)}" },
                snap = typedIncrementSnap(unit),
            )
        }
        DoneButton(close)
    }
}
