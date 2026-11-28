package io.github.alexanderop.eisen.screens

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `features/history-and-data.md` on the `lifter` fixture: the log, Progress and the backup's entry points in Settings.
 * The file pickers of a backup and an update over old data stay with the journey.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HistoryScreenTest {
    @get:Rule val app = EisenApp("lifter")
    private val screen get() = app.screen

    @Test fun `the log lists the sessions and workouts, newest first`() {
        screen.tab("Progress")
        screen.has("Log", "Cindy", "AMRAP", "Stopped early", "Workout B", "Sets: 5, 5, 5, 5, 5")
        screen.has(Regex(".* · 14 rounds · 20:00"))
        assertEquals(json("""["Cindy", 14, 1200]"""), json(listOf("name", "rounds", "seconds").map { saved("timerSessions.0.$it") }.toString()))
        assertEquals(json("""["AMRAP", false]"""), json(listOf("name", "completed").map { saved("timerSessions.1.$it") }.toString()))
        assertEquals(12, saved("history").let { it as kotlinx.serialization.json.JsonArray }.size)
        assertEquals(json("\"Workout B\""), saved("history.0.name"))
    }

    @Test fun `Progress shows the training calendar and the charts`() {
        screen.tab("Progress")
        screen.tap("Charts")
        screen.has("Consistency", "Last 12 weeks", "Weeks in a row", "All lifts")
        screen.has(Regex(".*: Fran, 7:42"), Regex(".*: Boxing, 23:00"), Regex(".*: Workout A, 47 min"))
    }

    @Test fun `Settings lists saving, sending and restoring a backup`() {
        screen.tap("Settings")
        screen.has("Weights", "Your data", "Save backup", "Send backup", "Restore backup")
    }
}
