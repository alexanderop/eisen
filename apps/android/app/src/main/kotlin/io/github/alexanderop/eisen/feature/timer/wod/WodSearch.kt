package io.github.alexanderop.eisen.feature.timer.wod

import java.text.Normalizer
import io.github.alexanderop.eisen.core.Exercise

/*
 * The port of the PWA's `domain/wodSearch.ts`: searching the WODs page and what it suggests while the
 * lifter types. `WodSearchTest` checks it with the same inputs as `wodSearch.test.ts`.
 */

private val ACCENTS = Regex("[̀-ͯ]")
private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")

/** Lower case, accents and punctuation gone, single spaces: what a search compares. */
fun searchText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase().replace(NOT_ALPHANUMERIC, " ").trim()

/**
 * How well [text] answers [query], both already through [searchText]:
 * 0 it starts with it, 1 one of its words starts with it, 2 it is somewhere inside. Null: not at all.
 * A query of several words matches when every word does; the weakest word decides.
 */
fun matchRank(text: String, query: String): Int? {
    if (query.isEmpty()) return null
    if (text.startsWith(query)) return 0
    var worst = 0
    for (word in query.split(" ")) {
        val rank = when {
            text.split(" ").any { it.startsWith(word) } -> 1
            word in text -> 2
            else -> return null
        }
        worst = maxOf(worst, rank)
    }
    return worst
}

/** The exercises a WOD uses, once each, in its order. Deleted exercises are left out. */
private fun movements(workout: Wod, find: FindExercise): List<Exercise> =
    workout.items.mapNotNull { find(it.exerciseId) }.distinctBy { it.id }

/**
 * Where a WOD answers the query: its name first (0 to 2), then its format ("amrap", "21 15 9": 3),
 * then one of its movements (4 word start, 5 inside). Null when nothing matches.
 */
private fun wodRank(workout: Wod, query: String, find: FindExercise): Int? {
    matchRank(searchText(wodName(workout)), query)?.let { return it }
    if (matchRank(searchText(wodFormat(workout)), query) != null) return 3
    return movements(workout, find).mapNotNull { exercise ->
        matchRank(searchText(exercise.name), query)?.let { if (it <= 1) 4 else 5 }
    }.minOrNull()
}

/**
 * The WODs that answer a typed query, best first; ties keep the order given.
 * An empty query answers with all of them.
 */
fun searchWods(wods: List<Wod>, typed: String, find: FindExercise): List<Wod> {
    val query = searchText(typed)
    if (query.isEmpty()) return wods
    return wods.mapNotNull { workout -> wodRank(workout, query, find)?.let { workout to it } }
        .sortedBy { it.second }
        .map { it.first }
}

enum class SuggestionKind { WOD, MOVEMENT }

/** One line under the search field: a WOD to open, or a movement to search for. */
data class WodSuggestion(val kind: SuggestionKind, val id: String, val text: String, val detail: String)

const val MAX_SUGGESTIONS = 6

/**
 * What to offer while the lifter types: WODs whose name matches, then movements that match with
 * how many WODs use each. Only matches at the start of a word are offered: one typed letter that
 * is somewhere inside a name is no suggestion. Best first, at most [MAX_SUGGESTIONS].
 */
fun suggestWods(wods: List<Wod>, typed: String, find: FindExercise): List<WodSuggestion> {
    val query = searchText(typed)
    if (query.isEmpty()) return emptyList()
    val named = wods.mapNotNull { workout -> matchRank(searchText(wodName(workout)), query)?.takeIf { it <= 1 }?.let { workout to it } }
        .sortedBy { it.second }
        .map { (workout) -> WodSuggestion(SuggestionKind.WOD, workout.id, wodName(workout), wodFormat(workout)) }

    val used = LinkedHashMap<String, Pair<Exercise, Int>>()
    for (workout in wods) {
        for (exercise in movements(workout, find)) used[exercise.id] = exercise to (used[exercise.id]?.second ?: 0) + 1
    }
    val moves = used.values.mapNotNull { (exercise, count) ->
        matchRank(searchText(exercise.name), query)?.takeIf { it <= 1 }?.let { Triple(exercise, count, it) }
    }
        .sortedWith(compareBy<Triple<Exercise, Int, Int>> { it.third }.thenByDescending { it.second })
        .map { (exercise, count) -> WodSuggestion(SuggestionKind.MOVEMENT, exercise.id, exercise.name, "$count ${if (count == 1) "WOD" else "WODs"}") }
    return (named + moves).take(MAX_SUGGESTIONS)
}
