package io.github.alexanderop.eisen.feature.food.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.AmountFor
import io.github.alexanderop.eisen.app.elm.Failure
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.Lookup
import io.github.alexanderop.eisen.app.elm.MIN_ONLINE_QUERY
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Online
import io.github.alexanderop.eisen.app.elm.againItems
import io.github.alexanderop.eisen.app.elm.IconPicker
import io.github.alexanderop.eisen.app.elm.canPickIcon
import io.github.alexanderop.eisen.feature.food.FoodIcon
import io.github.alexanderop.eisen.feature.food.guessIcon
import io.github.alexanderop.eisen.feature.food.iconOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import io.github.alexanderop.eisen.app.elm.foodEntry
import io.github.alexanderop.eisen.app.elm.foodResults
import io.github.alexanderop.eisen.app.elm.onlineResults
import io.github.alexanderop.eisen.core.ui.ConfirmButton
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.IntStepper
import io.github.alexanderop.eisen.core.ui.Stepper
import io.github.alexanderop.eisen.core.ui.pressShapes
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.MAX_AMOUNT
import io.github.alexanderop.eisen.feature.food.MAX_FOOD_NAME
import io.github.alexanderop.eisen.feature.food.MAX_FOOD_QUERY
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_KCAL
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_PROTEIN
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_CARBS
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_FAT
import io.github.alexanderop.eisen.feature.food.carbsAndFatFor
import io.github.alexanderop.eisen.feature.food.MAX_KCAL_PER_100
import io.github.alexanderop.eisen.feature.food.MAX_MACRO_PER_100
import io.github.alexanderop.eisen.feature.food.MAX_QUICK_KCAL
import io.github.alexanderop.eisen.feature.food.MAX_QUICK_PROTEIN
import io.github.alexanderop.eisen.feature.food.MIN_AMOUNT
import io.github.alexanderop.eisen.feature.food.SearchHit
import io.github.alexanderop.eisen.feature.food.amountChips
import io.github.alexanderop.eisen.feature.food.amountText
import io.github.alexanderop.eisen.feature.food.barcodeProblem
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.gramsText
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.numberText
import io.github.alexanderop.eisen.feature.food.entryMinute
import io.github.alexanderop.eisen.feature.food.minuteOfDay
import kotlinx.coroutines.launch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import io.github.alexanderop.eisen.app.elm.LOGGED_DAY_NOTE
import io.github.alexanderop.eisen.app.elm.goalWeightView
import io.github.alexanderop.eisen.app.elm.noteText
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.core.ui.ChoiceGroup
import io.github.alexanderop.eisen.feature.food.Estimate
import io.github.alexanderop.eisen.feature.food.GoalDirection
import io.github.alexanderop.eisen.feature.food.PROTEIN_PER_KG
import io.github.alexanderop.eisen.feature.food.WeightGoal
import io.github.alexanderop.eisen.feature.food.bodyFrom
import io.github.alexanderop.eisen.feature.food.bodyIn
import io.github.alexanderop.eisen.feature.food.round1
import io.github.alexanderop.eisen.app.elm.LabelStep
import io.github.alexanderop.eisen.app.elm.PhotoFailure
import io.github.alexanderop.eisen.app.elm.PhotoSource
import io.github.alexanderop.eisen.feature.food.FoodUnit
import io.github.alexanderop.eisen.feature.food.LabelMark
import io.github.alexanderop.eisen.feature.food.LabelValue

/** The one sheet over the Food screen, as the model says. */
@Composable
internal fun FoodSheets(model: Model, dispatch: (Msg) -> Unit) {
    val close = { dispatch(Msg.Food.CloseSheet) }
    when (val sheet = model.food.sheet) {
        null -> Unit
        is FoodSheet.Amount -> AmountSheet(sheet, canPickIcon(model, sheet), minuteOfDay(model.now, model.zone), dispatch, close)
        is FoodSheet.Quick -> QuickSheet(sheet.target, changing(model, sheet.target), minuteOfDay(model.now, model.zone), model.zone, dispatch, close)
        is FoodSheet.Own -> OwnSheet(sheet, sheet.id?.let { id -> model.state.foods.firstOrNull { it.id == id } }, dispatch, close)
        FoodSheet.Goal -> GoalSheet(model, dispatch, close)
        is FoodSheet.Barcode -> BarcodeSheet(sheet.note, dispatch, close)
        is FoodSheet.LookingUp -> LookupSheet(sheet.lookup, dispatch, close)
        FoodSheet.Credit -> CreditSheet(close)
        is FoodSheet.Meal -> MealSheets(model, sheet.sheet, dispatch, close)
        is FoodSheet.Photo -> PhotoSheet(sheet.step, dispatch, close)
    }
}

