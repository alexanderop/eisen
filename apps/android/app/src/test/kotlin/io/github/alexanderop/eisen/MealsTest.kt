package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.MealRow
import io.github.alexanderop.eisen.app.elm.MealSheet
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.canSaveMeal
import io.github.alexanderop.eisen.app.elm.copyOffer
import io.github.alexanderop.eisen.app.elm.CopyOffer
import io.github.alexanderop.eisen.app.elm.exportJson
import io.github.alexanderop.eisen.app.elm.suggestedMealName
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.MAX_MEALS
import io.github.alexanderop.eisen.feature.food.MAX_MEAL_ITEMS
import io.github.alexanderop.eisen.feature.food.Meal
import io.github.alexanderop.eisen.feature.food.MealItem
import io.github.alexanderop.eisen.feature.food.entryIds
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.itemWithAmount
import io.github.alexanderop.eisen.feature.food.mealItemOf
import io.github.alexanderop.eisen.feature.food.mealName
import io.github.alexanderop.eisen.feature.food.mealTotals
import io.github.alexanderop.eisen.feature.food.mealWithin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/** Meals and "Copy yesterday": every transition of the meal sheet through `update`, and the pure parts. */
class MealsTest {
    private val skyr = Food("off:4056489000006", "Skyr Natur", "Milbona", "4056489000006", kcal = 60.0, protein = 11.0, pack = 450.0, source = FoodSource.SCANNED)
    private val oats = Food("fdc:173904", "Oats", kcal = 379.0, protein = 13.2, carbs = 67.7, fat = 6.5, serving = 80.0, source = FoodSource.PLAIN)

    /** 8 o'clock on Thursday, 26 November 2026, UTC: breakfast. */
    private val morning = ZonedDateTime.parse("2026-11-26T08:00:00Z").toInstant().toEpochMilli()
    private val today = "2026-11-26"

    private fun Step.food(msg: Msg, now: Long = morning, id: String = "n1"): Step =
        update(msg, model, Env(now) { id }).let { (next, cmds) -> Step(next, cmds) }

    private fun Step.ignores(msg: Msg) {
        val after = food(msg)
        assertSame("$msg changed the model", model, after.model)
        assertTrue("$msg asked for ${after.cmds}", after.cmds.isEmpty())
    }

    private val Step.mealSheet get() = (model.food.sheet as? FoodSheet.Meal)?.sheet

    private fun opened(state: AppState = freshState(WeightUnit.KG).copy(foods = listOf(skyr))) =
        Step(Model(state, plainFoods = listOf(oats), now = morning)).food(Msg.Food.Open)

    /** Breakfast logged today: oats 80 g, skyr 450 g and a quick 100 kcal for the coffee. */
    private fun breakfast(): Step = opened()
        .food(Msg.Food.OpenSearch).food(Msg.Food.Search("oat")).food(Msg.Food.Pick(oats.id)).food(Msg.Food.ConfirmAmount, id = "e1")
        .food(Msg.Food.Scanned("4056489000006")).food(Msg.Food.ConfirmAmount, now = morning + 1, id = "e2")
        .food(Msg.Food.OpenQuick).food(Msg.Food.SaveQuick(100, null, "Coffee"), now = morning + 2, id = "e3")

    /** Breakfast saved as a meal of the oats and the skyr, without the coffee. */
    private fun saved(): Step = breakfast().food(Msg.Food.NewMeal).food(Msg.Food.TogglePick("e3")).food(Msg.Food.SaveNewMeal("Breakfast"), id = "m1")

    // Making one

    @Test fun `Save as meal opens with every entry of the day picked, a tick takes one out and puts it back`() {
        val pick = breakfast().food(Msg.Food.NewMeal)
        assertEquals(MealSheet.Pick(listOf("e1", "e2", "e3")), pick.mealSheet)
        val without = pick.food(Msg.Food.TogglePick("e2"))
        assertEquals(MealSheet.Pick(listOf("e1", "e3")), without.mealSheet)
        // Back in the order of the day, not at the end.
        assertEquals(MealSheet.Pick(listOf("e1", "e2", "e3")), without.food(Msg.Food.TogglePick("e2")).mealSheet)
        pick.ignores(Msg.Food.TogglePick("gone"))
        // An empty day has nothing to make a meal of.
        opened().ignores(Msg.Food.NewMeal)
        breakfast().ignores(Msg.Food.TogglePick("e1"))
    }

