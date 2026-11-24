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
    @SerialName("warmup") WARMUP("warmup", "Warm-up", "Easy jabs and reminders to move and stay loose", 0xFFE9E3CF),
    @SerialName("jab") JAB("jab", "Jab only", "Single, double and triple jabs, head and body", 0xFFC8F031),
    @SerialName("two") TWO("two", "2-punch combos", "Jab-cross, jab-hook, cross-hook", 0xFF7EE081),
    @SerialName("three") THREE("three", "3-punch combos", "Jab-cross-hook and its cousins", 0xFFFFD23F),
    @SerialName("four") FOUR("four", "4+ punch combos", "Long combinations to chain", 0xFFFF8A3D),
    @SerialName("body") BODY("body", "Body shots", "Head and body, changing levels", 0xFFFF4D4D),
    @SerialName("hooks") HOOKS("hooks", "Hooks and uppercuts", "Hooks and uppercuts in close", 0xFFC77DFF),
    @SerialName("defense") DEFENSE("defense", "Defense", "Slip or roll, then answer", 0xFF4FE0B5),
    @SerialName("build") BUILD("build", "Build-up", "The coach builds a combination punch by punch, like pad work", 0xFF59C3FF),
    @SerialName("own") OWN("own", "My combos", "Only the combinations you put together", 0xFFD9B38C),
    @SerialName("mix") MIX("mix", "Mix", "The coach picks from every theme", 0xFFFF7AD9),
    @SerialName("freestyle") FREESTYLE("freestyle", "Freestyle", "No calls: you box, the clock runs", 0xFF9DB4FF),
}

val BOXING_THEMES: List<BoxingTheme> = BoxingTheme.entries

/** How often the coach throws in a reminder between combinations: "Move!", "Hands up!". */
@Serializable
enum class BoxingCues(val title: String) {
    @SerialName("off") OFF("Off"),
    @SerialName("some") SOME("Some"),
    @SerialName("many") MANY("Many"),
}

val BOXING_CUE_LEVELS: List<BoxingCues> = BoxingCues.entries

@Serializable
data class BoxingRound(
    val theme: BoxingTheme,
    /** Seconds between two calls. */
    val every: Int,
    /** Seconds a speed or a power stretch lasts; they take turns, speed first. 0 = no split. */
    val split: Int,
    /** Ids of the only combinations of the theme to call. Empty = all of them. */
    val pick: List<String> = emptyList(),
    /** How often the coach throws in a reminder between combinations. */
    val cues: BoxingCues = BoxingCues.OFF,
    /** Seconds at the end of the round that go non-stop: short combinations back to back. 0 = none. */
    val burnout: Int = 0,
)

/** How the coach says a combination: "Jab, cross, hook" or "One, two, three". */
@Serializable
enum class BoxingStyle {
    @SerialName("names") NAMES,
    @SerialName("numbers") NUMBERS,
}

/** Which hand leads. The calls are the same either way; it decides what the key says about hands. */
@Serializable
enum class BoxingStance(val title: String) {
    @SerialName("orthodox") ORTHODOX("Orthodox"),
    @SerialName("southpaw") SOUTHPAW("Southpaw"),
}

/** How long the coach waits for a combination to be thrown before the next thing is said. [perPunch]: milliseconds a punch is given. */
@Serializable
enum class BoxingRoom(val title: String, val hint: String, val perPunch: Long) {
    @SerialName("roomy") ROOMY("Roomy", "Extra time to throw every combination. For learning them.", 750),
    @SerialName("normal") NORMAL("Normal", "About half a second a punch.", 500),
    @SerialName("tight") TIGHT("Tight", "Barely time to finish before the next call.", 350),
}

val BOXING_ROOMS: List<BoxingRoom> = BoxingRoom.entries

/** A combination the lifter put together. It is said piece by piece from the recordings of its moves. [id]: "own-1", unique in its plan. */
@Serializable
data class OwnCombo(val id: String, val moves: List<String>)

const val MIN_OWN_MOVES = 2
const val MAX_OWN_MOVES = 6
const val MAX_OWN_COMBOS = 12

@Serializable
data class BoxingPlan(
    /** One entry per round of the timer. */
    val rounds: List<BoxingRound>,
    val style: BoxingStyle,
    val stance: BoxingStance = BoxingStance.ORTHODOX,
    val room: BoxingRoom = BoxingRoom.NORMAL,
    /** The lifter's own combinations. They travel with the plan, so a preset keeps its own. */
    val own: List<OwnCombo> = emptyList(),
)

const val MIN_BOXING_EVERY = 3
const val MAX_BOXING_EVERY = 15
const val DEFAULT_BOXING_EVERY = 6
val BOXING_SPLITS: List<Int> = listOf(0, 15, 20, 30, 60)
val BOXING_BURNOUTS: List<Int> = listOf(0, 15, 30)
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

