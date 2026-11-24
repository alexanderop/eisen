package io.github.alexanderop.eisen.feature.workout

/*
 * Reps in reserve (RIR): after a set the lifter says how many more reps were left in it, with one tap
 * on the rest screen. Never required. Estimates are good close to failure and poor far from it, so
 * only the last set counts for progression, and only to add, see [applyProgression].
 */

/** The highest rating, shown as "4+": four or more. */
const val MAX_RIR = 4

/** A last set rated this or higher doubles the next weight jump. */
const val RIR_BOOST_FROM = 3
const val RIR_BOOST_FACTOR = 2

/** The rating of set [set], null when it has none. */
fun rirOf(log: ExerciseLog, set: Int): Int? = log.rir.getOrNull(set)?.takeIf { it in 0..MAX_RIR }

/**
 * The log with set [set] rated [rir], or unrated with null. The list is filled up with null to the set,
 * and is empty again once no set is rated, so a log without ratings is saved without the field.
 */
fun withRir(log: ExerciseLog, set: Int, rir: Int?): ExerciseLog {
    val next = List(maxOf(log.rir.size, set + 1)) { at -> if (at == set) rir else log.rir.getOrNull(at) }
    return log.copy(rir = if (next.all { it == null }) emptyList() else next)
}

/** Only a logged set with a target count of reps: a max-reps set goes to failure anyway, and a hold has no reps. */
fun canRate(log: ExerciseLog, set: Int): Boolean = !log.timed && log.targetReps != null && log.sets.getOrNull(set) != null

/** "0" to "3", and "4+". */
fun rirText(rir: Int): String = if (rir >= MAX_RIR) "$MAX_RIR+" else rir.toString()

/** "RIR 2 2 1 –", a set without a rating as "–". Null when no set is rated. */
fun rirLine(log: ExerciseLog): String? {
    val ratings = log.sets.indices.map { rirOf(log, it) }
    if (ratings.all { it == null }) return null
    return "RIR " + ratings.joinToString(" ") { it?.let(::rirText) ?: "–" }
}

/** " · RIR 2 2 1 –" after the reps, empty when no set is rated. */
fun rirSuffix(log: ExerciseLog): String = rirLine(log)?.let { " · $it" } ?: ""

/** The log with set [set] logged as [reps]. A cleared set loses its rating, a corrected count keeps it. */
fun withReps(log: ExerciseLog, set: Int, reps: Int?): ExerciseLog {
    val logged = log.copy(sets = log.sets.mapIndexed { at, value -> if (at == set) reps else value })
    return if (reps == null && log.rir.getOrNull(set) != null) withRir(logged, set, null) else logged
}

/** The last logged set has [RIR_BOOST_FROM] or more reps in reserve. */
internal fun boosts(log: ExerciseLog): Boolean {
    if (log.timed) return false
    val last = log.sets.indexOfLast { it != null }
    return last != -1 && (rirOf(log, last) ?: 0) >= RIR_BOOST_FROM
}
