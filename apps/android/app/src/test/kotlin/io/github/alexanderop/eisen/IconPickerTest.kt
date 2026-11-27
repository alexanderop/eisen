package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.AmountFor
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.IconPicker
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Online
import io.github.alexanderop.eisen.app.elm.addSuggestions
import io.github.alexanderop.eisen.app.elm.entryIcon
import io.github.alexanderop.eisen.app.elm.iconPickerOpen
import io.github.alexanderop.eisen.app.elm.knownFood
import io.github.alexanderop.eisen.app.elm.plateIcon
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodIcon
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.SearchAnswer
import io.github.alexanderop.eisen.feature.food.SearchHit
import io.github.alexanderop.eisen.feature.food.iconOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The grid of pictures ([IconPicker]): each transition over the add sheet and over the own food form, Back, where the
 * picture is kept (a kept food, a plain food, a hit not kept yet), and that a lookup sets the picture of its category
 * while a food the phone already knows needs no request for one.
 */
class IconPickerTest {
    private val skyr = Food("off:4056489000006", "Skyr Natur", "Milbona", "4056489000006", kcal = 60.0, protein = 11.0, pack = 450.0, source = FoodSource.SCANNED)
    private val banana = Food("fdc:173944", "Banana", kcal = 89.0, protein = 1.1, serving = 118.0, source = FoodSource.PLAIN, icon = FoodIcon.BANANA)
    private val noon = ZonedDateTime.parse("2026-11-27T12:00:00Z").toInstant().toEpochMilli()

    private fun step(state: AppState = freshState(WeightUnit.KG).copy(foods = listOf(skyr))): Step = Step(Model(state, plainFoods = listOf(banana), now = noon))

    private fun Step.food(msg: Msg, id: String = "n1"): Step = update(msg, model, Env(noon, zone = ZoneOffset.UTC) { id }).let { (next, cmds) -> Step(next, cmds) }

    private fun Step.ignores(msg: Msg) {
        val after = food(msg)
        assertSame("$msg changed the model", model, after.model)
        assertTrue("$msg asked for ${after.cmds}", after.cmds.isEmpty())
    }

    private val Step.sheet get() = model.food.sheet

    /** The add screen with the add sheet of [id] open, for the plate. */
    private fun picked(id: String = skyr.id, state: AppState = freshState(WeightUnit.KG).copy(foods = listOf(skyr))) =
        step(state).food(Msg.Food.OpenAdd).food(Msg.Food.Pick(id))

    // Over the add sheet

    @Test fun `Closed to Open, a tap on the add sheet's picture opens the grid`() {
        val sheet = picked()
        assertEquals(IconPicker.Closed, (sheet.sheet as FoodSheet.Amount).picker)
        val open = sheet.food(Msg.Food.OpenIcons)
        assertEquals(IconPicker.Open, (open.sheet as FoodSheet.Amount).picker)
        assertTrue(iconPickerOpen(open.model))
        open.ignores(Msg.Food.OpenIcons)
    }

    @Test fun `Open to Closed by a pick, a kept food takes the picture at once, its entries show it, no number changes`() {
        val day = step().food(Msg.Food.OpenAdd).food(Msg.Food.PlateAdd(skyr.id)).food(Msg.Food.LogPlate, id = "e1")
        val entry = day.model.state.foodLog.single()
        assertEquals(FoodIcon.YOGURT, entryIcon(day.model, entry))
        val open = day.food(Msg.Food.OpenEntry(entry.id)).food(Msg.Food.OpenIcons)
        val chose = open.food(Msg.Food.PickIcon(FoodIcon.ICE_CREAM))
        val sheet = chose.sheet as FoodSheet.Amount
        assertEquals(IconPicker.Closed, sheet.picker)
        assertEquals(FoodIcon.ICE_CREAM, sheet.food.icon)
        assertEquals(FoodIcon.ICE_CREAM, chose.model.state.foods.single().icon)
        assertEquals(FoodIcon.ICE_CREAM, entryIcon(chose.model, entry))
        assertEquals(day.model.state.foodLog, chose.model.state.foodLog)
        assertEquals(day.model.state.foods.map { it.copy(icon = null) }, chose.model.state.foods.map { it.copy(icon = null) })
        // Saved, so it stays after a restart.
        assertEquals(listOf(Cmd.Save(chose.model.state)), chose.cmds)
    }

    @Test fun `a plain food keeps the lifter's picture beside the asset, and shows it everywhere`() {
        val chose = picked(banana.id).food(Msg.Food.OpenIcons).food(Msg.Food.PickIcon(FoodIcon.FRUIT))
        assertEquals(mapOf(banana.id to "fruit"), chose.model.state.plainIcons)
        assertEquals(FoodIcon.FRUIT, knownFood(chose.model, banana.id)?.icon)
        assertEquals(listOf(banana), chose.model.plainFoods)
        assertEquals(FoodIcon.FRUIT, addSuggestions(chose.model).common.single { it.food.id == banana.id }.food.icon)
    }

    @Test fun `a hit not kept yet carries the picture onto the plate and is kept with it once logged`() {
        val hit = Food("off:4311501065525", "Milch", "Edeka", "4311501065525", kcal = 64.0, protein = 3.4, source = FoodSource.SCANNED, icon = FoodIcon.MILK)
        val searched = step().food(Msg.Food.OpenAdd).food(Msg.Food.Search("milch")).food(Msg.Food.SubmitSearch)
            .food(Msg.Food.Searched("milch", SearchAnswer.Found(listOf(SearchHit(hit.barcode!!, hit.name, hit.brand, hit, hit.icon)))))
        assertTrue(searched.model.food.add?.online is Online.Results)
        val chose = searched.food(Msg.Food.Pick(hit.id)).food(Msg.Food.OpenIcons).food(Msg.Food.PickIcon(FoodIcon.COFFEE))
        assertEquals(searched.model.state, chose.model.state)
        val plated = chose.food(Msg.Food.ConfirmAmount, id = "p1")
        assertEquals(FoodIcon.COFFEE, plateIcon(plated.model, plated.model.food.plate.single()))
        val logged = plated.food(Msg.Food.LogPlate, id = "e1")
        assertEquals(FoodIcon.COFFEE, logged.model.state.foods.single { it.id == hit.id }.icon)
    }