private fun boxingRound(round: BoxingRound?, own: List<OwnCombo>): BoxingRound {
    if (round == null) return DEFAULT_BOXING_ROUND
    // "My combos" without a combination of the lifter's own has nothing to call.
    val theme = if (round.theme == BoxingTheme.OWN && own.isEmpty()) BoxingTheme.MIX else round.theme
    // A freestyle round has no combinations, and a build-up has its own order: nothing to split or pick.
    val plain = theme != BoxingTheme.FREESTYLE && theme != BoxingTheme.BUILD
    val combos = if (plain) themeCombos(theme, own).map { it.id }.toSet() else emptySet()
    return BoxingRound(
        theme = theme,
        every = round.every.coerceIn(MIN_BOXING_EVERY, MAX_BOXING_EVERY),
        split = if (plain && round.split in BOXING_SPLITS) round.split else 0,
        pick = if (plain) round.pick.filter { it in combos } else emptyList(),
        cues = round.cues,
        burnout = if (theme != BoxingTheme.FREESTYLE && round.burnout in BOXING_BURNOUTS) round.burnout else 0,
    )
}

/** The lifter's own combinations from a file or an edit: known moves, 2 to 6 of them, unique ids, 12 at most. */
private fun ownCombos(own: List<OwnCombo>?): List<OwnCombo> {
    val list = mutableListOf<OwnCombo>()
    for (entry in own.orEmpty()) {
        val moves = entry.moves.filter { id -> BOXING_MOVES.any { it.id == id } }.take(MAX_OWN_MOVES)
        val fresh = OWN_ID.matches(entry.id) && list.none { it.id == entry.id }
        if (fresh && moves.size >= MIN_OWN_MOVES && list.size < MAX_OWN_COMBOS) list += OwnCombo(entry.id, moves)
    }
    return list
}

private val OWN_ID = Regex("^own-\\d+$")

/**
 * A plan with exactly [rounds] rounds, each within its limits. A round that is missing copies the
 * last one there is, so raising the number of rounds repeats what the lifter set up last.
 */
fun boxingPlan(plan: BoxingPlan?, rounds: Int): BoxingPlan {
    val given = plan?.rounds.orEmpty()
    val own = ownCombos(plan?.own)
    return BoxingPlan(
        rounds = List(maxOf(0, rounds)) { boxingRound(given.getOrNull(it) ?: given.lastOrNull(), own) },
        style = plan?.style ?: BoxingStyle.NAMES,
        stance = plan?.stance ?: BoxingStance.ORTHODOX,
        room = plan?.room ?: BoxingRoom.NORMAL,
        own = own,
    )
}

/**
 * One thing in a combination the lifter puts together: a punch, or a move between punches. [name]: as
 * shown and spoken with names ("Jab", "Hook to the body"). [number]: as shown with numbers ("1", "3B",
 * "Slip"). [sayNumber]: as spoken with numbers ("One", "Three to the body"). [punch]: false for a slip, a
 * roll or a step, which are not counted as punches. [length]: milliseconds its recording is given
 * before the next piece is said.
 */
data class BoxingMove(val id: String, val name: String, val number: String, val sayNumber: String, val punch: Boolean, val length: Long)

private val HEAD: List<BoxingMove> = listOf(
    BoxingMove("1", "Jab", "1", "One", true, 950),
    BoxingMove("2", "Cross", "2", "Two", true, 1050),
    BoxingMove("3", "Hook", "3", "Three", true, 750),
    BoxingMove("4", "Rear hook", "4", "Four", true, 1100),
    BoxingMove("5", "Lead uppercut", "5", "Five", true, 1200),
    BoxingMove("6", "Rear uppercut", "6", "Six", true, 1050),
)

/** Measured on the recordings, like every length here: the longer of the two styles and a short gap. */
private val BODY_LENGTHS = listOf(1600L, 1450L, 1500L, 1550L)

/** Everything a combination of the lifter's own can be put together from. */
val BOXING_MOVES: List<BoxingMove> = HEAD +
    HEAD.take(4).mapIndexed { index, it ->
        BoxingMove("${it.id}b", "${it.name} to the body", "${it.id}B", "${it.sayNumber} to the body", true, BODY_LENGTHS[index])
    } +
    listOf(
        BoxingMove("slip", "Slip", "Slip", "Slip", false, 750),
        BoxingMove("roll", "Roll", "Roll", "Roll", false, 600),
        BoxingMove("back", "Step back", "Back", "Step back", false, 1100),
        BoxingMove("pivot", "Pivot", "Pivot", "Pivot", false, 750),
    )

/** A move by its id. */
fun boxingMove(id: String): BoxingMove = BOXING_MOVES.firstOrNull { it.id == id } ?: BOXING_MOVES[0]

