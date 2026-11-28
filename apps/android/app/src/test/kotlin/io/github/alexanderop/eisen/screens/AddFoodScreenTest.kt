package io.github.alexanderop.eisen.screens

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The add screen: the search with Scan and Label under it, and a food from the list onto the plate and into the day. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddFoodScreenTest {
    @get:Rule val app = EisenApp("food")
    private val screen get() = app.screen

    @Test fun `Scan and Label sit under the search, Dish only on a phone with Gemini Nano`() {
        screen.tap("New")
        screen.tap("Add food")
        screen.has("Add food", "Search foods", "Scan", "Label", "COMMON FOODS")
        // Robolectric has no AICore: the model is missing, as on a phone without it.
        screen.gone("Dish")
    }

    @Test fun `a food goes onto the plate and is logged in two taps`() {
        screen.tap("+ Add")
        screen.tap("Put Banana on the plate")
        screen.has("Take Banana off the plate", "Log 105 kcal")
        screen.tap("Log 105 kcal")
        screen.has("2,295 kcal left", "Banana, 118 g, 105 kcal")
    }
}
