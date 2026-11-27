package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.DishAi
import io.github.alexanderop.eisen.feature.food.MAX_FOOD_QUERY
import io.github.alexanderop.eisen.feature.food.MAX_PLATE_ITEMS
import io.github.alexanderop.eisen.feature.food.entryIds
import io.github.alexanderop.eisen.feature.food.matchDish
import io.github.alexanderop.eisen.feature.food.plateItemOf
import io.github.alexanderop.eisen.feature.food.readDish

/*
 * The photo of a dish: whether the phone has the model, its download, the choice of camera or photo, the wait, the
 * model's answer and what goes onto the plate. The one place [DishStep] and [FoodModel.dishAi] change. The photo never
 * reaches the model of the app: the runtime shows it to Gemini Nano and sends what it wrote.
 */

/** The photo of a dish after [msg], see [DishStep]. A Msg that does not fit the step it finds changes nothing. */
internal fun dishAfter(msg: Msg.Food, model: Model, env: Env): Next {
    val dish = model.food.sheet as? FoodSheet.Dish
    val step = dish?.step
    val ai = model.food.dishAi
    fun to(next: FoodSheet?) = model.food { it.copy(sheet = next) }
    fun at(next: DishStep) = to(FoodSheet.Dish(next))
    return when (msg) {
        is Msg.Food.DishAiChecked -> if (msg.status == ai) model.only() else model.food { it.copy(dishAi = msg.status) }.only()
        Msg.Food.ReadDish -> if (model.food.sheet != null || model.food.add == null || ai == DishAi.MISSING) model.only() else at(DishStep.Choose).only()
        Msg.Food.DownloadDishAi ->
            if (step != DishStep.Choose || ai != DishAi.DOWNLOADABLE) model.only() else model.food { it.copy(dishAi = DishAi.DOWNLOADING) } and Cmd.DownloadDishAi
        Msg.Food.DishAiDownloadFailed ->
            if (ai != DishAi.DOWNLOADING) model.only()
            else model.food { it.copy(dishAi = DishAi.DOWNLOADABLE) } and Cmd.Ui(UiEvent.Snackbar("The download did not finish. Try again, on Wi-Fi.", tag = FOOD_SNACKBAR))
        is Msg.Food.ChoosePhoto ->
            if (step != DishStep.Choose || ai != DishAi.READY) model.only() else at(DishStep.Waiting(msg.source)) and Cmd.Ui(UiEvent.TakePhoto(msg.source, PhotoFor.DISH))
        Msg.Food.PhotoChosen -> if (step !is DishStep.Waiting) model.only() else at(DishStep.Reading).only()
        Msg.Food.PhotoCancelled -> if (step !is DishStep.Waiting) model.only() else to(null).only()
        is Msg.Food.DishRead -> {
            if (step != DishStep.Reading) return model.only()
            val guesses = readDish(msg.answer)
            if (guesses.isEmpty()) return at(DishStep.NothingFound).only()
            val foods = knownFoods(model)
            // Two names that mean one food are that food once, at the first amount.
            val found = guesses.mapNotNull { guess -> matchDish(guess.name, foods, model.state.foodLog)?.let { it to guess.grams } }.distinctBy { it.first.id }
            val ids = entryIds(env.newId(), found.size)
            val items = found.mapIndexed { index, (food, grams) -> plateItemOf(food, grams, ids[index]) }
            at(DishStep.Found(items, guesses.filter { matchDish(it.name, foods, model.state.foodLog) == null }.map { it.name })).only()
        }
        is Msg.Food.DishFailed -> if (step is DishStep.Waiting || step == DishStep.Reading) at(DishStep.Failed(msg.why)).only() else model.only()
        Msg.Food.RetryPhoto -> if (step is DishStep.Found || step == DishStep.NothingFound || step is DishStep.Failed) at(DishStep.Choose).only() else model.only()
        Msg.Food.PutDish -> if (step !is DishStep.Found || step.items.isEmpty()) model.only() else putDish(model, step, env, query = null)
        is Msg.Food.SearchDish -> if (step !is DishStep.Found || msg.name !in step.missing) model.only() else putDish(model, step, env, query = msg.name)
        else -> model.only()
    }
}

/**
 * The foods of [found] onto the plate and the sheet closed; with [query] the search of the add screen holds it. A plate
 * too full for them takes none and says so (see [plateAfter]), and the sheet stays.
 */
private fun putDish(model: Model, found: DishStep.Found, env: Env, query: String?): Next {
    val (plated, cmds) = plateAfter(PlateEvent.Put(found.items), model, env)
    if (found.items.isNotEmpty() && model.food.plate.size + found.items.size > MAX_PLATE_ITEMS) return plated and cmds
    return plated.food { it.copy(sheet = null, add = if (query == null) it.add else it.add?.copy(query = query.take(MAX_FOOD_QUERY))) } and cmds
}
