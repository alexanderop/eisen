package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.CheckInAnswer
import io.github.alexanderop.eisen.feature.food.Estimate
import io.github.alexanderop.eisen.feature.food.MAX_BODY_KG
import io.github.alexanderop.eisen.feature.food.MIN_BODY_KG
import io.github.alexanderop.eisen.feature.food.START_BODY_KG
import io.github.alexanderop.eisen.feature.food.WeightEntry
import io.github.alexanderop.eisen.feature.food.bodyFrom
import io.github.alexanderop.eisen.feature.food.bodyIn
import io.github.alexanderop.eisen.feature.food.bodyKg
import io.github.alexanderop.eisen.feature.food.carbsAndFatFor
import io.github.alexanderop.eisen.feature.food.dayAfter
import io.github.alexanderop.eisen.feature.food.dayBefore
import io.github.alexanderop.eisen.feature.food.dayTitle
import io.github.alexanderop.eisen.feature.food.entriesOf
import io.github.alexanderop.eisen.feature.food.estimate
import io.github.alexanderop.eisen.feature.food.goalWithin
import io.github.alexanderop.eisen.feature.food.isPastOrToday
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.localDay
import io.github.alexanderop.eisen.feature.food.mondayOf
import io.github.alexanderop.eisen.feature.food.round1
import io.github.alexanderop.eisen.feature.food.roundKcal
import io.github.alexanderop.eisen.feature.food.signedPace
import io.github.alexanderop.eisen.feature.food.targetOf
import io.github.alexanderop.eisen.feature.food.trendAt
import io.github.alexanderop.eisen.feature.food.trendOf
import io.github.alexanderop.eisen.feature.food.weekOf
import io.github.alexanderop.eisen.feature.food.weightOf
import io.github.alexanderop.eisen.feature.food.weightUpTo
import io.github.alexanderop.eisen.feature.food.withWeight

/*
 * The weight log and the weekly check-in: the sheet that logs a weight (see [WeightSheet]) and the check-in's machine
 * (see [CheckIn], [checkInAfter]). Every Msg reads the clock and the time zone, so "today" is the day the phone is in.
 */

internal fun weight(msg: Msg.Weight, model: Model, env: Env): Next {
    val (next, cmds) = weightStep(msg, model, env)
    // A Msg that changes nothing but the clock it read gives the same model back, as every ignored Msg does.
    return if (cmds.isEmpty() && next !== model && next.copy(now = model.now, zone = model.zone) == model) model.only() else next to cmds
}

private fun weightStep(msg: Msg.Weight, model: Model, env: Env): Next {
    val timed = model.copy(now = env.now, zone = env.zone)
    val today = localDay(env.now, env.zone)
    val sheet = model.weight.sheet
    val weights = model.state.weights
    fun opened(next: WeightSheet?): Model = timed.weight { it.copy(sheet = next) }
    return when (msg) {
        is Msg.Weight.Open -> {
            val day = msg.day ?: today
            if (!isPastOrToday(day, today) || sheet?.day == day) return model.only()
            opened(WeightSheet(day, startValue(model, day))).only()
        }
        is Msg.Weight.SetValue -> {
            sheet ?: return model.only()
            val value = valueWithin(model, msg.value) ?: sheet.value
            if (value == sheet.value) model.only() else model.weight { it.copy(sheet = sheet.copy(value = value)) }.only()
        }
        // Another day keeps the number typed, unless that day has a weight of its own.
        Msg.Weight.PreviousDay -> {
            sheet ?: return model.only()
            val day = dayBefore(sheet.day)
            if (day == sheet.day) return model.only()
            opened(WeightSheet(day, weightOf(weights, day)?.let { shown(model, it.kg) } ?: sheet.value)).only()
        }
        Msg.Weight.NextDay -> {
            sheet ?: return model.only()
            val day = dayAfter(sheet.day)
            if (!isPastOrToday(day, today) || day == sheet.day) return model.only()
            opened(WeightSheet(day, weightOf(weights, day)?.let { shown(model, it.kg) } ?: sheet.value)).only()
        }
        Msg.Weight.Save -> {
            sheet ?: return model.only()
            val entry = WeightEntry(sheet.day, bodyKg(bodyFrom(sheet.value, model.state.settings.unit)), env.now)
            // The same weight again on the same day changes nothing on disk, and keeps when it was first logged.
            val kept = weightOf(weights, sheet.day)?.takeIf { it.kg == entry.kg }
            opened(null).withState { state -> if (kept != null) state else state.copy(weights = withWeight(state.weights, entry)) }.only()
        }
        Msg.Weight.Delete -> {
            sheet ?: return model.only()
            val gone = weightOf(weights, sheet.day) ?: return model.only()
            val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(Feel.TAP))) else emptyList()
            val title = dayTitle(gone.day, today).let { if (it == "Today" || it == "Yesterday") it.lowercase() else it }
            opened(null).withState { state -> state.copy(weights = state.weights.filter { it.day != gone.day }) }.undo { it.copy(deletedWeight = gone) } and
                felt + Cmd.Ui(UiEvent.Snackbar("Deleted the weight of $title", actionLabel = "Undo", onAction = Msg.Weight.UndoDelete, millis = FOOD_UNDO_MS, tag = WEIGHT_SNACKBAR))
        }
        Msg.Weight.UndoDelete -> {
            val deleted = model.undo.deletedWeight ?: return model.only()
            val back = model.undo { it.copy(deletedWeight = null) }
            // A weight logged on that day since is the newer one: it stays.
            if (weightOf(back.state.weights, deleted.day) != null) back.only() else back.withState { it.copy(weights = withWeight(it.weights, deleted)) }.only()
        }
        Msg.Weight.Close -> if (sheet == null) model.only() else model.weight { it.copy(sheet = null) }.only()
        Msg.Weight.TakeCheckIn -> checkInAfter(timed, CheckInEvent.TAKE)
        Msg.Weight.KeepCheckIn -> checkInAfter(timed, CheckInEvent.KEEP)
    }
}

