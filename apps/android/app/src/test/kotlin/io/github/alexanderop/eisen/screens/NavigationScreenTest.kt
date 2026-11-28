package io.github.alexanderop.eisen.screens

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bar: Today, its `+` and Progress, and every screen that is no tab reached from them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationScreenTest {
    @get:Rule val app = EisenApp("food")
    private val screen get() = app.screen

    @Test fun `Today shows the rings, the next workout and the food card`() {
        screen.has("Settings", "New", "Progress", "0 of 3 workouts in the last 7 days. Opens Progress.", "0 of 2,400 kcal today. Opens the Food diary.")
        screen.find("Start workout")
        screen.find("FOOD TODAY")
    }

    @Test fun `the plus opens the daily things and the screens that are no tab`() {
        screen.tap("New")
        screen.has("Start workout", "Add food", "Log weight", "Workouts", "Timer", "Food diary")
        screen.tap("Food diary")
        screen.has("Change goal", "2,400 kcal left")
        screen.tab("Timer")
        screen.has("WODs", "Timers")
        screen.tab("Workouts")
        screen.has("Workout A")
        screen.tab("Today")
        screen.has("Settings")
    }

    @Test fun `a ring opens what it counts`() {
        screen.tap("0 of 2,400 kcal today. Opens the Food diary.")
        screen.has("Change goal")
        screen.tab("Today")
        screen.tap("0 of 3 workouts in the last 7 days. Opens Progress.")
        screen.has("Log", "Charts")
        screen.tap("Charts")
        // The food fixture has no workout yet.
        screen.has("Finish your first workout and your progress shows up here.", "Weight")
    }

    @Test fun `log weight from the plus opens the weight sheet over Today`() {
        screen.tap("New")
        screen.tap("Log weight")
        screen.has("Weight", "Previous day")
    }
}