    @Test fun `a new meal keeps the picked entries with their amounts and numbers, and closes the sheet`() {
        val pick = breakfast().food(Msg.Food.NewMeal).food(Msg.Food.TogglePick("e3"))
        pick.ignores(Msg.Food.SaveNewMeal("  "))
        val saved = pick.food(Msg.Food.SaveNewMeal(" Breakfast "), id = "m1")
        val meal = saved.state.meals.single()
        assertEquals("meal:m1" to "Breakfast", meal.id to meal.name)
        assertEquals(breakfast().state.foodLog.take(2).map(::mealItemOf), meal.items)
        assertEquals(MealItem("fdc:173904", "Oats", 80.0, kcal = 303, protein = 10.6, carbs = 54.2, fat = 5.2), meal.items[0])
        assertNull(saved.model.food.sheet)
        assertEquals(breakfast().state.foodLog, saved.state.foodLog)
        // Nothing picked: nothing saved.
        breakfast().food(Msg.Food.NewMeal).food(Msg.Food.TogglePick("e1")).food(Msg.Food.TogglePick("e2")).food(Msg.Food.TogglePick("e3"))
            .ignores(Msg.Food.SaveNewMeal("Breakfast"))
    }

    @Test fun `no more meals than the limit, no more items than the limit`() {
        val full = (1..MAX_MEALS).fold(breakfast()) { at, i -> at.food(Msg.Food.NewMeal).food(Msg.Food.SaveNewMeal("Meal $i"), id = "m$i") }
        assertEquals(MAX_MEALS, full.state.meals.size)
        assertTrue(!canSaveMeal(full.model))
        full.ignores(Msg.Food.NewMeal)
        val many = (1..MAX_MEAL_ITEMS + 5).fold(opened()) { at, i -> at.food(Msg.Food.OpenQuick).food(Msg.Food.SaveQuick(i, null, ""), now = morning + i, id = "q$i") }
        val pick = many.food(Msg.Food.NewMeal)
        assertEquals(MAX_MEAL_ITEMS, (pick.mealSheet as MealSheet.Pick).picked.size)
        pick.ignores(Msg.Food.TogglePick("q${MAX_MEAL_ITEMS + 1}"))
    }

    // Logging one

    @Test fun `one tap on a meal logs each of its foods on the day shown, with Undo`() {
        val tomorrow = saved().food(Msg.Food.Close).food(Msg.Food.Open, now = morning + 86_400_000)
        val added = tomorrow.food(Msg.Food.AddMeal("meal:m1"), now = morning + 86_400_000, id = "a")
        val new = added.state.foodLog.drop(3)
        assertEquals(listOf("a", "a.2"), new.map { it.id })
        assertEquals(listOf("2026-11-27", "2026-11-27"), new.map { it.day })
        assertEquals(saved().state.meals.single().items, new.map(::mealItemOf))
        val snackbar = added.uiEvents.filterIsInstance<UiEvent.Snackbar>().single()
        assertEquals("Added Breakfast, 2 foods", snackbar.text)
        assertEquals(Msg.Food.UndoAdded, snackbar.onAction)
        val undone = added.food(Msg.Food.UndoAdded)
        assertEquals(saved().state.foodLog, undone.state.foodLog)
        undone.ignores(Msg.Food.UndoAdded)
        tomorrow.ignores(Msg.Food.AddMeal("gone"))
        // Each food is an entry of its own, changed like any other.
        val entry = added.food(Msg.Food.OpenEntry("a.2")).food(Msg.Food.SetAmount(225.0)).food(Msg.Food.ConfirmAmount)
        assertEquals(135, entry.state.foodLog.first { it.id == "a.2" }.kcal)
    }

    @Test fun `a meal whose food was deleted still logs with its own numbers`() {
        val meal = Meal("meal:x", "Shake", listOf(MealItem("own:gone", "Whey", 30.0, kcal = 120, protein = 24.0)))
        val added = opened(freshState(WeightUnit.KG).copy(meals = listOf(meal))).food(Msg.Food.AddMeal("meal:x"), id = "a")
        assertEquals(listOf(Triple("Whey", 120, 24.0)), added.state.foodLog.map { Triple(it.name, it.kcal, it.protein) })
    }

    // Changing and deleting one

