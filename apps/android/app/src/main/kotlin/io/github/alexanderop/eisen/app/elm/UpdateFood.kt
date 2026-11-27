package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.FoodIcon
import io.github.alexanderop.eisen.feature.food.key
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.FoodUnit
import io.github.alexanderop.eisen.feature.food.MAX_AMOUNT
import io.github.alexanderop.eisen.feature.food.MAX_FOOD_QUERY
import io.github.alexanderop.eisen.feature.food.MIN_AMOUNT
import io.github.alexanderop.eisen.feature.food.PRODUCT_REQUESTS_PER_MINUTE
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.SEARCH_REQUESTS_PER_MINUTE
import io.github.alexanderop.eisen.feature.food.SearchAnswer
import io.github.alexanderop.eisen.feature.food.amountText
import io.github.alexanderop.eisen.feature.food.barcodeProblem
import io.github.alexanderop.eisen.feature.food.dayAfter
import io.github.alexanderop.eisen.feature.food.dayBefore
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.foodOfEntry
import io.github.alexanderop.eisen.feature.food.foodIconNamed
import io.github.alexanderop.eisen.feature.food.foodWithin
import io.github.alexanderop.eisen.feature.food.goalWithin
import io.github.alexanderop.eisen.feature.food.isPastOrToday
import io.github.alexanderop.eisen.feature.food.localDay
import io.github.alexanderop.eisen.feature.food.normalBarcode
import io.github.alexanderop.eisen.feature.food.quickEntry
import io.github.alexanderop.eisen.feature.food.round1
import io.github.alexanderop.eisen.feature.food.startAmount
import io.github.alexanderop.eisen.feature.food.underLimit
import io.github.alexanderop.eisen.feature.food.withRequest
import io.github.alexanderop.eisen.feature.food.entryIds
import io.github.alexanderop.eisen.feature.food.plusDays
import io.github.alexanderop.eisen.feature.food.maxPace
import io.github.alexanderop.eisen.feature.food.weightGoalWithin
import io.github.alexanderop.eisen.feature.food.weekOf
import io.github.alexanderop.eisen.feature.food.atOfDay
import io.github.alexanderop.eisen.feature.food.entryMinute
import io.github.alexanderop.eisen.feature.food.plateItemOf
import io.github.alexanderop.eisen.feature.food.plateItemOfMeal
import io.github.alexanderop.eisen.feature.food.plateItemWithAmount
import io.github.alexanderop.eisen.feature.food.quickPlateItem

/*
 * The food log: the Food screen, its sheets, the scanner's answer and Open Food Facts. The one place the Food
 * screen and its sheet change; the lookup and the online search have machines of their own, [lookupAfter] and
 * [onlineAfter]. Every food Msg reads the clock and the time zone, so "today" is the day the phone is in.
 */

internal fun food(msg: Msg.Food, model: Model, env: Env): Next {
    val (next, cmds) = foodStep(msg, model, env)
    // A Msg that changes nothing but the clock it read gives the same model back, as every ignored Msg does.
    return if (cmds.isEmpty() && next !== model && next.copy(now = model.now, zone = model.zone) == model) model.only() else next to cmds
}

