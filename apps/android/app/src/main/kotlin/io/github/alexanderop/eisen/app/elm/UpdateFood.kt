package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
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
    return when (msg) {
        Msg.Food.Open -> timed.food { it.copy(day = today, sheet = null) }.only()
        Msg.Food.OpenAdd -> timed.food { it.copy(day = today, sheet = FoodSheet.Search()) }.only()
        Msg.Food.OpenScan -> timed.food { it.copy(day = today, sheet = null) } and Cmd.Ui(UiEvent.ScanBarcode)
        Msg.Food.Close -> if (!open) model.only() else timed.food { it.copy(day = null, sheet = null) }.only()
        Msg.Food.PreviousDay -> model.food.day?.let { day -> timed.food { it.copy(day = dayBefore(day)) }.only() } ?: model.only()
        Msg.Food.NextDay -> {
            val day = model.food.day ?: return model.only()
            val next = dayAfter(day)
            if (!isPastOrToday(next, today)) model.only() else timed.food { it.copy(day = next) }.only()
        }
        Msg.Food.Scan -> if (!open) model.only() else model and Cmd.Ui(UiEvent.ScanBarcode)
        // The scanner is another screen: its barcode comes back to the Food screen, opened again if it was left.
        is Msg.Food.Scanned -> {
            val code = normalBarcode(msg.barcode)
            if (code.isEmpty()) model.only() else lookupAfter(timed.food { it.copy(day = it.day ?: today) }, LookupEvent.Start(code), env)
        }
        is Msg.Food.ScanFailed -> timed.food { it.copy(day = it.day ?: today, sheet = FoodSheet.Barcode(scanNote(msg.why))) }.only()
        Msg.Food.TypeBarcode -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Barcode()) }.only()
        is Msg.Food.LookUp -> {
            val code = msg.barcode.trim()
            if (!open || barcodeProblem(code) != null) model.only() else lookupAfter(timed, LookupEvent.Start(normalBarcode(code)), env)
        }
        is Msg.Food.Looked -> lookupAfter(model, LookupEvent.Answer(normalBarcode(msg.barcode), msg.answer), env)
        Msg.Food.Retry -> lookupAfter(timed, LookupEvent.Retry, env)
        is Msg.Food.Pick -> {
            val search = model.food.sheet as? FoodSheet.Search ?: return model.only()
            val found = knownFood(model, msg.foodId) ?: (search.online as? Online.Results)?.foods?.firstOrNull { it.id == msg.foodId }
            if (found == null) model.only() else timed.food { it.copy(sheet = FoodSheet.Amount(found, startAmount(found, model.state.foodLog))) }.only()
        }
        is Msg.Food.SetAmount -> {
            val sheet = model.food.sheet as? FoodSheet.Amount ?: return model.only()
            val amount = if (msg.amount.isFinite()) round1(msg.amount.coerceIn(MIN_AMOUNT, MAX_AMOUNT)) else sheet.amount
            if (amount == sheet.amount) model.only() else model.food { it.copy(sheet = sheet.copy(amount = amount)) }.only()
        }
        Msg.Food.ConfirmAmount -> confirmAmount(timed, env, today)
        is Msg.Food.OpenEntry -> {
            val entry = model.state.foodLog.firstOrNull { it.id == msg.id }
            if (!open || entry == null) return model.only()
            val amount = entry.amount
            val food = entry.foodId?.let { knownFood(model, it) } ?: foodOfEntry(entry)
            val sheet = if (amount == null || food == null) FoodSheet.Quick(entry.id) else FoodSheet.Amount(food, amount, entry.id)
            timed.food { it.copy(sheet = sheet) }.only()
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
        Msg.Food.OpenQuick -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Quick()) }.only()
        is Msg.Food.SaveQuick -> saveQuick(timed, msg, env, today)
        is Msg.Food.NewOwn -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Own(name = msg.name.trim())) }.only()
        is Msg.Food.EditOwn ->
            if (!open || model.state.foods.none { it.id == msg.id && it.source == FoodSource.OWN }) model.only()
            else timed.food { it.copy(sheet = FoodSheet.Own(id = msg.id)) }.only()
        is Msg.Food.SaveOwn -> saveOwn(timed, msg, env)
        is Msg.Food.DeleteOwn -> {
            if (model.state.foods.none { it.id == msg.id && it.source == FoodSource.OWN }) return model.only()
            val sheet = model.food.sheet
            val stays = sheet !is FoodSheet.Own && !(sheet is FoodSheet.Amount && sheet.food.id == msg.id && sheet.entryId == null)
            // What was logged of it stays as it was: entries keep their own numbers.
            timed.withState { state -> state.copy(foods = state.foods.filter { it.id != msg.id }) }.food { it.copy(sheet = if (stays) sheet else null) }.only()
        }
        Msg.Food.OpenGoal -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Goal) }.only()
        is Msg.Food.SaveGoal -> {
            if (model.food.sheet != FoodSheet.Goal) return model.only()
            timed.withState { it.copy(foodGoal = goalWithin(FoodGoal(msg.kcal, msg.protein))) }.food { it.copy(sheet = null) }.only()
        }
        Msg.Food.OpenSearch -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Search()) }.only()
        is Msg.Food.Search -> {
            val sheet = model.food.sheet as? FoodSheet.Search ?: return model.only()
            val query = msg.query.take(MAX_FOOD_QUERY)
            if (query == sheet.query) model.only() else model.food { it.copy(sheet = sheet.copy(query = query)) }.only()
        }
        Msg.Food.SubmitSearch -> onlineAfter(timed, OnlineEvent.Submit, env)
        is Msg.Food.Searched -> onlineAfter(model, OnlineEvent.Answer(msg.query, msg.answer), env)
        Msg.Food.OpenCredit -> if (!open) model.only() else timed.food { it.copy(sheet = FoodSheet.Credit) }.only()
        Msg.Food.CloseSheet -> if (model.food.sheet == null) model.only() else model.food { it.copy(sheet = null) }.only()
    }
}