/**
 * A combination the coach can call. [id] names its two recordings in `assets/voice/timer`:
 * `box-<id>-n` spoken with names and `box-<id>-d` with numbers.
 * Numbers: 1 jab, 2 cross, 3 lead hook, 4 rear hook, 5 lead uppercut, 6 rear uppercut; B = to the body.
 * [moves]: only for a combination of the lifter's own, the moves it is said from, piece by piece.
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
    val moves: List<String>? = null,
)

private fun combo(id: String, themes: List<BoxingTheme>, names: String, numbers: String, say: String, sayNumbers: String) =
    BoxingCombo(id, themes, names, numbers, say, sayNumbers)

private val JAB = listOf(BoxingTheme.JAB)
private val JAB_WARMUP = listOf(BoxingTheme.JAB, BoxingTheme.WARMUP)
private val JAB_BODY = listOf(BoxingTheme.JAB, BoxingTheme.BODY)
private val TWO = listOf(BoxingTheme.TWO)
private val TWO_WARMUP = listOf(BoxingTheme.TWO, BoxingTheme.WARMUP)
private val THREE = listOf(BoxingTheme.THREE)
private val FOUR = listOf(BoxingTheme.FOUR)
private val BODY = listOf(BoxingTheme.BODY)
private val BODY_HOOKS = listOf(BoxingTheme.BODY, BoxingTheme.HOOKS)
private val HOOKS = listOf(BoxingTheme.HOOKS)
private val DEFENSE = listOf(BoxingTheme.DEFENSE)

val BOXING_COMBOS: List<BoxingCombo> = listOf(
    combo("jab", JAB_WARMUP, "Jab", "1", "Jab.", "One."),
    combo("double-jab", JAB_WARMUP, "Double jab", "1 – 1", "Double jab.", "One, one."),
    combo("triple-jab", JAB, "Triple jab", "1 – 1 – 1", "Triple jab.", "One, one, one."),
    combo("jab-body", JAB_BODY, "Jab to the body", "1B", "Jab to the body.", "One to the body."),
    combo("jab-jab-body", JAB_BODY, "Jab · Jab to the body", "1 – 1B", "Jab, jab to the body.", "One, one to the body."),
    combo("jab-body-jab", JAB_BODY, "Jab to the body · Jab", "1B – 1", "Jab to the body, jab to the head.", "One to the body, one to the head."),

    combo("jab-cross", TWO_WARMUP, "Jab · Cross", "1 – 2", "Jab, cross.", "One, two."),
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


/**
 * What a build-up climbs: each ladder is one combination growing a punch at a time, every step a
 * combination there is a recording of.
 */
val BUILD_LADDERS: List<List<String>> = listOf(
    listOf("jab", "jab-cross", "jab-cross-hook", "jab-cross-hook-cross", "five"),
    listOf("jab", "jab-cross", "jab-cross-uppercut", "jab-cross-uppercut-cross"),
    listOf("jab", "double-jab", "jab-jab-cross", "jab-jab-cross-hook"),
    listOf("jab", "jab-hook", "jab-hook-cross"),
    listOf("cross-hook", "cross-hook-cross", "cross-hook-cross-hook"),
    listOf("hook", "hook-cross", "hook-cross-hook"),
    listOf("jab-cross", "jab-cross-hook", "jab-cross-hook-body"),
)

private val LADDERS: List<List<BoxingCombo>> = BUILD_LADDERS.map { ladder -> ladder.map { id -> BOXING_COMBOS.first { it.id == id } } }

/** A combination of the lifter's own as the coach calls and the screen shows it. */
fun ownCombo(own: OwnCombo): BoxingCombo {
    val moves = own.moves.map(::boxingMove)
    return BoxingCombo(
        id = own.id,
        themes = listOf(BoxingTheme.OWN),
        names = moves.joinToString(" · ") { it.name },
        numbers = moves.joinToString(" – ") { it.number },
        say = "${moves.joinToString(", ") { it.name }}.",
        sayNumbers = "${moves.joinToString(", ") { it.sayNumber }}.",
        moves = own.moves,
    )
}

/** The combinations a round of this theme draws from. Freestyle has none; the mix has the lifter's own too. */
fun themeCombos(theme: BoxingTheme, own: List<OwnCombo> = emptyList()): List<BoxingCombo> = when (theme) {
    BoxingTheme.FREESTYLE -> emptyList()
    BoxingTheme.OWN -> own.map(::ownCombo)
    BoxingTheme.MIX -> BOXING_COMBOS + own.map(::ownCombo)
    BoxingTheme.BUILD -> BUILD_LADDERS.flatten().toSet().let { steps -> BOXING_COMBOS.filter { it.id in steps } }
    else -> BOXING_COMBOS.filter { theme in it.themes }
}

/** What a round calls: the picked combinations of its theme, or all of them when none is picked. */
fun roundCombos(round: BoxingRound, own: List<OwnCombo> = emptyList()): List<BoxingCombo> {
    val all = themeCombos(round.theme, own)
    val picked = all.filter { it.id in round.pick }
    return picked.ifEmpty { all }
}

