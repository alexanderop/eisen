package io.github.alexanderop.eisen.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

/*
 * The port of the PWA's `domain/boxing.ts`. The cue schedules with their seeds are the contract between
 * the apps: `BoxingTest` checks them with the same inputs as `boxing.test.ts`.
 */

/**
 * A boxing workout: rounds on the timer, each with its own theme, and a coach who calls the
 * combinations. The round length, the rest and the number of rounds are the timer's `work`, `rest`
 * and `rounds`; what happens in each round is the [BoxingPlan].
 *
 * What a round is about: which combinations the coach calls. [key] names its recording
 * (`box-theme-<key>`), [title] and [hint] are shown, and [color] (ARGB) is the same in both apps:
 * a round is recognised by it in the plan and while it runs.
 */
@Serializable
enum class BoxingTheme(val key: String, val title: String, val hint: String, val color: Long) {
    @SerialName("jab") JAB("jab", "Jab only", "Single, double and triple jabs, head and body", 0xFFC8F031),
    @SerialName("two") TWO("two", "2-punch combos", "Jab-cross, jab-hook, cross-hook", 0xFF7EE081),
    @SerialName("three") THREE("three", "3-punch combos", "Jab-cross-hook and its cousins", 0xFFFFD23F),
    @SerialName("four") FOUR("four", "4+ punch combos", "Long combinations to chain", 0xFFFF8A3D),
    @SerialName("body") BODY("body", "Body shots", "Head and body, changing levels", 0xFFFF4D4D),
    @SerialName("hooks") HOOKS("hooks", "Hooks and uppercuts", "Hooks and uppercuts in close", 0xFFC77DFF),
    @SerialName("defense") DEFENSE("defense", "Defense", "Slip or roll, then answer", 0xFF4FE0B5),
    @SerialName("mix") MIX("mix", "Mix", "The coach picks from every theme", 0xFFFF7AD9),
    @SerialName("freestyle") FREESTYLE("freestyle", "Freestyle", "No calls: you box, the clock runs", 0xFF9DB4FF),
}

val BOXING_THEMES: List<BoxingTheme> = BoxingTheme.entries

@Serializable
data class BoxingRound(
    val theme: BoxingTheme,
    /** Seconds between two calls. */
    val every: Int,
    /** Seconds a speed or a power stretch lasts; they take turns, speed first. 0 = no split. */
    val split: Int,
    /** Ids of the only combinations of the theme to call. Empty = all of them. */
    val pick: List<String> = emptyList(),
)

/** How the coach says a combination: "Jab, cross, hook" or "One, two, three". */
@Serializable
enum class BoxingStyle {
    @SerialName("names") NAMES,
    @SerialName("numbers") NUMBERS,
}

@Serializable
data class BoxingPlan(
    /** One entry per round of the timer. */
    val rounds: List<BoxingRound>,
    val style: BoxingStyle,
)

const val MIN_BOXING_EVERY = 3
const val MAX_BOXING_EVERY = 15
const val DEFAULT_BOXING_EVERY = 6
val BOXING_SPLITS: List<Int> = listOf(0, 15, 20, 30, 60)
const val MAX_BOXING_ROUNDS = 15
const val MIN_BOXING_ROUND_SECONDS = 30
const val MAX_BOXING_ROUND_SECONDS = 600
const val MIN_BOXING_REST_SECONDS = 10
const val MAX_BOXING_REST_SECONDS = 300

val DEFAULT_BOXING_ROUND = BoxingRound(BoxingTheme.MIX, DEFAULT_BOXING_EVERY, 0)

/** A pace the setup offers by name. `every` is free in the data; a pace is the nearest one. */
data class BoxingPace(val name: String, val every: Int)

val BOXING_PACES: List<BoxingPace> = listOf(
    BoxingPace("Easy", 8),
    BoxingPace("Steady", 6),
    BoxingPace("Fast", 4),
    BoxingPace("All out", 3),
)

/** The named pace closest to a number of seconds. */
fun paceOf(every: Int): String =
    BOXING_PACES.reduce { best, pace -> if (abs(pace.every - every) < abs(best.every - every)) pace else best }.name

