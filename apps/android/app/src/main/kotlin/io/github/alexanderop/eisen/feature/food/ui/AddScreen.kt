package io.github.alexanderop.eisen.feature.food.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.AddScreen
import io.github.alexanderop.eisen.app.elm.MIN_ONLINE_QUERY
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Online
import io.github.alexanderop.eisen.app.elm.addSuggestions
import io.github.alexanderop.eisen.app.elm.foodResults
import io.github.alexanderop.eisen.app.elm.onlineResults
import io.github.alexanderop.eisen.app.elm.plateIcon
import io.github.alexanderop.eisen.app.elm.plateItemOfFood
import io.github.alexanderop.eisen.feature.food.FoodIcon
import io.github.alexanderop.eisen.feature.food.MEAL_ICON
import io.github.alexanderop.eisen.feature.food.iconOfHit
import io.github.alexanderop.eisen.feature.food.iconOf
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.pathIcon
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.feature.food.AgainItem
import io.github.alexanderop.eisen.feature.food.DishAi
import io.github.alexanderop.eisen.feature.food.MAX_FOOD_QUERY
import io.github.alexanderop.eisen.feature.food.PlateItem
import io.github.alexanderop.eisen.feature.food.amountText
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.mealTotals
import io.github.alexanderop.eisen.feature.food.plateTotals

/**
 * The add screen over the Food tab: it stays open while foods are collected on the plate. The search and Scan at the top,
 * the foods usual at this time, the meals, and the plate pinned at the bottom with one button that logs it all. `+` puts
 * a food on the plate at its usual amount; a tap on the row opens its amount first. [snackbars] shows the app's
 * snackbars above the plate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(model: Model, dispatch: (Msg) -> Unit, snackbars: @Composable () -> Unit) {
    val add = model.food.add ?: return
    // Under an open sheet the screen is out of reach, for TalkBack too.
    val covered = model.food.sheet != null
    // Not under the Scaffold of the tabs: the surface gives the texts their colour.
    Surface(color = EisenColors.Background, contentColor = EisenColors.Foreground) {
    BoxWithConstraints {
    // The foods of an open plate take at most a third of the screen: its button and the list above always keep room.
    val plateRoom = minOf(240.dp, maxHeight / 3)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)) {
        CenterAlignedTopAppBar(
            title = { Text("Add food", fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) },
            navigationIcon = {
                TextButton(onClick = { dispatch(Msg.Food.CloseAdd) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Back, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Back", modifier = Modifier.padding(start = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = EisenColors.Background),
        )
        SearchRow(add.query, dispatch)
        WaysRow(model.food.dishAi != DishAi.MISSING, dispatch)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (add.query.isBlank()) suggestions(model, dispatch) else results(model, add, dispatch)
            }
            Box(Modifier.align(Alignment.BottomCenter)) { snackbars() }
        }
        if (model.food.plate.isNotEmpty()) PlateBar(model, model.food.plate, plateRoom, dispatch)
    }
    }
    }
    FoodSheets(model, dispatch)
}

/** The search field. */
@Composable
private fun SearchRow(query: String, dispatch: (Msg) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { dispatch(Msg.Food.Search(it.take(MAX_FOOD_QUERY))) },
            label = { Text("Search foods") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { dispatch(Msg.Food.SubmitSearch) }),
            modifier = Modifier.weight(1f),
        )
    }
}

private val LabelIcon = pathIcon("Label", "M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M9 8h6", "M9 12h6", "M9 16h4")
private val DishIcon = pathIcon("Dish", "M4 8h3l2-3h6l2 3h3v11H4z", "M12 16.5a3.5 3.5 0 1 0 0-7a3.5 3.5 0 1 0 0 7")

/** Under the search, the ways in that need the camera: a barcode, a label, and a dish on a phone that has the model. */
@Composable
private fun WaysRow(dish: Boolean, dispatch: (Msg) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WayTile(ScanIcon, "Scan", Modifier.weight(1f)) { dispatch(Msg.Food.Scan) }
        // A product without a barcode: its nutrition table, read from a photo.
        WayTile(LabelIcon, "Label", Modifier.weight(1f)) { dispatch(Msg.Food.ReadLabel()) }
        // Only on a phone that has the model: it names the foods on a photo, on the phone.
        if (dish) WayTile(DishIcon, "Dish", Modifier.weight(1f)) { dispatch(Msg.Food.ReadDish) }
    }
}