/** The combination a theme's tile shows as its example. Null where there is nothing to show. */
val THEME_EXAMPLES: Map<BoxingTheme, String?> = mapOf(
    BoxingTheme.WARMUP to "jab",
    BoxingTheme.JAB to "double-jab",
    BoxingTheme.TWO to "jab-cross",
    BoxingTheme.THREE to "jab-cross-hook",
    BoxingTheme.FOUR to "jab-cross-hook-cross",
    BoxingTheme.BODY to "jab-cross-body",
    BoxingTheme.HOOKS to "uppercut-hook",
    BoxingTheme.DEFENSE to "slip-cross",
    BoxingTheme.BUILD to null,
    BoxingTheme.OWN to null,
    BoxingTheme.MIX to null,
    BoxingTheme.FREESTYLE to null,
)

/** What the coach says besides combinations: the file name without extension and its text. */
val BOXING_LINES: Map<String, String> = linkedMapOf(
    "box-theme-warmup" to "Warm up. Stay loose.",
    "box-theme-jab" to "Jab only.",
    "box-theme-two" to "Two punch combos.",
    "box-theme-three" to "Three punch combos.",
    "box-theme-four" to "Long combos.",
    "box-theme-body" to "Body shots.",
    "box-theme-hooks" to "Hooks and uppercuts.",
    "box-theme-defense" to "Defense.",
    "box-theme-build" to "Build it up.",
    "box-theme-own" to "Your combos.",
    "box-theme-mix" to "Mix it up.",
    "box-theme-freestyle" to "Freestyle.",
    "box-speed" to "Speed!",
    "box-power" to "Power!",
    "box-again" to "Again!",
    "box-one-more" to "One more!",
    "box-nonstop" to "Non-stop!",
    "box-cue-move" to "Move!",
    "box-cue-hands" to "Hands up!",
    "box-cue-chin" to "Chin down!",
    "box-cue-breathe" to "Breathe!",
    "box-cue-toes" to "Stay on your toes!",
    "box-cue-circle" to "Circle out!",
    "box-cue-angle" to "Find an angle!",
    "box-cue-snap" to "Snap it back!",
)

/** The reminders the coach throws in between combinations. */
val BOXING_CUES: List<String> = BOXING_LINES.keys.filter { it.startsWith("box-cue-") }

/** True for a reminder: "Move!", "Hands up!". */
fun isCoachCue(sound: String): Boolean = sound in BOXING_CUES

/** The recording of one move of a combination of the lifter's own. */
fun moveSound(id: String, style: BoxingStyle): String = "box-move-$id-${if (style == BoxingStyle.NUMBERS) "d" else "n"}"

private val COMBO_SOUND = Regex("^box-(.+)-[nd]$")

/** The recording of a combination in a style. */
fun comboSound(combo: BoxingCombo, style: BoxingStyle): String = "box-${combo.id}-${if (style == BoxingStyle.NUMBERS) "d" else "n"}"

/** The combination a sound is the recording of, or null for any other sound. */
fun soundCombo(sound: String): BoxingCombo? {
    val id = COMBO_SOUND.find(sound)?.groupValues?.get(1) ?: return null
    return BOXING_COMBOS.firstOrNull { it.id == id }
}

/**
 * The combination a cue calls, or null when it is anything else. A combination of the lifter's own
 * is several cues, one per move: the first one carries its id.
 */
fun cueCombo(cue: TimerCue, own: List<OwnCombo> = emptyList()): BoxingCombo? {
    if (cue.combo != null) return own.firstOrNull { it.id == cue.combo }?.let(::ownCombo)
    return soundCombo(cue.sound)
}

/**
 * The sounds of a combination as the coach calls it, as milliseconds after the call and recording:
 * one recording for a combination of the catalog, one per move for one of the lifter's own, each
 * after the moves before it are said.
 */
fun comboSounds(combo: BoxingCombo, style: BoxingStyle): List<Pair<Long, String>> {
    val moves = combo.moves ?: return listOf(0L to comboSound(combo, style))
    var offset = 0L
    return moves.map { id -> (offset to moveSound(id, style)).also { offset += boxingMove(id).length } }
}

