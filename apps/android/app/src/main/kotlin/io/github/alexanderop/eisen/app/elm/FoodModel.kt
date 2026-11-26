package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry

/**
 * The Food screen: a day of what was eaten, over the tabs like the workout, with Back. [day] is the day it
 * shows (`2026-11-26`), null while it is closed. [sheet] is the one sheet over it. [lookups] and [searches]
 * are when the last requests to Open Food Facts went out, within the last minute, to keep its rate limits.
 * Nothing of it is saved: after a restart the screen and a lookup that was going are gone, the log is not.
 */
data class FoodModel(
    val day: String? = null,
    val sheet: FoodSheet? = null,
    val lookups: List<Long> = emptyList(),
    val searches: List<Long> = emptyList(),
)

/**
 * The one sheet over the Food screen. None is null. Every change of it is made in `food` (UpdateFood.kt).
 *
 *                OpenSearch, OpenQuick, OpenGoal, OpenCredit, TypeBarcode, NewOwn, OpenEntry
 *   ┌──────┐ ───────────────────────────────────────────────────────────────▶ that sheet
 *   │ none │ ◀─────────────── CloseSheet, ConfirmAmount, SaveQuick, SaveGoal, DeleteOwn, AddAgain, Close
 *   └──────┘
 *   Search ── Pick(id) ──▶ Amount          Own ── SaveOwn ──▶ Amount of the food saved
 *   Amount of an own food ── EditOwn ──▶ Own(id)
 *   Scanned, LookUp, Retry: the lookup, see [Lookup]
 */
sealed interface FoodSheet {
    /**
     * The add sheet of [food] with [amount] chosen, in its unit. [entryId]: the entry whose amount it changes,
     * null for a new one.
     */
    data class Amount(val food: Food, val amount: Double, val entryId: String? = null) : FoodSheet

    /** Quick kcal for a meal out: new, or the quick entry [entryId] changed. */
    data class Quick(val entryId: String? = null) : FoodSheet

    /**
     * The form of an own food. [id]: the food it edits, null for a new one. [barcode] and [name]: filled in from a
     * lookup that found nothing to log. [note]: what the form says above it, why it opened.
     */
    data class Own(val id: String? = null, val barcode: String? = null, val name: String = "", val note: String? = null) : FoodSheet

    data object Goal : FoodSheet

    /** Typing a barcode, from the search or after the scanner gave none; [note] says why. */
    data class Barcode(val note: String? = null) : FoodSheet

    /** A barcode being looked up at Open Food Facts. */
    data class LookingUp(val lookup: Lookup) : FoodSheet

    /** The search: [query] filters what the phone knows at once, [online] is what Open Food Facts found for a submitted one. */
    data class Search(val query: String = "", val online: Online = Online.Idle) : FoodSheet

    /** Where the food data comes from, and what leaves the phone. */
    data object Credit : FoodSheet
}

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
 * The online part of the search. One request per submit, never per keystroke. [onlineAfter] is the one place it changes.
 *
 *   ┌──────┐ SubmitSearch(q) ┌──────────────┐ Searched(q, Found)  ┌──────────────────┐
 *   │ Idle │ ──────────────▶ │ Searching(q) │ ──────────────────▶ │ Results(q, foods)│
 *   └──────┘                 └──────────────┘ ───────┐            └──────────────────┘
 *      ▲  Results and Failed take another submit       │ Searched(q, Offline or Busy)
 *      │                                              ▼
 *      └──── (a new search sheet) ─────────── Failed(q, why)
 *   A submit of the query being searched, of less than two letters, or an answer for another query: nothing.
 *   Over the rate limit a submit is Failed(q, BUSY) without a request.
 */
sealed interface Online {
    data object Idle : Online

    data class Searching(val query: String) : Online

    data class Results(val query: String, val foods: List<Food>) : Online

    data class Failed(val query: String, val why: Failure) : Online
}

/** Why the scanner gave no barcode. */
enum class ScanFailure { CANCELLED, UNAVAILABLE, FAILED }

/** An entry deleted from the log at [index]: Undo puts it back there. */
data class DeletedFoodEntry(val entry: FoodEntry, val index: Int)

/** The tag of the food snackbars, one at a time. */
const val FOOD_SNACKBAR = "food"

/** How long a food snackbar offers Undo: a phone in one hand in a kitchen needs a moment to see it. */
const val FOOD_UNDO_MS = 8_000L
