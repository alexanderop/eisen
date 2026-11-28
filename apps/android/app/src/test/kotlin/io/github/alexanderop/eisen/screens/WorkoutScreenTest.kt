package io.github.alexanderop.eisen.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `features/workout.md` on the JVM: start, log a set, the rest counting down, rate it, finish early; pick the reps by
 * holding a set, a double tap, the rest bar. The notification, the vibration and the rest that ends in a killed process
 * stay with the journey.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutScreenTest {
    @get:Rule val app = EisenApp()
    private val screen get() = app.screen

    /** The countdown of the rest screen, `1:27`. */
    private fun restLeft(): String = screen.texts().first { Regex("[0-9]:[0-9]{2}").matches(it) }

    @Test fun `start, log a set, rest, rate it and finish early`() {
        screen.tap("Start workout")
        screen.has("Finish", "Workout A", "Warmup", "Hack Squat", "5×5 80kg")

        screen.tap("5", 0)
        screen.has("Set 1 of 5 done", "Rest", "of 1:30", "All reps done.", "How many more could you have done?", "Up next: set 2 of 5")
        assertEquals("1:30", restLeft())
        screen.later(3)
        assertEquals("1:27", restLeft())

        screen.tap("2 reps in reserve")
        assertEquals(json("\"hackSquat\""), saved("active.exercises.0.id"))
        assertEquals(json("[5, null, null, null, null]"), saved("active.exercises.0.sets"))
        assertEquals(json("[2]"), saved("active.exercises.0.rir"))

        screen.tap("Minimise rest timer")
        screen.tap("Finish")
        screen.has("Finish early?", "Keep going")
        screen.tap("Finish workout")
        screen.has("Workout saved", "1 of 18 sets")
        assertEquals(json("null"), saved("active"))
        assertEquals(json("\"Workout A\""), saved("history.0.name"))
        assertEquals(json("[5, null, null, null, null]"), saved("history.0.exercises.0.sets"))
        assertEquals(json("[2]"), saved("history.0.exercises.0.rir"))
    }

    @Test fun `a held set picks its reps, the rest screen corrects them, and the hint goes for good`() {
        screen.tap("Start workout")
        screen.has("Tap: all reps. Hold: pick the reps.")
        screen.hold("5")
        screen.has("5 reps", "4 reps", "3 reps", "2 reps", "1 rep", "0 reps")
        screen.back()
        screen.gone("4 reps")
        assertEquals(json("[null, null, null, null, null]"), saved("active.exercises.0.sets"))

        screen.hold("5")
        screen.tap("3 reps")
        screen.has("Set 1 of 5 done", "Missed reps. Rest longer.", "of 5:00")
        assertEquals(json("true"), saved("pickedReps"))
        assertEquals(json("[3, null, null, null, null]"), saved("active.exercises.0.sets"))

        screen.tap("4 reps")
        screen.has("80kg × 4", "Missed reps. Rest longer.")
        assertEquals(json("[4, null, null, null, null]"), saved("active.exercises.0.sets"))

        app.restart()
        screen.has("Finish")
        screen.gone("Tap: all reps. Hold: pick the reps.")

        // A slide from set 2 to `4 reps` and letting go logs 4 in one step. After the restart the rest is a bar; set 1 reads 4, set 2 is the first 5.
        screen.slide("5", 0, "4 reps")
        screen.has("Set 2 of 5 done", "Missed reps. Rest longer.")
        assertEquals(json("[4, 4, null, null, null]"), saved("active.exercises.0.sets"))
    }

    @Test fun `a double tap logs the set once, and the rest bar opens its screen after Do later and a restart`() {
        screen.tap("Start workout")
        // Two taps 150 ms apart, a bouncing thumb: the second is inside the tap guard and counts nothing.
        screen.doubleTap("5", 0, gap = 0.15)
        screen.has("Set 1 of 5 done", "All reps done.", "Rest", "of 1:30")
        assertEquals(json("[5, null, null, null, null]"), saved("active.exercises.0.sets"))

        screen.tap("Minimise rest timer")
        // Set 1 reads its 5 reps too: the second 5 is set 2.
        screen.hold("5", 1)
        screen.tap("3 reps")
        screen.has("Set 2 of 5 done", "Missed reps. Rest longer.")
        screen.tap("4 reps")
        screen.has("80kg × 4")
        assertEquals(json("[5, 4, null, null, null]"), saved("active.exercises.0.sets"))

        screen.tap("Minimise rest timer")
        screen.tap("Missed reps. Rest longer.")
        screen.has("Set 2 of 5 done")
        screen.tap("Minimise rest timer")
        screen.tap("5×5 80kg")
        screen.tap("Do later")
        screen.tap("Missed reps. Rest longer.")
        screen.has("Set 2 of 5 done", "Hack Squat")

        screen.tap("Minimise rest timer")
        app.restart()
        screen.tap("Missed reps. Rest longer.")
        screen.has("Set 2 of 5 done")
        assertTrue(saved("active") != json("null"))
    }
}