/** The entry or the plate item a sheet for [target] changes; null for a new one. */
private fun changing(model: Model, target: AmountFor): io.github.alexanderop.eisen.feature.food.FoodEntry? = when (target) {
    AmountFor.Log, AmountFor.Plate -> null
    is AmountFor.Entry -> foodEntry(model, target.id)
    is AmountFor.OnPlate -> model.food.plate.firstOrNull { it.entry.id == target.id }?.entry
}

/** What the button of a sheet for [target] says it does with [kcal]. */
internal fun confirmText(target: AmountFor, kcal: Int): String = when (target) {
    AmountFor.Log -> "Add ${kcalText(kcal)} kcal"
    AmountFor.Plate -> "Add ${kcalText(kcal)} kcal to the plate"
    is AmountFor.Entry, is AmountFor.OnPlate -> "Save ${kcalText(kcal)} kcal"
}

/** A bottom sheet with a title and a close button, like the workout's. [full]: opens all the way, for the ones that type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = EisenColors.Card) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            FilledTonalIconButton(
                onClick = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.MutedForeground),
            ) {
                Icon(Icons.Close, contentDescription = "Close")
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shapes = pressShapes(14.dp)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shapes = pressShapes(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** A label above its control, so both keep their room at a large font. */
@Composable
internal fun Field(label: String, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = EisenColors.MutedForeground)
        control()
    }
}

/** "60 kcal · 11 g protein" of a food per 100. */
internal fun per100Text(food: Food): String = "${numberText(food.kcal)} kcal · ${numberText(food.protein)} g protein"

/**
 * The add sheet: the food's picture (a tap changes it, when [canPick]), what the food has per 100, the amount by chips
 * (½, 1, 2 of the serving or the pack) or typed, and a button that says what it adds.
 */
