package io.github.alexanderop.eisen.feature.workout

import kotlin.math.max
import io.github.alexanderop.eisen.core.roundTo

class PlateLoad(
    /** Plates for one side of the bar, heaviest first. */
    val plates: List<Double>,
    /** Weight per side that the available plates cannot make up. */
    val remainder: Double,
)

fun platesPerSide(total: Double, bar: Double, available: List<Double>): PlateLoad {
    var rest = (total - bar) / 2
    val plates = mutableListOf<Double>()
    if (rest <= 0) return PlateLoad(plates, 0.0)

    for (plate in available.sortedDescending()) {
        while (rest >= plate - 1e-9) {
            plates += plate
            rest -= plate
        }
    }
    return PlateLoad(plates, max(0.0, roundTo(rest, 0.01)))
}

data class WarmupSet(val weight: Double, val reps: Int, val sets: Int)

private val RAMP = listOf(0.4 to 5, 0.6 to 3, 0.8 to 2)

/**
 * Two sets with the empty bar, then lighter sets that ramp up to the work weight.
 * A machine has no bar, so it starts with the first ramp set.
 */
fun warmupSets(work: Double, bar: Double, step: Double): List<WarmupSet> {
    if (work <= bar) return emptyList()
    val result = mutableListOf<WarmupSet>()
    if (bar > 0) result += WarmupSet(bar, 5, 2)
    var last = bar
    for ((fraction, reps) in RAMP) {
        val weight = roundTo(work * fraction, step)
        if (weight > last && weight < work) {
            result += WarmupSet(weight, reps, 1)
            last = weight
        }
    }
    return result
}
