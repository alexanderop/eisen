package io.github.alexanderop.eisen.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.content.FileProvider
import io.github.alexanderop.eisen.BuildConfig
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.incrementStep
import io.github.alexanderop.eisen.domain.stepFor
import io.github.alexanderop.eisen.domain.typedIncrementSnap
import io.github.alexanderop.eisen.domain.typedWeightSnap
import io.github.alexanderop.eisen.domain.unitDefaults
import io.github.alexanderop.eisen.health.HealthConnectRows
import io.github.alexanderop.eisen.rest.Cues
import io.github.alexanderop.eisen.rest.Rest
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.ExerciseTile
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.IntStepper
import io.github.alexanderop.eisen.ui.Stepper
import io.github.alexanderop.eisen.ui.fmt
import io.github.alexanderop.eisen.ui.formatClock
import io.github.alexanderop.eisen.ui.pathIcon
import io.github.alexanderop.eisen.ui.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

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
private const val NOT_A_BACKUP = "That file is not an Eisen backup. Nothing was changed."

private val UNITS = listOf(WeightUnit.KG to "Kilograms", WeightUnit.LB to "Pounds")

/** Bars come in odd weights (a 33lb women's bar, a 7kg technique bar), so a typed one is kept finer than the plates. */
private const val BAR_SNAP = 0.5

/** A rest is typed in seconds, because a number keyboard has no ":". */
private const val REST_TYPED_IN = " sec"
private const val REST_SNAP = 5

/** An hour. Only there so a slip of the thumb (9000 instead of 90) does not set a rest of hours. */
private const val MAX_REST = 3600

/** What the open sheet changes. A working weight is `exercise:<id>`, so it fits in saved state. */
private object Editing {
    const val UNIT = "unit"
    const val BAR = "bar"
    const val REST_DONE = "restDone"
    const val REST_MISSED = "restMissed"
    const val EXERCISE = "exercise:"
}

/** The same name the PWA gives its download, so backups from both apps sort together. */
private fun backupName(): String = "eisen-backup-${LocalDate.now(ZoneOffset.UTC)}.json"

/** Every weighted exercise that is part of a workout, once. */
private fun weightedExercises(): List<Exercise> =
    Store.state.routines.flatMap { routine -> routine.slots.map { it.exerciseId } }.distinct()
        .mapNotNull { Store.findExercise(it) }
        .filter { it.equipment != Equipment.BODYWEIGHT }

/** Writes the backup into the folder the FileProvider shares and opens the share sheet with it. */
private suspend fun shareBackup(context: Context) {
    val name = backupName()
    val data = Store.exportData()
    val file = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "backups")
        // Only today's file is kept, so old backups do not pile up in the cache.
        folder.mkdirs()
        folder.listFiles()?.forEach { it.delete() }
        File(folder, name).apply { writeText(data) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, name)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private suspend fun saveBackup(context: Context, uri: Uri): Boolean {
    val data = Store.exportData()
    return withContext(Dispatchers.IO) {
        runCatching {
            // "wt" empties the file first, so a shorter backup over an old one leaves no tail behind.
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data.toByteArray()) } != null
        }.getOrDefault(false)
    }
}

private suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
}

