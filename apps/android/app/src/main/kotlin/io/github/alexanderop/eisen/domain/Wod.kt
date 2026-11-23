package io.github.alexanderop.eisen.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/*
 * The port of the PWA's `domain/wod.ts`. Both apps must behave the same, and `WodTest` checks it with
 * the same inputs as `wod.test.ts`.
 */

/** What one line of a WOD counts: reps, seconds held or metres covered. */
@Serializable(with = WodUnitSerializer::class)
enum class WodUnit(val key: String) {
    REPS("reps"),
    SECONDS("seconds"),
    METERS("meters"),
}

val WOD_UNITS: List<WodUnit> = WodUnit.entries

/** A unit the app does not know, from a hand-edited backup, is read as reps, the way `wod()` brings it into its limits. */
internal object WodUnitSerializer : KSerializer<WodUnit> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("WodUnit", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: WodUnit) = encoder.encodeString(value.key)
    override fun deserialize(decoder: Decoder): WodUnit = decoder.decodeString().let { key -> WodUnit.entries.firstOrNull { it.key == key } } ?: WodUnit.REPS
}

@Serializable
data class WodItem(
    val exerciseId: String,
    /** How much of it, in [unit]. 0 = as many as possible. */
    val amount: Int,
    val unit: WodUnit,
    /** Weight in kg, whatever unit the app shows. 0 = none. */
    val load: Double,
)

/**
 * A workout of the day: a timer and what to do while it runs.
 * forTime: [rounds] rounds of the items, as fast as possible. With a [scheme] like 21-15-9 every
 * round has its own count for the items counted in reps, and there are as many rounds as numbers.
 * amrap: the items over and over until the time is up. The lifter counts rounds by tapping.
 * emom: all items at the start of every interval.
 * tabata: one item per round, taking turns.
 */
@Serializable
data class Wod(
    val id: String,
    /** Empty while the lifter has not named it: then it goes by its format, see [wodName]. */
    val name: String,
    val timer: TimerConfig,
    /** For Time only. 1 in the other modes. */
    val rounds: Int,
    /** For Time only: reps per round. Empty = every round as the items say. */
    val scheme: List<Int>,
    val items: List<WodItem>,
)

const val MAX_WOD_NAME = 40
const val MAX_WOD_ITEMS = 12
const val MAX_WOD_AMOUNT = 10000
const val MAX_WOD_LOAD = 500.0
const val MAX_WOD_ROUNDS = 60
const val MAX_SCHEME_LENGTH = 12
const val MAX_SCHEME_REPS = 999

/** Schemes the builder offers with one tap. */
val SCHEMES: List<List<Int>> = listOf(
    listOf(21, 15, 9),
    listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1),
    listOf(50, 40, 30, 20, 10),
    listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
)

/** A WOD from the builder, a backup or the library, brought into its limits. */
fun wod(draft: Wod): Wod {
    val timer = timerConfig(draft.timer)
    val forTime = timer.mode == TimerMode.FOR_TIME
    val scheme = if (forTime) draft.scheme.take(MAX_SCHEME_LENGTH).map { it.coerceIn(1, MAX_SCHEME_REPS) } else emptyList()
    return Wod(
        id = draft.id,
        name = draft.name.trim().take(MAX_WOD_NAME),
        timer = timer,
        rounds = when {
            !forTime -> 1
            scheme.isNotEmpty() -> scheme.size
            else -> draft.rounds.coerceIn(1, MAX_WOD_ROUNDS)
        },
        scheme = scheme,
        items = draft.items.take(MAX_WOD_ITEMS).map { item ->
            WodItem(
                exerciseId = item.exerciseId,
                amount = item.amount.coerceIn(0, MAX_WOD_AMOUNT),
                unit = item.unit,
                load = roundTo(item.load.coerceIn(0.0, MAX_WOD_LOAD), 0.5),
            )
        },
    )
}

/** Rounds the WOD has. Null in an AMRAP: there the lifter counts them. */
fun wodRounds(workout: Wod): Int? = when (workout.timer.mode) {
    TimerMode.AMRAP -> null
    TimerMode.FOR_TIME -> workout.rounds
    else -> workout.timer.rounds
}

/**
 * The round to show while it runs, 1 for the first. For Time and AMRAP go by the lifter's taps,
 * the other two by the clock ([clockRound], from the timer's view).
 */