private fun boxingRound(round: BoxingRound?): BoxingRound {
    if (round == null) return DEFAULT_BOXING_ROUND
    val theme = round.theme
    return BoxingRound(
        theme = theme,
        every = round.every.coerceIn(MIN_BOXING_EVERY, MAX_BOXING_EVERY),
        // A freestyle round has no coach: nobody to call speed and power.
        split = if (theme != BoxingTheme.FREESTYLE && round.split in BOXING_SPLITS) round.split else 0,
        pick = round.pick.filter { id -> themeCombos(theme).any { it.id == id } },
    )
}

/**
 * A plan with exactly [rounds] rounds, each within its limits. A round that is missing copies the
 * last one there is, so raising the number of rounds repeats what the lifter set up last.
 */
fun boxingPlan(plan: BoxingPlan?, rounds: Int): BoxingPlan {
    val given = plan?.rounds.orEmpty()
    return BoxingPlan(List(maxOf(0, rounds)) { boxingRound(given.getOrNull(it) ?: given.lastOrNull()) }, plan?.style ?: BoxingStyle.NAMES)
}

/**
 * A combination the coach can call. [id] names its two recordings in `assets/voice/timer`:
 * `box-<id>-n` spoken with names and `box-<id>-d` with numbers.
 * Numbers: 1 jab, 2 cross, 3 lead hook, 4 rear hook, 5 lead uppercut, 6 rear uppercut; B = to the body.
 */
data class BoxingCombo(
    val id: String,
    /** The themes it belongs to. Every combination is also part of the mix. */
    val themes: List<BoxingTheme>,
    /** As shown: "Jab · Cross · Hook". */
    val names: String,
    /** As shown: "1 – 2 – 3". */
    val numbers: String,
    /** As spoken with names. */
    val say: String,
    /** As spoken with numbers. */
    val sayNumbers: String,
)

private fun combo(id: String, themes: List<BoxingTheme>, names: String, numbers: String, say: String, sayNumbers: String) =
    BoxingCombo(id, themes, names, numbers, say, sayNumbers)

private val JAB = listOf(BoxingTheme.JAB)
private val JAB_BODY = listOf(BoxingTheme.JAB, BoxingTheme.BODY)
private val TWO = listOf(BoxingTheme.TWO)
private val THREE = listOf(BoxingTheme.THREE)
private val FOUR = listOf(BoxingTheme.FOUR)
private val BODY = listOf(BoxingTheme.BODY)
private val BODY_HOOKS = listOf(BoxingTheme.BODY, BoxingTheme.HOOKS)
private val HOOKS = listOf(BoxingTheme.HOOKS)
private val DEFENSE = listOf(BoxingTheme.DEFENSE)

