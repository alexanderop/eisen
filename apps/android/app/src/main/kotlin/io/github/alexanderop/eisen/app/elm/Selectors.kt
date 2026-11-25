package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.core.CATALOG
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.ExerciseProgress
import io.github.alexanderop.eisen.feature.workout.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.feature.workout.Routine
import io.github.alexanderop.eisen.app.state.Settings
import io.github.alexanderop.eisen.feature.timer.wod.FindExercise
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.feature.timer.wod.WOD_LIBRARY
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.timer.wod.Wod
import io.github.alexanderop.eisen.feature.timer.wod.findWod
import io.github.alexanderop.eisen.feature.timer.wod.isBodyweightWod
import io.github.alexanderop.eisen.feature.timer.wod.isWodRecord
import io.github.alexanderop.eisen.feature.timer.wod.wodLines
import io.github.alexanderop.eisen.feature.timer.wod.WodItem
import io.github.alexanderop.eisen.feature.timer.wod.WodUnit
import io.github.alexanderop.eisen.core.holdText
import io.github.alexanderop.eisen.feature.timer.wod.distanceText
import io.github.alexanderop.eisen.core.formatWeight
import io.github.alexanderop.eisen.feature.timer.wod.loadIn
import io.github.alexanderop.eisen.feature.workout.canRate
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.feature.workout.startProgress
import io.github.alexanderop.eisen.feature.timer.wod.WodSuggestion
import io.github.alexanderop.eisen.core.motionOf
import io.github.alexanderop.eisen.core.REST_EXERCISE
import io.github.alexanderop.eisen.feature.timer.wod.searchWods
import io.github.alexanderop.eisen.feature.timer.wod.suggestWods
import java.time.Instant
import java.time.ZoneOffset

/*
 * What screens read from the model besides its fields. Pure, so a screen and a test see the same.
 */

val Model.settings: Settings get() = state.settings

/** The built-in exercises followed by the lifter's own. */
fun exercises(model: Model): List<Exercise> = CATALOG + model.state.customExercises

fun findExercise(model: Model, id: String): Exercise? = model.state.findExercise(id)

/** The exercise a name stands for, the way [Msg.Exercises.AddCustom] matches it. Null for an empty name. */
fun exerciseNamed(model: Model, name: String): Exercise? {
    val trimmed = name.trim().take(EXERCISE_NAME_LENGTH)
    if (trimmed.isEmpty()) return null
    return exercises(model).firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
}

private val NO_PROGRESS = ExerciseProgress(weight = 0.0, fails = 0, increment = 0.0)

/** The working weight of an exercise, or its starting weight when it was never used. 0 for plain bodyweight. */
fun progressOf(model: Model, id: String): ExerciseProgress = progressOf(model.state, id)

fun progressOf(state: AppState, id: String): ExerciseProgress {
    state.progress[id]?.let { return it }
    val exercise = state.findExercise(id)
    if (exercise == null || exercise.equipment == Equipment.BODYWEIGHT) return NO_PROGRESS
    return startProgress(exercise, state.settings.unit)
}

/**
 * The routine the home screen offers next. There is always one: a fresh state has two,
 * a backup without any is refused, and the last one cannot be removed.
 */
fun nextRoutine(model: Model): Routine =
    model.state.routines.firstOrNull { it.id == model.state.nextRoutineId } ?: model.state.routines[0]

/** The workout screen shows instead of the tabs. A hold timer is drawn on it, so it only exists while this is true. */
fun showsWorkout(model: Model): Boolean = model.nav.workoutOpen && model.state.active != null

/** The routine open in the Workouts tab's editor, if it still exists. */
fun openRoutine(model: Model): Routine? = model.nav.routine?.let { id -> model.state.routines.firstOrNull { it.id == id } }

/** The rest screen covers the workout screen. A minimised one leaves the rest bar showing. */
fun showsRestScreen(model: Model): Boolean = showsWorkout(model) && model.restScreen.let { it != null && it !is RestScreen.Minimised }

/** The rest screen shows the reps in reserve chips for set [set] of [log]. */
fun ratesSet(settings: Settings, log: ExerciseLog, set: Int): Boolean = settings.trackRir && canRate(log, set)

/**
 * True when [Msg.Nav.Back] does something: it asks before it ends an interval timer, closes a hold timer
 * before the workout. Otherwise the back gesture leaves the app.
 */
fun handlesBack(model: Model): Boolean =
    model.timer != null || showsWorkout(model) || (model.nav.tab == Tab.WORKOUTS && openRoutine(model) != null) || model.nav.tab != Tab.HOME

/** A workout is running or waiting for an answer. Settings that would split its log are locked meanwhile. */
fun workoutRunning(model: Model): Boolean = model.session.active != null

/** The whole state as JSON. The PWA reads the same file. */
fun exportJson(model: Model): String = encodeState(model.state, pretty = true)

/** The same name the PWA gives its download, so backups from both apps sort together. */
fun backupFileName(now: Long): String = "eisen-backup-${Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()}.json"

