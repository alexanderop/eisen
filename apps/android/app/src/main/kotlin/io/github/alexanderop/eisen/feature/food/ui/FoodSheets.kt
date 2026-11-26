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
import io.github.alexanderop.eisen.app.elm.Failure
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.Lookup
import io.github.alexanderop.eisen.app.elm.MIN_ONLINE_QUERY
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Online
import io.github.alexanderop.eisen.app.elm.againItems
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
import kotlinx.coroutines.launch

/** The one sheet over the Food screen, as the model says. */
@Composable
internal fun FoodSheets(model: Model, dispatch: (Msg) -> Unit) {
    val close = { dispatch(Msg.Food.CloseSheet) }
    when (val sheet = model.food.sheet) {
        null -> Unit
        is FoodSheet.Amount -> AmountSheet(sheet, dispatch, close)
        is FoodSheet.Quick -> QuickSheet(sheet.entryId?.let { foodEntry(model, it) }, dispatch, close)
        is FoodSheet.Own -> OwnSheet(sheet, sheet.id?.let { id -> model.state.foods.firstOrNull { it.id == id } }, dispatch, close)
        FoodSheet.Goal -> GoalSheet(model.state.foodGoal.kcal ?: 0, model.state.foodGoal.protein ?: 0, dispatch, close)
        is FoodSheet.Barcode -> BarcodeSheet(sheet.note, dispatch, close)
        is FoodSheet.LookingUp -> LookupSheet(sheet.lookup, dispatch, close)
        is FoodSheet.Search -> SearchSheet(model, sheet, dispatch, close)
        FoodSheet.Credit -> CreditSheet(close)
        is FoodSheet.Meal -> MealSheets(model, sheet.sheet, dispatch, close)
    }
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
private fun per100Text(food: Food): String = "${numberText(food.kcal)} kcal · ${numberText(food.protein)} g protein"

/**
 * The add sheet: what the food has per 100, the amount by chips (½, 1, 2 of the serving or the pack) or typed,
 * and a button that says what it adds.
 */
@Composable
private fun AmountSheet(sheet: FoodSheet.Amount, dispatch: (Msg) -> Unit, close: () -> Unit) {
    val food = sheet.food
    val adds = entryOf(food, sheet.amount, "", 0, "")
    FoodSheet(food.name, close) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (food.brand.isNotBlank()) Text(food.brand, color = EisenColors.MutedForeground)
            Text(per100Text(food), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("per 100 ${food.unit.label}", color = EisenColors.MutedForeground)
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
        PrimaryButton("${if (sheet.entryId == null) "Add" else "Save"} ${kcalText(adds.kcal)} kcal") { dispatch(Msg.Food.ConfirmAmount) }
        if (food.source == FoodSource.OWN && sheet.entryId == null) {
            TextButton(onClick = { dispatch(Msg.Food.EditOwn(food.id)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Edit food", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Quick kcal for a meal out: calories, protein if known, a name if wanted. Opens with the keyboard on the calories. */
@Composable
private fun QuickSheet(entry: io.github.alexanderop.eisen.feature.food.FoodEntry?, dispatch: (Msg) -> Unit, close: () -> Unit) {
    var kcal by rememberSaveable { mutableIntStateOf(entry?.kcal ?: 0) }
    var protein by rememberSaveable { mutableIntStateOf(entry?.protein?.toInt() ?: 0) }
    var name by rememberSaveable { mutableStateOf(entry?.name?.takeIf { it != io.github.alexanderop.eisen.feature.food.QUICK_NAME }.orEmpty()) }
    FoodSheet(if (entry == null) "Quick kcal" else "Change entry", close) {
        Field("Calories") {
            IntStepper(value = kcal, onValueChange = { kcal = it }, label = "calories", step = 50, min = 0, max = MAX_QUICK_KCAL, format = { "${kcalText(it)} kcal" }, autoFocus = entry == null)
        }
        Field("Protein, if you know it") {
            IntStepper(value = protein, onValueChange = { protein = it }, label = "protein", step = 5, min = 0, max = MAX_QUICK_PROTEIN, format = { "$it g" })
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_FOOD_NAME) },
            label = { Text("Name (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(if (entry == null) "Add ${kcalText(kcal)} kcal" else "Save ${kcalText(kcal)} kcal", enabled = kcal > 0) {
            dispatch(Msg.Food.SaveQuick(kcal, protein.takeIf { it > 0 }, name))
        }
    }
}

/**
 * An own food: name, calories and protein per 100 g, carbs and fat if wanted, a serving if there is one. Also where a
 * barcode lands that Open Food Facts does not know, or knows without calories, with the barcode and the name filled in.
 */
@Composable
private fun OwnSheet(sheet: FoodSheet.Own, editing: Food?, dispatch: (Msg) -> Unit, close: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(editing?.name ?: sheet.name) }
    var kcal by rememberSaveable { mutableDoubleStateOf(editing?.kcal ?: 0.0) }
    var protein by rememberSaveable { mutableDoubleStateOf(editing?.protein ?: 0.0) }
    var carbs by rememberSaveable { mutableDoubleStateOf(editing?.carbs ?: 0.0) }
    var fat by rememberSaveable { mutableDoubleStateOf(editing?.fat ?: 0.0) }
    var serving by rememberSaveable { mutableDoubleStateOf(editing?.serving ?: 0.0) }
    FoodSheet(if (editing == null) "New food" else "Edit food", close) {
        sheet.note?.let { Text(it, color = EisenColors.Warn) }
        (editing?.barcode ?: sheet.barcode)?.let { Text("Barcode $it", color = EisenColors.MutedForeground) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_FOOD_NAME) },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Eyebrow("Per 100 g")
        Field("Calories") {
            Stepper(value = kcal, onValueChange = { kcal = it }, step = 5.0, snap = 0.1, min = 0.0, max = MAX_KCAL_PER_100, label = "calories per 100 g", format = { "${numberText(it)} kcal" })
        }
        Field("Protein") {
            Stepper(value = protein, onValueChange = { protein = it }, step = 1.0, snap = 0.1, min = 0.0, max = MAX_MACRO_PER_100, label = "protein per 100 g", format = { "${numberText(it)} g" })
        }
        Field("Carbs (optional)") {
            Stepper(value = carbs, onValueChange = { carbs = it }, step = 1.0, snap = 0.1, min = 0.0, max = MAX_MACRO_PER_100, label = "carbs per 100 g", format = { if (it == 0.0) "None" else "${numberText(it)} g" })
        }
        Field("Fat (optional)") {
            Stepper(value = fat, onValueChange = { fat = it }, step = 1.0, snap = 0.1, min = 0.0, max = MAX_MACRO_PER_100, label = "fat per 100 g", format = { if (it == 0.0) "None" else "${numberText(it)} g" })
        }
        Field("Serving (optional)") {
            Stepper(value = serving, onValueChange = { serving = it }, step = 10.0, snap = 1.0, min = 0.0, max = MAX_AMOUNT, label = "serving", format = { if (it == 0.0) "None" else "${numberText(it)} g" })
        }
        PrimaryButton("Save food", enabled = name.isNotBlank()) {
            dispatch(Msg.Food.SaveOwn(name, kcal, protein, carbs, fat, serving))
        }
        if (editing != null) {
            ConfirmButton("Delete food", "Tap again to delete", onConfirm = { dispatch(Msg.Food.DeleteOwn(editing.id)) }, modifier = Modifier.fillMaxWidth())
            Text("What you logged of it keeps its numbers.", color = EisenColors.MutedForeground, fontSize = 13.sp)
        }
    }
}

/** The calories and the protein a day should reach. 0 is no goal. */
@Composable
private fun GoalSheet(startKcal: Int, startProtein: Int, dispatch: (Msg) -> Unit, close: () -> Unit) {
    var kcal by rememberSaveable { mutableIntStateOf(startKcal) }
    var protein by rememberSaveable { mutableIntStateOf(startProtein) }
    FoodSheet("Goal per day", close) {
        Field("Calories") {
            IntStepper(value = kcal, onValueChange = { kcal = it }, label = "calorie goal", step = 50, min = 0, max = MAX_GOAL_KCAL, format = { if (it == 0) "None" else "${kcalText(it)} kcal" })
        }
        Field("Protein") {
            IntStepper(value = protein, onValueChange = { protein = it }, label = "protein goal", step = 5, min = 0, max = MAX_GOAL_PROTEIN, format = { if (it == 0) "None" else "$it g" })
        }
        PrimaryButton("Save goal") { dispatch(Msg.Food.SaveGoal(kcal, protein)) }
    }
}

/** Typing a barcode: when the scanner gave none, or from the search. */
@Composable
private fun BarcodeSheet(note: String?, dispatch: (Msg) -> Unit, close: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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

private fun failureText(why: Failure): String = when (why) {
    Failure.OFFLINE -> "No network. Check the connection and try again."
    Failure.BUSY -> "Open Food Facts is busy. Try again in a minute."
}

/**
 * The search: what the phone knows filters as it is typed, and the keyboard's search key (or the button) asks Open
 * Food Facts once. With nothing typed it offers "Again", a typed barcode, a new food and quick kcal.
 */
@Composable
private fun SearchSheet(model: Model, sheet: FoodSheet.Search, dispatch: (Msg) -> Unit, close: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val query = sheet.query
    val local = foodResults(model)
    val online = onlineResults(model)
    FoodSheet("Add food", close) {
        OutlinedTextField(
            value = query,
            onValueChange = { dispatch(Msg.Food.Search(it.take(MAX_FOOD_QUERY))) },
            label = { Text("Search foods") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { dispatch(Msg.Food.SubmitSearch) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (query.isBlank()) {
            val again = againItems(model)
            if (model.state.meals.isNotEmpty()) MealChips(model.state.meals, onAdd = { dispatch(Msg.Food.AddMeal(it.id)) })
            if (again.isNotEmpty()) AgainChips(again) { dispatch(Msg.Food.AddAgain(it.food.id, it.amount)) }
            SecondaryButton("Type a barcode") { dispatch(Msg.Food.TypeBarcode) }
            SecondaryButton("New food") { dispatch(Msg.Food.NewOwn()) }
            SecondaryButton("Quick kcal") { dispatch(Msg.Food.OpenQuick) }
            return@FoodSheet
        }
        if (local.isEmpty()) Text("Nothing on this phone matches “${query.trim()}”.", color = EisenColors.MutedForeground)
        FoodList(local) { dispatch(Msg.Food.Pick(it.id)) }
        Eyebrow("Open Food Facts")
        when (val state = sheet.online) {
            is Online.Searching -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = EisenColors.Primary, strokeWidth = 3.dp)
                Text("Searching for “${state.query}”…")
            }
            is Online.Failed -> {
                Text(failureText(state.why), color = EisenColors.Warn)
                SecondaryButton("Search again") { dispatch(Msg.Food.SubmitSearch) }
            }
            is Online.Results -> {
                if (online.isEmpty()) Text("Nothing more found for “${state.query}”.", color = EisenColors.MutedForeground)
                HitList(online) { dispatch(Msg.Food.Pick(it.id)) }
                if (state.query != query.trim() && query.trim().length >= MIN_ONLINE_QUERY) SecondaryButton("Search Open Food Facts") { dispatch(Msg.Food.SubmitSearch) }
            }
            Online.Idle -> if (query.trim().length >= MIN_ONLINE_QUERY) SecondaryButton("Search Open Food Facts") { dispatch(Msg.Food.SubmitSearch) }
        }
        SecondaryButton("New food “${query.trim()}”") { dispatch(Msg.Food.NewOwn(query)) }
    }
}

/** Foods as rows: name, brand, and the numbers per 100. A tap opens the add sheet. */
@Composable
private fun FoodList(foods: List<Food>, onPick: (Food) -> Unit) {
    Column {
        foods.forEachIndexed { index, food ->
            if (index > 0) HorizontalDivider(color = EisenColors.Border)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onPick(food) }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(food.name, fontWeight = FontWeight.SemiBold)
                val brand = food.brand.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
                Text("$brand${per100Text(food)} per 100 ${food.unit.label}", color = EisenColors.MutedForeground, fontSize = 13.sp)
            }
        }
    }
}

/** What Open Food Facts found: name, brand, and the numbers per 100 when the search had them. A tap opens the add sheet, or looks the product up first. */
@Composable
private fun HitList(hits: List<SearchHit>, onPick: (SearchHit) -> Unit) {
    Column {
        hits.forEachIndexed { index, hit ->
            if (index > 0) HorizontalDivider(color = EisenColors.Border)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onPick(hit) }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(hit.name, fontWeight = FontWeight.SemiBold)
                val brand = hit.brand.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
                val food = hit.food
                Text("$brand${if (food != null) "${per100Text(food)} per 100 ${food.unit.label}" else "tap to look up its calories"}", color = EisenColors.MutedForeground, fontSize = 13.sp)
            }
        }
    }
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
    }
}
