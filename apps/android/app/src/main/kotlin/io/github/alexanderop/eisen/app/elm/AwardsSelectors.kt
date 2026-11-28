package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.core.convert
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.core.formatNumber
import io.github.alexanderop.eisen.core.formatWeight
import io.github.alexanderop.eisen.core.holdText
import io.github.alexanderop.eisen.feature.awards.Awards
import io.github.alexanderop.eisen.feature.awards.FOOD_STREAK_DAYS
import io.github.alexanderop.eisen.feature.awards.Medal
import io.github.alexanderop.eisen.feature.awards.MedalGroup
import io.github.alexanderop.eisen.feature.awards.PERFECT_WEEK
import io.github.alexanderop.eisen.feature.awards.SQUAT_IDS
import io.github.alexanderop.eisen.feature.awards.STREAK_WEEKS
import io.github.alexanderop.eisen.feature.awards.SavedAwards
import io.github.alexanderop.eisen.feature.awards.TONNE_MILESTONES
import io.github.alexanderop.eisen.feature.awards.Tier
import io.github.alexanderop.eisen.feature.awards.WORKOUT_MILESTONES
import io.github.alexanderop.eisen.feature.awards.Xp
import io.github.alexanderop.eisen.feature.awards.levelOf
import io.github.alexanderop.eisen.feature.awards.recordTier
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.Outcome
import io.github.alexanderop.eisen.feature.workout.RecordEvent
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.feature.workout.longestHold
import io.github.alexanderop.eisen.feature.workout.outcome
import io.github.alexanderop.eisen.feature.workout.recordEvents
import io.github.alexanderop.eisen.feature.workout.recordIds
import io.github.alexanderop.eisen.feature.workout.totalReps
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * The awards, worked out from the saved history each time: the XP of everything done, the level it makes, and every
 * medal of the case with how far it is. The numbers they use are in `feature/awards/Awards.kt`.
 */

/** The awards of [model]: its saved state, in its time zone, as of its clock (which only moves the "7 of 12"s). */
fun awards(model: Model): Awards = awardsOf(model.state, model.now, model.zone)

fun awardsOf(state: AppState, now: Long, zone: ZoneId): Awards {
    val records = recordEvents(state.history)
    return Awards(xpOf(state, records.size), levelOf(xpOf(state, records.size)), medalsOf(state, records, now, zone))
}

/** The XP of everything in [state], with its number of [records] (see [recordEvents]). */
fun xpOf(state: AppState, records: Int = recordEvents(state.history).size): Long {
    val sets = state.history.sumOf { workout -> workout.exercises.sumOf { log -> log.sets.count { it != null && it > 0 } } }
    return state.history.size.toLong() * Xp.WORKOUT +
        sets.toLong() * Xp.SET +
        records.toLong() * Xp.RECORD +
        state.timerSessions.size.toLong() * Xp.TIMER +
        state.foodLog.map { it.day }.distinct().size.toLong() * Xp.FOOD_DAY +
        state.weights.size.toLong() * Xp.WEIGHT
}

/** The ids of the earned medals of [state]: what the case marks new until the lifter has seen them. */
fun earnedMedalIds(state: AppState, zone: ZoneId): Set<String> =
    awardsOf(state, 0, zone).earned.map { it.id }.toSet()

/**
 * What saving a workout brought: the state [before] it was saved and [after]. The medals it shows are the earned ones
 * the lifter has not seen yet, which are seen from then on, and the record medal of every lift that set a new record in
 * it: the medal was seen before, its new best was not.
 */
fun savedAwards(before: AppState, after: AppState, now: Long, zone: ZoneId): SavedAwards {
    val was = awardsOf(before, now, zone)
    val got = awardsOf(after, now, zone)
    val seen = after.seenMedals.toSet()
    val beaten = recordIds(after.history, 0).map { "record:$it" }.toSet()
    val medals = got.earned.filter { it.id !in seen || it.id in beaten }.sortedBy { it.id !in beaten }
    return SavedAwards(got.xp - was.xp, was.level, got.level, medals, beaten)
}

/** The state with every earned medal marked seen. The same state when nothing is new. */
fun withMedalsSeen(state: AppState, earned: Set<String>): AppState {
    val fresh = earned - state.seenMedals.toSet()
    return if (fresh.isEmpty()) state else state.copy(seenMedals = state.seenMedals + fresh.sorted())
}

/**
 * The exercises of the running workout that hold a record as logged so far: the ones the medals count once it is
 * saved. A lift counts once every set is done at a weight heavier than ever, bodyweight work once its reps are more.
 */
fun liveRecords(model: Model): Set<String> {
    val active = model.state.active ?: return emptySet()
    return recordIds(listOf(active) + model.state.history, 0)
}

/** The medal case shows the Medals view of Progress. */
fun showsMedals(model: Model): Boolean =
    model.nav.tab == Tab.HISTORY && model.nav.history == HistoryView.MEDALS && !showsWorkout(model) && model.timer == null

// The medals

private fun medalsOf(state: AppState, records: List<RecordEvent>, now: Long, zone: ZoneId): List<Medal> =
    recordMedals(state, records) + consistencyMedals(state, now, zone) + milestoneMedals(state, zone)