fun wodRound(workout: Wod, taps: Int, clockRound: Int): Int = when (workout.timer.mode) {
    TimerMode.AMRAP -> taps + 1
    TimerMode.FOR_TIME -> minOf(taps / tapsPerRound(workout) + 1, workout.rounds)
    else -> clockRound
}

/** True once the last step of a For Time is tapped off: that tap stops the clock. */
fun wodDone(workout: Wod, taps: Int): Boolean {
    val steps = wodSteps(workout) ?: return false
    return taps >= steps
}

/** The indices in `items` of what takes a tap: everything but the rests. */
private fun tappable(workout: Wod): List<Int> = workout.items.indices.filter { !isRest(workout.items[it]) }

/**
 * In a For Time the items of a round are ticked off one by one: the index in `items` of the one
 * to do now. Rests are skipped: they take no tap. The last one stays current once everything is done.
 */
fun wodStep(workout: Wod, taps: Int): Int {
    val tappable = tappable(workout)
    if (tappable.isEmpty()) return 0
    return tappable[if (wodDone(workout, taps)) tappable.size - 1 else taps % tappable.size]
}

/** True for a pause between the work: the clock counts it down, it takes no tap. */
fun isRest(item: WodItem): Boolean = item.exerciseId == REST_EXERCISE

/** A pause a For Time is in: seconds [left] of it, rounded up, and its [total]. */
data class WodRest(val left: Int, val total: Int)

/**
 * The pause a For Time is in, if any: it starts by itself with the tap on the item before it and
 * runs for the seconds of the rest items that follow that item. A rest after the very last item is
 * none: the WOD is over with that tap. [splits] are the taps so far, [elapsed] the seconds into the WOD now.
 */
fun wodRest(workout: Wod, splits: List<Double>, elapsed: Double): WodRest? {
    val taps = splits.size
    if (workout.timer.mode != TimerMode.FOR_TIME || taps == 0 || wodDone(workout, taps)) return null
    val tappable = tappable(workout)
    if (tappable.isEmpty()) return null
    // The item just ticked off, and the rests between it and the next one to tick.
    val last = tappable[(taps - 1) % tappable.size]
    val total = workout.items.drop(last + 1).takeWhile(::isRest).sumOf { if (it.unit == WodUnit.SECONDS) it.amount else 0 }
    val left = total - (elapsed - splits[taps - 1])
    return if (total > 0 && left > 0) WodRest(kotlin.math.ceil(left).toInt(), total) else null
}

/** JavaScript's `Math.round`: halves go up. */
private fun jsRound(value: Double): Int = Math.floor(value + 0.5).toInt()

/**
 * How a For Time run stands against the best one, in seconds: negative is ahead, positive behind.
 * It is the gap at the last tap, and it grows live once the current item takes longer than it did
 * in the best run. Null when there is no best with splits to measure against.
 * [splits] are this run's taps so far, [elapsed] the seconds into the WOD now.
 */
fun liveGap(best: TimerSession?, splits: List<Double>, elapsed: Double): Int? {
    val pace = best?.splits
    if (pace.isNullOrEmpty()) return null
    val taps = splits.size
    if (taps > pace.size) return null
    // Whole seconds the way the clock and the "Best" next to it show them, so the three never disagree.
    val atLastTap = if (taps == 0) Int.MIN_VALUE else jsRound(splits[taps - 1]) - jsRound(pace[taps - 1])
    val onThisItem = if (taps < pace.size) Math.floor(elapsed).toInt() - jsRound(pace[taps]) else Int.MIN_VALUE
    val gap = maxOf(atLastTap, onThisItem)
    // Before the first tap there is only the live part: until the best run's first split passes, it is level.
    return if (taps == 0) maxOf(0, gap) else gap
}

/**
 * How an AMRAP stands against the best one, in rounds: positive is ahead.
 * Null when the best run has no splits.
 */
fun roundsGap(best: TimerSession?, taps: Int, elapsed: Double): Int? {
    val pace = best?.splits
    if (pace.isNullOrEmpty()) return null
    return taps - pace.count { it <= elapsed }
}