private fun scanNote(why: ScanFailure): String = when (why) {
    ScanFailure.CANCELLED -> "No barcode scanned. Type its digits instead."
    ScanFailure.UNAVAILABLE -> "The scanner is not available yet: Google Play services may still be installing it. Type the barcode instead."
    ScanFailure.FAILED -> "The scanner did not work. Type the barcode instead."
}

/** The day what is logged now goes to: the one the Food screen shows, else today. */
private fun logDay(model: Model, today: String): String = model.food.day ?: today

/** A food the phone knows: one it keeps (scanned or own) or a plain one. */
fun knownFood(model: Model, id: String): Food? = model.state.foods.firstOrNull { it.id == id } ?: model.plainFoods.firstOrNull { it.id == id }

/** The food the phone keeps for [barcode], the lifter's own before a scanned one. */
fun foodWithBarcode(model: Model, barcode: String): Food? =
    model.state.foods.filter { it.barcode == barcode }.minByOrNull { if (it.source == FoodSource.OWN) 0 else 1 }

/** [food] kept by the phone: a product of Open Food Facts once found needs no network again. Plain foods are the app's. */
private fun Model.keeping(food: Food): Model {
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
    val entryId = sheet.entryId
    if (entryId != null) {
        val old = log.firstOrNull { it.id == entryId } ?: return closed.only()
        val changed = entryOf(sheet.food, sheet.amount, old.id, old.at, old.day).copy(name = old.name, foodId = old.foodId, unit = old.unit)
        return closed.withState { state -> state.copy(foodLog = state.foodLog.map { if (it.id == entryId) changed else it }) }.only()
    }
    val entry = entryOf(sheet.food, sheet.amount, env.newId(), env.now, logDay(model, today))
    return closed.keeping(sheet.food).withState { it.copy(foodLog = it.foodLog + entry) }.only()
}

private fun deleteEntry(model: Model, id: String): Next {
    val index = model.state.foodLog.indexOfFirst { it.id == id }
    if (index == -1) return model.only()
    val entry = model.state.foodLog[index]
    val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(Feel.TAP))) else emptyList()
    // A second delete replaces the first snackbar, so only the newest one can be undone.
    return model.withState { state -> state.copy(foodLog = state.foodLog.filter { it.id != id }) }
        .undo { it.copy(deletedFoodEntry = DeletedFoodEntry(entry, index)) }
        .food { food -> if ((food.sheet as? FoodSheet.Amount)?.entryId == id || (food.sheet as? FoodSheet.Quick)?.entryId == id) food.copy(sheet = null) else food } and
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
    val id = sheet.entryId
    if (id != null) {
        val old = model.state.foodLog.firstOrNull { it.id == id } ?: return closed.only()
        val changed = quickEntry(msg.kcal, msg.protein, msg.name, old.id, old.at, old.day)
        return closed.withState { state -> state.copy(foodLog = state.foodLog.map { if (it.id == id) changed else it }) }.only()
    }
    val entry = quickEntry(msg.kcal, msg.protein, msg.name, env.newId(), env.now, logDay(model, today))
    return closed.withState { it.copy(foodLog = it.foodLog + entry) }.only()
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
            unit = old?.unit ?: FoodUnit.G,
            serving = given(msg.serving),
            pack = old?.pack,
            source = FoodSource.OWN,
        ),
    )
    // Entries already logged keep their numbers; only the food changes. Then it can be logged.
    val saved = model.withState { state ->
        if (old != null) state.copy(foods = state.foods.map { if (it.id == food.id) food else it }) else state.copy(foods = state.foods + food)
    }
    return saved.food { it.copy(sheet = FoodSheet.Amount(food, startAmount(food, saved.state.foodLog))) }.only()
}