private fun time(workout: Workout): Long = workout.finishedAt ?: workout.startedAt

/** What a record of [log] is, as the case names it: "Hack Squat 145kg", "Dips 35 reps", "Plank 1:30". */
private fun recordText(log: ExerciseLog, unit: WeightUnit): String = when {
    log.equipment != Equipment.BODYWEIGHT -> formatWeight(log.weight, unit)
    log.timed -> holdText(longestHold(log))
    else -> counted(totalReps(log), "rep")
}

/** One record medal per lift of the current workouts, then any other exercise that holds a record. */
private fun recordMedals(state: AppState, records: List<RecordEvent>): List<Medal> {
    val byId = records.groupBy { it.log.id }
    val ids = LinkedHashSet<String>()
    for (routine in state.routines) for (slot in routine.slots) ids += slot.exerciseId
    for (event in records) ids += event.log.id
    return ids.mapNotNull { id ->
        val events = byId[id].orEmpty()
        val exercise = state.findExercise(id)
        val name = exercise?.name ?: events.lastOrNull()?.log?.name ?: return@mapNotNull null
        val best = events.lastOrNull()
        val bodyweight = (best?.log?.equipment ?: exercise?.equipment) == Equipment.BODYWEIGHT
        val timed = best?.log?.timed ?: exercise?.timed ?: false
        if (best == null) {
            val how = when {
                timed -> "Hold $name longer than ever before."
                bodyweight -> "Do more reps of $name in all than ever before."
                else -> "Do every set of $name at a heavier weight than ever before."
            }
            Medal("record:$id", MedalGroup.RECORDS, Tier.BRONZE, "PR", name, null, 0f, "No record yet", how)
        } else {
            val value = recordText(best.log, best.workout.unit)
            val what = when {
                timed -> "A hold of $value, longer than ever before."
                bodyweight -> "$value in all, more than ever before."
                else -> "Every set done at $value, heavier than ever before."
            }
            Medal(
                "record:$id", MedalGroup.RECORDS, recordTier(events.size), "PR", "$name $value", time(best.workout), 1f, "",
                "$what ${counted(events.size, "record")} of $name so far.",
            )
        }
    }
}