private fun foodStep(msg: Msg.Food, model: Model, env: Env): Next {
    val timed = model.copy(now = env.now, zone = env.zone)
    val today = localDay(env.now, env.zone)
    val open = model.food.day != null
    val add = model.food.add
    return when (msg) {
        Msg.Food.Open -> timed.onFoodTab(today).food { it.copy(day = today, add = null, sheet = null) }.only()
        Msg.Food.OpenAdd -> addAfter(AddEvent.Open, timed.onFoodTab(today))
        Msg.Food.OpenScan -> timed.onFoodTab(today).food { it.copy(day = today, add = null, sheet = null) } and Cmd.Ui(UiEvent.ScanBarcode)
        Msg.Food.CloseAdd -> addAfter(AddEvent.Close, model)
        Msg.Food.PreviousWeek -> if (add != null) model.only() else model.food.day?.let { day -> timed.food { it.copy(day = plusDays(day, -7)) }.only() } ?: model.only()
        // The week after, on the same weekday, or on today when that is still to come. Nothing from the week of today.
        Msg.Food.NextWeek -> {
            val day = model.food.day ?: return model.only()
            if (add != null || today in weekOf(day)) return model.only()
            val next = plusDays(day, 7)
            timed.food { it.copy(day = if (isPastOrToday(next, today)) next else today) }.only()
        }
        // On the add screen the scanner runs one food after the other onto the plate; on the day it opens once.
        Msg.Food.Scan -> when {
            add != null -> addAfter(AddEvent.Scan, timed)
            open -> model and Cmd.Ui(UiEvent.ScanBarcode)
            else -> model.only()
        }
        // The scanner is another screen: its barcode comes back to the Food tab, opened again if it was left.
        is Msg.Food.Scanned -> {
            val code = normalBarcode(msg.barcode)
            if (code.isEmpty()) model.only() else lookupAfter(timed.onFoodTab(today), LookupEvent.Start(code, direct = add?.loop != null), env)
        }
        is Msg.Food.ScanFailed -> when {
            add != null -> addAfter(AddEvent.ScanFailed(msg.why, scanNote(msg.why)), timed)
            else -> timed.onFoodTab(today).food { it.copy(sheet = FoodSheet.Barcode(scanNote(msg.why))) }.only()
        }
        Msg.Food.TypeBarcode -> when {
            add != null -> addAfter(AddEvent.Type, timed)
            open -> timed.food { it.copy(sheet = FoodSheet.Barcode()) }.only()
            else -> model.only()
        }
        is Msg.Food.LookUp -> {
            val code = msg.barcode.trim()
            if (!open || barcodeProblem(code) != null) model.only() else lookupAfter(timed, LookupEvent.Start(normalBarcode(code), direct = add?.loop != null), env)
        }
        is Msg.Food.Looked -> lookupAfter(model, LookupEvent.Answer(normalBarcode(msg.barcode), msg.answer), env)
        Msg.Food.Retry -> lookupAfter(timed, LookupEvent.Retry, env)
        is Msg.Food.Pick -> pickFood(timed, msg.foodId, direct = false, env)
        is Msg.Food.PlateAdd -> pickFood(timed, msg.foodId, direct = true, env)
        is Msg.Food.SetAmount -> {
            val sheet = model.food.sheet as? FoodSheet.Amount ?: return model.only()
            val amount = if (msg.amount.isFinite()) round1(msg.amount.coerceIn(MIN_AMOUNT, MAX_AMOUNT)) else sheet.amount
            if (amount == sheet.amount) model.only() else model.food { it.copy(sheet = sheet.copy(amount = amount)) }.only()
        }
        is Msg.Food.SetTime -> {
            val sheet = model.food.sheet as? FoodSheet.Amount ?: return model.only()
            val minute = msg.minute.coerceIn(0, 24 * 60 - 1)
            if (sheet.target !is AmountFor.Entry || minute == sheet.minute) model.only() else model.food { it.copy(sheet = sheet.copy(minute = minute)) }.only()
        }
        Msg.Food.ConfirmAmount -> confirmAmount(timed, env, today)
        is Msg.Food.OpenEntry -> {
            val entry = model.state.foodLog.firstOrNull { it.id == msg.id }
            if (!open || add != null || entry == null) return model.only()
            val sheet = sheetOf(model, entry, AmountFor.Entry(entry.id))
            timed.food { it.copy(sheet = if (sheet is FoodSheet.Amount) sheet.copy(minute = entryMinute(entry, env.zone)) else sheet) }.only()
        }
        is Msg.Food.OpenPlateItem -> {
            val item = model.food.plate.firstOrNull { it.entry.id == msg.id }
            if (add == null || item == null) return model.only()
            timed.food { it.copy(sheet = sheetOf(model, item.entry, AmountFor.OnPlate(item.entry.id), item.food)) }.only()
        }
        is Msg.Food.DeleteEntry -> deleteEntry(model, msg.id)
        Msg.Food.UndoDeleteEntry -> {
            val deleted = model.undo.deletedFoodEntry ?: return model.only()
            val back = model.undo { it.copy(deletedFoodEntry = null) }
            if (back.state.foodLog.any { it.id == deleted.entry.id }) back.only()
            else back.withState { state -> state.copy(foodLog = state.foodLog.toMutableList().apply { add(deleted.index.coerceIn(0, size), deleted.entry) }) }.only()
        }
        is Msg.Food.AddAgain -> addAgain(timed, msg, env, today)
        Msg.Food.UndoAgain -> {
            val id = model.undo.againEntry ?: return model.only()
            model.undo { it.copy(againEntry = null) }.withState { state -> state.copy(foodLog = state.foodLog.filter { it.id != id }) }.only()
        }
        // On the add screen a quick entry goes on the plate, on the day it is logged at once.
        Msg.Food.OpenQuick -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Quick(if (add != null) AmountFor.Plate else AmountFor.Log)) }.only()
        is Msg.Food.SaveQuick -> saveQuick(timed, msg, env, today)
        is Msg.Food.NewOwn -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Own(name = msg.name.trim())) }.only()
        is Msg.Food.EditOwn -> {
            val own = model.state.foods.firstOrNull { it.id == msg.id && it.source == FoodSource.OWN }
            if (!open || own == null) model.only() else timed.food { it.copy(sheet = FoodSheet.Own(id = msg.id, icon = own.icon)) }.only()
        }
        is Msg.Food.SaveOwn -> saveOwn(timed, msg, env)
        is Msg.Food.DeleteOwn -> {
            if (model.state.foods.none { it.id == msg.id && it.source == FoodSource.OWN }) return model.only()
            val sheet = model.food.sheet
            val new = (sheet as? FoodSheet.Amount)?.let { it.food.id == msg.id && (it.target == AmountFor.Log || it.target == AmountFor.Plate) } == true
            val stays = sheet !is FoodSheet.Own && !new
            // What was logged of it, and what is on the plate, stays as it was: they keep their own numbers.
            timed.withState { state -> state.copy(foods = state.foods.filter { it.id != msg.id }) }.food { it.copy(sheet = if (stays) sheet else null) }.only()
        }
        is Msg.Food.ReadLabel, is Msg.Food.ChoosePhoto, Msg.Food.PhotoChosen, Msg.Food.PhotoCancelled, is Msg.Food.LabelRecognized,
        is Msg.Food.LabelFailed, Msg.Food.RetryPhoto, Msg.Food.TypeItIn,
        -> labelAfter(msg, model)
        Msg.Food.OpenIcons -> iconAfter(IconEvent.Open, model)
        is Msg.Food.PickIcon -> iconAfter(IconEvent.Pick(msg.icon), model)
        Msg.Food.CloseIcons -> iconAfter(IconEvent.Close, model)
        Msg.Food.OpenGoal -> if (!open || add != null) model.only() else timed.food { it.copy(sheet = FoodSheet.Goal) }.only()
        is Msg.Food.SaveGoal -> {
            if (model.food.sheet != FoodSheet.Goal) return model.only()
            // The pace is kept within 1 % of the trend weight a week, the check-in's switch as it was set.
            val plan = msg.weight?.let { weightGoalWithin(model.state.weightGoal.copy(direction = it.direction, pace = it.pace, adjust = it.adjust), maxPace(trendNow(model))) }
            timed.withState { it.copy(foodGoal = goalWithin(FoodGoal(msg.kcal, msg.protein, msg.carbs, msg.fat)), weightGoal = plan ?: it.weightGoal) }.food { it.copy(sheet = null) }.only()
        }
        is Msg.Food.Search -> {
            if (add == null) return model.only()
            val query = msg.query.take(MAX_FOOD_QUERY)
            if (query == add.query) model.only() else model.food { it.copy(add = add.copy(query = query)) }.only()
        }
        Msg.Food.SubmitSearch -> onlineAfter(timed, OnlineEvent.Submit, env)
        is Msg.Food.Searched -> onlineAfter(model, OnlineEvent.Answer(msg.query, msg.answer), env)
        Msg.Food.OpenCredit -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Credit) }.only()
        Msg.Food.CloseSheet -> if (model.food.sheet == null) model.only() else addAfter(AddEvent.SheetClosed, model.food { it.copy(sheet = null) })
        // A bar of the week: that day, never one to come.
        is Msg.Food.OpenDay -> if (!isPastOrToday(msg.day, today)) model.only() else timed.onFoodTab(today).food { it.copy(day = msg.day, add = null, sheet = null) }.only()
        is Msg.Food.PlateMeal -> {
            val meal = model.state.meals.firstOrNull { it.id == msg.id }
            if (add == null || meal == null) return model.only()
            plateAfter(PlateEvent.Put(meal.items.zip(entryIds(env.newId(), meal.items.size), ::plateItemOfMeal)), model.food { it.copy(sheet = null) }, env)
        }
        is Msg.Food.RemovePlateItem -> plateAfter(PlateEvent.Remove(msg.id), model, env)
        Msg.Food.ClearPlate -> plateAfter(PlateEvent.Clear, model, env)
        Msg.Food.LogPlate -> plateAfter(PlateEvent.Log, timed, env)
        Msg.Food.OpenMeals, Msg.Food.NewMeal, is Msg.Food.NewMealOf, is Msg.Food.TogglePick, is Msg.Food.SaveNewMeal, is Msg.Food.EditMeal,
        is Msg.Food.SetMealAmount, is Msg.Food.RemoveMealItem, is Msg.Food.SaveMeal, is Msg.Food.DeleteMeal,
        is Msg.Food.AddMeal, Msg.Food.CopyDayBefore, Msg.Food.UndoAdded,
        -> mealAfter(msg, timed, env)
    }
}