@Composable
private fun WayTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    io.github.alexanderop.eisen.core.ui.EisenCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/** With nothing typed: the foods usual at this time, the other recent ones (or common ones for a new phone), the meals, and the other ways in. */
private fun androidx.compose.foundation.lazy.LazyListScope.suggestions(model: Model, dispatch: (Msg) -> Unit) {
    val suggested = addSuggestions(model)
    fun section(title: String, items: List<AgainItem>) {
        if (items.isEmpty()) return
        item(key = "head-$title") { Eyebrow(title, Modifier.padding(top = 12.dp, bottom = 4.dp)) }
        items(items, key = { "$title-${it.food.id}" }) { item ->
            val adds = entryOf(item.food, item.amount, "", 0, "")
            FoodRow(
                name = item.food.name,
                icon = iconOf(item.food),
                detail = "${amountText(item.amount, item.food.unit)} · ${kcalText(adds.kcal)} kcal",
                onPlate = plateItemOfFood(model, item.food.id),
                onOpen = { dispatch(Msg.Food.Pick(item.food.id)) },
                onPlus = { dispatch(Msg.Food.PlateAdd(item.food.id)) },
                dispatch = dispatch,
            )
        }
    }
    section("Usual at this time", suggested.usual)
    section("Recent", suggested.recent)
    section("Common foods", suggested.common)
    val meals = model.state.meals
    if (meals.isNotEmpty()) {
        item(key = "head-meals") { Eyebrow("Meals", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
        items(meals, key = { "meal-${it.id}" }) { meal ->
            FoodRow(
                name = meal.name,
                icon = MEAL_ICON,
                detail = "${counted(meal.items.size, "food")} · ${kcalText(mealTotals(meal).kcal)} kcal",
                onPlate = null,
                onOpen = { dispatch(Msg.Food.PlateMeal(meal.id)) },
                onPlus = { dispatch(Msg.Food.PlateMeal(meal.id)) },
                dispatch = dispatch,
            )
        }
    }
    item(key = "ways") {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Type a barcode") { dispatch(Msg.Food.TypeBarcode) }
            SecondaryButton("New food") { dispatch(Msg.Food.NewOwn()) }
            SecondaryButton("Quick kcal") { dispatch(Msg.Food.OpenQuick) }
        }
    }
}

/** With something typed: what the phone knows, then what Open Food Facts found for a submitted search. */
private fun androidx.compose.foundation.lazy.LazyListScope.results(model: Model, add: AddScreen, dispatch: (Msg) -> Unit) {
    val query = add.query
    val local = foodResults(model)
    val online = onlineResults(model)
    if (local.isEmpty()) item(key = "none") { Text("Nothing on this phone matches “${query.trim()}”.", color = EisenColors.MutedForeground, modifier = Modifier.padding(vertical = 8.dp)) }
    items(local, key = { "local-${it.id}" }) { food ->
        val brand = food.brand.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
        FoodRow(
            name = food.name,
            icon = iconOf(food),
            detail = "$brand${per100Text(food)} per 100 ${food.unit.label}",
            onPlate = plateItemOfFood(model, food.id),
            onOpen = { dispatch(Msg.Food.Pick(food.id)) },
            onPlus = { dispatch(Msg.Food.PlateAdd(food.id)) },
            dispatch = dispatch,
        )
    }
    item(key = "online") { Eyebrow("Open Food Facts", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
    when (val state = add.online) {
        is Online.Searching -> item(key = "searching") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = EisenColors.Primary, strokeWidth = 3.dp)
                Text("Searching for “${state.query}”…")
            }
        }
        is Online.Failed -> item(key = "failed") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(failureText(state.why), color = EisenColors.Warn)
                SecondaryButton("Search again") { dispatch(Msg.Food.SubmitSearch) }
            }
        }
        is Online.Results -> {
            if (online.isEmpty()) item(key = "nothing") { Text("Nothing more found for “${state.query}”.", color = EisenColors.MutedForeground) }
            items(online, key = { "hit-${it.id}" }) { hit ->
                val brand = hit.brand.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
                val food = hit.food
                FoodRow(
                    name = hit.name,
                    icon = iconOfHit(hit),
                    detail = "$brand${if (food != null) "${per100Text(food)} per 100 ${food.unit.label}" else "tap to look up its calories"}",
                    onPlate = plateItemOfFood(model, hit.id),
                    onOpen = { dispatch(Msg.Food.Pick(hit.id)) },
                    onPlus = { dispatch(Msg.Food.PlateAdd(hit.id)) },
                    dispatch = dispatch,
                )
            }
            if (state.query != query.trim() && query.trim().length >= MIN_ONLINE_QUERY) item(key = "again") { SecondaryButton("Search Open Food Facts") { dispatch(Msg.Food.SubmitSearch) } }
        }
        Online.Idle -> if (query.trim().length >= MIN_ONLINE_QUERY) item(key = "ask") { SecondaryButton("Search Open Food Facts") { dispatch(Msg.Food.SubmitSearch) } }
    }
    item(key = "new") { Box(Modifier.padding(top = 10.dp)) { SecondaryButton("New food “${query.trim()}”") { dispatch(Msg.Food.NewOwn(query)) } } }
}

