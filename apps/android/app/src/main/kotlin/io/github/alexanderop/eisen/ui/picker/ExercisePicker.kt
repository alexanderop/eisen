package io.github.alexanderop.eisen.ui.picker

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.CUSTOM_GROUP
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.GROUPS
import io.github.alexanderop.eisen.domain.kindLabel
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.ExerciseTile
import io.github.alexanderop.eisen.ui.Icons
import io.github.alexanderop.eisen.ui.pathIcon
import kotlinx.coroutines.launch

private val SearchIcon = pathIcon("Search", "m21 21-4.34-4.34", "M11 3a8 8 0 1 0 0 16 8 8 0 1 0 0-16z")

private const val ALL = "All"
private const val NAME_LENGTH = 40

/** The lifter's own exercises come first, then the catalog groups. */
private val ORDER = listOf(CUSTOM_GROUP) + GROUPS

private fun groupTitle(group: String) = if (group == CUSTOM_GROUP) "Your exercises" else group

private fun filterLabel(group: String) = if (group == CUSTOM_GROUP) "Yours" else group

private class Section(val group: String, val items: List<Exercise>)

/**
 * A bottom sheet to pick an exercise from the catalog or to create an own one.
 * [exclude] holds the ids that are already in the list and cannot be picked again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(exclude: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    // Full height right away: the list is long and the search field needs room above the keyboard.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf(Equipment.BARBELL) }

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
                    if (creating) "New exercise" else "Add exercise",
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
                    onCreate = {
                        Store.addCustomExercise(name, equipment)?.let { exercise -> close { onPick(exercise.id) } }
                    },
                    onBack = { creating = false },
                )
            } else {
                PickList(
                    exclude = exclude,
                    query = query,
                    onQuery = { query = it },
                    onPick = { id -> close { onPick(id) } },
                    onCreate = {
                        name = query.trim()
                        creating = true
                    },
                )
            }
        }
    }
}

@Composable
private fun PickList(exclude: Set<String>, query: String, onQuery: (String) -> Unit, onPick: (String) -> Unit, onCreate: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(ALL) }
    val exercises = Store.exercises
    val hasCustom = exercises.any { it.group == CUSTOM_GROUP }
    val filters = (listOf(ALL) + ORDER).filter { it != CUSTOM_GROUP || hasCustom }
    // Deleting the last own exercise removes the "Yours" chip. Fall back to all instead of an empty list.
    val active = if (filter in filters) filter else ALL

    val needle = query.trim().lowercase()
    // The lifter's own exercises stay listed even when already added, so they can be deleted here.
    val available = exercises.filter { exercise ->
        (exercise.group == CUSTOM_GROUP || exercise.id !in exclude) &&
            (active == ALL || exercise.group == active) &&
            exercise.name.lowercase().contains(needle)
    }
    val sections = ORDER.map { group -> Section(group, available.filter { it.group == group }) }.filter { it.items.isNotEmpty() }
    // Lives inside the sheet, so it reaches the keyboard of the sheet's own window.
    val focus = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Search exercises") },
        leadingIcon = { Icon(SearchIcon, contentDescription = null) },
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
                onClick = { filter = group },
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
        modifier = Modifier.fillMaxWidth(),
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
                    groupTitle(section.group),
                    Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            items(section.items, key = { it.id }) { exercise ->
                PickRow(
                    exercise,
                    added = exercise.id in exclude,
                    onPick = {
                        // The keyboard would otherwise stay up while the sheet slides away.
                        focus.clearFocus()
                        onPick(exercise.id)
                    },
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
private fun PickRow(exercise: Exercise, added: Boolean, onPick: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .clickable(enabled = !added, role = Role.Button, onClickLabel = "Add ${exercise.name}", onClick = onPick)
                    .alpha(if (added) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExerciseTile(exercise.image, exercise.name)
                Text(exercise.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    if (added) "Added" else kindLabel(exercise.id, exercise.equipment),
                    color = EisenColors.MutedForeground,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            if (exercise.group == CUSTOM_GROUP) {
                ConfirmButton(
                    label = "Delete",
                    confirmLabel = "Confirm",
                    onConfirm = { Store.deleteCustomExercise(exercise.id) },
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