/**
 * The sheet that changes [entry] (of the log, or an item of the plate, as [target] says): its add sheet, worked out from
 * [food] or the food it was logged of, or from its own numbers when that food is gone; quick kcal has none.
 */
private fun sheetOf(model: Model, entry: FoodEntry, target: AmountFor, food: Food? = null): FoodSheet {
    val amount = entry.amount
    val per100 = food ?: entry.foodId?.let { knownFood(model, it) } ?: foodOfEntry(entry)
    return if (amount == null || per100 == null) FoodSheet.Quick(target) else FoodSheet.Amount(per100, amount, target)
}

/**
 * A food of the add screen: a tap opens its add sheet for the plate, a `+` ([direct]) puts it on the plate at its usual
 * amount. A hit of the online search without its numbers is looked up by its barcode first.
 */
private fun pickFood(model: Model, id: String, direct: Boolean, env: Env): Next {
    val add = model.food.add ?: return model.only()
    val hit = (add.online as? Online.Results)?.hits?.firstOrNull { it.id == id }
    val found = knownFood(model, id) ?: hit?.food
    return when {
        found != null -> landed(model, found, direct, env)
        // The search did not carry its calories: the product is looked up by its barcode, which has them.
        hit != null -> lookupAfter(model, LookupEvent.Start(hit.barcode, direct), env)
        else -> model.only()
    }
}