    @Test fun `a meal is changed, an amount, an item taken out, a new name, and what was logged stays`() {
        val logged = saved().food(Msg.Food.AddMeal("meal:m1"), id = "a")
        val list = logged.food(Msg.Food.OpenMeals)
        assertEquals(MealSheet.All, list.mealSheet)
        val edit = list.food(Msg.Food.EditMeal("meal:m1"))
        val items = saved().state.meals.single().items
        assertEquals(MealSheet.Edit("meal:m1", items.map { MealRow(it, it.amount) }), edit.mealSheet)
        val stepped = edit.food(Msg.Food.SetMealAmount(0, 90.0)).food(Msg.Food.SetMealAmount(0, 100.0)).food(Msg.Food.SetMealAmount(0, 80.0))
        // Up and down again: the numbers are the ones it had, nothing drifted.
        assertEquals(edit.mealSheet, stepped.mealSheet)
        val changed = edit.food(Msg.Food.SetMealAmount(0, 40.0)).food(Msg.Food.RemoveMealItem(1)).food(Msg.Food.SaveMeal("Small breakfast"))
        val meal = changed.state.meals.single()
        assertEquals("Small breakfast", meal.name)
        assertEquals(listOf(MealItem("fdc:173904", "Oats", 40.0, kcal = 152, protein = 5.3, carbs = 27.1, fat = 2.6)), meal.items)
        assertEquals(MealSheet.All, changed.mealSheet)
        assertEquals(logged.state.foodLog, changed.state.foodLog)
        // Every item taken out: it cannot be saved so, only deleted.
        edit.food(Msg.Food.RemoveMealItem(0)).food(Msg.Food.RemoveMealItem(0)).ignores(Msg.Food.SaveMeal("Breakfast"))
        edit.ignores(Msg.Food.SaveMeal(" "))
        edit.ignores(Msg.Food.RemoveMealItem(5))
        edit.ignores(Msg.Food.SetMealAmount(5, 10.0))
        list.ignores(Msg.Food.EditMeal("gone"))
        list.ignores(Msg.Food.SetMealAmount(0, 10.0))
    }

    @Test fun `a quick item has no amount to change`() {
        val withCoffee = breakfast().food(Msg.Food.NewMeal).food(Msg.Food.SaveNewMeal("Breakfast"), id = "m1").food(Msg.Food.EditMeal("meal:m1"))
        withCoffee.ignores(Msg.Food.SetMealAmount(2, 50.0))
    }

    @Test fun `deleting a meal leaves what was logged of it, and its sheet goes back to the list`() {
        val logged = saved().food(Msg.Food.AddMeal("meal:m1"), id = "a")
        val deleted = logged.food(Msg.Food.EditMeal("meal:m1")).food(Msg.Food.DeleteMeal("meal:m1"))
        assertEquals(emptyList<Meal>(), deleted.state.meals)
        assertEquals(MealSheet.All, deleted.mealSheet)
        assertEquals(logged.state.foodLog, deleted.state.foodLog)
        deleted.ignores(Msg.Food.DeleteMeal("meal:m1"))
        // A meal gone under its open sheet (a backup) closes it on Save.
        val editing = saved().food(Msg.Food.EditMeal("meal:m1"))
        val gone = Step(editing.model.copy(state = editing.state.copy(meals = emptyList())))
        assertNull(gone.food(Msg.Food.SaveMeal("Breakfast")).model.food.sheet)
    }

    @Test fun `the meal sheets close like every sheet, and need the Food screen`() {
        assertNull(saved().food(Msg.Food.OpenMeals).food(Msg.Food.CloseSheet).model.food.sheet)
        assertNull(saved().food(Msg.Food.OpenMeals).food(Msg.Food.AddMeal("meal:m1"), id = "a").model.food.sheet)
        val closed = saved().food(Msg.Food.Close)
        closed.ignores(Msg.Food.OpenMeals)
        closed.ignores(Msg.Food.AddMeal("meal:m1"))
        saved().food(Msg.Food.OpenMeals).ignores(Msg.Food.OpenMeals)
    }

    // Copy yesterday

