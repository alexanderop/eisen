package io.github.alexanderop.eisen.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The awards on the `lifter` fixture: the level chip on Today, the medal case under Progress, a record during the
 * workout and the medals and XP on the card of the saved workout. Confetti and the flip of the medals are drawing; the
 * emulator's screenshots show them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AwardsScreenTest {
    @get:Rule val app = EisenApp("lifter")
    private val screen get() = app.screen

    @Test fun `the level chip on Today opens Progress on the medal case`() {
        screen.has("Today", "Level 7, Copper. Medals")
        screen.tap("Level 7, Copper. Medals")
        screen.has("Progress", "Medals", "Lv 7 · Copper", "3 levels to Bronze · 11 of 18 medals", "RECORDS", "CONSISTENCY", "MILESTONES")
        // What was earned before now counts as seen, and stays so in the file.
        assertEquals(11, saved("seenMedals").let { it as kotlinx.serialization.json.JsonArray }.size)
    }

    @Test fun `the medal case shows earned medals with their day and locked ones with how far they are`() {
        screen.tab("Progress")
        screen.tap("Medals")
        screen.has("Hack Squat 140kg", "Dips 35 reps", "4 weeks in a row", "Perfect week", "10 workouts", "10 tonnes lifted")
        screen.has("12 weeks in a row", "4 of 12", "Food logged 7 days", "0 of 7", "50 workouts", "12 of 50", "100 tonnes lifted", "80 of 100 t")
        // No weight logged: the bodyweight squat is not in the case.
        screen.gone("Bodyweight squat")

        screen.tap("12 weeks in a row")
        screen.has("Consistency · Silver", "A workout or an interval timer every week, Monday to Sunday, 12 weeks in a row.")
        screen.tap("Close")
        screen.tap("Perfect week")
        screen.has("Consistency · Gold", "Earned Oct 30")
    }

    @Test fun `a record during the workout is said, and the saved workout reveals the medals and the XP`() {
        screen.tap("Start workout")
        screen.has("Hack Squat", "5×5 145kg")
        for (set in 0 until 5) {
            screen.tap("5", set)
            if (set < 4) screen.tap("Minimise rest timer")
            screen.later(1)
        }
        // 145 kg with every set done beats the 140 kg record: TalkBack hears it, the card says it.
        screen.has("New record: Hack Squat")
        screen.back()
        screen.has("New record")

        screen.tap("Finish")
        screen.tap("Finish workout")
        screen.has("Workout saved", "You earned", "Hack Squat 145kg", "+200 XP", "Lv 7")
        // Eleven medals had never been seen: three flip in, the rest are counted.
        screen.has("and 8 more medals")
        assertTrue(saved("seenMedals").toString().contains("record:hackSquat"))

        // Dismissed, the card goes with its medals.
        screen.tap("Dismiss")
        screen.gone("You earned")
    }
}
