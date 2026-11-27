package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodIcon
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.LabelReading
import io.github.alexanderop.eisen.feature.food.MealItem
import io.github.alexanderop.eisen.feature.food.PlateItem
import io.github.alexanderop.eisen.feature.food.SearchHit

/**
 * The Food tab: a day of what was eaten. [day] is the day it shows (`2026-11-26`), null on every other tab (the tab and
 * its day change together, see `nav` in Update.kt). [add] is the add screen over it, null while it is closed. [sheet] is
 * the one sheet over either of them. [plate] is what was collected on the add screen and not yet logged: it stays when
 * the screen or the tab is left, and is kept in a file of its own across a restart (see [Cmd.SavePlate]). [lookups] and
 * [searches] are when the last requests to Open Food Facts went out, within the last minute, to keep its rate limits.
 * Only the plate is saved: after a restart the day, the screens and a lookup that was going are gone, the log is not.
 */
data class FoodModel(
    val day: String? = null,
    val add: AddScreen? = null,
    val sheet: FoodSheet? = null,
    val plate: List<PlateItem> = emptyList(),
    val lookups: List<Long> = emptyList(),
    val searches: List<Long> = emptyList(),
)

/**
 * The add screen: the search ([query] filters what the phone knows at once, [online] is what Open Food Facts found for a
 * submitted one), the foods usual at this time, the meals, and the plate at the bottom. It stays open while foods are
 * collected. [loop] is a run of barcodes going straight onto the plate. [addAfter] (UpdatePlate.kt) is the one place the
 * screen and its loop change.
 *
 *              OpenAdd                       Scan                  a food lands on the plate: the scanner again
 *   ┌────────┐ ──────▶ ┌─────────────────┐ ──────▶ ┌──────────┐ ◀─┘
 *   │ closed │         │ open, no loop   │ ◀────── │ SCANNING │ ── the scanner fails (not a cancel) ──┐
 *   │ (null) │ ◀────── │                 │ cancel, └──────────┘                                       ▼
 *   └────────┘         └─────────────────┘ a sheet                   ┌────────┐ ◀─┘ a food lands on the plate:
 *    CloseAdd, Back,     ▲  │  TypeBarcode put away                  │ TYPING │      the barcode sheet again
 *    LogPlate, another   │  └──────────────────────────────────────▶ └────────┘
 *    tab                 └─── a sheet put away, a barcode nobody knows (its own food form) ───┘
 */
data class AddScreen(val query: String = "", val online: Online = Online.Idle, val loop: Loop? = null)

/** A run of barcodes onto the plate: from the scanner, opened again after each food, or typed, one sheet after the other. */
enum class Loop { SCANNING, TYPING }

/** Where the amount of an add sheet or a quick entry goes. */
sealed interface AmountFor {
    /** A new entry, logged at once on the day shown. */
    data object Log : AmountFor

    /** A new item on the plate. */
    data object Plate : AmountFor

    /** Entry [id] of the log changes. */
    data class Entry(val id: String) : AmountFor

    /** Item [id] of the plate changes. */
    data class OnPlate(val id: String) : AmountFor
}

/**
 * The one sheet over the Food tab or the add screen. None is null. Every change of it is made in `food` (UpdateFood.kt).
 *
 *                OpenQuick, OpenGoal, OpenCredit, TypeBarcode, NewOwn, OpenEntry, OpenPlateItem, OpenMeals, NewMeal
 *   ┌──────┐ ───────────────────────────────────────────────────────────────▶ that sheet
 *   │ none │ ◀─────────────── CloseSheet, ConfirmAmount, SaveQuick, SaveGoal, DeleteOwn, AddAgain, AddMeal, a tab
 *   └──────┘
 *   Meal: the meals, see [MealSheet]
 *   Pick(id) on the add screen ──▶ Amount for the plate       Own ── SaveOwn ──▶ Amount of the food saved
 *   Pick(id of a hit without its numbers) ──▶ LookingUp its barcode, see [Lookup]
 *   Amount of an own food ── EditOwn ──▶ Own(id)
 *   Scanned, LookUp, Retry: the lookup, see [Lookup]; a run of barcodes onto the plate, see [AddScreen]
 *   ReadLabel on the add screen or a new food's form ──▶ Photo: the photo of a label, see [LabelStep]
 */
sealed interface FoodSheet {
    /**
     * The add sheet of [food] with [amount] chosen, in its unit, and [target], where that amount goes. [minute]: the time
     * of day an entry of the log is set to (null: it keeps the one it has, or has none).
     */
    data class Amount(
        val food: Food,
        val amount: Double,
        val target: AmountFor = AmountFor.Log,
        val minute: Int? = null,
        /** The grid of pictures over it: the food's picture can be changed, see [IconPicker]. */
        val picker: IconPicker = IconPicker.Closed,
    ) : FoodSheet

    /** Quick kcal for a meal out: logged, put on the plate, or a quick entry or plate item changed. */
    data class Quick(val target: AmountFor = AmountFor.Log) : FoodSheet