val BOXING_COMBOS: List<BoxingCombo> = listOf(
    combo("jab", JAB, "Jab", "1", "Jab.", "One."),
    combo("double-jab", JAB, "Double jab", "1 – 1", "Double jab.", "One, one."),
    combo("triple-jab", JAB, "Triple jab", "1 – 1 – 1", "Triple jab.", "One, one, one."),
    combo("jab-body", JAB_BODY, "Jab to the body", "1B", "Jab to the body.", "One to the body."),
    combo("jab-jab-body", JAB_BODY, "Jab · Jab to the body", "1 – 1B", "Jab, jab to the body.", "One, one to the body."),
    combo("jab-body-jab", JAB_BODY, "Jab to the body · Jab", "1B – 1", "Jab to the body, jab to the head.", "One to the body, one to the head."),

    combo("jab-cross", TWO, "Jab · Cross", "1 – 2", "Jab, cross.", "One, two."),
    combo("jab-hook", TWO, "Jab · Hook", "1 – 3", "Jab, hook.", "One, three."),
    combo("cross-hook", TWO, "Cross · Hook", "2 – 3", "Cross, hook.", "Two, three."),
    combo("hook-cross", TWO, "Hook · Cross", "3 – 2", "Hook, cross.", "Three, two."),
    combo("cross-uppercut", TWO, "Cross · Uppercut", "2 – 5", "Cross, uppercut.", "Two, five."),
    combo("jab-uppercut", TWO, "Jab · Uppercut", "1 – 6", "Jab, uppercut.", "One, six."),

    combo("jab-cross-hook", THREE, "Jab · Cross · Hook", "1 – 2 – 3", "Jab, cross, hook.", "One, two, three."),
    combo("jab-jab-cross", THREE, "Jab · Jab · Cross", "1 – 1 – 2", "Jab, jab, cross.", "One, one, two."),
    combo("cross-hook-cross", THREE, "Cross · Hook · Cross", "2 – 3 – 2", "Cross, hook, cross.", "Two, three, two."),
    combo("hook-cross-hook", THREE, "Hook · Cross · Hook", "3 – 2 – 3", "Hook, cross, hook.", "Three, two, three."),
    combo("jab-hook-cross", THREE, "Jab · Hook · Cross", "1 – 3 – 2", "Jab, hook, cross.", "One, three, two."),
    combo("jab-cross-uppercut", THREE, "Jab · Cross · Uppercut", "1 – 2 – 5", "Jab, cross, uppercut.", "One, two, five."),
    combo("jab-cross-jab", THREE, "Jab · Cross · Jab", "1 – 2 – 1", "Jab, cross, jab.", "One, two, one."),

    combo("jab-cross-hook-cross", FOUR, "Jab · Cross · Hook · Cross", "1 – 2 – 3 – 2", "Jab, cross, hook, cross.", "One, two, three, two."),
    combo("jab-jab-cross-hook", FOUR, "Jab · Jab · Cross · Hook", "1 – 1 – 2 – 3", "Jab, jab, cross, hook.", "One, one, two, three."),
    combo("jab-cross-uppercut-cross", FOUR, "Jab · Cross · Uppercut · Cross", "1 – 2 – 5 – 2", "Jab, cross, uppercut, cross.", "One, two, five, two."),
    combo("cross-hook-cross-hook", FOUR, "Cross · Hook · Cross · Hook", "2 – 3 – 2 – 3", "Cross, hook, cross, hook.", "Two, three, two, three."),
    combo("jab-uppercut-hook-cross", FOUR, "Jab · Uppercut · Hook · Cross", "1 – 6 – 3 – 2", "Jab, uppercut, hook, cross.", "One, six, three, two."),
    combo("five", FOUR, "Jab · Cross · Hook · Cross · Jab", "1 – 2 – 3 – 2 – 1", "Jab, cross, hook, cross, jab.", "One, two, three, two, one."),
    combo("six", FOUR, "Jab · Cross · Hook · Uppercut · Hook · Cross", "1 – 2 – 3 – 6 – 3 – 2", "Jab, cross, hook, uppercut, hook, cross.", "One, two, three, six, three, two."),

    combo("cross-body", BODY, "Cross to the body", "2B", "Cross to the body.", "Two to the body."),
    combo("hook-body", BODY_HOOKS, "Hook to the body", "3B", "Hook to the body.", "Three to the body."),
    combo("jab-cross-body", BODY, "Jab · Cross to the body", "1 – 2B", "Jab, cross to the body.", "One, two to the body."),
    combo("jab-body-cross", BODY, "Jab to the body · Cross", "1B – 2", "Jab to the body, cross to the head.", "One to the body, two to the head."),
    combo("jab-cross-hook-body", BODY, "Jab · Cross · Hook to the body", "1 – 2 – 3B", "Jab, cross, hook to the body.", "One, two, three to the body."),
    combo("hook-body-hook", BODY_HOOKS, "Hook to the body · Hook", "3B – 3", "Hook to the body, hook to the head.", "Three to the body, three to the head."),

    combo("hook", HOOKS, "Hook", "3", "Hook.", "Three."),
    combo("double-hook", HOOKS, "Double hook", "3 – 3", "Double hook.", "Three, three."),
    combo("uppercut", HOOKS, "Uppercut", "6", "Uppercut.", "Six."),
    combo("uppercut-hook", HOOKS, "Uppercut · Hook", "6 – 3", "Uppercut, hook.", "Six, three."),
    combo("hook-uppercut-hook", HOOKS, "Hook · Uppercut · Hook", "3 – 6 – 3", "Hook, uppercut, hook.", "Three, six, three."),

    combo("slip-cross", DEFENSE, "Slip · Cross", "Slip – 2", "Slip, cross.", "Slip, two."),
    combo("slip-slip-cross", DEFENSE, "Slip · Slip · Cross", "Slip – Slip – 2", "Slip, slip, cross.", "Slip, slip, two."),
    combo("roll-hook", DEFENSE, "Roll · Hook", "Roll – 3", "Roll, hook.", "Roll, three."),
    combo("jab-slip-cross", DEFENSE, "Jab · Slip · Cross", "1 – Slip – 2", "Jab, slip, cross.", "One, slip, two."),
    combo("jab-cross-roll-cross", DEFENSE, "Jab · Cross · Roll · Cross", "1 – 2 – Roll – 2", "Jab, cross, roll, cross.", "One, two, roll, two."),
    combo("step-back-jab-cross", DEFENSE, "Step back · Jab · Cross", "Back – 1 – 2", "Step back, jab, cross.", "Step back, one, two."),
)