// The lookup

internal sealed interface LookupEvent {
    data class Start(val barcode: String) : LookupEvent
    data class Answer(val barcode: String, val answer: ProductAnswer) : LookupEvent
    data object Retry : LookupEvent
}

/** The lookup after [event], with the sheet it leads to and the request it sends. The one place it changes, see [Lookup]. */
internal fun lookupAfter(model: Model, event: LookupEvent, env: Env): Next {
    val current = (model.food.sheet as? FoodSheet.LookingUp)?.lookup
    fun sheet(next: FoodSheet?) = model.food { it.copy(sheet = next) }
    fun request(code: String): Next {
        if (!underLimit(model.food.lookups, env.now, PRODUCT_REQUESTS_PER_MINUTE)) return sheet(FoodSheet.LookingUp(Lookup.Failed(code, Failure.BUSY))).only()
        return sheet(FoodSheet.LookingUp(Lookup.Looking(code))).food { it.copy(lookups = withRequest(it.lookups, env.now)) } and Cmd.LookUpProduct(code)
    }
    return when (event) {
        is LookupEvent.Start -> {
            val known = foodWithBarcode(model, event.barcode)
            when {
                known != null -> sheet(FoodSheet.Amount(known, startAmount(known, model.state.foodLog))).only()
                current is Lookup.Looking && current.barcode == event.barcode -> model.only()
                else -> request(event.barcode)
            }
        }
        LookupEvent.Retry -> if (current is Lookup.Failed) request(current.barcode) else model.only()
        is LookupEvent.Answer -> {
            if (current !is Lookup.Looking || current.barcode != event.barcode) return model.only()
            when (val answer = event.answer) {
                is ProductAnswer.Found -> {
                    val found = foodWithin(answer.food.copy(barcode = event.barcode, source = FoodSource.SCANNED))
                    val kept = model.keeping(found)
                    kept.food { it.copy(sheet = FoodSheet.Amount(found, startAmount(found, kept.state.foodLog))) }.only()
                }
                ProductAnswer.NotFound -> sheet(FoodSheet.Own(barcode = event.barcode, note = "Open Food Facts does not know this barcode. Add it as your own food.")).only()
                is ProductAnswer.NoKcal -> sheet(FoodSheet.Own(barcode = event.barcode, name = answer.name, note = "Open Food Facts has no calories for this product. Add them from its label.")).only()
                ProductAnswer.Offline -> sheet(FoodSheet.LookingUp(Lookup.Failed(event.barcode, Failure.OFFLINE))).only()
                ProductAnswer.Busy -> sheet(FoodSheet.LookingUp(Lookup.Failed(event.barcode, Failure.BUSY))).only()
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
    val sheet = model.food.sheet as? FoodSheet.Search ?: return model.only()
    fun online(next: Online) = model.food { it.copy(sheet = sheet.copy(online = next)) }
    return when (event) {
        OnlineEvent.Submit -> {
            val query = sheet.query.trim()
            val online = sheet.online
            when {
                query.length < MIN_ONLINE_QUERY -> model.only()
                online is Online.Searching && online.query == query -> model.only()
                !underLimit(model.food.searches, env.now, SEARCH_REQUESTS_PER_MINUTE) -> online(Online.Failed(query, Failure.BUSY)).only()
                else -> online(Online.Searching(query)).food { it.copy(searches = withRequest(it.searches, env.now)) } and Cmd.SearchFoods(query)
            }
        }
        is OnlineEvent.Answer -> {
            val online = sheet.online
            if (online !is Online.Searching || online.query != event.query) return model.only()
            when (val answer = event.answer) {
                is SearchAnswer.Found -> online(Online.Results(event.query, answer.foods.map(::foodWithin))).only()
                SearchAnswer.Offline -> online(Online.Failed(event.query, Failure.OFFLINE)).only()
                SearchAnswer.Busy -> online(Online.Failed(event.query, Failure.BUSY)).only()
            }
        }
    }
}

/** The entry of the log with [id]. */
fun foodEntry(model: Model, id: String): FoodEntry? = model.state.foodLog.firstOrNull { it.id == id }