/**
 * A food (or a meal) of the add screen: its picture, a tap on the row opens its amount, `+` puts it on the plate. A food
 * on the plate shows a check instead, which takes it off again. The picture is decoration: the row reads the name.
 */
@Composable
private fun FoodRow(name: String, icon: FoodIcon, detail: String, onPlate: PlateItem?, onOpen: () -> Unit, onPlus: () -> Unit, dispatch: (Msg) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RowIcon(icon)
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = "Choose the amount") { onOpen() }
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(detail, color = EisenColors.MutedForeground, fontSize = 13.sp)
        }
        if (onPlate != null) {
            FilledIconButton(
                onClick = { dispatch(Msg.Food.RemovePlateItem(onPlate.entry.id)) },
                modifier = Modifier.size(48.dp).semantics { stateDescription = "On the plate" },
                shape = CircleShape,
            ) { Icon(Icons.Check, contentDescription = "Take $name off the plate") }
        } else {
            FilledTonalIconButton(
                onClick = onPlus,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
            ) { Icon(Icons.Plus, contentDescription = "Put $name on the plate") }
        }
    }
}

/**
 * The plate, pinned at the bottom: how many foods and what they add up to, the foods themselves when it is opened (a tap
 * changes the amount, the cross takes one off), "Clear", and one button that logs it all.
 */
@Composable
private fun PlateBar(model: Model, plate: List<PlateItem>, room: androidx.compose.ui.unit.Dp, dispatch: (Msg) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val totals = plateTotals(plate)
    val summary = "${counted(plate.size, "food")} · ${kcalText(totals.kcal)} kcal"
    Column(Modifier.fillMaxWidth().background(EisenColors.Card).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = if (open) "Hide the foods" else "Show the foods") { open = !open }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Plate, $summary"
                        stateDescription = if (open) "Open" else "Closed"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Plate")
                    Text(summary, fontWeight = FontWeight.Bold)
                }
                Icon(if (open) Icons.ChevronDown else Icons.ChevronUp, contentDescription = null, tint = EisenColors.MutedForeground)
            }
            TextButton(onClick = { dispatch(Msg.Food.ClearPlate) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear", fontWeight = FontWeight.SemiBold) }
        }
        if (open) {
            Column(Modifier.heightIn(max = room).verticalScroll(rememberScrollState())) {
                plate.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = EisenColors.Border)
                    val entry = item.entry
                    val amount = entry.amount?.let { amountText(it, entry.unit) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RowIcon(plateIcon(model, item))
                        Column(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.Button, onClickLabel = "Change") { dispatch(Msg.Food.OpenPlateItem(entry.id)) }
                                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(entry.name, amount, "${kcalText(entry.kcal)} kcal").joinToString(", ") }
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(entry.name, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(amount, "${kcalText(entry.kcal)} kcal").joinToString(" · "), color = EisenColors.MutedForeground, fontSize = 13.sp)
                        }
                        androidx.compose.material3.IconButton(onClick = { dispatch(Msg.Food.RemovePlateItem(entry.id)) }) {
                            Icon(Icons.Close, contentDescription = "Take ${entry.name} off the plate")
                        }
                    }
                }
            }
        }
        PrimaryButton("Log ${kcalText(totals.kcal)} kcal") { dispatch(Msg.Food.LogPlate) }
    }
}