/** The combinations a round of this theme draws from. Freestyle has none. */
fun themeCombos(theme: BoxingTheme): List<BoxingCombo> = when (theme) {
    BoxingTheme.FREESTYLE -> emptyList()
    BoxingTheme.MIX -> BOXING_COMBOS
    else -> BOXING_COMBOS.filter { theme in it.themes }
}

/** What a round calls: the picked combinations of its theme, or all of them when none is picked. */
fun roundCombos(round: BoxingRound): List<BoxingCombo> {
    val all = themeCombos(round.theme)
    val picked = all.filter { it.id in round.pick }
    return picked.ifEmpty { all }
}

/** The combination a theme's tile shows as its example. Null where there is nothing to show. */
val THEME_EXAMPLES: Map<BoxingTheme, String?> = mapOf(
    BoxingTheme.JAB to "double-jab",
    BoxingTheme.TWO to "jab-cross",
    BoxingTheme.THREE to "jab-cross-hook",
    BoxingTheme.FOUR to "jab-cross-hook-cross",
    BoxingTheme.BODY to "jab-cross-body",
    BoxingTheme.HOOKS to "uppercut-hook",
    BoxingTheme.DEFENSE to "slip-cross",
    BoxingTheme.MIX to null,
    BoxingTheme.FREESTYLE to null,
)

/** What the coach says besides combinations: the file name without extension and its text. */
val BOXING_LINES: Map<String, String> = linkedMapOf(
    "box-theme-jab" to "Jab only.",
    "box-theme-two" to "Two punch combos.",
    "box-theme-three" to "Three punch combos.",
    "box-theme-four" to "Long combos.",
    "box-theme-body" to "Body shots.",
    "box-theme-hooks" to "Hooks and uppercuts.",
    "box-theme-defense" to "Defense.",
    "box-theme-mix" to "Mix it up.",
    "box-theme-freestyle" to "Freestyle.",
    "box-speed" to "Speed!",
    "box-power" to "Power!",
)

private val COMBO_SOUND = Regex("^box-(.+)-[nd]$")

/** The recording of a combination in a style. */
fun comboSound(combo: BoxingCombo, style: BoxingStyle): String = "box-${combo.id}-${if (style == BoxingStyle.NUMBERS) "d" else "n"}"

/** The combination a sound is the recording of, or null for any other sound. */
fun soundCombo(sound: String): BoxingCombo? {
    val id = COMBO_SOUND.find(sound)?.groupValues?.get(1) ?: return null
    return BOXING_COMBOS.firstOrNull { it.id == id }
}

/**
 * Every recording the boxing coach needs, file name to spoken text.
 * The script that records the voice reads the PWA's copy, so the app and the recordings cannot drift apart.
 */
fun boxingRecordings(): Map<String, String> {
    val list = LinkedHashMap(BOXING_LINES)
    for (combo in BOXING_COMBOS) {
        list[comboSound(combo, BoxingStyle.NAMES)] = combo.say
        list[comboSound(combo, BoxingStyle.NUMBERS)] = combo.sayNumbers
    }
    return list
}