/** Punches a combination throws: the moves that are punches, or the numbers in "1 – 2 – 3B" (3; "Slip – 2" is 1). */
fun comboPunches(combo: BoxingCombo): Int =
    combo.moves?.count { boxingMove(it).punch } ?: combo.numbers.count { it.isDigit() }

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
    for (move in BOXING_MOVES) {
        list[moveSound(move.id, BoxingStyle.NAMES)] = "${move.name}."
        list[moveSound(move.id, BoxingStyle.NUMBERS)] = "${move.sayNumber}."
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

/** After a short combination is said, the lifter gets this long to throw it before the next call. */
private const val AFTER_CALL = 600L

/** From this many pieces on a combination is drilled: thrown two or three times before the next one. */
const val DRILL_FROM = 3

/** How long "Again!" takes to say. */
private const val AGAIN_LENGTH = 1000L

/** How long a reminder takes to say, and the breath after it. */
private const val CUE_LENGTH = 1500L
private const val AFTER_CUE = 700L

/** How long "Non-stop!" takes to say. */
private const val NONSTOP_LENGTH = 1500L

/** The share of combinations that are followed by a reminder. */
private fun cueChance(level: BoxingCues): Double = when (level) {
    BoxingCues.OFF -> 0.0
    BoxingCues.SOME -> 0.25
    BoxingCues.MANY -> 0.5
}

/** Seconds between two reminders in a freestyle round: the shortest wait and how much longer it may get. */
private fun freestyleCues(level: BoxingCues): Pair<Int, Int> = when (level) {
    BoxingCues.OFF -> 0 to 0
    BoxingCues.SOME -> 16 to 9
    BoxingCues.MANY -> 9 to 6
}

/** How long a reminder shows on the run screen. */
private const val SAY_SHOWS = 2000L

/** Milliseconds one go at a combination of this many pieces takes: a breath, and for each punch what the plan's room gives. */
fun throwTime(pieces: Int, room: BoxingRoom = BoxingRoom.NORMAL): Long = pieces * room.perPunch + 500

/** How often a round reminds: a warm-up always a lot, whatever the round says. */
fun cueLevel(round: BoxingRound): BoxingCues = if (round.theme == BoxingTheme.WARMUP) BoxingCues.MANY else round.cues

/** Seconds of non-stop at the end of a round [work] seconds long. A round under twice the burnout has none. */
fun burnoutOf(round: BoxingRound, work: Int): Int = if (round.burnout > 0 && work >= round.burnout * 2) round.burnout else 0

/** True for the coach's "Again!" and "One more!": the combination called last is thrown once more. */
fun isAgain(sound: String): Boolean = sound == "box-again" || sound == "box-one-more"

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

/** How long the coach takes to call a combination: its recording, or the moves of one of the lifter's own one after the other. */
fun comboLength(combo: BoxingCombo, style: BoxingStyle): Long =
    combo.moves?.sumOf { boxingMove(it).length } ?: sayLength(if (style == BoxingStyle.NUMBERS) combo.sayNumbers else combo.say)

/** Speed stretches call faster, and only the short combinations. Power keeps the round's pace. */
private const val SPEED_FACTOR = 0.75

/** Pieces a combination is said in: "Jab, cross, hook" has three, one of the lifter's own one per move. */
private fun parts(combo: BoxingCombo): Int = combo.moves?.size ?: combo.say.split(",").size

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

enum class BoxingFocus { SPEED, POWER, BURNOUT }

/** The stretch a moment of a round is in, [into] milliseconds after the round started. Null without a split. */
fun focusAt(round: BoxingRound, into: Long): BoxingFocus? {
    if (round.split <= 0) return null
    return if (Math.floorMod(Math.floorDiv(into, round.split * SECOND), 2L) == 0L) BoxingFocus.SPEED else BoxingFocus.POWER
}

/** JavaScript's `Math.floor(random() * size)`. */
private fun draw(random: () -> Double, size: Int): Int = Math.floor(random() * size).toInt()

/**
 * Every sound of a boxing timer from Start to the end, in order: the beeps and the lines about
 * rounds and rest like any timer, what each round is about, speed and power where a round is
 * split, the last thirty and ten seconds of a round, the combinations, the reminders, and the
 * non-stop finish of a round that has one.
 * The same [seed] gives the same combinations. No combination is called twice in a row.
 * A plan without reminders, burnouts, build-ups and combinations of the lifter's own draws exactly
 * what it drew before they came, so its schedule is the same.
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
        val burn = burnoutOf(round, config.work) * SECOND
        // Milliseconds into the round where the non-stop finish starts: the end when there is none.
        val burnFrom = work - burn
        val limit = work - LAST_CALL
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
            // The first stretch is announced after the theme, the others as they begin. None in the non-stop finish.
            var into = 0L
            while (into < limit) {
                val focus = focusAt(round, into) ?: break
                val at = if (into == 0L) FIRST_CALL - 1500 else into
                if (at < burnFrom) say(start + at, "box-${focus.name.lowercase()}")
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
        if (burn > 0) sayAfter(end - burn, "box-nonstop")

        val random = seededRandom(seed + number * 7919)

        // A call never talks over another line: it waits until that one is over, and it is not
        // started when another line would begin before it is said.
        fun place(from: Long, length: Long): Long {
            var at = from
            while (true) {
                val clash = spoken.firstOrNull { it > at - VOICE_GAP && it < at + length } ?: break
                at = clash + VOICE_GAP
            }
            return at
        }

        fun call(at: Long, combo: BoxingCombo) {
            val sounds = comboSounds(combo, plan.style)
            sounds.forEachIndexed { step, (offset, sound) -> cues += TimerCue(at + offset, sound, if (step == 0) combo.moves?.let { combo.id } else null) }
        }

        // A long combination is drilled: said once, thrown, then "Again!" once or twice.
        fun drill(at: Long, length: Long, pieces: Int, goes: Int): Long {
            var free = at + length + throwTime(pieces, plan.room)
            for (go in 2..goes) {
                val again = place(free, AGAIN_LENGTH + 500)
                if (again - start > minOf(limit, burnFrom)) break
                cues += TimerCue(again, if (go == 3) "box-one-more" else "box-again")
                free = again + AGAIN_LENGTH + throwTime(pieces, plan.room)
            }
            return free
        }

        // A reminder between combinations: never the same twice in a row.
        val level = cueLevel(round)
        var lastCue = ""
        fun chooseCue(): String {
            var pick = draw(random, BOXING_CUES.size)
            if (BOXING_CUES[pick] == lastCue) pick = (pick + 1) % BOXING_CUES.size
            return BOXING_CUES[pick]
        }
        fun remind(free: Long): Long {
            // Off draws nothing, so a round without reminders calls what it called before they came.
            if (level == BoxingCues.OFF) return free
            if (random() >= cueChance(level)) return free
            val cue = chooseCue()
            val at = place(free, CUE_LENGTH + 500)
            if (at - start > limit || at - start >= burnFrom) return free
            cues += TimerCue(at, cue)
            lastCue = cue
            return at + CUE_LENGTH + AFTER_CUE
        }

        // One of the lifter's own is said move by move: it is not started when its last move would come after the bell.
        fun overruns(into: Long, combo: BoxingCombo, length: Long): Boolean = combo.moves != null && into + length > work

        fun pace(into: Long): Long =
            // JavaScript's `Math.round`: halves go up.
            Math.floor(round.every * (if (focusAt(round, into) == BoxingFocus.SPEED) SPEED_FACTOR else 1.0) * 2 + 0.5).toLong() * 500

        var previous = ""
        var into = if (round.split > 0) FIRST_CALL + 1000 else FIRST_CALL
        when (round.theme) {
            BoxingTheme.FREESTYLE -> if (level != BoxingCues.OFF) {
                // No combinations: only the reminders, now and then.
                val (wait, more) = freestyleCues(level)
                into = FIRST_CALL + 4000
                while (into <= limit) {
                    val at = place(start + into, CUE_LENGTH + 500)
                    if (at - start > limit) break
                    val cue = chooseCue()
                    cues += TimerCue(at, cue)
                    lastCue = cue
                    into = at - start + (wait + draw(random, more)) * SECOND
                }
            }
            BoxingTheme.BUILD -> {
                // A ladder at a time, step by step, every step thrown twice and the top two or three times.
                var climbed = -1
                climbing@ while (true) {
                    var ladder = draw(random, LADDERS.size)
                    if (ladder == climbed) ladder = (ladder + 1) % LADDERS.size
                    climbed = ladder
                    val steps = LADDERS[ladder]
                    for ((step, combo) in steps.withIndex()) {
                        val length = comboLength(combo, plan.style)
                        val at = place(start + into, length)
                        into = at - start
                        if (into > limit || into >= burnFrom) break@climbing
                        previous = combo.id
                        call(at, combo)
                        val goes = if (step < steps.lastIndex) 2 else if (random() < 1.0 / 3) 3 else 2
                        val free = remind(drill(at, length, parts(combo), goes))
                        into += maxOf(Math.ceil((free - at) / 500.0).toLong() * 500, pace(into))
                    }
                }
            }
            else -> {
                val pool = roundCombos(round, plan.own)
                val quick = speedCombos(pool)
                while (into <= limit && into < burnFrom) {
                    val from = if (focusAt(round, into) == BoxingFocus.SPEED) quick else pool
                    var pick = draw(random, from.size)
                    if (from[pick].id == previous && from.size > 1) pick = (pick + 1) % from.size
                    val combo = from[pick]
                    val length = comboLength(combo, plan.style)
                    val at = place(start + into, length)
                    into = at - start
                    if (into > limit || into >= burnFrom || overruns(into, combo, length)) break
                    previous = combo.id
                    call(at, combo)
                    val pieces = parts(combo)
                    var free = if (pieces >= DRILL_FROM) drill(at, length, pieces, if (random() < 1.0 / 3) 3 else 2) else at + length + AFTER_CALL
                    free = remind(free)
                    into += maxOf(Math.ceil((free - at) / 500.0).toLong() * 500, pace(into))
                }
            }
        }

        if (burn > 0) {
            // The non-stop finish: short combinations back to back, no drill, no reminders.
            val quick = speedCombos(if (round.theme == BoxingTheme.BUILD) themeCombos(BoxingTheme.BUILD) else roundCombos(round, plan.own))
            into = Math.ceil(maxOf(into, burnFrom + NONSTOP_LENGTH + 500) / 500.0).toLong() * 500
            while (into <= limit) {
                var pick = draw(random, quick.size)
                if (quick[pick].id == previous && quick.size > 1) pick = (pick + 1) % quick.size
                val combo = quick[pick]
                val length = comboLength(combo, plan.style)
                val at = place(start + into, length)
                into = at - start
                if (into > limit || overruns(into, combo, length)) break
                previous = combo.id
                call(at, combo)
                into += Math.ceil((length + AFTER_CALL) / 500.0).toLong() * 500
            }
        }

        beepInto(end)
        if (calls.phases) cues += TimerCue(end + VOICE_AFTER_BEEP, if (last) "complete" else "rest")
    }
    // Stable like the JavaScript sort.
    return cues.sortedBy { it.at }
}

/** How many combinations a run calls. A combination that is drilled counts once, one of the lifter's own too. */
fun comboCount(cues: List<TimerCue>): Int = cues.count { it.combo != null || soundCombo(it.sound) != null }

/**
 * The combinations called in [cues] up to [until] milliseconds after Start, and the punches they
 * asked for: each combination once for its call and once more for every "Again!" or "One more!"
 * after it.
 */
fun boxingTotals(cues: List<TimerCue>, own: List<OwnCombo>, until: Long): Pair<Int, Int> {
    var combos = 0
    var punches = 0
    var last: BoxingCombo? = null
    for (cue in cues) {
        if (cue.at > until) continue
        val called = cueCombo(cue, own)
        if (called != null) {
            last = called
            combos += 1
            punches += comboPunches(called)
        } else if (isAgain(cue.sound) && last != null) {
            punches += comboPunches(last)
        }
    }
    return combos to punches
}

/**
 * What a boxing run shows at a moment. [round]: the plan of the round the timer is in, or of the one
 * coming up during the lead-in and a rest. [focus]: speed or power in a split round, the non-stop
 * finish once it started; null without either, during the lead-in and during a rest. [combo]: the
 * combination called last in this round, null before the first call and outside a round. [go]: which
 * go at that combination this is, 1 for the first, 0 without a combination. [goes]: how many goes the
 * coach asks for, 1 for a short combination, 2 or 3 for one that is drilled.
 * [say]: the reminder or "Non-stop!" said in the last two seconds, as its text. [since] and [next]:
 * when the call on screen was said (the combination or its "Again!") and when the next call of the
 * round comes, the end of the round when none does. [step] and [steps]: in a build-up, where the
 * combination on screen stands in the ladder being climbed, 0 elsewhere.
 */
data class BoxingView(
    val round: BoxingRound,
    val focus: BoxingFocus?,
    val combo: BoxingCombo?,
    val go: Int = 0,
    val goes: Int = 0,
    val say: String? = null,
    val since: Long? = null,
    val next: Long? = null,
    val step: Int = 0,
    val steps: Int = 0,
)

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
    if (view.phase != TimerPhase.WORK) return BoxingView(round, null, null, 0, 0)
    val start = active - view.phaseElapsed
    val end = start + (view.phaseTotal ?: 0)
    var combo: BoxingCombo? = null
    var go = 0
    var goes = 0
    var say: String? = null
    var since: Long? = null
    var next: Long? = null
    var shownAt = -1
    val called = mutableListOf<BoxingCombo>()
    for (cue in cues) {
        if (cue.at < start || cue.at >= end) continue
        val combination = cueCombo(cue, plan.own)
        if (combination != null) {
            if (cue.at > active) {
                if (next == null) next = cue.at
                break
            }
            called += combination
            shownAt = called.lastIndex
            combo = combination
            go = 1
            goes = 1
            since = cue.at
        } else if (isAgain(cue.sound) && combo != null) {
            goes += 1
            if (cue.at <= active) {
                go += 1
                since = cue.at
            } else if (next == null) {
                next = cue.at
            }
        } else if ((isCoachCue(cue.sound) || cue.sound == "box-nonstop") && cue.at <= active && active - cue.at <= SAY_SHOWS) {
            say = BOXING_LINES[cue.sound]
        }
    }
    val burnFrom = (config.work - burnoutOf(round, config.work)) * SECOND
    val focus = if (view.phaseElapsed >= burnFrom) BoxingFocus.BURNOUT else focusAt(round, view.phaseElapsed)
    var step = 0
    var steps = 0
    if (round.theme == BoxingTheme.BUILD && shownAt >= 0) {
        // The ladder is read from the calls: the rest of the round's calls tell ladders with the same first steps apart.
        val all = cues.filter { it.at in start until end }.mapNotNull { cueCombo(it, plan.own) }
        ladderSteps(all.map { it.id }).getOrNull(shownAt)?.let { (at, of) ->
            step = at
            steps = of
        }
    }
    return BoxingView(round, focus, combo, go, goes, say, since, next ?: end, step, steps)
}