@Composable
private fun AmountSheet(sheet: FoodSheet.Amount, canPick: Boolean, now: Int, dispatch: (Msg) -> Unit, close: () -> Unit) {
    val food = sheet.food
    val adds = entryOf(food, sheet.amount, "", 0, "")
    val icon = iconOf(food)
    FoodSheet(food.name, close) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PictureButton(icon, enabled = canPick) { dispatch(Msg.Food.OpenIcons) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (food.brand.isNotBlank()) Text(food.brand, color = EisenColors.MutedForeground)
                Text(per100Text(food), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("per 100 ${food.unit.label}", color = EisenColors.MutedForeground)
            }
        }
        val chips = amountChips(food)
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (chip in chips) {
                    FilterChip(
                        selected = chip.amount == sheet.amount,
                        onClick = { dispatch(Msg.Food.SetAmount(chip.amount)) },
                        label = { Text(chip.label, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp).semantics { if (chip.label.length <= 2) contentDescription = "${chip.label} = ${amountText(chip.amount, food.unit)}" },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = EisenColors.Muted,
                            labelColor = EisenColors.Foreground,
                            selectedContainerColor = EisenColors.Primary,
                            selectedLabelColor = EisenColors.OnPrimary,
                        ),
                        border = null,
                    )
                }
            }
        }
        Field(if (food.unit.label == "ml") "Or millilitres" else "Or grams") {
            Stepper(
                value = sheet.amount,
                onValueChange = { dispatch(Msg.Food.SetAmount(it)) },
                step = 10.0,
                snap = 1.0,
                min = MIN_AMOUNT,
                max = MAX_AMOUNT,
                label = "amount",
                format = { amountText(it, food.unit) },
            )
        }
        Text("${gramsText(adds.protein)} protein", color = EisenColors.MutedForeground)
        val target = sheet.target
        if (target is AmountFor.Entry) TimeField(sheet.minute, now) { dispatch(Msg.Food.SetTime(it)) }
        PrimaryButton(confirmText(target, adds.kcal)) { dispatch(Msg.Food.ConfirmAmount) }
        if (target is AmountFor.OnPlate) SecondaryButton("Take off the plate") { dispatch(Msg.Food.RemovePlateItem(target.id)) }
        if (food.source == FoodSource.OWN && (target == AmountFor.Log || target == AmountFor.Plate)) {
            TextButton(onClick = { dispatch(Msg.Food.EditOwn(food.id)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Edit food", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (sheet.picker == IconPicker.Open) IconPickerSheet(icon, onPick = { dispatch(Msg.Food.PickIcon(it)) }, onClose = { dispatch(Msg.Food.CloseIcons) })
}

/**
 * A food's picture on top of its sheet, 64 dp. A tap opens the grid of pictures; TalkBack says "Picture: Milk" and that a
 * tap changes it. [enabled] false: the picture only shows (an entry whose food is gone).
 */
@Composable
private fun PictureButton(icon: FoodIcon, enabled: Boolean, onOpen: () -> Unit) {
    Box(
        Modifier
            .size(HeaderIconSize + 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(EisenColors.Muted)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = "Change the picture") { onOpen() } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "Picture: ${icon.label}" },
        contentAlignment = Alignment.Center,
    ) {
        FoodIconImage(icon, size = HeaderIconSize)
    }
}

/**
 * Quick kcal for a meal out: calories, protein if known, a name if wanted. Opens with the keyboard on the calories.
 * [entry]: the quick entry or plate item it changes, null for a new one, which [target] logs or puts on the plate.
 */
@Composable
private fun QuickSheet(target: AmountFor, entry: io.github.alexanderop.eisen.feature.food.FoodEntry?, now: Int, zone: java.time.ZoneId, dispatch: (Msg) -> Unit, close: () -> Unit) {
    var kcal by rememberSaveable { mutableIntStateOf(entry?.kcal ?: 0) }
    // -1: no time of its own (yet).
    var minute by rememberSaveable { mutableIntStateOf(entry?.let { entryMinute(it, zone) } ?: -1) }
    var protein by rememberSaveable { mutableIntStateOf(entry?.protein?.toInt() ?: 0) }
    var name by rememberSaveable { mutableStateOf(entry?.name?.takeIf { it != io.github.alexanderop.eisen.feature.food.QUICK_NAME }.orEmpty()) }
    FoodSheet(if (entry == null) "Quick kcal" else "Change entry", close) {
        Field("Calories") {
            IntStepper(value = kcal, onValueChange = { kcal = it }, label = "calories", step = 50, snap = 1, min = 0, max = MAX_QUICK_KCAL, format = { "${kcalText(it)} kcal" }, autoFocus = entry == null)
        }
        Field("Protein, if you know it") {
            IntStepper(value = protein, onValueChange = { protein = it }, label = "protein", step = 5, snap = 1, min = 0, max = MAX_QUICK_PROTEIN, format = { "$it g" })
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_FOOD_NAME) },
            label = { Text("Name (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        if (target is AmountFor.Entry) TimeField(minute.takeIf { it >= 0 }, now) { minute = it }
        PrimaryButton(confirmText(target, kcal), enabled = kcal > 0) {
            dispatch(Msg.Food.SaveQuick(kcal, protein.takeIf { it > 0 }, name, minute.takeIf { it >= 0 && target is AmountFor.Entry }))
        }
        if (target is AmountFor.OnPlate) SecondaryButton("Take off the plate") { dispatch(Msg.Food.RemovePlateItem(target.id)) }
    }
}

/**
 * When an entry was eaten: its hour and minute, each typed or stepped. An entry without a time of its own (filled in later
 * for an earlier day) offers "Set a time", which starts at the time it is now.
 */
@Composable
private fun TimeField(minute: Int?, now: Int, onChange: (Int) -> Unit) {
    Field("Time") {
        if (minute == null) {
            SecondaryButton("Set a time") { onChange(now - now % 5) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntStepper(value = minute / 60, onValueChange = { onChange(it.coerceIn(0, 23) * 60 + minute % 60) }, label = "hour", step = 1, min = 0, max = 23, format = { "%02d".format(it) })
                IntStepper(value = minute % 60, onValueChange = { onChange(minute / 60 * 60 + it.coerceIn(0, 59)) }, label = "minute", step = 5, snap = 1, min = 0, max = 59, format = { "%02d".format(it) })
            }
        }
    }
}

/**
 * An own food: name, calories and protein per 100 g, carbs and fat if wanted, a serving if there is one. Also where a
 * barcode lands that Open Food Facts does not know, or knows without calories, with the barcode and the name filled in;
 * and where a photo of the label lands, its values filled in, each marked "read" or "check", the name to type. A new
 * food's values can be read from a photo too.
 */
@Composable
private fun OwnSheet(sheet: FoodSheet.Own, editing: Food?, dispatch: (Msg) -> Unit, close: () -> Unit) {
    val read = sheet.read
    var name by rememberSaveable(read) { mutableStateOf(editing?.name ?: sheet.name) }
    var kcal by rememberSaveable(read) { mutableDoubleStateOf(editing?.kcal ?: read?.kcal?.value ?: 0.0) }
    var protein by rememberSaveable(read) { mutableDoubleStateOf(editing?.protein ?: read?.protein?.value ?: 0.0) }
    var carbs by rememberSaveable(read) { mutableDoubleStateOf(editing?.carbs ?: read?.carbs?.value ?: 0.0) }
    var fat by rememberSaveable(read) { mutableDoubleStateOf(editing?.fat ?: read?.fat?.value ?: 0.0) }
    var serving by rememberSaveable(read) { mutableDoubleStateOf(editing?.serving ?: read?.serving ?: 0.0) }
    val unit = editing?.unit ?: read?.unit ?: FoodUnit.G
    // Until a picture is chosen, the one the name typed suggests.
    val icon = sheet.icon ?: guessIcon(name) ?: FoodIcon.PLATE
    val focus = remember { FocusRequester() }
    // Filled from a photo: the name is what is left to type.
    LaunchedEffect(read) { if (read != null) focus.requestFocus() }
    FoodSheet(if (editing == null) "New food" else "Edit food", close) {
        sheet.note?.let { Text(it, color = EisenColors.Warn) }
        (editing?.barcode ?: sheet.barcode)?.let { Text("Barcode $it", color = EisenColors.MutedForeground) }
        if (read != null) Text(readNote(read.values), color = EisenColors.MutedForeground)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PictureButton(icon, enabled = true) { dispatch(Msg.Food.OpenIcons) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_FOOD_NAME) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        val per = "per 100 ${unit.label}"
        // A new food's values can be read from a photo of its label: beside the head of the values, so the form keeps its room.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Per 100 ${unit.label}")
            if (editing == null) {
                TextButton(onClick = { dispatch(Msg.Food.ReadLabel(name)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (read == null) "Read from a photo" else "Read another photo", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // A value read from the photo keeps its mark while it is the value read.
        fun mark(value: Double, from: LabelValue?): LabelMark? = from?.takeIf { it.value == value }?.mark
        MarkedField("Calories", mark(kcal, read?.kcal)) {
            Stepper(value = kcal, onValueChange = { kcal = it }, step = 5.0, snap = 0.1, min = 0.0, max = MAX_KCAL_PER_100, label = "calories $per", format = { "${numberText(it)} kcal" })
        }
        MarkedField("Protein", mark(protein, read?.protein)) {
            Stepper(value = protein, onValueChange = { protein = it }, step = 1.0, snap = 0.1, min = 0.0, max = MAX_MACRO_PER_100, label = "protein $per", format = { "${numberText(it)} g" })
        }
        MarkedField("Carbs (optional)", mark(carbs, read?.carbs)) {
            Stepper(value = carbs, onValueChange = { carbs = it }, step = 1.0, snap = 0.1, min = 0.0, max = MAX_MACRO_PER_100, label = "carbs $per", format = { if (it == 0.0) "None" else "${numberText(it)} g" })
        }
        MarkedField("Fat (optional)", mark(fat, read?.fat)) {
            Stepper(value = fat, onValueChange = { fat = it }, step = 1.0, snap = 0.1, min = 0.0, max = MAX_MACRO_PER_100, label = "fat $per", format = { if (it == 0.0) "None" else "${numberText(it)} g" })
        }
        Field("Serving (optional)") {
            Stepper(value = serving, onValueChange = { serving = it }, step = 10.0, snap = 1.0, min = 0.0, max = MAX_AMOUNT, label = "serving", format = { if (it == 0.0) "None" else "${numberText(it)} ${unit.label}" })
        }
        PrimaryButton("Save food", enabled = name.isNotBlank()) {
            dispatch(Msg.Food.SaveOwn(name, kcal, protein, carbs, fat, serving))
        }
        if (editing != null) {
            ConfirmButton("Delete food", "Tap again to delete", onConfirm = { dispatch(Msg.Food.DeleteOwn(editing.id)) }, modifier = Modifier.fillMaxWidth())
            Text("What you logged of it keeps its numbers.", color = EisenColors.MutedForeground, fontSize = 13.sp)
        }
    }
    if (sheet.picker == IconPicker.Open) IconPickerSheet(icon, onPick = { dispatch(Msg.Food.PickIcon(it)) }, onClose = { dispatch(Msg.Food.CloseIcons) })
}

/** What the form says above the values a photo filled in. */
internal fun readNote(values: List<LabelValue?>): String = buildString {
    append("Read from the photo of the label.")
    if (values.any { it?.mark == LabelMark.CHECK }) append(" Look at the values marked check before you save: they do not add up, or the photo did not say all.")
    if (values.any { it == null }) append(" What it could not read is empty.")
}

/**
 * A field of the own food form with how its value came from a photo: "read", or "check" when it should be looked at. In
 * the same calm colours as the rest; TalkBack says "Calories read from the photo" (", check it").
 */
@Composable
private fun MarkedField(label: String, mark: LabelMark?, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = EisenColors.MutedForeground)
            if (mark != null) {
                val name = label.substringBefore(" (")
                Text(
                    if (mark == LabelMark.READ) "read" else "check",
                    color = if (mark == LabelMark.READ) EisenColors.MutedForeground else EisenColors.Foreground,
                    fontWeight = if (mark == LabelMark.READ) FontWeight.Normal else FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EisenColors.Muted)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clearAndSetSemantics { contentDescription = if (mark == LabelMark.READ) "$name read from the photo" else "$name read from the photo, check it" },
                )
            }
        }
        control()
    }
}

/** The photo of a label: camera or photo, the wait, the reading, and what to do when it found nothing. */
@Composable
private fun PhotoSheet(step: LabelStep, dispatch: (Msg) -> Unit, close: () -> Unit) {
    FoodSheet("Photo of the label", close) {
        when (step) {
            LabelStep.Choose -> {
                Text("The nutrition table straight on, close enough that its numbers fill the photo. It is read on this phone and then thrown away.")
                PrimaryButton("Take a photo") { dispatch(Msg.Food.ChoosePhoto(PhotoSource.CAMERA)) }
                SecondaryButton("Choose a photo") { dispatch(Msg.Food.ChoosePhoto(PhotoSource.PICKER)) }
            }
            is LabelStep.Waiting -> Waiting(if (step.source == PhotoSource.CAMERA) "Waiting for the camera…" else "Waiting for the photo…")
            LabelStep.Reading -> Waiting("Reading the label…")
            LabelStep.NothingFound -> {
                Text("No nutrition table found on this photo. One straight on, with sharp numbers, reads best.")
                PrimaryButton("Try again") { dispatch(Msg.Food.RetryPhoto) }
                SecondaryButton("Type it in") { dispatch(Msg.Food.TypeItIn) }
            }
            is LabelStep.Failed -> {
                Text(
                    when (step.why) {
                        PhotoFailure.NO_APP -> "This phone has no app for that. Choose a photo instead."
                        PhotoFailure.UNREADABLE -> "This photo could not be read."
                    },
                )
                PrimaryButton("Try again") { dispatch(Msg.Food.RetryPhoto) }
                SecondaryButton("Type it in") { dispatch(Msg.Food.TypeItIn) }
            }
        }
    }
}

@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), color = EisenColors.Primary, strokeWidth = 3.dp)
        Text(text)
    }
}