/** "12s ahead", "1:05 behind", "On pace". */
fun gapText(seconds: Int): String = when {
    seconds == 0 -> "On pace"
    seconds < 0 -> "${holdText(-seconds)} ahead"
    else -> "${holdText(seconds)} behind"
}

/** "2 rounds ahead", "1 round behind", "On pace". */
fun roundsGapText(rounds: Int): String {
    if (rounds == 0) return "On pace"
    val count = kotlin.math.abs(rounds)
    return "$count ${if (count == 1) "round" else "rounds"} ${if (rounds > 0) "ahead" else "behind"}"
}

/** What there is to do in this round. A Tabata takes its items in turns, one per round. */
fun roundItems(workout: Wod, round: Int): List<WodItem> {
    if (workout.timer.mode != TimerMode.TABATA || workout.items.isEmpty()) return workout.items
    return listOf(workout.items[Math.floorMod(round - 1, workout.items.size)])
}

/** How much of an item this round asks for: the scheme's number for reps, else its own amount. */
fun itemAmount(workout: Wod, item: WodItem, round: Int): Int {
    if (item.unit != WodUnit.REPS || workout.scheme.isEmpty()) return item.amount
    return workout.scheme[minOf(round, workout.scheme.size).coerceAtLeast(1) - 1]
}

/** A WOD's weight in the unit the app shows: half kilos, or the 5 lb steps of a plate rack. */
fun loadIn(kg: Double, unit: WeightUnit): Double =
    if (unit == WeightUnit.KG) roundTo(kg, 0.5) else roundTo(convert(kg, WeightUnit.KG, WeightUnit.LB), 5.0)

/** A weight typed in the app's unit, as the kg a WOD stores. */
fun loadFrom(value: Double, unit: WeightUnit): Double = roundTo(convert(value, unit, WeightUnit.KG), 0.5)

/** "400 m", "1.6 km". */
fun distanceText(meters: Int): String =
    if (meters >= 1000 && meters % 100 == 0) "${formatNumber(meters / 1000.0)} km" else "$meters m"

private fun loadSuffix(item: WodItem, unit: WeightUnit): String =
    if (item.load > 0) " ${formatWeight(loadIn(item.load, unit), unit)}" else ""

/**
 * "21 × Thruster 43kg", "400 m Run", "30s Plank", "Max Push-ups".
 * The "×" keeps a count from reading as bad grammar next to a name that is singular or plural.
 */
fun itemText(item: WodItem, amount: Int, name: String, unit: WeightUnit): String {
    val load = loadSuffix(item, unit)
    return when {
        amount == 0 -> "Max $name$load"
        item.unit == WodUnit.METERS -> "${distanceText(amount)} $name$load"
        item.unit == WodUnit.SECONDS -> "${holdText(amount)} $name$load"
        else -> "$amount × $name$load"
    }
}

typealias FindExercise = (String) -> Exercise?

/** The lines of one round, ready to show. An exercise that no longer exists is left out. */
fun roundLines(workout: Wod, round: Int, find: FindExercise, unit: WeightUnit): List<String> =
    roundItems(workout, round).mapNotNull { item ->
        find(item.exerciseId)?.let { itemText(item, itemAmount(workout, item, round), it.name, unit) }
    }

/**
 * The lines of the whole WOD for its card and its detail. With a scheme the reps are left to
 * the headline: "Thruster 43kg" under "21-15-9".
 */
fun wodLines(workout: Wod, find: FindExercise, unit: WeightUnit): List<String> =
    workout.items.mapNotNull { item ->
        val exercise = find(item.exerciseId) ?: return@mapNotNull null
        if (item.unit == WodUnit.REPS && workout.scheme.isNotEmpty()) "${exercise.name}${loadSuffix(item, unit)}"
        else itemText(item, item.amount, exercise.name, unit)
    }

/** "21-15-9 · For Time", "5 rounds · For Time", "AMRAP 20:00", "EMOM 30 × 1:00", "Tabata 8 × 20s / 10s". */
fun wodFormat(workout: Wod): String {
    val timer = workout.timer
    return when {
        timer.mode == TimerMode.AMRAP -> "AMRAP ${holdText(timer.work)}"
        timer.mode == TimerMode.EMOM -> "EMOM ${timer.rounds} × ${holdText(timer.work)}"
        timer.mode == TimerMode.TABATA -> "Tabata ${timer.rounds} × ${holdText(timer.work)} / ${holdText(timer.rest)}"
        workout.scheme.isNotEmpty() -> "${workout.scheme.joinToString("-")} · For Time"
        workout.rounds > 1 -> "${workout.rounds} rounds · For Time"
        else -> "For Time"
    }
}