    /**
     * The form of an own food. [id]: the food it edits, null for a new one. [barcode] and [name]: filled in from a
     * lookup that found nothing to log. [note]: what the form says above it, why it opened. [direct]: the food goes
     * straight onto the plate once it is saved (it came from a run of barcodes or a `+`). [icon]: the picture the lifter
     * chose (the one the food has, when it is edited); null while he has not, and then the picture follows the name typed.
     * [read]: what a photo of the label gave, which fills the form, each value marked read or to check; null when it was
     * not read from a photo.
     */
    data class Own(
        val id: String? = null,
        val barcode: String? = null,
        val name: String = "",
        val note: String? = null,
        val direct: Boolean = false,
        val icon: FoodIcon? = null,
        val picker: IconPicker = IconPicker.Closed,
        val read: LabelReading? = null,
    ) : FoodSheet

    data object Goal : FoodSheet

    /** Typing a barcode, from the add screen or after the scanner gave none; [note] says why, or what landed on the plate. */
    data class Barcode(val note: String? = null) : FoodSheet

    /** A barcode being looked up at Open Food Facts. [direct]: what it finds goes straight onto the plate. */
    data class LookingUp(val lookup: Lookup, val direct: Boolean = false) : FoodSheet

    /** Where the food data comes from, and what leaves the phone. */
    data object Credit : FoodSheet

    /** Making, changing and deleting meals, see [MealSheet]. */
    data class Meal(val sheet: MealSheet) : FoodSheet

    /**
     * The photo of a label being taken and read, see [LabelStep]. [form]: the own food form it fills in: the one it was
     * started from (a barcode nobody knew, a name typed), or null when it was started on the add screen.
     */
    data class Photo(val step: LabelStep, val form: Own? = null) : FoodSheet
}

/** Where the photo of a label comes from: the phone's camera app, or the system's photo picker. */
enum class PhotoSource { CAMERA, PICKER }

/** Why a photo gave nothing to read: no camera app on the phone, or a photo that could not be opened or read. */
enum class PhotoFailure { NO_APP, UNREADABLE }

/**
 * A photo of a nutrition table, read on the phone and thrown away. Nothing is kept or sent, and nothing is saved or
 * logged until the lifter saves the form it fills. [labelAfter] (UpdateLabel.kt) is the one place it changes.
 *
 *   ReadLabel ┌────────┐ ChoosePhoto(source) ┌─────────────────┐ PhotoChosen ┌─────────┐ LabelRecognized(a table)
 *   ────────▶ │ Choose │ ──────────────────▶ │ Waiting(source) │ ──────────▶ │ Reading │ ────────────────────────▶ the own
 *             └────────┘                     └─────────────────┘             └─────────┘   food form, filled: read
 *                 ▲                          PhotoCancelled: back to where it began, │ │
 *                 │                          the add screen or the form as it was    │ │ LabelRecognized(no table)
 *                 │ RetryPhoto       ┌──────────────┐ ◀────────────────────────────────┘ │
 *                 ├───────────────── │ NothingFound │                                    │ LabelFailed(why), also
 *                 │                  └──────────────┘                                    │ from Waiting (no camera app)
 *                 │ RetryPhoto       ┌─────────────┐ ◀────────────────────────────────────┘
 *                 └───────────────── │ Failed(why) │
 *                                    └─────────────┘
 *   TypeItIn (NothingFound, Failed): the form, as it began. CloseSheet, Back: from each step, closed.
 *   A photo, an answer or a failure that comes when no photo is awaited changes nothing. After a restart the flow is gone.
 */
sealed interface LabelStep {
    data object Choose : LabelStep

    /** The camera app or the photo picker is open. */
    data class Waiting(val source: PhotoSource) : LabelStep

    /** The text recognizer reads the photo. */
    data object Reading : LabelStep

    /** The photo has no nutrition table the reader could find. */
    data object NothingFound : LabelStep

    data class Failed(val why: PhotoFailure) : LabelStep
}

/**
 * The grid of the 42 pictures, a sheet over the add sheet of a food or over the own food form. [iconAfter] (UpdateFood.kt)
 * is the one place it changes.
 *
 *   ┌────────┐ OpenIcons ┌──────┐ PickIcon(icon): the picture of the food (or of the form) is [FoodIcon] icon
 *   │ Closed │ ────────▶ │ Open │ ───────────────────────────────────────────────────────────────────────▶ Closed
 *   └────────┘ ◀──────── └──────┘
 *              CloseIcons (its close button, Back, a swipe down): nothing changes
 *
 *   Only over the add sheet of a food the phone knows (or one it is about to log) and over the own food form; anywhere
 *   else OpenIcons and PickIcon change nothing. A food the phone keeps takes the picture at once and keeps it; a plain
 *   food keeps it in [io.github.alexanderop.eisen.app.state.AppState.plainIcons]. A picture changes no number.
 */