    @Test fun `an empty day copies the day before, with Undo`() {
        val tomorrow = breakfast().food(Msg.Food.Close).food(Msg.Food.Open, now = morning + 86_400_000)
        assertEquals(CopyOffer("Copy yesterday", 3, 673), copyOffer(tomorrow.model))
        val copied = tomorrow.food(Msg.Food.CopyDayBefore, now = morning + 86_400_000, id = "c")
        val new = copied.state.foodLog.drop(3)
        assertEquals(listOf("c", "c.2", "c.3"), new.map { it.id })
        assertEquals(breakfast().state.foodLog.map { it.copy(id = "", at = 0, day = "") }, new.map { it.copy(id = "", at = 0, day = "") })
        assertTrue(new.all { it.day == "2026-11-27" })
        assertEquals("Copied 3 foods", copied.uiEvents.filterIsInstance<UiEvent.Snackbar>().single().text)
        assertEquals(breakfast().state.foodLog, copied.food(Msg.Food.UndoAdded).state.foodLog)
        // Now it has entries: no second copy.
        assertNull(copyOffer(copied.model))
        copied.ignores(Msg.Food.CopyDayBefore)
        // A day whose day before is empty has nothing to copy.
        assertNull(copyOffer(opened().model))
        opened().ignores(Msg.Food.CopyDayBefore)
        // Two days back the button names the day.
        val earlier = breakfast().food(Msg.Food.Close).food(Msg.Food.Open, now = morning + 2 * 86_400_000).food(Msg.Food.PreviousDay, now = morning + 2 * 86_400_000)
        assertEquals("Copy Thu, Nov 26", copyOffer(earlier.model)?.label)
    }

    // Saved with the rest

    @Test fun `meals are saved with the rest, in backups, cleared by Reset, and older files load without them`() {
        val full = saved()
        val loaded = loadState(encodeState(full.state)) as Loaded.Ok
        assertEquals(full.state.meals, loaded.state.meals)
        val reset = full.food(Msg.Data.Reset)
        assertEquals(emptyList<Meal>(), reset.state.meals)
        assertEquals(full.state.meals, reset.food(Msg.Data.Import(exportJson(full.model))).state.meals)
        val old = encodeState(freshState(WeightUnit.KG)).replace(",\"meals\":[]", "")
        assertTrue(!old.contains("meals"))
        assertEquals(emptyList<Meal>(), (loadState(old) as Loaded.Ok).state.meals)
        // A meal written by hand beyond its limits is brought into them; one without items is dropped.
        val wild = freshState(WeightUnit.KG).copy(meals = listOf(Meal("a", "x".repeat(500), List(50) { MealItem(name = "q", kcal = Int.MAX_VALUE) }), Meal("b", "Empty", emptyList())))
        val tamed = (loadState(encodeState(wild)) as Loaded.Ok).state.meals.single()
        assertEquals(MAX_MEAL_ITEMS to 40, tamed.items.size to tamed.name.length)
    }

    // The pure parts

    @Test fun `an item at another amount scales from its own numbers`() {
        val item = mealItemOf(entryOf(oats, 80.0, "e", 0, "d"))
        // 303 kcal and 10.6 g of protein in 80 g: 606 kcal and 21.2 g in 160 g.
        assertEquals(item.copy(amount = 160.0, kcal = 606, protein = 21.2, carbs = 108.4, fat = 10.4), itemWithAmount(item, 160.0))
        val quick = MealItem(name = "Coffee", kcal = 100)
        assertSame(quick, itemWithAmount(quick, 50.0))
        assertEquals(403, mealTotals(Meal("m", "B", listOf(item, quick))).kcal)
        assertNull(mealWithin(Meal("m", "B", emptyList())))
        assertEquals("Meal", mealWithin(Meal("m", " ", listOf(quick)))?.name)
    }

    @Test fun `the ids of one tap's entries are unique while the first is`() {
        assertEquals(listOf("x", "x.2", "x.3"), entryIds("x", 3))
        assertEquals(emptyList<String>(), entryIds("x", 0))
    }

    @Test fun `a new meal is named by the time of day, never like one there is`() {
        assertEquals("Breakfast", mealName(8, emptyList()))
        assertEquals("Lunch", mealName(12, emptyList()))
        assertEquals("Snack", mealName(16, emptyList()))
        assertEquals("Dinner", mealName(20, emptyList()))
        assertEquals("Dinner", mealName(2, emptyList()))
        assertEquals("Breakfast 3", mealName(8, listOf("breakfast", "Breakfast 2")))
        assertEquals("Breakfast", suggestedMealName(opened().model))
    }
}