/** What a WOD is called: its name, or its format while the lifter has not given it one. */
fun wodName(workout: Wod): String = workout.name.ifEmpty { wodFormat(workout) }

/**
 * True when two versions of a WOD ask for the same work, so a time or a round count of one can be
 * compared with the other. The name and the cap of a For Time do not change the work.
 */
fun sameWork(a: Wod, b: Wod): Boolean {
    fun work(workout: Wod): List<Any> = listOf(
        workout.timer.mode,
        if (workout.timer.mode == TimerMode.FOR_TIME) 0 else listOf(workout.timer.work, workout.timer.rest, workout.timer.rounds),
        workout.rounds,
        workout.scheme,
        workout.items,
    )
    return work(a) == work(b)
}

/** Up to three different pictures for the card, in the order of the items. */
fun wodImages(workout: Wod, find: FindExercise): List<String> =
    workout.items.mapNotNull { find(it.exerciseId)?.image }.distinct().take(3)

/** True when nothing in it needs a weight: every item is a bodyweight exercise without load. */
fun isBodyweightWod(workout: Wod, find: FindExercise): Boolean =
    workout.items.all { item -> item.load == 0.0 && find(item.exerciseId)?.equipment == Equipment.BODYWEIGHT }

/**
 * The best finished run of a WOD: the fastest For Time that was finished (not capped, not
 * stopped), or the AMRAP with the most rounds. EMOM and Tabata have nothing to beat.
 */
fun wodBest(sessions: List<TimerSession>, wodId: String): TimerSession? {
    var best: TimerSession? = null
    for (session in sessions) {
        if (session.wodId != wodId || timerOutcome(session) != TimerOutcome.COMPLETED) continue
        when (session.config.mode) {
            TimerMode.FOR_TIME -> if (best == null || session.seconds < best.seconds) best = session
            TimerMode.AMRAP -> if (session.rounds > 0 && (best == null || session.rounds > best.rounds)) best = session
            else -> Unit
        }
    }
    return best
}

/** "4:52" for a For Time, "18 rounds" for an AMRAP. */
fun bestText(session: TimerSession): String =
    if (session.config.mode == TimerMode.FOR_TIME) holdText(session.seconds)
    else "${session.rounds} ${if (session.rounds == 1) "round" else "rounds"}"

/**
 * True when this session beat every earlier run of its WOD. [others] are the sessions without it.
 * A first run has nothing to beat, so it is not a record.
 */
fun isWodRecord(others: List<TimerSession>, session: TimerSession): Boolean {
    val wodId = session.wodId
    if (wodId == null || timerOutcome(session) != TimerOutcome.COMPLETED) return false
    val before = wodBest(others, wodId) ?: return false
    if (session.config.mode == TimerMode.FOR_TIME) return session.seconds < before.seconds
    return session.config.mode == TimerMode.AMRAP && session.rounds > before.rounds
}

private fun reps(exerciseId: String, amount: Int, load: Number = 0) = WodItem(exerciseId, amount, WodUnit.REPS, load.toDouble())

private fun meters(exerciseId: String, amount: Int) = WodItem(exerciseId, amount, WodUnit.METERS, 0.0)

private fun rest(seconds: Int) = WodItem("rest", seconds, WodUnit.SECONDS, 0.0)

private fun forTime(id: String, name: String, cap: Int, items: List<WodItem>, rounds: Int = 1, scheme: List<Int> = emptyList()) =
    wod(Wod(id, name, TimerConfig(TimerMode.FOR_TIME, work = cap, rest = 0, rounds = 1), rounds, scheme, items))

private fun amrap(id: String, name: String, seconds: Int, items: List<WodItem>) =
    wod(Wod(id, name, TimerConfig(TimerMode.AMRAP, work = seconds, rest = 0, rounds = 1), 1, emptyList(), items))

/**
 * Well-known benchmark workouts, ready to start. Weights are the usual ones in kg.
 * They are not stored in the state: the lifter starts them as they are, or edits a copy.
 */