/**
 * The calories, protein, carbs and fat a day should reach; 0 is no goal (carbs and fat are optional). "Fill carbs and
 * fat from the rest" works out the two from the calories left after protein, so nobody has to do the arithmetic. Below
 * them the weight goal: lose, maintain or gain at a pace of at most 1 % of the trend weight a week, the calories that
 * pace takes from what the lifter burns, and "Adjust my target each week" for the weekly check-in.
 */
@Composable
private fun GoalSheet(model: Model, dispatch: (Msg) -> Unit, close: () -> Unit) {
    val goal = model.state.foodGoal
    val plan = model.state.weightGoal
    val weight = goalWeightView(model)
    val unit = weight.unit
    var kcal by rememberSaveable { mutableIntStateOf(goal.kcal ?: 0) }
    var protein by rememberSaveable { mutableIntStateOf(goal.protein ?: 0) }
    var carbs by rememberSaveable { mutableIntStateOf(goal.carbs ?: 0) }
    var fat by rememberSaveable { mutableIntStateOf(goal.fat ?: 0) }
    var direction by rememberSaveable { mutableStateOf(plan.direction) }
    // The pace in the unit of the settings, to a tenth.
    var pace by rememberSaveable { mutableDoubleStateOf(round1(bodyIn(plan.pace, unit)).coerceAtMost(weight.maxPace)) }
    var adjust by rememberSaveable { mutableStateOf(plan.adjust) }
    val chosen = WeightGoal(direction, bodyFrom(pace, unit), adjust)
    FoodSheet("Goal per day", close) {
        Field("Calories") {
            IntStepper(value = kcal, onValueChange = { kcal = it }, label = "calorie goal", step = 50, snap = 1, min = 0, max = MAX_GOAL_KCAL, format = { if (it == 0) "None" else "${kcalText(it)} kcal" })
        }
        Field("Protein") {
            IntStepper(value = protein, onValueChange = { protein = it }, label = "protein goal", step = 5, snap = 1, min = 0, max = MAX_GOAL_PROTEIN, format = { if (it == 0) "None" else "$it g" })
        }
        weight.protein?.let { suggested ->
            SecondaryButton("Protein from your weight: $suggested g") { protein = suggested }
            Text("${numberText(PROTEIN_PER_KG)} g a day per kg of your trend weight.", color = EisenColors.MutedForeground, fontSize = 13.sp)
        }
        Field("Carbs (optional)") {
            IntStepper(value = carbs, onValueChange = { carbs = it }, label = "carbs goal", step = 5, snap = 1, min = 0, max = MAX_GOAL_CARBS, format = { if (it == 0) "None" else "$it g" })
        }
        Field("Fat (optional)") {
            IntStepper(value = fat, onValueChange = { fat = it }, label = "fat goal", step = 5, snap = 1, min = 0, max = MAX_GOAL_FAT, format = { if (it == 0) "None" else "$it g" })
        }
        val fill = carbsAndFatFor(kcal, protein)
        if (fill != null) {
            SecondaryButton("Fill carbs and fat from the rest") {
                carbs = fill.first
                fat = fill.second
            }
            Text("Fat a quarter of the calories, carbs the rest after protein.", color = EisenColors.MutedForeground, fontSize = 13.sp)
        }
        HorizontalDivider(color = EisenColors.Border)
        Eyebrow("Weight goal")
        ChoiceGroup(GoalDirection.entries, direction, { it.label }, { direction = it })
        if (direction != GoalDirection.MAINTAIN) {
            Field("Pace") {
                Stepper(
                    value = pace,
                    onValueChange = { pace = it },
                    step = 0.1,
                    snap = 0.1,
                    min = 0.0,
                    max = weight.maxPace,
                    label = "pace",
                    format = { "${numberText(it)} ${unit.label} a week" },
                )
            }
            Text("At most 1% of your weight a week: ${numberText(weight.maxPace)} ${unit.label}.", color = EisenColors.MutedForeground, fontSize = 13.sp)
        }
        when (val burn = weight.burn) {
            is Estimate.Ready -> {
                val target = weight.target(chosen) ?: burn.kcal
                Text("You burn about ${kcalText(burn.kcal)} kcal. For this goal: ${kcalText(target)} kcal a day.", color = EisenColors.Foreground)
                noteText(burn.note)?.let { Text(it, color = EisenColors.MutedForeground, fontSize = 13.sp) }
                if (target != kcal) SecondaryButton("Use ${kcalText(target)} kcal") { kcal = target }
            }
            is Estimate.Learning -> Text("Learning what you burn: ${burn.days} of ${counted(burn.needed, "day")} with food and a weight.", color = EisenColors.MutedForeground)
        }
        Text(LOGGED_DAY_NOTE, color = EisenColors.MutedForeground, fontSize = 13.sp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = adjust, role = Role.Switch, onValueChange = { adjust = it }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Adjust my target each week", fontWeight = FontWeight.SemiBold)
                Text("Each Monday a check-in offers a new target from what you ate and weighed. Nothing changes until you take it.", color = EisenColors.MutedForeground, fontSize = 13.sp)
            }
            Switch(checked = adjust, onCheckedChange = null)
        }
        PrimaryButton("Save goal") { dispatch(Msg.Food.SaveGoal(kcal, protein, carbs, fat, chosen)) }
    }
}