private const val SECOND = 1000L

/** How long after the round is called the coach says what it is about. */
private const val THEME_AFTER_ROUND = 1700L

/** The first combination of a round comes this long after its start: the round and theme are said first. */
private const val FIRST_CALL = 6 * SECOND

/** No combination is called this close to the end of a round: the bell is coming. */
private const val LAST_CALL = 4 * SECOND

/** Room one spoken line needs before the next one starts. */
private const val VOICE_GAP = 2500L

/** After a combination is said, the lifter gets this long to start throwing before the next call. */
private const val AFTER_CALL = 600L

/**
 * How long the coach takes to say a line, in milliseconds: an upper estimate from its parts and
 * words, a little above what the recordings measure. The next call waits for it, so a long
 * combination at a fast pace is never talked over.
 */
fun sayLength(text: String): Long {
    val parts = text.split(",").size
    val words = text.replace(Regex("[^a-zA-Z ]"), "").trim().split(Regex("\\s+")).size
    return maxOf(parts * 1100L, words * 460L) + 400
}

/** Speed stretches call faster, and only the short combinations. Power keeps the round's pace. */
private const val SPEED_FACTOR = 0.75

/** Pieces a combination is said in: "Jab, cross, hook" has three. */
private fun parts(combo: BoxingCombo): Int = combo.say.split(",").size

/** The combinations for a speed stretch: the shortest of the round's and those one piece longer. */
fun speedCombos(pool: List<BoxingCombo>): List<BoxingCombo> {
    val shortest = pool.minOf(::parts)
    return pool.filter { parts(it) <= shortest + 1 }
}

/**
 * A small random number generator with a seed (mulberry32), so a run can be worked out again
 * exactly: after a reload, in a test, and the same in both apps. Returns numbers from 0 up to 1.
 * `Int` wraps like JavaScript's `| 0` and `Math.imul`, `ushr` is `>>>`.
 */