    @Test fun `Open to Closed without a choice, the close button, Back or a swipe change nothing but the grid`() {
        val open = picked().food(Msg.Food.OpenIcons)
        val closed = open.food(Msg.Food.CloseIcons)
        assertEquals(picked().model, closed.model)
        // Back closes the grid first, then the sheet.
        val back = open.food(Msg.Nav.Back)
        assertEquals(picked().model, back.model)
        assertNull(back.food(Msg.Nav.Back).sheet)
        picked().ignores(Msg.Food.CloseIcons)
    }

    @Test fun `a pick while the grid is closed, or the grid without a sheet that has a picture, does nothing`() {
        picked().ignores(Msg.Food.PickIcon(FoodIcon.MILK))
        step().food(Msg.Food.Open).ignores(Msg.Food.OpenIcons)
        step().food(Msg.Food.Open).food(Msg.Food.OpenGoal).ignores(Msg.Food.OpenIcons)
        step().food(Msg.Food.Open).food(Msg.Food.OpenGoal).ignores(Msg.Food.PickIcon(FoodIcon.MILK))
    }

    @Test fun `an entry whose food is gone shows its name's picture and offers no grid`() {
        val day = step().food(Msg.Food.OpenAdd).food(Msg.Food.PlateAdd(skyr.id)).food(Msg.Food.LogPlate, id = "e1")
        val gone = Step(day.model.copy(state = day.model.state.copy(foods = emptyList())))
        val entry = gone.model.state.foodLog.single()
        assertEquals(FoodIcon.YOGURT, entryIcon(gone.model, entry))
        val sheet = gone.food(Msg.Food.OpenEntry(entry.id))
        assertEquals(AmountFor.Entry(entry.id), (sheet.sheet as FoodSheet.Amount).target)
        sheet.ignores(Msg.Food.OpenIcons)
    }

    // Over the own food form

    @Test fun `the own food form follows the name until a picture is chosen, which Save food keeps`() {
        val form = step().food(Msg.Food.OpenAdd).food(Msg.Food.NewOwn("Tante Emma Quark"))
        assertNull((form.sheet as FoodSheet.Own).icon)
        val open = form.food(Msg.Food.OpenIcons)
        assertEquals(IconPicker.Open, (open.sheet as FoodSheet.Own).picker)
        assertEquals(form.model, open.food(Msg.Food.CloseIcons).model)
        val chose = open.food(Msg.Food.PickIcon(FoodIcon.CHEESE))
        assertEquals(FoodSheet.Own(name = "Tante Emma Quark", icon = FoodIcon.CHEESE), chose.sheet)
        chose.ignores(Msg.Food.PickIcon(FoodIcon.MILK))
        val saved = chose.food(Msg.Food.SaveOwn("Tante Emma Quark", 70.0, 12.0, null, null, null), id = "q")
        assertEquals(FoodIcon.CHEESE, saved.model.state.foods.single { it.id == "own:q" }.icon)
        // Not chosen: none is saved, and the name says it.
        val plain = form.food(Msg.Food.SaveOwn("Tante Emma Quark", 70.0, 12.0, null, null, null), id = "r").model.state.foods.single { it.id == "own:r" }
        assertNull(plain.icon)
        assertEquals(FoodIcon.YOGURT, iconOf(plain))
    }

    @Test fun `editing an own food starts at its picture and keeps it`() {
        val own = Food("own:1", "Protein Bowl", kcal = 120.0, source = FoodSource.OWN, icon = FoodIcon.MEAL)
        val state = freshState(WeightUnit.KG).copy(foods = listOf(own))
        val form = step(state).food(Msg.Food.Open).food(Msg.Food.EditOwn(own.id))
        assertEquals(FoodIcon.MEAL, (form.sheet as FoodSheet.Own).icon)
        val saved = form.food(Msg.Food.SaveOwn("Protein Bowl", 130.0, 10.0, null, null, null))
        assertEquals(FoodIcon.MEAL, saved.model.state.foods.single().icon)
    }

    // The lookup

    @Test fun `a looked up product keeps the picture of its category, a food the phone knows needs no request`() {
        val add = step().food(Msg.Food.OpenAdd).food(Msg.Food.TypeBarcode)
        val milk = Food("off:4311501065525", "Milch", "Edeka", "4311501065525", kcal = 64.0, source = FoodSource.SCANNED, icon = FoodIcon.MILK)
        val looking = add.food(Msg.Food.LookUp(milk.barcode!!))
        assertEquals(listOf(Cmd.LookUpProduct(milk.barcode!!)), looking.cmds)
        val found = looking.food(Msg.Food.Looked(milk.barcode!!, ProductAnswer.Found(milk)))
        assertEquals(FoodIcon.MILK, found.model.state.foods.single { it.id == milk.id }.icon)
        // The Skyr of build 25 has no picture stored: its name says it, and typing its barcode asks nobody.
        val known = add.food(Msg.Food.LookUp(skyr.barcode!!))
        assertFalse(known.cmds.any { it is Cmd.LookUpProduct })
        assertNull(known.model.state.foods.single().icon)
        assertEquals(FoodIcon.YOGURT, iconOf(known.model.state.foods.single()))
    }
}
