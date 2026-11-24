package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.core.*
import io.github.alexanderop.eisen.app.state.*
import io.github.alexanderop.eisen.app.elm.*
import io.github.alexanderop.eisen.feature.share.*
import io.github.alexanderop.eisen.feature.progress.*
import io.github.alexanderop.eisen.feature.timer.*
import io.github.alexanderop.eisen.feature.timer.boxing.*
import io.github.alexanderop.eisen.feature.timer.wod.*
import io.github.alexanderop.eisen.feature.workout.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.util.Base64

/**
 * The "shared workouts" cases of `apps/web/src/domain/domain.test.ts`, and links made by the PWA.
 * A link made by one app must open in the other, so the format is checked against the PWA's own output.
 */
class ShareTest {
    private val all = listOf<Int?>(5, 5, 5, 5, 5)

    private fun workoutWith(sets: Map<String, List<Int?>>): Workout {
        val routine = defaultRoutines().first { it.id == "A" }
        val workout = createWorkout(routine, ::catalogExercise, freshState(WeightUnit.KG).progress, WeightUnit.KG, 0, "w1")
        return workout.copy(exercises = workout.exercises.map { it.copy(sets = sets[it.id] ?: it.sets) })
    }

    private val workout = workoutWith(mapOf("hackSquat" to all, "bench" to listOf(5, 5, 4, null, null), "dips" to listOf(12, 9, 7)))
        .copy(finishedAt = 50 * 60000L, name = "Größer & stärker 💪")
    private val shared = toShared(workout, setOf("hackSquat"))

    private fun btoa(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())

    @Test fun `leaves out skipped exercises and marks records`() {
        assertEquals(listOf("Hack Squat", "Bench Press", "Dips"), shared.exercises.map { it.name })
        assertEquals(listOf(true, false, false), shared.exercises.map { it.record })
        val dips = shared.exercises[2]
        assertEquals(Triple(true, 0.0, null), Triple(dips.bodyweight, dips.weight, dips.targetReps))
        assertEquals(50L, shared.minutes)
    }

    @Test fun `adds up volume, sets and reps`() {
        assertEquals(SharedStats(volume = 80.0 * 25 + 80 * 14, sets = 11, reps = 25 + 14 + 28), sharedStats(shared))
    }

    @Test fun `survives the trip through a link`() {
        val url = sharedUrl("https://example.com/eisen/#old", shared)
        assertTrue(url, Regex("^https://example\\.com/eisen/#w=[\\w-]+$").matches(url))
        assertEquals(shared, sharedFromHash("#" + URI(url).rawFragment))
    }

    @Test fun `rejects links that hold no workout`() {
        assertNull(sharedFromHash(""))
        assertNull(sharedFromHash("#w="))
        assertNull(decodeShared("not base64!"))
        assertNull(decodeShared(btoa("""{"a":1}""")))
        assertNull(decodeShared(btoa("""[1,"A",0,1,1,[]]""")))
        assertNull(decodeShared(btoa("""[1,"A",0,1,1,[["Squat",0,80,5,0,[5,"x"]]]]""")))
        assertNull(decodeShared(btoa("""[2,"A",0,1,1,[["Squat",0,80,5,0,[5]]]]""")))
        assertNull(decodeShared(encodeShared(shared.copy(name = "x".repeat(81)))))
    }

    @Test fun `rejects JSON that the browser would not parse`() {
        assertNull(decodeShared(btoa("""[1,"A",0,1,1,[["Squat",yes,80,5,0,[5]]]]""")))
        assertNull(decodeShared(btoa("""[1,"A",0,1,1,[["Squat",0,80,5,0,[5]]],]""")))
        // Only spaces, including the no-break and zero-width ones that JavaScript's trim removes.
        assertNull(decodeShared(btoa("[1,\" \u00A0\uFEFF\u3000 \",0,1,1,[[\"Squat\",0,80,5,0,[5]]]]")))
    }

    /** What the PWA's `sharedUrl` gives for the workout of the test above. */
    private val webBasic = "https://alexanderop.github.io/eisen/#w=WzEsIkdyw7bDn2VyICYgc3TDpHJrZXIg8J-SqiIsMCwwLDUwLFtbIkhhY2sgU3F1YXQiLDAsODAsNSwxLFs1LDUsNSw1LDVdXSxbIkJlbmNoIFByZXNzIiwwLDgwLDUsMCxbNSw1LDQsLTEsLTFdXSxbIkRpcHMiLDEsMCwwLDAsWzEyLDksN11dXV0"

    @Test fun `makes the same link as the PWA`() {
        assertEquals(webBasic, sharedUrl("https://alexanderop.github.io/eisen/", shared))
        assertEquals(shared, sharedFromHash("#" + URI(webBasic).rawFragment))
    }

    /**
     * Escapes, a lone surrogate, rounding of halves and a start between two minutes.
     * The PWA's `encodeShared` gave [webTricky] for [tricky].
     */
    private val tricky = SharedWorkout(
        name = "Quote \" back\\slash\nnew\ttab\u0001ctl   / <b> \uD83D lone",
        unit = WeightUnit.LB,
        startedAt = 1763740830000,
        minutes = null,
        exercises = listOf(
            SharedExercise("Rounding 1.005", false, 1.005, 5, listOf(5), false),
            SharedExercise("Rounding 82.555", false, 82.555, 3, listOf(3, 2, 0), true),
            SharedExercise("Float 0.1+0.2", false, 0.1 + 0.2, null, listOf(null, 20), false),
            SharedExercise("Big 9999.999", false, 9999.999, 30, List(10) { 30 }, false),
            SharedExercise("Tiny", false, 0.004, 1, listOf(1), false),
        ),
    )
    private val webTricky = "WzEsIlF1b3RlIFwiIGJhY2tcXHNsYXNoXG5uZXdcdHRhYlx1MDAwMWN0bCDigKggLyA8Yj4gXHVkODNkIGxvbmUiLDEsMjkzOTU2ODEsMCxbWyJSb3VuZGluZyAxLjAwNSIsMCwxLDUsMCxbNV1dLFsiUm91bmRpbmcgODIuNTU1IiwwLDgyLjU2LDMsMSxbMywyLDBdXSxbIkZsb2F0IDAuMSswLjIiLDAsMC4zLDAsMCxbLTEsMjBdXSxbIkJpZyA5OTk5Ljk5OSIsMCwxMDAwMCwzMCwwLFszMCwzMCwzMCwzMCwzMCwzMCwzMCwzMCwzMCwzMF1dLFsiVGlueSIsMCwwLDEsMCxbMV1dXV0"

    @Test fun `writes escapes and rounding exactly like the PWA`() {
        assertEquals(webTricky, encodeShared(tricky))
        val decoded = decodeShared(webTricky)
        assertEquals(
            tricky.copy(
                startedAt = 1763740860000,
                exercises = tricky.exercises.zip(listOf(1.0, 82.56, 0.3, 10000.0, 0.0)) { exercise, weight -> exercise.copy(weight = weight) },
            ),
            decoded,
        )
    }
}
