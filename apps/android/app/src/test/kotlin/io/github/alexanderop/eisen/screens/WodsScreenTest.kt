package io.github.alexanderop.eisen.screens

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `features/wods.md` on the `lifter` fixture: the list with the lifter's own WOD, the search, a detail, Fran run against
 * its best to a record, a first run of Grace and a run ended without saving. The clock is the test's: the lead-in and the
 * tap guard (0.8 s) pass with [later].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WodsScreenTest {
    @get:Rule val app = EisenApp("lifter")
    private val screen get() = app.screen

    private fun sessions(wod: String) = saved("timerSessions").let { it as kotlinx.serialization.json.JsonArray }.count { "\"wodId\":\"$wod\"" in it.toString() }

    /** A tick of a For Time item, a second after the one before: a tap within the guard is a bounce. */
    private fun tick(item: String) {
        screen.later(1)
        screen.tap(item)
    }

    @Test fun `the list, an own WOD, the search and a detail`() {
        screen.tab("Timer")
        screen.has("Benchmarks", "My WODs", "Best 7 rounds", "Best 7:42")
        screen.tap("Garage burner")
        screen.has("Start", "10 × Kettlebell Swing 16kg", "BEST")
        screen.tap("Back to the WODs")
        screen.has("Benchmarks")

        screen.tap("Search WODs")
        screen.type("fr")
        screen.has("21-15-9 · For Time")
        screen.tap("Fran")
        screen.has("Start", "Cap 10:00", "BEST", "7:42")
    }

    @Test fun `Fran run against the best to a record, a first run of Grace, and a run ended without saving`() {
        screen.tab("Timer")
        screen.tap("Fran")
        screen.tap("Start")
        screen.later(10)
        screen.has("WORK · 1/3", "Best 7:42", "On pace")
        screen.tap("21 × Thruster 43kg done. Next: 21 × Pull-ups.")
        screen.has("21 × Pull-ups done. Next: round 2.")
        screen.has(Regex("▲ .* ahead"))
        tick("21 × Pull-ups done. Next: round 2.")
        screen.has("WORK · 2/3")
        tick("15 × Thruster 43kg done. Next: 15 × Pull-ups.")
        tick("15 × Pull-ups done. Next: round 3.")
        screen.has("WORK · 3/3")
        tick("9 × Thruster 43kg done. Next: 9 × Pull-ups.")
        tick("9 × Pull-ups done. That finishes the WOD.")
        screen.has("Completed", "New record", "Previous best 7:42", "3 of 3 rounds", "Not finished? Undo")
        screen.has(Regex(".* faster than your best"))
        assertEquals(json("""["wod-fran", true, 3]"""), json(listOf("wodId", "completed", "rounds").map { saved("timerSessions.0.$it") }.toString()))
        assertEquals(6, saved("timerSessions.0.splits").let { it as kotlinx.serialization.json.JsonArray }.size)

        // The result ignores taps for a second after it shows.
        screen.later(1)
        screen.tap("Done")
        screen.has("Fran")
        screen.tap("Back to the WODs")
        screen.tap("Start Grace")
        screen.later(10)
        screen.has(Regex("WORK.*"))
        screen.has("First run: this sets your best")
        screen.tap("30 × Clean and Jerk 61kg done. That finishes the WOD.")
        screen.has("Completed")
        assertEquals(json("""["wod-grace", true]"""), json(listOf("wodId", "completed").map { saved("timerSessions.0.$it") }.toString()))
        assertEquals(1, saved("timerSessions.0.splits").let { it as kotlinx.serialization.json.JsonArray }.size)

        screen.later(1)
        screen.tap("Again")
        screen.later(10)
        screen.has(Regex("WORK.*"))
        screen.tap("End")
        // The end sheet ignores taps for 0.6 seconds after it opens.
        screen.later(1)
        screen.tap("Discard")
        screen.has("Benchmarks")
        assertEquals(1, sessions("wod-grace"))
    }
}