private fun scanNote(why: ScanFailure): String = when (why) {
    ScanFailure.CANCELLED -> "No barcode scanned. Type its digits instead."
    ScanFailure.INSTALLING -> "Google Play services is downloading the scanner, once. Type this barcode, and tap Scan again in a minute."
    ScanFailure.UNAVAILABLE -> "This phone has no scanner from Google Play services. Type the barcode instead."
    ScanFailure.FAILED -> "The scanner did not work. Type the barcode instead."
}

/**
 * The Food tab on screen, on the day it shows or on [today] when it was not showing. The tab and its day go together:
 * the day is null on every other tab (see `nav` in Update.kt).
 */
internal fun Model.onFoodTab(today: String): Model =
    nav { it.copy(tab = Tab.FOOD) }.food { it.copy(day = it.day ?: today) }

/** The day what is logged now goes to: the one the Food screen shows, else today. */
private fun logDay(model: Model, today: String): String = model.food.day ?: today

/** A food the phone knows: one it keeps (scanned or own) or a plain one, with the picture the lifter chose for it. */
fun knownFood(model: Model, id: String): Food? =
    model.state.foods.firstOrNull { it.id == id } ?: model.plainFoods.firstOrNull { it.id == id }?.let { withPlainIcon(model, it) }

/** A plain food with the picture the lifter chose for it, if he chose one. */
internal fun withPlainIcon(model: Model, food: Food): Food =
    model.state.plainIcons[food.id]?.let(::foodIconNamed)?.let { food.copy(icon = it) } ?: food