val WOD_LIBRARY: List<Wod> = listOf(
    forTime("wod-fran", "Fran", 600, listOf(reps("thruster", 21, 43), reps("pullups", 21)), 3, listOf(21, 15, 9)),
    amrap("wod-cindy", "Cindy", 1200, listOf(reps("pullups", 5), reps("pushups", 10), reps("airSquat", 15))),
    forTime("wod-helen", "Helen", 1200, listOf(meters("run", 400), reps("kettlebellSwing", 21, 24), reps("pullups", 12)), 3),
    forTime("wod-grace", "Grace", 600, listOf(reps("cleanAndJerk", 30, 61))),
    forTime("wod-annie", "Annie", 1200, listOf(reps("doubleUnder", 50), reps("situps", 50)), 5, listOf(50, 40, 30, 20, 10)),
    forTime("wod-karen", "Karen", 1200, listOf(reps("wallBall", 150, 9))),
    forTime("wod-angie", "Angie", 2400, listOf(reps("pullups", 100), reps("pushups", 100), reps("situps", 100), reps("airSquat", 100))),
    forTime("wod-barbara", "Barbara", 3600, listOf(reps("pullups", 20), reps("pushups", 30), reps("situps", 40), reps("airSquat", 50)), 5),
    wod(
        Wod(
            "wod-chelsea", "Chelsea", TimerConfig(TimerMode.EMOM, work = 60, rest = 0, rounds = 30), 1, emptyList(),
            listOf(reps("pullups", 5), reps("pushups", 10), reps("airSquat", 15)),
        ),
    ),
    amrap("wod-mary", "Mary", 1200, listOf(reps("handstandPushup", 5), reps("pistolSquat", 10), reps("pullups", 15))),
    forTime("wod-jackie", "Jackie", 1200, listOf(meters("rowErg", 1000), reps("thruster", 50, 20), reps("pullups", 30))),
    forTime("wod-diane", "Diane", 900, listOf(reps("deadlift", 21, 102), reps("handstandPushup", 21)), 3, listOf(21, 15, 9)),
    forTime("wod-isabel", "Isabel", 600, listOf(reps("snatch", 30, 61))),
    forTime("wod-dt", "DT", 1200, listOf(reps("deadlift", 12, 70), reps("hangPowerClean", 9, 70), reps("pushJerk", 6, 70)), 5),
    forTime(
        "wod-murph", "Murph", 3600,
        listOf(meters("run", 1600), reps("pullups", 100), reps("pushups", 200), reps("airSquat", 300), meters("run", 1600)),
    ),
    forTime("wod-venus", "Venus", 2400, listOf(reps("pushups", 50), reps("situps", 20), reps("airSquat", 50)), 4),
    forTime("wod-aphrodite", "Aphrodite", 2700, listOf(reps("burpee", 50), reps("airSquat", 50), reps("situps", 50)), 5, listOf(50, 40, 30, 20, 10)),
    forTime(
        "wod-helios", "Helios", 3600,
        listOf(
            reps("burpee", 100), reps("bodyweightLunge", 125), reps("climber", 150), reps("situps", 125),
            reps("climber", 150), reps("bodyweightLunge", 125), reps("burpee", 100),
        ),
    ),
    forTime(
        "wod-zeus", "Zeus", 3600,
        listOf(reps("handstandPushup", 10), reps("pullups", 20), reps("pushups", 30), reps("situps", 40), reps("airSquat", 50), rest(120)),
        4,
    ),
    forTime("wod-ares", "Ares", 1800, listOf(reps("pullups", 7), reps("situps", 7), meters("sprint", 40), meters("sprint", 40), rest(60)), 5),
    forTime("wod-gaia", "Gaia", 3600, listOf(reps("jumpingJack", 40), reps("jump", 30), reps("climber", 20), reps("standup", 10)), 10),
)

/** A library WOD or one of the lifter's own with this id. */
fun findWod(own: List<Wod>, id: String): Wod? = own.firstOrNull { it.id == id } ?: WOD_LIBRARY.firstOrNull { it.id == id }

/** True for a WOD that ships with the app: it cannot be edited or deleted, only copied. */
fun isLibraryWod(id: String): Boolean = WOD_LIBRARY.any { it.id == id }