sealed interface IconPicker {
    data object Closed : IconPicker

    data object Open : IconPicker
}

/**
 * The sheet of the meals. [mealAfter] (UpdateMeals.kt) is the one place it changes. Logging a meal needs no sheet: its
 * chip adds it in one tap, with Undo.
 *
 *            NewMeal (the day shown has entries: all of them picked)
 *   ┌──────┐ ───────────────────────────────────────────▶ ┌──────────────┐  TogglePick(an entry of that day)
 *   │      │ ◀──── SaveNewMeal(name): a meal of them ───── │ Pick(picked) │ ◀──┘
 *   │      │                                               └──────────────┘
 *   │ none │ ── OpenMeals ──▶ ┌─────┐ ── EditMeal(id) ──▶ ┌────────────────┐  SetMealAmount(i, amount),
 *   │      │                  │ All │ ◀── SaveMeal(name), │ Edit(id, rows) │  RemoveMealItem(i): its rows
 *   │      │                  └─────┘     DeleteMeal(id)  └────────────────┘
 *   └──────┘ ◀── CloseSheet, AddMeal(id), Close: from each of them
 *
 *   A save that would break a limit (no name, nothing picked, no item left, more than MAX_MEALS) does nothing.
 *   A meal that is gone (a Reset, a backup) closes the sheet that edits it. Nothing of this sheet changes an entry.
 */
sealed interface MealSheet {
    /** A new meal from the entries of the day shown: [picked] are the entry ids ticked, in the order of the day. */
    data class Pick(val picked: List<String>) : MealSheet

    /** All the meals, each to change. */
    data object All : MealSheet

    /** Meal [id] being changed: its items as saved, each with the amount it will have. Saved by SaveMeal(name). */
    data class Edit(val id: String, val rows: List<MealRow>) : MealSheet
}

/**
 * An item of a meal being changed: [saved] as the meal holds it, and the [amount] it is set to (null for a quick item).
 * Its numbers are worked out from [saved] each time, so stepping the amount up and down never drifts.
 */
data class MealRow(val saved: MealItem, val amount: Double?)

/**
 * Looking up a barcode. A food the phone knows by its barcode (found before, or the lifter's own) needs no
 * network: its add sheet opens at once. [lookupAfter] is the one place it changes.
 *
 *   Scanned(code), LookUp(code)          Looked(code, Found)
 *   ─────────────────────────▶ Looking ─────────────────────────▶ the add sheet, the product kept
 *            known: the add sheet │      Looked(code, NotFound or NoKcal)
 *                                 │ ───────────────────────────▶ the own food form with the barcode and the name
 *                                 │      Looked(code, Offline or Busy)
 *                                 └────────────────────────────▶ Failed ── Retry ──▶ Looking
 *   An answer for another barcode, or after the sheet went, changes nothing.
 *   Over the rate limit, a start or a Retry is Failed(BUSY) without a request.
 */
sealed interface Lookup {
    val barcode: String

    data class Looking(override val barcode: String) : Lookup

    data class Failed(override val barcode: String, val why: Failure) : Lookup
}

/** Why a request to Open Food Facts gave nothing: no network, or it asked to wait. */
enum class Failure { OFFLINE, BUSY }

/**
 * The online part of the search on the add screen. One request per submit, never per keystroke. [onlineAfter] is the one
 * place it changes.
 *
 *   ┌──────┐ SubmitSearch(q) ┌──────────────┐ Searched(q, Found)  ┌──────────────────┐
 *   │ Idle │ ──────────────▶ │ Searching(q) │ ──────────────────▶ │ Results(q, foods)│
 *   └──────┘                 └──────────────┘ ───────┐            └──────────────────┘
 *      ▲  Results and Failed take another submit       │ Searched(q, Offline or Busy)
 *      │                                              ▼
 *      └──── (the add screen opened again) ─── Failed(q, why)
 *   A submit of the query being searched, of less than two letters, or an answer for another query: nothing.
 *   Over the rate limit a submit is Failed(q, BUSY) without a request.
 */
sealed interface Online {
    data object Idle : Online

    data class Searching(val query: String) : Online

    /** What was found, best first. A hit without its numbers is looked up by its barcode when it is picked. */
    data class Results(val query: String, val hits: List<SearchHit>) : Online

    data class Failed(val query: String, val why: Failure) : Online
}

/** Why the scanner gave no barcode. */
enum class ScanFailure { CANCELLED, INSTALLING, UNAVAILABLE, FAILED }

/** Undo of a meal, a copied day or a logged plate: the entries it logged, by id. */
typealias AddedEntries = List<String>

/** An entry deleted from the log at [index]: Undo puts it back there. */
data class DeletedFoodEntry(val entry: FoodEntry, val index: Int)

/** The tag of the food snackbars, one at a time. */
const val FOOD_SNACKBAR = "food"

/** How long a food snackbar offers Undo: a phone in one hand in a kitchen needs a moment to see it. */
const val FOOD_UNDO_MS = 8_000L