/** The plain foods, each with the picture the lifter chose for it. */
fun plainFoodsOf(model: Model): List<Food> =
    if (model.state.plainIcons.isEmpty()) model.plainFoods else model.plainFoods.map { withPlainIcon(model, it) }

/** The food the phone keeps for [barcode], the lifter's own before a scanned one. */
fun foodWithBarcode(model: Model, barcode: String): Food? =
    model.state.foods.filter { it.barcode == barcode }.minByOrNull { if (it.source == FoodSource.OWN) 0 else 1 }

/** [food] kept by the phone: a product of Open Food Facts once found needs no network again. Plain foods are the app's. */
internal fun Model.keeping(food: Food): Model {
    if (food.source == FoodSource.PLAIN) return this
    val kept = foodWithin(food)
    return withState { state ->
        val others = state.foods.filter { it.id != kept.id }
        if (state.foods.any { it == kept }) state else state.copy(foods = others + kept)
    }
}

private fun confirmAmount(model: Model, env: Env, today: String): Next {
    val sheet = model.food.sheet as? FoodSheet.Amount ?: return model.only()
    val log = model.state.foodLog
    val closed = model.food { it.copy(sheet = null) }
    return when (val target = sheet.target) {
        AmountFor.Log -> {
            val entry = entryOf(sheet.food, sheet.amount, env.newId(), env.now, logDay(model, today))
            closed.keeping(sheet.food).withState { it.copy(foodLog = it.foodLog + entry) }.only()
        }
        AmountFor.Plate -> plateAfter(PlateEvent.Put(listOf(plateItemOf(sheet.food, sheet.amount, env.newId()))), closed, env)
        is AmountFor.Entry -> {
            val old = log.firstOrNull { it.id == target.id } ?: return closed.only()
            val changed = entryOf(sheet.food, sheet.amount, old.id, timeOf(old, sheet.minute, env), old.day).copy(name = old.name, foodId = old.foodId, unit = old.unit)
            closed.withState { state -> state.copy(foodLog = state.foodLog.map { if (it.id == target.id) changed else it }) }.only()
        }
        is AmountFor.OnPlate -> {
            val item = model.food.plate.firstOrNull { it.entry.id == target.id } ?: return closed.only()
            plateAfter(PlateEvent.Change(plateItemWithAmount(item, sheet.food, sheet.amount)), closed, env)
        }
    }
}

/** When [entry] was eaten once its time of day is set to [minute]: on its own day, so it has a time from then on. */
private fun timeOf(entry: FoodEntry, minute: Int?, env: Env): Long =
    if (minute == null || minute == entryMinute(entry, env.zone)) entry.at else atOfDay(entry.day, minute, env.zone) ?: entry.at

private fun deleteEntry(model: Model, id: String): Next {
    val index = model.state.foodLog.indexOfFirst { it.id == id }
    if (index == -1) return model.only()
    val entry = model.state.foodLog[index]
    val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(Feel.TAP))) else emptyList()
    // A second delete replaces the first snackbar, so only the newest one can be undone.
    return model.withState { state -> state.copy(foodLog = state.foodLog.filter { it.id != id }) }
        .undo { it.copy(deletedFoodEntry = DeletedFoodEntry(entry, index)) }
        .food { food -> if ((food.sheet as? FoodSheet.Amount)?.target == AmountFor.Entry(id) || (food.sheet as? FoodSheet.Quick)?.target == AmountFor.Entry(id)) food.copy(sheet = null) else food } and
        felt + Cmd.Ui(UiEvent.Snackbar("Deleted ${entry.name}", actionLabel = "Undo", onAction = Msg.Food.UndoDeleteEntry, millis = FOOD_UNDO_MS, tag = FOOD_SNACKBAR))
}