/** [kg] as the sheet shows it: in the unit of the settings, to a tenth. */
private fun shown(model: Model, kg: Double): Double = round1(bodyIn(kg, model.state.settings.unit))

/** A number typed or stepped on the sheet, in the unit of the settings and in the limits; null for one that is no number. */
private fun valueWithin(model: Model, value: Double): Double? {
    if (!value.isFinite()) return null
    val unit = model.state.settings.unit
    return round1(value.coerceIn(bodyIn(MIN_BODY_KG, unit), bodyIn(MAX_BODY_KG, unit)))
}

/** What the sheet of [day] starts at: that day's weight, else the last one before it, else the last one, else [START_BODY_KG]. */
private fun startValue(model: Model, day: String): Double {
    val weights = model.state.weights
    val kg = weightOf(weights, day)?.kg ?: weightUpTo(weights, day)?.kg ?: weights.lastOrNull()?.kg ?: START_BODY_KG
    return shown(model, kg)
}

// The weekly check-in

internal enum class CheckInEvent {
    /** The Food tab shows. */
    SEEN,
    TAKE,
    KEEP,
}

/** Where the weekly check-in of [model] stands, as of [Model.now]. See [CheckIn]. */
fun checkIn(model: Model): CheckIn {
    val state = model.state
    val goal = state.weightGoal
    if (!goal.adjust) return CheckIn.Off
    val week = mondayOf(today(model))
    goal.checkIn?.takeIf { it.week == week }?.let { return if (it.took) CheckIn.Taken(week, it.target) else CheckIn.Kept(week, it.was) }
    // Last week's numbers, up to Sunday: what is logged today changes nothing the card says.
    val sunday = dayBefore(week)
    val offer = when (val burn = estimate(state.foodLog, state.weights, sunday)) {
        is Estimate.Learning -> return CheckIn.NotEnoughData(week, burn.days, burn.needed)
        is Estimate.Ready -> {
            val days = weekOf(sunday).map { day -> entriesOf(state.foodLog, day).sumOf { it.kcal } to entriesOf(state.foodLog, day).isNotEmpty() }
            val logged = days.filter { it.second }
            val trend = trendOf(state.weights, sunday)
            CheckInOffer(
                intake = logged.takeIf { it.isNotEmpty() }?.let { roundKcal(it.sumOf { day -> day.first }.toDouble() / it.size) },
                loggedDays = logged.size,
                trendFrom = trendAt(trend, dayBefore(weekOf(sunday).first())),
                trendTo = trendAt(trend, sunday),
                expenditure = burn.kcal,
                target = targetOf(burn.kcal, signedPace(goal)),
                was = state.foodGoal.kcal,
                note = burn.note,
            )
        }
    }
    return if (model.weight.shown == week) CheckIn.Shown(week, offer) else CheckIn.Due(week, offer)
}

/**
 * The check-in after [event], the one place it moves (see [CheckIn]): the Food tab showing a card that is due, a tap on
 * Take (the target becomes the calorie goal; carbs and fat that were set are filled from the rest again) or on Keep.
 * Anything else changes nothing.
 */
internal fun checkInAfter(model: Model, event: CheckInEvent): Next {
    val now = checkIn(model)
    return when (event) {
        CheckInEvent.SEEN -> if (now is CheckIn.Due) model.weight { it.copy(shown = now.week) }.only() else model.only()
        CheckInEvent.TAKE -> {
            if (now !is CheckIn.Shown) return model.only()
            val offer = now.offer
            val goal = model.state.foodGoal
            val fill = if (goal.carbs != null && goal.fat != null) carbsAndFatFor(offer.target, goal.protein ?: 0) else null
            val taken = goalWithin(goal.copy(kcal = offer.target, carbs = fill?.first ?: goal.carbs, fat = fill?.second ?: goal.fat))
            model.withState { it.copy(foodGoal = taken, weightGoal = it.weightGoal.copy(checkIn = CheckInAnswer(now.week, took = true, target = offer.target, was = offer.was))) } and
                Cmd.Ui(UiEvent.Snackbar("Target this week: ${kcalText(offer.target)} kcal", tag = WEIGHT_SNACKBAR))
        }
        CheckInEvent.KEEP -> {
            if (now !is CheckIn.Shown) return model.only()
            model.withState { it.copy(weightGoal = it.weightGoal.copy(checkIn = CheckInAnswer(now.week, took = false, target = now.offer.target, was = now.offer.was))) }.only()
        }
    }
}

/** The Food tab shows, on any day, without its add screen over it: where the check-in's card is. */
internal fun showsCheckInCard(model: Model): Boolean = showsFood(model) && model.food.add == null
