package io.github.alexanderop.eisen.feature.awards

/*
 * XP, levels, ranks and medals. Nothing here is stored: the program works them out from the history every time
 * (see `app/elm/AwardsSelectors.kt`), so they hold for workouts done before this build and need no migration. The one
 * thing saved is which medals the lifter has already seen.
 */

/** What earns XP, all in one place. Only real work counts: a finished workout, its sets, a record, a day of food, a weigh-in. */
object Xp {
    const val WORKOUT = 100
    const val SET = 10
    const val RECORD = 50
    const val TIMER = 50
    const val FOOD_DAY = 20
    const val WEIGHT = 10
}

/** A level costs [LEVEL_BASE] + level × [LEVEL_STEP] XP to get through: level 1 takes 520, level 12 takes 1,840. */
const val LEVEL_BASE = 400
const val LEVEL_STEP = 120

/** The level after this many levels is not counted further. Far past what a lifetime of training earns. */
const val MAX_LEVEL = 999

fun levelCost(level: Int): Long = LEVEL_BASE + level.toLong() * LEVEL_STEP

/** The [level] reached with some XP, the XP [into] it and what the whole level [cost]s. */
data class Level(val level: Int, val into: Long, val cost: Long) {
    /** How full the bar of the level is, 0 to 1. */
    val fraction: Float get() = if (cost <= 0) 1f else (into.toFloat() / cost).coerceIn(0f, 1f)
}

/** The level of [xp] XP. Everybody starts on level 1 with 0 XP. */
fun levelOf(xp: Long): Level {
    var level = 1
    var left = xp.coerceAtLeast(0)
    while (level < MAX_LEVEL && left >= levelCost(level)) {
        left -= levelCost(level)
        level++
    }
    return Level(level, left.coerceAtMost(levelCost(level)), levelCost(level))
}

/** The metal of a level: from [from] on. */
enum class Rank(val label: String, val from: Int) {
    TIN("Tin", 1),
    COPPER("Copper", 5),
    BRONZE("Bronze", 10),
    IRON("Iron", 20),
    STEEL("Steel", 35),
    TITANIUM("Titanium", 50),
}

fun rankOf(level: Int): Rank = Rank.entries.last { level >= it.from || it == Rank.TIN }

/** The next rank up, null on the top one. */
fun nextRank(level: Int): Rank? = Rank.entries.firstOrNull { it.from > level }

/** "3 levels to Bronze", or "Top rank" on Titanium. */
fun nextRankText(level: Int): String {
    val next = nextRank(level) ?: return "Top rank"
    val levels = next.from - level
    return "$levels ${if (levels == 1) "level" else "levels"} to ${next.label}"
}

/** The metal of a medal, plainest first. Iron is the app's own and the top one. */
enum class Tier(val label: String) { BRONZE("Bronze"), SILVER("Silver"), GOLD("Gold"), IRON("Iron") }

/** The three shelves of the medal case. Each has its shape: a round medal on a ribbon, a hexagon, a banner. */
enum class MedalGroup(val title: String) { RECORDS("Records"), CONSISTENCY("Consistency"), MILESTONES("Milestones") }

/**
 * One medal of the case, earned or not. [glyph] is the short mark on it ("PR", "4W", "10t"). [earnedAt] is when it
 * was earned, null while it is locked; [progress] (0 to 1) and [progressText] ("7 of 12") say how far it is.
 * [how] says how it was earned, or how to earn it.
 */
data class Medal(
    val id: String,
    val group: MedalGroup,
    val tier: Tier,
    val glyph: String,
    val name: String,
    val earnedAt: Long?,
    val progress: Float,
    val progressText: String,
    val how: String,
) {
    val earned: Boolean get() = earnedAt != null
}

/** Everything the medal case shows: the XP, the level it makes and every medal in the case's order. */
data class Awards(val xp: Long, val level: Level, val medals: List<Medal>) {
    val rank: Rank get() = rankOf(level.level)
    val earned: List<Medal> get() = medals.filter { it.earned }
}

/**
 * What a saved workout brought: the [xp] it added, the level [before] and [after] it, and the medals that are new, the
 * ones of the [records] it set first.
 */
data class SavedAwards(val xp: Long, val before: Level, val after: Level, val medals: List<Medal>, val records: Set<String> = emptySet()) {
    val levelUp: Boolean get() = after.level > before.level
}

// The medals: what each asks for and the metal it is in. One place, so the numbers can be read side by side.

/** A record medal's metal by how many records of the lift there are: 1, 3, 6, 12. */
val RECORD_TIERS: List<Pair<Int, Tier>> = listOf(1 to Tier.BRONZE, 3 to Tier.SILVER, 6 to Tier.GOLD, 12 to Tier.IRON)

/** Weeks in a row, Monday to Sunday, with at least one workout or interval timer. */
val STREAK_WEEKS: List<Pair<Int, Tier>> = listOf(4 to Tier.BRONZE, 12 to Tier.SILVER, 26 to Tier.GOLD)

/** The workouts of a perfect week, Monday to Sunday: what a week of 5×5 asks for. */
const val PERFECT_WEEK = 3

/** Days in a row with food logged. */
const val FOOD_STREAK_DAYS = 7

/** Finished workouts. */
val WORKOUT_MILESTONES: List<Pair<Int, Tier>> = listOf(10 to Tier.BRONZE, 50 to Tier.SILVER, 100 to Tier.GOLD, 250 to Tier.IRON)

/** Tonnes lifted in all: weight × reps of every set with a weight. */
val TONNE_MILESTONES: List<Pair<Int, Tier>> = listOf(10 to Tier.SILVER, 100 to Tier.GOLD)

/** The squats that count for the bodyweight squat: every set done with at least the lifter's own weight. */
val SQUAT_IDS: Set<String> = setOf("squat", "frontSquat", "hackSquat")

/** The metal of a record medal with [records] records. */
fun recordTier(records: Int): Tier = RECORD_TIERS.lastOrNull { records >= it.first }?.second ?: Tier.BRONZE