/** Typing a barcode: when the scanner gave none, or from the search. */
@Composable
private fun BarcodeSheet(note: String?, dispatch: (Msg) -> Unit, close: () -> Unit) {
    // A new note is a new barcode to type: after one landed on the plate the field starts empty again.
    var code by rememberSaveable(note) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(note) { focus.requestFocus() }
    val problem = barcodeProblem(code)
    FoodSheet("Type a barcode", close) {
        note?.let { Text(it, color = EisenColors.MutedForeground) }
        OutlinedTextField(
            value = code,
            onValueChange = { typed -> code = typed.filter(Char::isDigit).take(14) },
            label = { Text("Barcode") },
            singleLine = true,
            isError = problem != null && code.length >= 8,
            supportingText = { if (problem != null && code.length >= 8) Text(problem) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (problem == null) dispatch(Msg.Food.LookUp(code)) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        PrimaryButton("Look up", enabled = problem == null) { dispatch(Msg.Food.LookUp(code)) }
    }
}

/** A barcode being looked up, or the lookup that got no answer, with Try again. */
@Composable
private fun LookupSheet(lookup: Lookup, dispatch: (Msg) -> Unit, close: () -> Unit) {
    FoodSheet("Barcode ${lookup.barcode}", close) {
        when (lookup) {
            is Lookup.Looking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = EisenColors.Primary, strokeWidth = 3.dp)
                Text("Looking it up at Open Food Facts…")
            }
            is Lookup.Failed -> {
                Text(failureText(lookup.why), color = EisenColors.Warn)
                PrimaryButton("Try again") { dispatch(Msg.Food.Retry) }
                SecondaryButton("Type another barcode") { dispatch(Msg.Food.TypeBarcode) }
            }
        }
    }
}

