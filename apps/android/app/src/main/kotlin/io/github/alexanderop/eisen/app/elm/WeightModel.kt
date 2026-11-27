package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.EstimateNote
import io.github.alexanderop.eisen.feature.food.WeightEntry

/**
 * The weight log's part of the model that is not saved. [sheet] is the sheet that logs a weight, over the Food tab or
 * History › Progress, null while it is closed. [shown] is the Monday of the week whose check-in card has been on the
 * Food tab, see [CheckIn]. After a restart both are gone: the sheet with the process, and a card not answered comes
 * back the next time the Food tab shows.
 */
data class WeightModel(val sheet: WeightSheet? = null, val shown: String? = null)

/**
 * The sheet that logs the weight of [day] (today or an earlier one, never one to come). [value] is the number on it, in
 * the unit of the settings, as it is being chosen; Save keeps it in kilograms. One weight per day: Save on a day that has
 * one replaces it.
 *
 *            Open(day)                          SetValue, PreviousDay, NextDay: the same sheet, another number or day
 *   ┌──────┐ ─────────▶ ┌────────────────────┐ ◀─┘
 *   │ none │            │ WeightSheet(d, v)  │
 *   └──────┘ ◀───────── └────────────────────┘
 *     Save (the weight of d is v), Delete (d has none, with Undo), Close, Back, another tab, a backup, a Reset
 */
data class WeightSheet(val day: String, val value: Double)

/** A weight swiped out or deleted from its sheet: Undo puts it back, unless its day has another weight by then. */
typealias DeletedWeight = WeightEntry

/**
 * What last week's numbers came to, for the weekly check-in: [intake] eaten a day on average over [loggedDays] of the
 * seven days with food logged (null: none), the trend from [trendFrom] to [trendTo] in kilograms (null without a trend
 * that long), what the lifter burns ([expenditure], about), and the [target] that reaches the goal pace from it, against
 * the goal of now, [was] (null: no calorie goal).
 */
data class CheckInOffer(
    val intake: Int?,
    val loggedDays: Int,
    val trendFrom: Double?,
    val trendTo: Double?,
    val expenditure: Int,
    val target: Int,
    val was: Int?,
    val note: EstimateNote? = null,
)

/**
 * The weekly check-in: once a week, the first time the Food tab shows on or after Monday with enough data, a card offers
 * a new calorie target from what the lifter ate and weighed (adherence neutral: what the plan said does not matter).
 * Nothing changes without a tap on Take. [checkIn] works out where it stands from the model; [checkInAfter] (UpdateWeight.kt)
 * is the one place it moves.
 *
 *                     a new week, enough data
 *   ┌─────────────┐ ──────────────────────▶ ┌──────────┐  the Food tab shows   ┌────────────┐  Take  ┌───────────┐
 *   │NotEnoughData│ ◀────────────────────── │ Due(w)   │ ────────────────────▶ │ Shown(w)   │ ─────▶ │ Taken(w)  │
 *   └─────────────┘   data taken away       └──────────┘ ◀──────────────────── └────────────┘  Keep  ┌───────────┐
 *                                              ▲   a restart (nothing of Shown is saved)        ─────▶ │ Kept(w)   │
 *                                              └───────────────────── the next Monday ─────────────── └───────────┘
 *   Off: "Adjust my target each week" switched off. Take and Keep only answer a card that is Shown.
 *   Weeks missed: one card for the week of now, from last week's numbers; nothing is owed for the weeks before.
 */
sealed interface CheckIn {
    data object Off : CheckIn

    /** Not yet [days] of the [needed] logged days up to last Sunday. [week]: the Monday of this week. */
    data class NotEnoughData(val week: String, val days: Int, val needed: Int) : CheckIn

    /** Enough data, this week not answered, the card not on screen yet. */
    data class Due(val week: String, val offer: CheckInOffer) : CheckIn

    /** The card is on the Food tab. */
    data class Shown(val week: String, val offer: CheckInOffer) : CheckIn

    /** This week's target was taken. */
    data class Taken(val week: String, val target: Int) : CheckIn

    /** This week's offer was left, the goal kept as it was. */
    data class Kept(val week: String, val was: Int?) : CheckIn
}

/** The tag of the weight snackbars. */
const val WEIGHT_SNACKBAR = "weight"