@Composable
fun SettingsScreen() {
    val state = Store.state
    val settings = state.settings
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var erasing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { message = if (saveBackup(context, uri)) "Backup saved." else "Could not save the backup." }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val restored = readText(context, uri)?.let { Store.importData(it) } == true
            if (restored) Rest.stop()
            message = if (restored) "Backup restored." else NOT_A_BACKUP
        }
    }

    fun toggleSound() {
        Store.updateSettings { it.copy(sound = !it.sound) }
        haptics.perform(Haptic.TAP)
        if (Store.state.settings.sound) Cues.preview(context)
    }

    fun toggleHaptics() {
        Store.updateSettings { it.copy(haptics = !it.haptics) }
        haptics.perform(Haptic.TAP)
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
                description = if (state.active != null) "Finish the current workout to change it." else null,
                value = UNITS.first { it.first == settings.unit }.second,
                enabled = state.active == null,
                onClick = { editing = Editing.UNIT },
            )
            SettingRow(icon = WeightIcon, label = "Bar weight", value = fmt(settings.bar), onClick = { editing = Editing.BAR })
        }

        Section("Working weights") {
            val weighted = weightedExercises()
            if (weighted.isEmpty()) Note("No weighted exercises in your workouts.")
            for (exercise in weighted) {
                SettingRow(
                    leading = { ExerciseTile(exercise.image, exercise.name, size = 36.dp) },
                    label = exercise.name,
                    value = fmt(Store.progressOf(exercise.id).weight),
                    onClick = { editing = Editing.EXERCISE + exercise.id },
                )
            }
        }

        Section("Rest timer") {
            SettingRow(icon = TimerIcon, label = "After a full set", value = formatClock(settings.restDone), onClick = { editing = Editing.REST_DONE })
            SettingRow(
                icon = HourglassIcon,
                label = "After missed reps",
                value = formatClock(settings.restMissed),
                onClick = { editing = Editing.REST_MISSED },
            )
            SwitchRow(icon = VolumeIcon, label = "Sound when rest ends", checked = settings.sound, onToggle = ::toggleSound)
            SwitchRow(icon = VibrateIcon, label = "Vibrate on taps", checked = settings.haptics, onToggle = ::toggleHaptics)
        }

        Section("Your data") {
            // The PWA downloads one file. A phone can either keep it in a folder the lifter picks
            // (Downloads, Drive) or hand it straight to another app, so both are offered.
            SettingRow(
                icon = DownloadIcon,
                label = "Save backup",
                description = "Everything is stored on this device only.",
                trailing = Trailing.NONE,
                onClick = { save.launch(backupName()) },
            )
            SettingRow(
                icon = ShareIcon,
                label = "Send backup",
                description = "Email it or keep it in another app.",
                trailing = Trailing.NONE,
                onClick = { scope.launch { runCatching { shareBackup(context) }.onFailure { message = "Could not create the backup." } } },
            )
            SettingRow(
                icon = UploadIcon,
                label = "Restore backup",
                trailing = Trailing.NONE,
                // Some file managers do not call a .json file JSON, so any file can be picked and is checked after.
                onClick = { restore.launch(arrayOf("application/json", "*/*")) },
            )
            HealthConnectRows()
            SettingRow(
                icon = TrashIcon,
                label = "Erase all data",
                tone = EisenColors.Destructive,
                trailing = Trailing.NONE,
                onClick = { erasing = true },
            )
            if (message.isNotEmpty()) Note(message, live = true)
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

    val target = editing
    when {
        target == Editing.UNIT -> UnitSheet(
            current = settings.unit,
            onPick = { unit ->
                Store.setUnit(unit)
                haptics.perform(Haptic.TAP)
            },
            onClose = { editing = null },
        )
        target == Editing.BAR -> NumberSheet(
            title = "Bar weight",
            hint = "The empty bar. Plates and warmups are worked out from it.",
            onClose = { editing = null },
        ) {
            Stepper(
                value = settings.bar,
                onValueChange = { bar -> Store.updateSettings { it.copy(bar = bar) } },
                step = unitDefaults(settings.unit).step,
                min = 0.0,
                label = "bar weight",
                format = { fmt(it) },
                snap = BAR_SNAP,
                large = true,
            )
        }
        target == Editing.REST_DONE -> NumberSheet(
            title = "Rest after a full set",
            hint = "The timer starts when you log a set with all reps.",
            onClose = { editing = null },
        ) {
            IntStepper(
                value = settings.restDone,
                onValueChange = { seconds -> Store.updateSettings { it.copy(restDone = seconds) } },
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
        target == Editing.REST_MISSED -> NumberSheet(
            title = "Rest after missed reps",
            hint = "A longer rest gives the next set a fair chance.",
            onClose = { editing = null },
        ) {
            IntStepper(
                value = settings.restMissed,
                onValueChange = { seconds -> Store.updateSettings { it.copy(restMissed = seconds) } },
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
        target != null && target.startsWith(Editing.EXERCISE) -> {
            val exercise = Store.findExercise(target.removePrefix(Editing.EXERCISE))
            if (exercise == null) {
                // A restored backup can drop the exercise while its sheet is open.
                LaunchedEffect(target) { editing = null }
            } else {
                WorkingWeightSheet(exercise, settings.unit, onClose = { editing = null })
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
                        Rest.stop()
                        Store.resetAll()
                        erasing = false
                        editing = null
                        message = "All data erased."
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
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, fontSize = 16.sp) },
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
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

/** The weight the next workout starts with, and how much is added after a workout with all reps. */
@Composable
private fun WorkingWeightSheet(exercise: Exercise, unit: WeightUnit, onClose: () -> Unit) {
    val progress = Store.progressOf(exercise.id)
    val step = stepFor(exercise.equipment, unit)
    val increment = incrementStep(unit)
    Sheet(exercise.name, onClose) { close ->
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            Stepper(
                value = progress.weight,
                onValueChange = { Store.setProgressWeight(exercise.id, it) },
                step = step,
                min = step,
                label = "${exercise.name} weight",
                format = { fmt(it) },
                snap = typedWeightSnap(exercise.equipment, unit),
                large = true,
            )
        }
        Hint("The weight your next workout starts with. Changing it clears missed attempts.")
        HorizontalDivider(color = EisenColors.Border)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add after all reps", color = EisenColors.MutedForeground, modifier = Modifier.weight(1f))
            Stepper(
                value = progress.increment,
                onValueChange = { Store.setIncrement(exercise.id, it) },
                step = increment,
                min = increment,
                label = "weight added after a full workout",
                format = { "+${fmt(it)}" },
                snap = typedIncrementSnap(unit),
            )
        }
        DoneButton(close)
    }
}