/**
 * Each call of a build-up round as its step in the ladder being climbed and the steps of that
 * ladder: (1, 5), (2, 5), ... A ladder starts at a combination that is a first step, and the calls
 * after it follow its steps. Ladders that start alike part after a step or two, and the one the
 * calls follow furthest is the one being climbed. A call that starts no ladder is (0, 0).
 */
fun ladderSteps(calls: List<String>): List<Pair<Int, Int>> {
    val list = mutableListOf<Pair<Int, Int>>()
    var at = 0
    while (at < calls.size) {
        fun followed(ladder: List<String>): Int {
            var count = 0
            while (count < ladder.size && at + count < calls.size && calls[at + count] == ladder[count]) count += 1
            return count
        }
        val ladder = BUILD_LADDERS.filter { it.first() == calls[at] }.maxByOrNull(::followed)
        if (ladder == null) {
            list += 0 to 0
            at += 1
            continue
        }
        val count = followed(ladder)
        for (step in 1..count) list += step to ladder.size
        at += count
    }
    return list
}

/**
 * "Jab only · Fast", "Mix · Steady · speed and power 30s", "3-punch combos · Fast · 2 picked", "Freestyle",
 * "Mix · Fast · reminders · 30s non-stop", "Build-up". A warm-up always has its reminders and does not say so.
 */