private fun dayOf(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

private fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * The runs of [units] in a row (weeks or days, by [step]): for each length reached, the first unit it was reached on.
 * [first] gives what was done first in a unit, so a medal is dated by the training that earned it.
 */
private fun runs(units: Set<LocalDate>, step: (LocalDate) -> LocalDate): Map<Int, LocalDate> {
    val reached = HashMap<Int, LocalDate>()
    var run = 0
    var last: LocalDate? = null
    for (unit in units.sorted()) {
        run = if (last != null && step(last) == unit) run + 1 else 1
        reached.putIfAbsent(run, unit)
        last = unit
    }
    return reached
}

/** The run that goes on now: up to the current unit, or the one before while the current one has nothing yet. */
private fun current(units: Set<LocalDate>, now: LocalDate, back: (LocalDate) -> LocalDate): Int {
    var unit = if (now in units) now else back(now)
    var run = 0
    while (unit in units) {
        run++
        unit = back(unit)
    }
    return run
}

private fun progress(done: Int, goal: Int): Float = (done.toFloat() / goal).coerceIn(0f, 1f)

private fun consistencyMedals(state: AppState, now: Long, zone: ZoneId): List<Medal> {
    val trainings = (state.history.map { it.startedAt } + state.timerSessions.map { it.startedAt }).sorted()
    val firstOfWeek = LinkedHashMap<LocalDate, Long>()
    for (at in trainings) firstOfWeek.putIfAbsent(mondayOf(dayOf(at, zone)), at)
    val weeks = firstOfWeek.keys
    val weekRuns = runs(weeks) { it.plusWeeks(1) }
    val thisWeek = mondayOf(dayOf(now, zone))
    val streak = current(weeks, thisWeek) { it.minusWeeks(1) }
    val streakMedals = STREAK_WEEKS.map { (count, tier) ->
        val week = weekRuns[count]
        val earned = week?.let { firstOfWeek[it] }
        Medal(
            "weeks:$count", MedalGroup.CONSISTENCY, tier, "${count}W", "$count weeks in a row", earned, progress(streak, count),
            "${minOf(streak, count)} of $count",
            "A workout or an interval timer every week, Monday to Sunday, $count weeks in a row.",
        )
    }

    // A perfect week: the third workout of a week, Monday to Sunday. Workouts of the program only.
    val workoutsByWeek = state.history.map { time(it) }.sorted().groupBy { mondayOf(dayOf(it, zone)) }
    val perfect = workoutsByWeek.entries.filter { it.value.size >= PERFECT_WEEK }.minByOrNull { it.key }?.value?.get(PERFECT_WEEK - 1)
    val doneThisWeek = workoutsByWeek[thisWeek]?.size ?: 0
    val perfectMedal = Medal(
        "perfect-week", MedalGroup.CONSISTENCY, Tier.GOLD, "$PERFECT_WEEK/$PERFECT_WEEK", "Perfect week", perfect,
        progress(doneThisWeek, PERFECT_WEEK), "${minOf(doneThisWeek, PERFECT_WEEK)} of $PERFECT_WEEK this week",
        "$PERFECT_WEEK workouts in one week, Monday to Sunday: what a week of 5×5 asks for.",
    )

    val firstOfDay = LinkedHashMap<LocalDate, Long>()
    for (entry in state.foodLog.sortedBy { it.at }) {
        val day = runCatching { LocalDate.parse(entry.day) }.getOrNull() ?: continue
        firstOfDay.putIfAbsent(day, entry.at)
    }
    val days = firstOfDay.keys
    val dayRuns = runs(days) { it.plusDays(1) }
    val foodRun = current(days, dayOf(now, zone)) { it.minusDays(1) }
    val foodMedal = Medal(
        "food:$FOOD_STREAK_DAYS", MedalGroup.CONSISTENCY, Tier.BRONZE, "${FOOD_STREAK_DAYS}d", "Food logged $FOOD_STREAK_DAYS days",
        dayRuns[FOOD_STREAK_DAYS]?.let { firstOfDay[it] }, progress(foodRun, FOOD_STREAK_DAYS), "${minOf(foodRun, FOOD_STREAK_DAYS)} of $FOOD_STREAK_DAYS",
        "Log what you eat $FOOD_STREAK_DAYS days in a row.",
    )
    return streakMedals + perfectMedal + foodMedal
}

/** Kilograms lifted in [workout]: weight × reps of every logged set with a weight. A hold or plain bodyweight adds none. */
private fun kilosOf(workout: Workout): Double = workout.exercises.sumOf { log ->
    if (log.equipment == Equipment.BODYWEIGHT || log.timed) 0.0 else convert(log.weight, workout.unit, WeightUnit.KG) * log.sets.sumOf { it ?: 0 }
}

/** "3.1" under ten tonnes, whole tonnes from there on, never rounded up past what was lifted. */
private fun tonnesText(tonnes: Double): String =
    if (tonnes < 10) formatNumber(Math.floor(tonnes * 10) / 10) else formatNumber(Math.floor(tonnes))

private fun milestoneMedals(state: AppState, zone: ZoneId): List<Medal> {
    val oldest = state.history.sortedBy { time(it) }
    val count = oldest.size
    val workouts = WORKOUT_MILESTONES.map { (goal, tier) ->
        Medal(
            "workouts:$goal", MedalGroup.MILESTONES, tier, "$goal", counted(goal, "workout"), oldest.getOrNull(goal - 1)?.let(::time),
            progress(count, goal), "${minOf(count, goal)} of $goal", "Finish ${counted(goal, "workout")}.",
        )
    }

    var kilos = 0.0
    val crossed = HashMap<Int, Long>()
    for (workout in oldest) {
        kilos += kilosOf(workout)
        for ((goal, _) in TONNE_MILESTONES) if (kilos >= goal * 1000.0) crossed.putIfAbsent(goal, time(workout))
    }
    val tonnes = TONNE_MILESTONES.map { (goal, tier) ->
        Medal(
            "tonnes:$goal", MedalGroup.MILESTONES, tier, "${goal}t", "$goal tonnes lifted", crossed[goal],
            (kilos / (goal * 1000.0)).toFloat().coerceIn(0f, 1f), "${tonnesText(minOf(kilos / 1000.0, goal.toDouble()))} of $goal t",
            "Lift $goal tonnes in all: the weight times the reps of every set.",
        )
    }
    return workouts + tonnes + listOfNotNull(bodyweightSquat(state, oldest, zone))
}

/**
 * A squat with every set done at the lifter's own weight or more: the weight logged last on or before that day (the
 * first one when the squat came before any). Not in the case before a weight is logged: there is nothing to compare.
 */
private fun bodyweightSquat(state: AppState, oldest: List<Workout>, zone: ZoneId): Medal? {
    val weights = state.weights.sortedBy { it.day }
    val latest = weights.lastOrNull() ?: return null
    val unit = state.settings.unit
    var earned: Long? = null
    var best = 0.0
    for (workout in oldest) {
        val day = dayOf(workout.startedAt, zone).toString()
        val body = (weights.lastOrNull { it.day <= day } ?: weights.first()).kg
        for (log in workout.exercises) {
            if (log.id !in SQUAT_IDS || outcome(log) != Outcome.SUCCESS) continue
            val kilos = convert(log.weight, workout.unit, WeightUnit.KG)
            best = maxOf(best, kilos)
            if (earned == null && kilos >= body) earned = time(workout)
        }
    }
    fun shown(kilos: Double) = formatWeight(convert(kilos, WeightUnit.KG, unit), unit)
    return Medal(
        "bodyweight-squat", MedalGroup.MILESTONES, Tier.IRON, "1×", "Bodyweight squat", earned,
        (best / latest.kg).toFloat().coerceIn(0f, 1f), if (best > 0) "${shown(best)} of ${shown(latest.kg)}" else "No squat yet",
        "Every set of a squat (Squat, Front Squat or Hack Squat) with at least your own weight on it.",
    )
}
