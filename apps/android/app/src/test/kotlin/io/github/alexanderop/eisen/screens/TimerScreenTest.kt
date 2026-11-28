package io.github.alexanderop.eisen.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `features/interval-timers.md` on the `short-timers` fixture: a setup, saved timers run to their end on the test's clock
 * with the voice, AMRAP's taps, For Time's hold, a run ended early (saved, then discarded), and "What was it?" on the
 * result and in History. The screen going off, the foreground service and its notification, and a killed process stay
 * with the journey.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerScreenTest {
    @get:Rule val app = EisenApp("short-timers")
    private val screen get() = app.screen

    private fun sessionCount() = saved("timerSessions").let { it as kotlinx.serialization.json.JsonArray }.size

    private fun session(vararg keys: String) = json(keys.map { saved("timerSessions.0.$it") }.toString())

    private fun timers() {
        screen.tab("Timer")
        screen.tap("Timers")
    }

    @Test fun `the Tabata card opens its setup on the defaults`() {
        timers()
        screen.tap("Tabata")
        screen.has("Start", "Work", "Rest", "Rounds", "Total 3:50", "Voice", "Save as preset")
        screen.tap("Back to the timers")
        screen.has("Saved timers")
    }

    @Test fun `a saved Tabata runs its lead-in, work and rest to Completed, with the voice`() {
        timers()
        screen.tap("Start Quick Tabata")
        screen.has("GET READY", "Round 1 of 2", "Next: Work 10s")
        screen.later(10)
        screen.has("WORK", "Round 1 of 2", "Next: Rest 5s")
        screen.later(10)
        screen.has("REST")
        screen.later(20)
        screen.has("Quick Tabata", "0:25", "2 rounds", "Completed", "Again", "Done", "Don't count this run")
        assertEquals(json("""["Quick Tabata", true, 2, 25]"""), session("name", "completed", "rounds", "seconds"))
        assertSaid("get-ready", "round-1", "rest", "last-round", "complete")
    }

    @Test fun `a saved EMOM runs to Completed`() {
        // The journey runs it with the screen off; that part is the phone's.
        timers()
        screen.tap("Start Quick EMOM")
        screen.has("GET READY")
        screen.later(32)
        screen.has("Completed")
        assertSaid("round-1", "last-round", "complete")
        assertEquals(json("""["Quick EMOM", true, 2]"""), session("name", "completed", "rounds"))
    }

    @Test fun `AMRAP counts a round a tap, a bounce not`() {
        timers()
        screen.tap("Start Quick AMRAP")
        screen.later(10)
        screen.has("WORK")
        screen.tap("0 rounds. Tap to count a round.")
        screen.has("1 round. Tap to count a round.")
        // Within 0.8 seconds of the last tap: a bounce.
        screen.later(0.3)
        screen.tap("1 round. Tap to count a round.")
        screen.has("1 round. Tap to count a round.")
        screen.later(1)
        screen.tap("1 round. Tap to count a round.")
        screen.has("2 rounds. Tap to count a round.")
        screen.later(30)
        screen.has("Completed")
        assertEquals(json("""["Quick AMRAP", true, 2, 30]"""), session("name", "completed", "rounds", "seconds"))
    }

    @Test fun `For Time is done when the lifter holds to finish`() {
        timers()
        screen.tap("Start Quick For Time")
        screen.later(10)
        screen.has("WORK", "Cap 1:00", "Hold to finish")
        screen.tap("Hold to finish")
        screen.has("Keep holding to finish")
        screen.hold("Keep holding to finish")
        screen.has("Completed")
        assertEquals(json("""["Quick For Time", true, "forTime"]"""), session("name", "completed", "config.mode"))
    }

    @Test fun `a run ended early is saved as Stopped early, and one discarded is not filed`() {
        timers()
        screen.tap("Start Quick Tabata")
        screen.later(40)
        screen.has("Completed")
        screen.later(1)
        screen.tap("Done")
        // The Tabata card opens on what was started last.
        screen.has("2 × 10s / 5s")
        screen.tap("Tabata")
        screen.tap("Start")
        screen.later(10)
        screen.has("WORK")
        screen.tap("End")
        screen.has("End timer?", "Keep going", "Save and end", "Discard")
        // The end sheet ignores taps for 0.6 seconds after it opens.
        screen.later(1)
        screen.tap("Save and end")
        screen.has("Stopped early")
        assertEquals(2, sessionCount())
        assertEquals(json("""["Tabata", false]"""), session("name", "completed"))

        screen.later(1)
        screen.tap("Again")
        screen.later(10)
        screen.has("WORK")
        screen.tap("End")
        screen.later(1)
        screen.tap("Discard")
        screen.has("Back to the timers")
        assertEquals(2, sessionCount())
        screen.tab("Progress")
        screen.has("Log", "Stopped early")
    }

    @Test fun `What was it - on the result, in the log, on the calendar, and changed in its sheet`() {
        timers()
        screen.tap("Start Quick Tabata")
        screen.later(40)
        screen.has("Completed", "What was it?", "Boxing", "Jump rope", "Battle rope", "Cross trainer", "Rowing", "Bike", "Running")
        assertEquals(json("null"), saved("timerSessions.0.activity"))
        // The result ignores taps for a second, its chips too.
        screen.later(1)
        screen.tap("Jump rope")
        assertEquals(json("\"jumpRope\""), saved("timerSessions.0.activity"))
        screen.tap("Done")
        screen.has("Saved timers")

        screen.tab("Progress")
        screen.has("Log")
        screen.has(Regex("Jump rope · .* · 2 rounds · 25s"))
        screen.tap("Charts")
        screen.has("Consistency")
        screen.tap(Regex(".*: Quick Tabata \\(Jump rope\\), 25s"))
        screen.has("Quick Tabata (Jump rope), 25s")
        screen.tap("Log")
        screen.tap("Quick Tabata")
        screen.has("What was it?", "Done", "Running")
        screen.tap("Battle rope")
        assertEquals(json("\"battleRope\""), saved("timerSessions.0.activity"))
        screen.tap("Battle rope")
        assertTrue(saved("timerSessions.0").toString(), "activity" !in saved("timerSessions.0").toString())
        screen.back()
        screen.has("Log")
        screen.has(Regex("[A-Z][a-z]{2}, .* · 2 rounds · 25s"))
        screen.gone("What was it?")
    }
}