private fun addAgain(model: Model, msg: Msg.Food.AddAgain, env: Env, today: String): Next {
    val food = knownFood(model, msg.foodId) ?: return model.only()
    if (model.food.day == null) return model.only()
    val entry = entryOf(food, msg.amount, env.newId(), env.now, logDay(model, today))
    val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(Feel.TAP))) else emptyList()
    val text = "Added ${entry.name}, ${amountText(entry.amount ?: 0.0, entry.unit)}"
    // One tap is easy to make by mistake: it can be taken back.
    return model.withState { it.copy(foodLog = it.foodLog + entry) }.undo { it.copy(againEntry = entry.id) }.food { it.copy(sheet = null) } and
        felt + Cmd.Ui(UiEvent.Snackbar(text, actionLabel = "Undo", onAction = Msg.Food.UndoAgain, millis = FOOD_UNDO_MS, tag = FOOD_SNACKBAR))
}

private fun saveQuick(model: Model, msg: Msg.Food.SaveQuick, env: Env, today: String): Next {
    val sheet = model.food.sheet as? FoodSheet.Quick ?: return model.only()
    if (msg.kcal <= 0) return model.only()
    val closed = model.food { it.copy(sheet = null) }
    return when (val target = sheet.target) {
        AmountFor.Log -> {
            val entry = quickEntry(msg.kcal, msg.protein, msg.name, env.newId(), env.now, logDay(model, today))
            closed.withState { it.copy(foodLog = it.foodLog + entry) }.only()
        }
        AmountFor.Plate -> plateAfter(PlateEvent.Put(listOf(quickPlateItem(msg.kcal, msg.protein, msg.name, env.newId()))), closed, env)
        is AmountFor.Entry -> {
            val old = model.state.foodLog.firstOrNull { it.id == target.id } ?: return closed.only()
            val changed = quickEntry(msg.kcal, msg.protein, msg.name, old.id, timeOf(old, msg.minute, env), old.day)
            closed.withState { state -> state.copy(foodLog = state.foodLog.map { if (it.id == target.id) changed else it }) }.only()
        }
        is AmountFor.OnPlate -> {
            if (model.food.plate.none { it.entry.id == target.id }) return closed.only()
            plateAfter(PlateEvent.Change(quickPlateItem(msg.kcal, msg.protein, msg.name, target.id)), closed, env)
        }
    }
}

private fun saveOwn(model: Model, msg: Msg.Food.SaveOwn, env: Env): Next {
    val sheet = model.food.sheet as? FoodSheet.Own ?: return model.only()
    if (msg.name.isBlank()) return model.only()
    val old = sheet.id?.let { id -> model.state.foods.firstOrNull { it.id == id && it.source == FoodSource.OWN } }
    if (sheet.id != null && old == null) return model.food { it.copy(sheet = null) }.only()
    // An optional number left at 0 is not given.
    fun given(value: Double?) = value?.takeIf { it > 0 }
    val food = foodWithin(
        Food(
            id = old?.id ?: "own:${env.newId()}",
            name = msg.name,
            brand = old?.brand.orEmpty(),
            barcode = old?.barcode ?: sheet.barcode,
            kcal = msg.kcal,
            protein = msg.protein,
            carbs = given(msg.carbs),
            fat = given(msg.fat),
            // A label read from a photo may be per 100 ml.
            unit = old?.unit ?: sheet.read?.unit ?: FoodUnit.G,
            serving = given(msg.serving),
            pack = old?.pack,
            source = FoodSource.OWN,
            icon = sheet.icon,
        ),
    )
    // Entries already logged keep their numbers; only the food changes. Then it can be logged, or it goes on the plate.
    val saved = model.withState { state ->
        if (old != null) state.copy(foods = state.foods.map { if (it.id == food.id) food else it }) else state.copy(foods = state.foods + food)
    }
    return landed(saved, food, direct = sheet.direct, env)
}

/**
 * Where a food goes once it is known (picked, found by its barcode, saved as an own food): without the add screen its
 * add sheet opens to log it; on the add screen its add sheet opens for the plate, or, [direct], it goes straight onto
 * the plate at its usual amount and a run of barcodes goes on with the next one.
 */