fun roundSummary(round: BoxingRound): String {
    val reminders = if (round.cues != BoxingCues.OFF && round.theme != BoxingTheme.WARMUP) " · reminders" else ""
    val burn = if (round.burnout > 0) " · ${round.burnout}s non-stop" else ""
    return when (round.theme) {
        BoxingTheme.FREESTYLE -> "${BoxingTheme.FREESTYLE.title}$reminders"
        BoxingTheme.BUILD -> "${BoxingTheme.BUILD.title}$reminders$burn"
        else -> {
            val split = if (round.split > 0) " · speed and power ${round.split}s" else ""
            val picked = if (round.pick.isNotEmpty()) " · ${round.pick.size} picked" else ""
            "${round.theme.title} · ${paceOf(round.every)}$split$picked$reminders$burn"
        }
    }
}

/** A plan where round [index] is replaced. */
fun withRound(plan: BoxingPlan, index: Int, round: BoxingRound): BoxingPlan =
    plan.copy(rounds = plan.rounds.mapIndexed { at, entry -> if (at == index) boxingRound(round, plan.own) else entry })

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

/** True for the moves of a combination of the lifter's own: 2 to 6 known moves. */
fun ownMovesValid(moves: List<String>): Boolean =
    moves.size in MIN_OWN_MOVES..MAX_OWN_MOVES && moves.all { id -> BOXING_MOVES.any { it.id == id } }

