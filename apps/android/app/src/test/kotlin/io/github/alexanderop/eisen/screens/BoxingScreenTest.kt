package io.github.alexanderop.eisen.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `features/boxing.md` on the `short-timers` fixture: the setup, a call and an end early, a saved round to the bell, the
 * minutes left in a round of 3:00, and the history row. The combinations come from a new seed every run: the test holds
 * the screen and the saved session to the same counts, not to fixed ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BoxingScreenTest {
    @get:Rule val app = EisenApp("short-timers")
    private val screen get() = app.screen

    /** The number in the first text on screen that [pattern] matches whole; the result spells one out ("One combination called", "About one punch"). */
    private fun count(pattern: Regex): Int = screen.texts().let { shown ->
        checkNotNull(shown.firstNotNullOfOrNull { pattern.matchEntire(it)?.groupValues?.get(1)?.let { n -> if (n.equals("one", ignoreCase = true)) 1 else n.toInt() } }) { "nothing matches $pattern: $shown" }
    }

    private fun timers() {
        screen.tab("Timer")
        screen.tap("Timers")
    }

    @Test fun `the setup, a call in the first round, and the run ended early`() {
        timers()
        screen.tap("Boxing")
        screen.has("Start", "6 rounds · 23:00 total", "Jab only")
        screen.tap("Start")
        screen.has("GET READY")
        // The lead-in, then the round and its theme are said; the first call comes about 6 seconds in.
        screen.later(17)
        screen.has(Regex("[1-6]B?( – [1-6]B?)*"))
        screen.has("ROUND 1/6  Jab only")
        assertSaid("get-ready", "round-1", "box-theme-jab")
        assertTrue(said().toString(), said().any { Regex("box-.*-n").matches(it) })

        screen.tap("End")
        screen.later(1)
        screen.tap("Save and end")
        screen.has("Stopped early", "Boxing", "0 rounds", "Round 1: Jab only · Fast")
        val combos = count(Regex("([0-9]+|One) combinations? called"))
        val punches = count(Regex("About ([0-9]+|one) punch(es)?"))
        assertTrue(combos > 0)
        assertEquals(json("""["boxing", false, $combos, $punches]"""), json(listOf("config.mode", "completed", "combos", "punches").map { saved("timerSessions.0.$it") }.toString()))
    }

    @Test fun `a saved round to the bell`() {
        timers()
        screen.tap("Start Quick boxing")
        screen.has("GET READY")
        screen.later(45)
        screen.has("Completed", "1 round")
        val combos = count(Regex("([0-9]+|One) combinations? called"))
        assertEquals(json("""["Quick boxing", true, 1, $combos]"""), json(listOf("name", "completed", "rounds", "combos").map { saved("timerSessions.0.$it") }.toString()))
        assertSaid("complete")
    }

    @Test fun `the minutes left are the round's, and the history row counts the punches`() {
        timers()
        screen.tap("Start Boxing 3:00 calls")
        screen.has("GET READY")
        // 10 seconds of lead-in and a minute of the round: its clock shows 1:59.
        screen.later(71)
        screen.has(Regex("1:5[0-9]"))
        screen.has("ROUND 1/1  2-punch combos")
        assertSaid("minutes-left-2")
        screen.later(130)
        screen.has("Completed")
        assertSaid("round-1", "minutes-left-2", "minutes-left-1", "thirty-seconds", "ten-seconds", "complete")
        assertEquals(json("""["Boxing 3:00 calls", true, 180]"""), json(listOf("name", "completed", "seconds").map { saved("timerSessions.0.$it") }.toString()))

        screen.later(1)
        screen.tap("Done")
        screen.tab("Progress")
        screen.has("Boxing 3:00 calls")
        screen.has(Regex(".* · about [0-9]+ punch(es)?"))
    }
}