internal fun landed(model: Model, food: Food, direct: Boolean, env: Env): Next {
    val start = startAmount(food, model.state.foodLog)
    val add = model.food.add
    return when {
        add == null -> model.food { it.copy(sheet = FoodSheet.Amount(food, start)) }.only()
        !direct -> model.food { it.copy(sheet = FoodSheet.Amount(food, start, AmountFor.Plate)) }.only()
        else -> {
            val (put, cmds) = plateAfter(PlateEvent.Put(listOf(plateItemOf(food, start, env.newId()))), model.food { it.copy(sheet = null) }, env)
            // A full plate ends the run: its snackbar says why.
            if (put.food.plate == model.food.plate) addAfter(AddEvent.SheetClosed, put).let { (ended, more) -> ended to cmds + more }
            else addAfter(AddEvent.Landed(food.name), put).let { (next, more) -> next to cmds + more }
        }
    }
}

// The pictures

internal sealed interface IconEvent {
    data object Open : IconEvent
    data class Pick(val icon: FoodIcon) : IconEvent
    data object Close : IconEvent
}

/**
 * Whether the add sheet [sheet] offers its food's picture to change: for a food the phone knows, or one it is about to
 * log or put on the plate (a hit of the search, kept once it is logged). Not for an entry whose food is gone.
 */
fun canPickIcon(model: Model, sheet: FoodSheet.Amount): Boolean =
    sheet.target == AmountFor.Log || sheet.target == AmountFor.Plate || knownFood(model, sheet.food.id) != null

/** The grid of pictures after [event]. The one place it changes, see [IconPicker]. A picture changes no number. */
internal fun iconAfter(event: IconEvent, model: Model): Next {
    val sheet = model.food.sheet
    fun with(next: FoodSheet) = model.food { it.copy(sheet = next) }
    return when (sheet) {
        is FoodSheet.Own -> when (event) {
            IconEvent.Open -> if (sheet.picker == IconPicker.Open) model.only() else with(sheet.copy(picker = IconPicker.Open)).only()
            IconEvent.Close -> if (sheet.picker == IconPicker.Closed) model.only() else with(sheet.copy(picker = IconPicker.Closed)).only()
            // The form keeps it until "Save food".
            is IconEvent.Pick -> if (sheet.picker == IconPicker.Closed) model.only() else with(sheet.copy(icon = event.icon, picker = IconPicker.Closed)).only()
        }
        is FoodSheet.Amount -> when (event) {
            IconEvent.Open -> if (sheet.picker == IconPicker.Open || !canPickIcon(model, sheet)) model.only() else with(sheet.copy(picker = IconPicker.Open)).only()
            IconEvent.Close -> if (sheet.picker == IconPicker.Closed) model.only() else with(sheet.copy(picker = IconPicker.Closed)).only()
            is IconEvent.Pick -> {
                if (sheet.picker == IconPicker.Closed) return model.only()
                val food = sheet.food
                val shown = with(sheet.copy(food = food.copy(icon = event.icon), picker = IconPicker.Closed))
                // Kept at once: the food's entries show it, and it stays after a restart.
                when {
                    model.state.foods.any { it.id == food.id } ->
                        shown.withState { state -> state.copy(foods = state.foods.map { if (it.id == food.id) it.copy(icon = event.icon) else it }) }.only()
                    model.plainFoods.any { it.id == food.id } ->
                        shown.withState { state -> state.copy(plainIcons = state.plainIcons + (food.id to event.icon.key)) }.only()
                    // A hit of the search the phone does not keep yet: the picture goes with it onto the plate or into the log.
                    else -> shown.only()
                }
            }
        }
        else -> model.only()
    }
}

// The lookup

internal sealed interface LookupEvent {
    /** [direct]: what is found goes straight onto the plate. */
    data class Start(val barcode: String, val direct: Boolean = false) : LookupEvent
    data class Answer(val barcode: String, val answer: ProductAnswer) : LookupEvent
    data object Retry : LookupEvent
}