/** Seconds left of [rest] at [now], rounded up. 0 once it is over. */
fun restSecondsLeft(rest: RestState, now: Long): Int = maxOf(0, Math.ceil((rest.endsAt - now) / 1000.0).toInt())

/**
 * The countdown notification of [rest], posted at [now]: when it starts, and again when it is moved.
 * The chip in the status bar is its chronometer; the bar is one segment, the tracker at the time gone.
 */
fun restCountdown(rest: RestState, now: Long): RestCountdown {
    val total = ((rest.endsAt - rest.startedAt) / 1000).toInt().coerceAtLeast(1)
    val gone = ((now - rest.startedAt) / 1000).toInt().coerceIn(0, total)
    return RestCountdown(
        title = "Rest",
        text = rest.note,
        endsAt = rest.endsAt,
        extendLabel = "+${EXTEND_SECONDS}s",
        skipLabel = "Skip",
        progress = NoticeProgress(listOf(NoticeSegment(total, NoticeTone.REST)), gone),
    )
}

fun restOver(rest: RestState) = RestOver(title = "Rest is over", text = "Start your next set.", endedAt = rest.endsAt)

/** "Get ready" until the hold starts, then the hold. */
enum class HoldPhase { LEAD_IN, HOLD }

fun holdPhase(hold: HoldTimer, now: Long): HoldPhase = if (now < hold.startsAt) HoldPhase.LEAD_IN else HoldPhase.HOLD

/** When the hold ends by itself: at its target, or at [MAX_HELD_SECONDS] when it is held as long as possible. */
fun holdEndsAt(hold: HoldTimer): Long = hold.startsAt + (hold.target ?: MAX_HELD_SECONDS) * 1000L

/** Whole seconds held at [now], never more than the hold can log. */
fun heldSeconds(hold: HoldTimer, now: Long): Int =
    ((now - hold.startsAt) / 1000).coerceIn(0, (hold.target ?: MAX_HELD_SECONDS).toLong()).toInt()

/** The big number of the timer: seconds of "Get ready" left, seconds of the countdown left, or seconds held. */
fun holdSecondsShown(hold: HoldTimer, now: Long): Int = when {
    now < hold.startsAt -> Math.ceil((hold.startsAt - now) / 1000.0).toInt()
    hold.target != null -> maxOf(0, Math.ceil((holdEndsAt(hold) - now) / 1000.0).toInt())
    else -> heldSeconds(hold, now)
}

// WODs

/** How the screens find the exercises of a WOD: built-in and own. */
fun wodFind(model: Model): FindExercise = { id -> model.state.findExercise(id) }

/** The WOD whose detail shows, if it still exists. */
fun openWod(model: Model): Wod? = model.wods.detail?.let { findWod(model.state.wods, it) }

/** True when the draft in the builder edits one of the lifter's own WODs, false for a new one. */
fun draftEditsOwn(model: Model, draft: WodDraft): Boolean = model.state.wods.any { it.id == draft.wod.id }

/** The WODs of [wods] the chip lets through. */
private fun chipped(model: Model, wods: List<Wod>): List<Wod> {
    val find = wodFind(model)
    return wods.filter { wod ->
        when (model.wods.filter) {
            WodFilter.ALL -> true
            WodFilter.NO_WEIGHTS -> isBodyweightWod(wod, find)
            WodFilter.FOR_TIME -> wod.timer.mode == TimerMode.FOR_TIME
            WodFilter.AMRAP -> wod.timer.mode == TimerMode.AMRAP
        }
    }
}

/** The benchmarks the chip lets through. */
fun benchmarks(model: Model): List<Wod> = chipped(model, WOD_LIBRARY)

/** The lifter's own WODs the chip lets through: the chips sit over the whole page. */
fun ownWods(model: Model): List<Wod> = chipped(model, model.state.wods)

/** True while the WODs page shows what the search found instead of its sections. */
fun searching(model: Model): Boolean = model.wods.query.isNotBlank()

/**
 * What the search finds, the lifter's own and the benchmarks together, best first, through the chip
 * as well. Every WOD while nothing is typed.
 */
fun searchedWods(model: Model): List<Wod> = searchWods(chipped(model, model.state.wods + WOD_LIBRARY), model.wods.query, wodFind(model))

/** The list under the search field, over the same WODs as the results, through the chip: empty while it is closed. */
fun wodSuggestions(model: Model): List<WodSuggestion> =
    if (!model.wods.suggesting) emptyList() else suggestWods(chipped(model, model.state.wods + WOD_LIBRARY), model.wods.query, wodFind(model))

/** What the page says when the search found nothing: "No WODs match “fra”", with the chip when one is on. */
fun noMatchText(model: Model): String {
    val typed = "No WODs match \u201c${model.wods.query.trim()}\u201d"
    return if (model.wods.filter == WodFilter.ALL) typed else "$typed in ${model.wods.filter.label}"
}

/** Most figures a banner shows: more would get too small to read on a phone. */
const val MAX_BANNER_FIGURES = 4