/** A plan with a new combination of the lifter's own, under the lowest free `own-<n>`. Unchanged when the moves are not one or there are 12. */
fun withOwn(plan: BoxingPlan, moves: List<String>): BoxingPlan {
    if (!ownMovesValid(moves) || plan.own.size >= MAX_OWN_COMBOS) return plan
    val taken = plan.own.map { it.id }.toSet()
    val id = generateSequence(1) { it + 1 }.map { "own-$it" }.first { it !in taken }
    return plan.copy(own = plan.own + OwnCombo(id, moves))
}

/** A plan where the combination [id] of the lifter's own has new [moves]. Unchanged when there is none or the moves are not one. */
fun withOwnMoves(plan: BoxingPlan, id: String, moves: List<String>): BoxingPlan {
    if (!ownMovesValid(moves) || plan.own.none { it.id == id }) return plan
    return plan.copy(own = plan.own.map { if (it.id == id) it.copy(moves = moves) else it })
}

/**
 * A plan without the combination [id] of the lifter's own. No round picks it any more, and a
 * "My combos" round with nothing left to call becomes a mix.
 */
fun withoutOwn(plan: BoxingPlan, id: String): BoxingPlan {
    if (plan.own.none { it.id == id }) return plan
    val left = plan.copy(own = plan.own.filter { it.id != id }, rounds = plan.rounds.map { it.copy(pick = it.pick - id) })
    return boxingPlan(left, left.rounds.size)
}

/**
 * A template laid over a number of rounds: its rounds over and over, so a ladder of four on
 * eight rounds climbs twice. The style, the stance, the room and the lifter's own combinations stay
 * those of [plan]: a template is about the rounds.
 */
fun templatePlan(template: List<BoxingRound>, rounds: Int, plan: BoxingPlan): BoxingPlan =
    plan.copy(rounds = List(maxOf(0, rounds)) { boxingRound(template[it % template.size], plan.own) })

/** [templatePlan] over a plan with nothing but a style. */
fun templatePlan(template: List<BoxingRound>, rounds: Int, style: BoxingStyle): BoxingPlan =
    templatePlan(template, rounds, BoxingPlan(emptyList(), style))

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
    BoxingTemplate("Pad work", "The coach builds every combination with you", listOf(BoxingRound(BoxingTheme.BUILD, 6, 0))),
    BoxingTemplate(
        "Full session",
        "Warm up, build, mix, burn out",
        listOf(
            BoxingRound(BoxingTheme.WARMUP, 8, 0),
            BoxingRound(BoxingTheme.TWO, 4, 0),
            BoxingRound(BoxingTheme.THREE, 6, 0, cues = BoxingCues.SOME),
            BoxingRound(BoxingTheme.BUILD, 6, 0),
            BoxingRound(BoxingTheme.MIX, 6, 30),
            BoxingRound(BoxingTheme.MIX, 4, 0, cues = BoxingCues.SOME, burnout = 30),
        ),
    ),
)