/** The lookup after [event], with the sheet it leads to and the request it sends. The one place it changes, see [Lookup]. */
internal fun lookupAfter(model: Model, event: LookupEvent, env: Env): Next {
    val looking = model.food.sheet as? FoodSheet.LookingUp
    val current = looking?.lookup
    fun sheet(next: FoodSheet?) = model.food { it.copy(sheet = next) }
    fun request(code: String, direct: Boolean): Next {
        if (!underLimit(model.food.lookups, env.now, PRODUCT_REQUESTS_PER_MINUTE)) return sheet(FoodSheet.LookingUp(Lookup.Failed(code, Failure.BUSY), direct)).only()
        return sheet(FoodSheet.LookingUp(Lookup.Looking(code), direct)).food { it.copy(lookups = withRequest(it.lookups, env.now)) } and Cmd.LookUpProduct(code)
    }
    return when (event) {
        is LookupEvent.Start -> {
            val known = foodWithBarcode(model, event.barcode)
            when {
                known != null -> landed(model, known, event.direct, env)
                current is Lookup.Looking && current.barcode == event.barcode -> model.only()
                else -> request(event.barcode, event.direct)
            }
        }
        LookupEvent.Retry -> if (current is Lookup.Failed) request(current.barcode, looking.direct) else model.only()
        is LookupEvent.Answer -> {
            if (current !is Lookup.Looking || current.barcode != event.barcode) return model.only()
            val direct = looking.direct
            when (val answer = event.answer) {
                is ProductAnswer.Found -> {
                    val found = foodWithin(answer.food.copy(barcode = event.barcode, source = FoodSource.SCANNED))
                    landed(model.keeping(found), found, direct, env)
                }
                ProductAnswer.NotFound -> sheet(FoodSheet.Own(barcode = event.barcode, note = "Open Food Facts does not know this barcode. Add it as your own food.", direct = direct)).only()
                is ProductAnswer.NoKcal -> sheet(FoodSheet.Own(barcode = event.barcode, name = answer.name, note = "Open Food Facts has no calories for this product. Add them from its label.", direct = direct)).only()
                ProductAnswer.Offline -> sheet(FoodSheet.LookingUp(Lookup.Failed(event.barcode, Failure.OFFLINE), direct)).only()
                ProductAnswer.Busy -> sheet(FoodSheet.LookingUp(Lookup.Failed(event.barcode, Failure.BUSY), direct)).only()
            }
        }
    }
}

// The online search

internal sealed interface OnlineEvent {
    data object Submit : OnlineEvent
    data class Answer(val query: String, val answer: SearchAnswer) : OnlineEvent
}

/** The fewest letters a search of Open Food Facts is sent with. */
const val MIN_ONLINE_QUERY = 2

/** The online search after [event]. The one place it changes, see [Online]. */
internal fun onlineAfter(model: Model, event: OnlineEvent, env: Env): Next {
    val add = model.food.add ?: return model.only()
    fun online(next: Online) = model.food { it.copy(add = add.copy(online = next)) }
    return when (event) {
        OnlineEvent.Submit -> {
            val query = add.query.trim()
            val online = add.online
            when {
                query.length < MIN_ONLINE_QUERY -> model.only()
                online is Online.Searching && online.query == query -> model.only()
                !underLimit(model.food.searches, env.now, SEARCH_REQUESTS_PER_MINUTE) -> online(Online.Failed(query, Failure.BUSY)).only()
                else -> online(Online.Searching(query)).food { it.copy(searches = withRequest(it.searches, env.now)) } and Cmd.SearchFoods(query)
            }
        }
        is OnlineEvent.Answer -> {
            val online = add.online
            if (online !is Online.Searching || online.query != event.query) return model.only()
            when (val answer = event.answer) {
                is SearchAnswer.Found -> online(Online.Results(event.query, answer.hits.map { hit -> hit.copy(food = hit.food?.let(::foodWithin)) })).only()
                SearchAnswer.Offline -> online(Online.Failed(event.query, Failure.OFFLINE)).only()
                SearchAnswer.Busy -> online(Online.Failed(event.query, Failure.BUSY)).only()
            }
        }
    }
}

/** The entry of the log with [id]. */
fun foodEntry(model: Model, id: String): FoodEntry? = model.state.foodLog.firstOrNull { it.id == id }