/**
 * The motion strips of a WOD's banner: its movements in order, each once, those that have a
 * strip, at most [MAX_BANNER_FIGURES]. Two exercises that share a strip show it once.
 * Empty when none has one: the card keeps its still tile.
 */
fun bannerMotions(workout: Wod): List<String> =
    workout.items.mapNotNull { motionOf(it.exerciseId) }.distinct().take(MAX_BANNER_FIGURES)

/**
 * The slots of a WOD's banner: its movements in order, each once and without the rests, at most
 * [MAX_BANNER_FIGURES]. One whose figure an earlier one already shows is left out; one without a
 * figure keeps its slot and shows its still picture.
 */
fun bannerSlots(workout: Wod): List<String> {
    val slots = mutableListOf<String>()
    val shown = mutableSetOf<String>()
    for (item in workout.items) {
        val id = item.exerciseId
        if (id == REST_EXERCISE || id in slots) continue
        val strip = motionOf(id)
        if (strip != null && !shown.add(strip)) continue
        slots += id
    }
    return slots.take(MAX_BANNER_FIGURES)
}

/**
 * A suggestion's text cut around what was typed, so that part can be bold: "Pu" in "Push-ups"
 * gives ("", "Pu", "sh-ups"). A match at the start of a word wins over one inside a word.
 * Without a match the whole text is the first part.
 */
fun typedParts(text: String, typed: String): Triple<String, String, String> {
    val needle = typed.trim().lowercase()
    val lower = text.lowercase()
    if (needle.isEmpty()) return Triple(text, "", "")
    var at = -1
    var index = lower.indexOf(needle)
    while (index != -1) {
        if (index == 0 || !lower[index - 1].let { it in 'a'..'z' || it in '0'..'9' }) {
            at = index
            break
        }
        if (at == -1) at = index
        index = lower.indexOf(needle, index + 1)
    }
    if (at == -1) return Triple(text, "", "")
    return Triple(text.substring(0, at), text.substring(at, at + needle.length), text.substring(at + needle.length))
}

/** The items whose exercise still exists, each with its line of [wodLines]. */
fun wodRows(wod: Wod, find: FindExercise, unit: WeightUnit): List<Pair<Exercise, String>> =
    wod.items.mapNotNull { find(it.exerciseId) }.zip(wodLines(wod, find, unit))

/** The runs a [session] is measured against: the ones before it. */
fun earlierRuns(sessions: List<TimerSession>, session: TimerSession): List<TimerSession> =
    sessions.filter { it.id != session.id && it.startedAt < session.startedAt }

/** True when [session] was a record when it was done, like a strength record: compared with earlier runs only. */
fun wasWodRecord(sessions: List<TimerSession>, session: TimerSession): Boolean = isWodRecord(earlierRuns(sessions, session), session)

/** The newest run of a WOD. */
fun lastWodRun(sessions: List<TimerSession>, wodId: String): TimerSession? =
    sessions.filter { it.wodId == wodId }.maxByOrNull { it.startedAt }

/** What a row of the builder says about an item: "21 reps · 43kg", "400 m", "30s", "Max", "by scheme". */
fun wodItemSummary(item: WodItem, wod: Wod, unit: WeightUnit): String {
    val amount = when {
        item.unit == WodUnit.REPS && wod.scheme.isNotEmpty() -> "by scheme"
        item.amount == 0 -> "Max"
        item.unit == WodUnit.REPS -> "${item.amount} ${if (item.amount == 1) "rep" else "reps"}"
        item.unit == WodUnit.SECONDS -> holdText(item.amount)
        else -> distanceText(item.amount)
    }
    val load = if (item.load > 0) " · " + formatWeight(loadIn(item.load, unit), unit) else ""
    return amount + load
}

/** The exercises the builder's picker starts on: the ones the benchmarks use, and the Conditioning group. */
val WOD_EXERCISES: Set<String> = WOD_LIBRARY.flatMap { wod -> wod.items.map { it.exerciseId } }.toSet() +
    CATALOG.filter { it.group == "Conditioning" }.map { it.id }

/**
 * How well [name] matches what was typed: 0 when it starts a word ("run" in "Run"), 1 inside a word
 * ("run" in "Cable Crunch"), null when it is not in the name. Hits that start a word are listed first.
 */
fun matchRank(name: String, typed: String): Int? {
    val needle = typed.trim().lowercase()
    val text = name.lowercase()
    if (needle.isEmpty()) return 0
    if (needle !in text) return null
    return if (Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(needle)).containsMatchIn(text)) 0 else 1
}

private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

/**
 * What the home screen counts: [total] trainings, [lastWeek] of them in the seven days before now, and when
 * the [last] one started. A finished interval timer is training like a workout. Null before the first one.
 */
data class TrainingStats(val total: Int, val lastWeek: Int, val last: Long)

fun trainingStats(state: AppState, now: Long): TrainingStats? {
    val times = state.history.map { it.startedAt } + state.timerSessions.map { it.startedAt }
    val last = times.maxOrNull() ?: return null
    return TrainingStats(times.size, times.count { now - it < WEEK_MILLIS }, last)
}
