package io.github.alexanderop.eisen.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.MutableState
import androidx.compose.ui.draw.clip
import io.github.alexanderop.eisen.app.elm.matchRank
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.core.CUSTOM_GROUP
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.core.GROUPS
import io.github.alexanderop.eisen.core.kindLabel
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.ExerciseTile
import io.github.alexanderop.eisen.core.ui.Icons
import kotlinx.coroutines.launch
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState
import io.github.alexanderop.eisen.core.ui.ButtonHeight
import io.github.alexanderop.eisen.core.ui.ConfirmButton
import io.github.alexanderop.eisen.core.ui.Eyebrow


private const val ALL = "All"

/** The first chip of the builder's picker: what WODs are made of. */
const val WOD_FILTER = "WOD"
private const val NAME_LENGTH = 40

/** The lifter's own exercises come first, then the catalog groups. */
private val ORDER = listOf(CUSTOM_GROUP) + GROUPS

private fun groupTitle(group: String) = if (group == CUSTOM_GROUP) "Your exercises" else group

private fun filterLabel(group: String) = if (group == CUSTOM_GROUP) "Yours" else group

private class Section(val group: String, val items: List<Exercise>)

/**
 * A bottom sheet to pick an exercise from the catalog or to create an own one.
 * [exercises] is everything that can be picked, the lifter's own ones included.
 * [exclude] holds the ids that are already in the list and cannot be picked again.
 * [onCreate] gets a name that is not blank, the kind and whether it is held for time; the caller
 * creates the exercise (or finds the one with that name) and adds it where the picker was opened. [onDelete] removes one of the lifter's own.
 * The sheet closes itself before [onPick] and [onCreate] are called.
 *
 * [multi]: the builder's way of picking: a tap ticks a row, and "Add 3" hands the ticked ones to
 * [onPickMany] in the order they were ticked. [wodIds] adds the "WOD" chip, which shows only those.
 * [query] and [filter] may be kept by the caller, so the search and the chip stay while it is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    exclude: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (name: String, equipment: Equipment, timed: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
    multi: Boolean = false,
    onPickMany: (List<String>) -> Unit = {},
    wodIds: Set<String>? = null,
    query: MutableState<String>? = null,
    filter: MutableState<String>? = null,
) {
    // Full height right away: the list is long and the search field needs room above the keyboard.
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    val ownQuery = rememberSaveable { mutableStateOf("") }
    val searched = query ?: ownQuery
    val ownFilter = rememberSaveable { mutableStateOf(if (wodIds != null) WOD_FILTER else ALL) }
    val chip = filter ?: ownFilter
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var name by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf(Equipment.BARBELL) }
    var timed by rememberSaveable { mutableStateOf(false) }

    // Slide the sheet away first, then tell the caller, so it does not vanish mid-frame.
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EisenColors.Card,
        contentColor = EisenColors.Foreground,
    ) {
        Column(Modifier.fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        creating -> "New exercise"
                        multi -> "Add movements"
                        else -> "Add exercise"
                    },
                    modifier = Modifier.weight(1f).semantics { heading() },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                FilledTonalIconButton(
                    onClick = { close(onDismiss) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.MutedForeground),
                ) {
                    Icon(Icons.Close, contentDescription = "Close")
                }
            }
            if (creating) {
                CreateForm(
                    name = name,
                    onName = { name = it.take(NAME_LENGTH) },
                    equipment = equipment,
                    onEquipment = { equipment = it },
                    timed = timed,
                    onTimed = { timed = it },
                    onCreate = {
                        val typed = name
                        val kind = equipment
                        val held = timed
                        if (typed.isNotBlank()) close { onCreate(typed, kind, held) }
                    },
                    onBack = { creating = false },
                )
            } else {
                PickList(
                    exercises = exercises,
                    exclude = exclude,
                    query = searched.value,
                    onQuery = { searched.value = it },
                    filter = chip.value,
                    onFilter = { chip.value = it },
                    wodIds = wodIds,
                    picked = if (multi) picked else null,
                    onPick = { id ->
                        if (multi) picked = if (id in picked) picked - id else picked + id
                        else close { onPick(id) }
                    },
                    onDelete = onDelete,
                    onCreate = {
                        name = searched.value.trim()
                        creating = true
                    },
                )
                if (multi) {
                    val chosen = picked
                    Button(
                        onClick = { close { onPickMany(chosen) } },
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(if (chosen.isEmpty()) "Tap movements to add" else "Add ${chosen.size}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PickList(
    exercises: List<Exercise>,
    exclude: Set<String>,
    query: String,
    onQuery: (String) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    wodIds: Set<String>?,
    picked: List<String>?,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val hasCustom = exercises.any { it.group == CUSTOM_GROUP }
    val filters = listOfNotNull(WOD_FILTER.takeIf { wodIds != null }) + (listOf(ALL) + ORDER).filter { it != CUSTOM_GROUP || hasCustom }
    // Deleting the last own exercise removes the "Yours" chip. Fall back to all instead of an empty list.
    val active = if (filter in filters) filter else ALL

    // The lifter's own exercises stay listed even when already added, so they can be deleted here.
    val available = exercises.filter { exercise ->
        (exercise.group == CUSTOM_GROUP || exercise.id !in exclude) &&
            when (active) {
                ALL -> true
                WOD_FILTER -> exercise.id in wodIds.orEmpty()
                else -> exercise.group == active
            } &&
            matchRank(exercise.name, query) != null
    }
    // While searching, hits that start a word come first, whatever their group: "run" finds Run before Cable Crunch.
    val searching = query.isNotBlank()
    val sections = if (searching) {
        listOf(Section("", available.sortedBy { matchRank(it.name, query) })).filter { it.items.isNotEmpty() }
    } else {
        ORDER.map { group -> Section(group, available.filter { it.group == group }) }.filter { it.items.isNotEmpty() }
    }
    // Lives inside the sheet, so it reaches the keyboard of the sheet's own window.
    val focus = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Search exercises") },
        leadingIcon = { Icon(Icons.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQuery("") }) { Icon(Icons.Close, contentDescription = "Clear search") } }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        // A search is not a sentence: no capitals, and no autocorrect turning a typed "ohp" into "oh".
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
        // The list already filters while typing, so the search key just puts the keyboard away.
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it }) { group ->
            FilterChip(
                selected = active == group,
                onClick = { onFilter(group) },
                label = { Text(filterLabel(group), fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.height(40.dp),
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = EisenColors.Muted,
                    labelColor = EisenColors.MutedForeground,
                    selectedContainerColor = EisenColors.Primary,
                    selectedLabelColor = EisenColors.OnPrimary,
                ),
                border = null,
            )
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item(key = "create") {
            val typed = query.trim()
            FilledTonalButton(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().height(ButtonHeight),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
            ) {
                Text(
                    if (typed.isNotEmpty() && sections.isEmpty()) "Create “$typed”" else "Create your own exercise",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
        for (section in sections) {
            item(key = "head-${section.group}") {
                Eyebrow(
                    if (searching) "Matches" else groupTitle(section.group),
                    Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            items(section.items, key = { it.id }) { exercise ->
                PickRow(
                    exercise,
                    added = exercise.id in exclude,
                    ticked = picked?.let { exercise.id in it },
                    onPick = {
                        // The keyboard would otherwise stay up while the sheet slides away.
                        focus.clearFocus()
                        onPick(exercise.id)
                    },
                    onDelete = { onDelete(exercise.id) },
                )
            }
        }
        if (sections.isEmpty()) {
            item(key = "none") {
                Text(
                    "No exercise matches.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = EisenColors.MutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PickRow(exercise: Exercise, added: Boolean, ticked: Boolean?, onPick: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .then(
                        if (ticked != null) Modifier.toggleable(value = ticked, role = Role.Checkbox, onValueChange = { onPick() })
                        else Modifier.clickable(enabled = !added, role = Role.Button, onClickLabel = "Add ${exercise.name}", onClick = onPick),
                    )
                    .alpha(if (added) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExerciseTile(exercise.image, exercise.name)
                Text(exercise.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    when {
                        added -> "Added"
                        exercise.timed -> "${kindLabel(exercise.id, exercise.equipment)} · Timed"
                        else -> kindLabel(exercise.id, exercise.equipment)
                    },
                    color = EisenColors.MutedForeground,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
                if (ticked != null) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (ticked) EisenColors.Primary else EisenColors.Muted)
                            .border(1.5.dp, if (ticked) EisenColors.Primary else EisenColors.Border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ticked) Icon(Icons.Check, contentDescription = null, tint = EisenColors.OnPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (exercise.group == CUSTOM_GROUP) {
                ConfirmButton(
                    label = "Delete",
                    confirmLabel = "Confirm",
                    onConfirm = onDelete,
                    ghost = true,
                    height = 48.dp,
                )
            }
        }
        HorizontalDivider(color = EisenColors.Border)
    }
}

@Composable
private fun CreateForm(
    name: String,
    onName: (String) -> Unit,
    equipment: Equipment,
    onEquipment: (Equipment) -> Unit,
    timed: Boolean,
    onTimed: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    val canCreate = name.trim().isNotEmpty()
    val focus = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    // The form is opened to type a name: the keyboard comes up, the cursor after a name taken from the search.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
    // A plain scrolling column: the form is short, and it must stay reachable above the keyboard.
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = field,
            onValueChange = { next ->
                val text = next.text.take(NAME_LENGTH)
                field = if (text == next.text) next else TextFieldValue(text, TextRange(text.length))
                onName(text)
            },
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            label = { Text("Name") },
            placeholder = { Text("Cable Lateral Raise") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus()
                if (canCreate) onCreate()
            }),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Type")
            // Two by two like the PWA: "Machine / dumbbell" is too long for four in a row.
            for (pair in Equipment.entries.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in pair) {
                        FilterChip(
                            selected = equipment == option,
                            onClick = { onEquipment(option) },
                            label = {
                                Text(option.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = EisenColors.Muted,
                                labelColor = EisenColors.MutedForeground,
                                selectedContainerColor = EisenColors.Primary,
                                selectedLabelColor = EisenColors.OnPrimary,
                            ),
                            border = null,
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = timed, role = Role.Switch, onValueChange = onTimed),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Timed", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Held for time, like a plank", color = EisenColors.MutedForeground, fontSize = 13.sp)
            }
            // The row is the toggle, so the switch itself takes no clicks of its own.
            Switch(checked = timed, onCheckedChange = null)
        }
        Spacer(Modifier.size(0.dp))
        Button(
            onClick = {
                focus.clearFocus()
                onCreate()
            },
            enabled = canCreate,
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Create and add", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        ) {
            Text("Back to the list", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