internal fun failureText(why: Failure): String = when (why) {
    Failure.OFFLINE -> "No network. Check the connection and try again."
    Failure.BUSY -> "Open Food Facts is busy. Try again in a minute."
}

/** Where the food data comes from, its licences, and what leaves the phone. */
@Composable
private fun CreditSheet(close: () -> Unit) {
    val links = TextLinkStyles(SpanStyle(color = EisenColors.Primary, textDecoration = TextDecoration.Underline))
    FoodSheet("Food data", close) {
        Text("Food data: Open Food Facts (ODbL) and USDA FoodData Central", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            buildAnnotatedString {
                append("Products come from ")
                withLink(LinkAnnotation.Url("https://openfoodfacts.org", links)) { append("Open Food Facts") }
                append(", a free database made by its contributors, under the Open Database License (ODbL).")
            },
        )
        Text(
            buildAnnotatedString {
                append("The plain foods (rice, eggs, oats, ...) come from ")
                withLink(LinkAnnotation.Url("https://fdc.nal.usda.gov", links)) { append("USDA FoodData Central") }
                append(", in the public domain.")
            },
        )
        Text("A barcode you scan or type, and a search you send, go to Open Food Facts. Nothing else leaves the phone: what you eat stays on it. With Health Connect sync on in Settings, it is also written to Health Connect, on this phone.")
        Text("A photo of a label is read on this phone and then thrown away: it is not kept, and not sent anywhere.")
    }
}