fun seededRandom(seed: Int): () -> Double {
    var state = seed
    return {
        state += 0x6d2b79f5
        var value = (state xor (state ushr 15)) * (1 or state)
        value = (value + (value xor (value ushr 7)) * (61 or value)) xor value
        ((value xor (value ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

enum class BoxingFocus { SPEED, POWER }

/** The stretch a moment of a round is in, [into] milliseconds after the round started. Null without a split. */
fun focusAt(round: BoxingRound, into: Long): BoxingFocus? {
    if (round.split <= 0) return null
    return if (Math.floorMod(Math.floorDiv(into, round.split * SECOND), 2L) == 0L) BoxingFocus.SPEED else BoxingFocus.POWER
}

/**
 * Every sound of a boxing timer from Start to the end, in order: the beeps and the lines about
 * rounds and rest like any timer, what each round is about, speed and power where a round is
 * split, the last thirty and ten seconds of a round, and the combinations.
 * The same [seed] gives the same combinations. No combination is called twice in a row.
 */
fun boxingCues(config: TimerConfig, calls: TimerCalls, seed: Int): List<TimerCue> {
    val plan = boxingPlan(config.boxing, config.rounds)
    val lead = TIMER_LEAD_IN_SECONDS * SECOND
    val work = config.work * SECOND
    val rest = config.rest * SECOND
    val cues = mutableListOf<TimerCue>()
    fun beepInto(at: Long) {
        if (!calls.beeps) return
        for (before in listOf(3, 2, 1)) cues += TimerCue(at - before * SECOND, "beep")
        cues += TimerCue(at, "beep-long")
    }
    if (calls.phases) cues += TimerCue(0, "get-ready")

    for (index in 0 until config.rounds) {
        val round = plan.rounds[index]
        val number = index + 1
        val start = lead + index * (work + rest)
        val end = start + work
        val last = number == config.rounds
        val spoken = mutableListOf<Long>()
        fun say(at: Long, sound: String) {
            cues += TimerCue(at, sound)
            spoken += at
        }

        beepInto(start)
        if (calls.phases) {
            val call = if (last && config.rounds > 1) "last-round" else "round-$number"
            say(start + VOICE_AFTER_BEEP, call)
            say(start + VOICE_AFTER_BEEP + THEME_AFTER_ROUND, "box-theme-${round.theme.key}")
        }
        if (round.split > 0) {
            // The first stretch is announced after the theme, the others as they begin.
            var into = 0L
            while (into < work - LAST_CALL) {
                val focus = focusAt(round, into)!!
                say(start + if (into == 0L) FIRST_CALL - 1500 else into, "box-${focus.name.lowercase()}")
                into += round.split * SECOND
            }
        }
        // A time call that lands on "Speed" or "Power" waits until that one is over.
        fun sayAfter(at: Long, sound: String) {
            val clash = spoken.firstOrNull { abs(it - at) < VOICE_GAP }
            say(if (clash == null) at else clash + VOICE_GAP, sound)
        }
        if (calls.final) {
            if (work >= 90 * SECOND) sayAfter(end - 30 * SECOND, "thirty-seconds")
            if (work >= 30 * SECOND) sayAfter(end - 10 * SECOND, "ten-seconds")
        }

        val pool = if (round.theme == BoxingTheme.FREESTYLE) emptyList() else roundCombos(round)
        if (pool.isNotEmpty()) {
            val quick = speedCombos(pool)
            val random = seededRandom(seed + number * 7919)
            var previous = ""
            var into = if (round.split > 0) FIRST_CALL + 1000 else FIRST_CALL
            while (into <= work - LAST_CALL) {
                val from = if (focusAt(round, into) == BoxingFocus.SPEED) quick else pool
                var pick = Math.floor(random() * from.size).toInt()
                if (from[pick].id == previous && from.size > 1) pick = (pick + 1) % from.size
                val combo = from[pick]
                val length = sayLength(if (plan.style == BoxingStyle.NUMBERS) combo.sayNumbers else combo.say)
                // A call never talks over another line: it waits until that one is over, and it is not
                // started when another line would begin before it is said.
                var at = start + into
                while (true) {
                    val clash = spoken.firstOrNull { it > at - VOICE_GAP && it < at + length } ?: break
                    at = clash + VOICE_GAP
                }
                into = at - start
                if (into > work - LAST_CALL) break
                previous = combo.id
                cues += TimerCue(at, comboSound(combo, plan.style))
                // JavaScript's `Math.round`: halves go up.
                val pace = Math.floor(round.every * (if (focusAt(round, into) == BoxingFocus.SPEED) SPEED_FACTOR else 1.0) * 2 + 0.5).toLong() * 500
                into += maxOf(Math.ceil((length + AFTER_CALL) / 500.0).toLong() * 500, pace)
            }
        }

        beepInto(end)
        if (calls.phases) cues += TimerCue(end + VOICE_AFTER_BEEP, if (last) "complete" else "rest")
    }
    // Stable like the JavaScript sort.
    return cues.sortedBy { it.at }
}

/** How many combinations a run calls. */
fun comboCount(cues: List<TimerCue>): Int = cues.count { soundCombo(it.sound) != null }

/**
 * What a boxing run shows at a moment. [round]: the plan of the round the timer is in, or of the one
 * coming up during the lead-in and a rest. [focus]: null without a split, during the lead-in and
 * during a rest. [combo]: the combination called last in this round, null before the first call and
 * outside a round.
 */
data class BoxingView(val round: BoxingRound, val focus: BoxingFocus?, val combo: BoxingCombo?)

/**
 * Where a boxing run stands [active] milliseconds after Start, pauses not counted.
 * [cues] is what [boxingCues] gave for this run.
 */
fun boxingView(config: TimerConfig, cues: List<TimerCue>, active: Long): BoxingView {
    val plan = boxingPlan(config.boxing, config.rounds)
    val view = timerView(config, active)
    // During a rest the round counted is the one just boxed: show the one that comes next.
    val index = if (view.phase == TimerPhase.REST) view.round else view.round - 1
    val round = plan.rounds[minOf(index, plan.rounds.size - 1)]
    if (view.phase != TimerPhase.WORK) return BoxingView(round, null, null)
    val start = active - view.phaseElapsed
    var combo: BoxingCombo? = null
    for (cue in cues) {
        if (cue.at > active) break
        if (cue.at >= start) combo = soundCombo(cue.sound) ?: combo
    }
    return BoxingView(round, focusAt(round, view.phaseElapsed), combo)
}

/** "Jab only · Fast", "Mix · Steady · speed and power 30s", "3-punch combos · Fast · 2 picked", "Freestyle". */
fun roundSummary(round: BoxingRound): String {
    if (round.theme == BoxingTheme.FREESTYLE) return BoxingTheme.FREESTYLE.title
    val split = if (round.split > 0) " · speed and power ${round.split}s" else ""
    val picked = if (round.pick.isNotEmpty()) " · ${round.pick.size} picked" else ""
    return "${round.theme.title} · ${paceOf(round.every)}$split$picked"
}

/** A plan where round [index] is replaced. */
fun withRound(plan: BoxingPlan, index: Int, round: BoxingRound): BoxingPlan =
    plan.copy(rounds = plan.rounds.mapIndexed { at, entry -> if (at == index) boxingRound(round) else entry })

/** A plan where every round is a copy of round [index]. */
fun copiedToAll(plan: BoxingPlan, index: Int): BoxingPlan {
    val source = plan.rounds.getOrNull(index) ?: return plan
    return plan.copy(rounds = plan.rounds.map { source })
}

/** A plan where round [index] swaps places with the one [by] rounds later (or earlier, negative). */
fun movedRound(plan: BoxingPlan, index: Int, by: Int): BoxingPlan {
    val to = index + by
    if (index !in plan.rounds.indices || to !in plan.rounds.indices) return plan
    val rounds = plan.rounds.toMutableList()
    rounds[index] = plan.rounds[to]
    rounds[to] = plan.rounds[index]
    return plan.copy(rounds = rounds)
}

/** A plan with a copy of round [index] right after it. Unchanged at the most rounds a workout can have. */
fun duplicatedRound(plan: BoxingPlan, index: Int): BoxingPlan {
    val source = plan.rounds.getOrNull(index) ?: return plan
    if (plan.rounds.size >= MAX_BOXING_ROUNDS) return plan
    return plan.copy(rounds = plan.rounds.toMutableList().apply { add(index + 1, source) })
}

/** A plan without round [index]. The last round there is cannot be removed. */
fun withoutRound(plan: BoxingPlan, index: Int): BoxingPlan {
    if (index !in plan.rounds.indices || plan.rounds.size <= 1) return plan
    return plan.copy(rounds = plan.rounds.filterIndexed { at, _ -> at != index })
}

/**
 * A template laid over a number of rounds: its rounds over and over, so a ladder of four on
 * eight rounds climbs twice. The style is the lifter's, not the template's.
 */
fun templatePlan(template: List<BoxingRound>, rounds: Int, style: BoxingStyle): BoxingPlan =
    BoxingPlan(List(maxOf(0, rounds)) { boxingRound(template[it % template.size]) }, style)

/** A workout to start from. */
data class BoxingTemplate(val name: String, val hint: String, val rounds: List<BoxingRound>)

val BOXING_TEMPLATES: List<BoxingTemplate> = listOf(
    BoxingTemplate(
        "Ladder",
        "One punch more every round",
        listOf(
            BoxingRound(BoxingTheme.JAB, 4, 0),
            BoxingRound(BoxingTheme.TWO, 4, 0),
            BoxingRound(BoxingTheme.THREE, 6, 0),
            BoxingRound(BoxingTheme.FOUR, 6, 0),
        ),
    ),
    BoxingTemplate("Speed and power", "30 seconds fast, 30 seconds heavy", listOf(BoxingRound(BoxingTheme.MIX, 6, 30))),
    BoxingTemplate(
        "Head and body",
        "Change levels all the way",
        listOf(
            BoxingRound(BoxingTheme.JAB, 4, 0),
            BoxingRound(BoxingTheme.BODY, 6, 0),
            BoxingRound(BoxingTheme.HOOKS, 6, 0),
        ),
    ),
    BoxingTemplate("Classic", "Every round a mix", listOf(BoxingRound(BoxingTheme.MIX, 6, 0))),
)
