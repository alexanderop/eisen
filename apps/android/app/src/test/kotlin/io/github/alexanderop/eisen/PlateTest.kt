package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.AddScreen
import io.github.alexanderop.eisen.app.elm.AmountFor
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.Flags
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.Lookup
import io.github.alexanderop.eisen.app.elm.Loop
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Notifications
import io.github.alexanderop.eisen.app.elm.ScanFailure
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.decodePlate
import io.github.alexanderop.eisen.app.elm.encodePlate
import io.github.alexanderop.eisen.app.elm.init
import io.github.alexanderop.eisen.app.elm.plateItemOfFood
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.MAX_PLATE_ITEMS
import io.github.alexanderop.eisen.feature.food.Meal
import io.github.alexanderop.eisen.feature.food.MealItem
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.plateItemOf
import io.github.alexanderop.eisen.feature.food.plateTotals
import io.github.alexanderop.eisen.feature.food.quickPlateItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The add screen and the plate: every transition of the add screen and its run of barcodes, and of the plate, through `update` alone. */
class PlateTest {
    private val skyr = Food("off:4056489000006", "Skyr Natur", "Milbona", "4056489000006", kcal = 60.0, protein = 11.0, pack = 450.0, source = FoodSource.SCANNED)
    private val oats = Food("fdc:173904", "Oats", kcal = 379.0, protein = 13.2, serving = 80.0, source = FoodSource.PLAIN)
    private val banana = Food("fdc:1105314", "Banana", kcal = 89.0, protein = 1.1, serving = 118.0, source = FoodSource.PLAIN)
    private val nutella = Food("off:3017620422003", "Nutella", "Ferrero", "3017620422003", kcal = 539.0, protein = 6.3, pack = 400.0, source = FoodSource.SCANNED)
    private val breakfast = Meal("meal:b", "Breakfast", listOf(MealItem("fdc:173904", "Oats", 80.0, kcal = 303, protein = 10.6), MealItem(null, "Coffee", kcal = 20)))

    /** Noon on Thursday, 26 November 2026, UTC. */
    private val noon = ZonedDateTime.parse("2026-11-26T12:00:00Z").toInstant().toEpochMilli()
    private val today = "2026-11-26"

    private fun Step.food(msg: Msg, now: Long = noon, id: String = "n1"): Step =
        update(msg, model, Env(now, zone = ZoneOffset.UTC) { id }).let { (next, cmds) -> Step(next, cmds) }

    private fun Step.ignores(msg: Msg) {
        val after = food(msg)
        assertSame("$msg changed the model", model, after.model)
        assertTrue("$msg asked for ${after.cmds}", after.cmds.isEmpty())
    }

    private fun home(state: AppState = freshState(WeightUnit.KG).copy(foods = listOf(skyr), meals = listOf(breakfast))) =
        Step(Model(state, plainFoods = listOf(oats, banana), now = noon))

    private fun adding() = home().food(Msg.Food.OpenAdd)

    private val Step.plate get() = model.food.plate
    private val Step.add get() = model.food.add
    private val Step.sheet get() = model.food.sheet

    // The add screen

    @Test fun `Add opens the add screen over the Food tab on today, and Back, its close button and another tab close it and keep the plate`() {
        val add = adding()
        assertEquals(Tab.FOOD, add.model.nav.tab)
        assertEquals(today, add.model.food.day)
        assertEquals(AddScreen(), add.add)
        val plated = add.food(Msg.Food.PlateAdd(oats.id), id = "p1")
        for (leave in listOf(Msg.Food.CloseAdd, Msg.Nav.Back, Msg.Nav.SelectTab(Tab.HOME))) {
            val left = plated.food(leave)
            assertNull("$leave", left.add)
            assertEquals("$leave", plated.plate, left.plate)
        }
        // Back from the add screen stays on the Food tab, its day still shown.
        assertEquals(today, plated.food(Msg.Nav.Back).model.food.day)
        home().ignores(Msg.Food.CloseAdd)
    }

    @Test fun `the add screen opens on the day the Food tab shows`() {
        val yesterday = home().food(Msg.Food.OpenDay("2026-11-25")).food(Msg.Food.OpenAdd)
        assertEquals("2026-11-25", yesterday.model.food.day)
        val logged = yesterday.food(Msg.Food.PlateAdd(oats.id), id = "p1").food(Msg.Food.LogPlate, id = "e1")
        assertEquals(listOf("2026-11-25"), logged.state.foodLog.map { it.day })
    }

    // One food: + then Log

    @Test fun `plus puts a food on the plate at its usual amount, and Log writes it in two taps`() {
        val plated = adding().food(Msg.Food.PlateAdd(oats.id), id = "p1")
        assertEquals(listOf(plateItemOf(oats, 80.0, "p1")), plated.plate)
        assertEquals(plated.plate.single(), plateItemOfFood(plated.model, oats.id))
        assertEquals(listOf(Cmd.SavePlate(plated.plate)), plated.cmds)
        val logged = plated.food(Msg.Food.LogPlate, id = "e1")
        assertEquals(listOf(entryOf(oats, 80.0, "e1", noon, today)), logged.state.foodLog)
        assertEquals(emptyList<Any>(), logged.plate)
        assertNull(logged.add)
        assertTrue(Cmd.SavePlate(emptyList()) in logged.cmds)
        val snackbar = logged.uiEvents.filterIsInstance<UiEvent.Snackbar>().single()
        assertEquals("Logged 1 food", snackbar.text)
        assertEquals(Msg.Food.UndoAdded, snackbar.onAction)
    }

    @Test fun `the usual amount is the one logged last`() {
        val once = adding().food(Msg.Food.PlateAdd(oats.id), id = "p1").food(Msg.Food.OpenPlateItem("p1")).food(Msg.Food.SetAmount(40.0)).food(Msg.Food.ConfirmAmount)
            .food(Msg.Food.LogPlate, id = "e1")
        assertEquals(40.0, once.food(Msg.Food.OpenAdd).food(Msg.Food.PlateAdd(oats.id), id = "p2").plate.single().entry.amount)
    }

    // A breakfast of three

    @Test fun `three foods on the plate log as three entries at once, with one Undo for all of them`() {
        val plate = adding().food(Msg.Food.PlateAdd(oats.id), id = "p1").food(Msg.Food.PlateAdd(skyr.id), id = "p2").food(Msg.Food.PlateAdd(banana.id), id = "p3")
        assertEquals(listOf("Oats", "Skyr Natur", "Banana"), plate.plate.map { it.entry.name })
        assertEquals(303 + 270 + 105, plateTotals(plate.plate).kcal)
        val logged = plate.food(Msg.Food.LogPlate, id = "e1", now = noon + 5_000)
        assertEquals(listOf("e1", "e1.2", "e1.3"), logged.state.foodLog.map { it.id })
        assertTrue(logged.state.foodLog.all { it.at == noon + 5_000 && it.day == today })
        assertEquals(plate.plate.map { it.entry.kcal }, logged.state.foodLog.map { it.kcal })
        assertEquals("Logged 3 foods", logged.uiEvents.filterIsInstance<UiEvent.Snackbar>().single().text)
        assertEquals(emptyList<Any>(), logged.food(Msg.Food.UndoAdded).state.foodLog)
    }

    @Test fun `a meal puts each of its foods on the plate with its own numbers`() {
        val plated = adding().food(Msg.Food.PlateMeal(breakfast.id), id = "m1")
        assertEquals(listOf("Oats" to 303, "Coffee" to 20), plated.plate.map { it.entry.name to it.entry.kcal })
        assertEquals(listOf("m1", "m1.2"), plated.plate.map { it.entry.id })
        adding().ignores(Msg.Food.PlateMeal("gone"))
        home().food(Msg.Food.Open).ignores(Msg.Food.PlateMeal(breakfast.id))
    }

    // The plate's items

    @Test fun `a tap on a row opens its amount for the plate, a tap on an item changes it, and either goes back to the add screen`() {
        val picked = adding().food(Msg.Food.Pick(skyr.id))
        assertEquals(FoodSheet.Amount(skyr, 450.0, AmountFor.Plate), picked.sheet)
        val plated = picked.food(Msg.Food.SetAmount(225.0)).food(Msg.Food.ConfirmAmount, id = "p1")
        assertNull(plated.sheet)
        assertEquals(AddScreen(), plated.add)
        assertEquals(listOf(plateItemOf(skyr, 225.0, "p1")), plated.plate)
        val item = plated.food(Msg.Food.OpenPlateItem("p1"))
        assertEquals(FoodSheet.Amount(skyr, 225.0, AmountFor.OnPlate("p1")), item.sheet)
        val changed = item.food(Msg.Food.SetAmount(450.0)).food(Msg.Food.ConfirmAmount)
        assertEquals(listOf(plateItemOf(skyr, 450.0, "p1")), changed.plate)
        assertNull(changed.sheet)
        plated.ignores(Msg.Food.OpenPlateItem("gone"))
    }

    @Test fun `an item comes off the plate by its cross or its sheet, and Clear empties the plate without logging`() {
        val two = adding().food(Msg.Food.PlateAdd(oats.id), id = "p1").food(Msg.Food.PlateAdd(skyr.id), id = "p2")
        assertEquals(listOf("p2"), two.food(Msg.Food.RemovePlateItem("p1")).plate.map { it.entry.id })
        val fromSheet = two.food(Msg.Food.OpenPlateItem("p1")).food(Msg.Food.RemovePlateItem("p1"))
        assertNull(fromSheet.sheet)
        assertEquals(listOf("p2"), fromSheet.plate.map { it.entry.id })
        two.ignores(Msg.Food.RemovePlateItem("gone"))
        val cleared = two.food(Msg.Food.ClearPlate)
        assertEquals(emptyList<Any>(), cleared.plate)
        assertEquals(emptyList<Any>(), cleared.state.foodLog)
        cleared.ignores(Msg.Food.ClearPlate)
        cleared.ignores(Msg.Food.LogPlate)
    }

    @Test fun `quick kcal goes on the plate from the add screen, and is logged at once from the day`() {
        val quick = adding().food(Msg.Food.OpenQuick)
        assertEquals(FoodSheet.Quick(AmountFor.Plate), quick.sheet)
        val plated = quick.food(Msg.Food.SaveQuick(850, 40, "Pizza"), id = "q1")
        assertEquals(listOf(quickPlateItem(850, 40, "Pizza", "q1")), plated.plate)
        val item = plated.food(Msg.Food.OpenPlateItem("q1"))
        assertEquals(FoodSheet.Quick(AmountFor.OnPlate("q1")), item.sheet)
        assertEquals(900, item.food(Msg.Food.SaveQuick(900, null, "Pizza")).plate.single().entry.kcal)
        assertEquals(FoodSheet.Quick(AmountFor.Log), home().food(Msg.Food.Open).food(Msg.Food.OpenQuick).sheet)
    }

    @Test fun `the plate holds MAX_PLATE_ITEMS foods, and says so instead of taking more`() {
        val full = (1..MAX_PLATE_ITEMS).fold(adding()) { at, i -> at.food(Msg.Food.PlateAdd(oats.id), id = "p$i") }
        assertEquals(MAX_PLATE_ITEMS, full.plate.size)
        val more = full.food(Msg.Food.PlateAdd(banana.id), id = "p99")
        assertEquals(full.plate, more.plate)
        assertEquals("The plate holds 30 foods. Log it first.", more.uiEvents.filterIsInstance<UiEvent.Snackbar>().single().text)
        assertEquals(full.plate, full.food(Msg.Food.PlateMeal(breakfast.id), id = "m1").plate)
    }

    // A run of barcodes

    @Test fun `Scan on the add screen puts each scanned food on the plate and opens the scanner again until it is cancelled`() {
        val scanning = adding().food(Msg.Food.Scan)
        assertEquals(Loop.SCANNING, scanning.add!!.loop)
        assertEquals(listOf(UiEvent.ScanBarcode), scanning.uiEvents)
        // A food the phone knows: on the plate, and the scanner again.
        val first = scanning.food(Msg.Food.Scanned("4056489000006"), id = "p1")
        assertEquals(listOf("Skyr Natur"), first.plate.map { it.entry.name })
        assertEquals(listOf(UiEvent.ScanBarcode), first.uiEvents)
        assertNull(first.sheet)
        // One Open Food Facts has to answer: looked up, then on the plate and the scanner again.
        val looking = first.food(Msg.Food.Scanned("3017620422003"))
        assertEquals(FoodSheet.LookingUp(Lookup.Looking("3017620422003"), direct = true), looking.sheet)
        val second = looking.food(Msg.Food.Looked("3017620422003", ProductAnswer.Found(nutella)), id = "p2")
        assertEquals(listOf("Skyr Natur", "Nutella"), second.plate.map { it.entry.name })
        assertEquals(listOf(UiEvent.ScanBarcode), second.uiEvents)
        // A cancel goes back to the add screen with the plate as it is, and no sheet to type a barcode.
        val cancelled = second.food(Msg.Food.ScanFailed(ScanFailure.CANCELLED))
        assertEquals(AddScreen(), cancelled.add)
        assertNull(cancelled.sheet)
        assertEquals(second.plate, cancelled.plate)
    }

    @Test fun `when the scanner fails on the add screen the barcodes are typed, one after the other`() {
        val failed = adding().food(Msg.Food.Scan).food(Msg.Food.ScanFailed(ScanFailure.INSTALLING))
        assertEquals(Loop.TYPING, failed.add!!.loop)
        assertTrue((failed.sheet as FoodSheet.Barcode).note!!.contains("downloading the scanner"))
    }

    @Test fun `typed barcodes on the add screen go on the plate one after the other, until the sheet is closed`() {
        val typing = adding().food(Msg.Food.TypeBarcode)
        assertEquals(Loop.TYPING, typing.add!!.loop)
        assertEquals(FoodSheet.Barcode(), typing.sheet)
        val one = typing.food(Msg.Food.LookUp("4056489000006"), id = "p1")
        assertEquals(FoodSheet.Barcode("Skyr Natur is on the plate, 1 food now. Type the next barcode, or close this."), one.sheet)
        val two = one.food(Msg.Food.LookUp("4056489000006"), id = "p2")
        assertEquals(listOf("p1", "p2"), two.plate.map { it.entry.id })
        val closed = two.food(Msg.Food.CloseSheet)
        assertEquals(AddScreen(), closed.add)
        assertEquals(two.plate, closed.plate)
    }

    @Test fun `a barcode nobody knows leads to its own food form, and the saved food comes back on the plate`() {
        val form = adding().food(Msg.Food.TypeBarcode).food(Msg.Food.LookUp("4260999999990")).food(Msg.Food.Looked("4260999999990", ProductAnswer.NotFound))
        val own = form.sheet as FoodSheet.Own
        assertEquals(true, own.direct)
        assertEquals("4260999999990", own.barcode)
        val saved = form.food(Msg.Food.SaveOwn("Tante Emma Quark", 70.0, 12.0, null, null, null), id = "q")
        assertEquals(listOf("Tante Emma Quark"), saved.plate.map { it.entry.name })
        // The run goes on: the next barcode can be typed.
        assertTrue(saved.sheet is FoodSheet.Barcode)
        // A new food from the add screen's button opens its amount for the plate instead.
        val newFood = adding().food(Msg.Food.NewOwn("Bread")).food(Msg.Food.SaveOwn("Bread", 250.0, 9.0, null, null, null), id = "b")
        assertEquals(AmountFor.Plate, (newFood.sheet as FoodSheet.Amount).target)
    }

    @Test fun `a hit without its numbers goes on the plate once its barcode is looked up`() {
        val bare = io.github.alexanderop.eisen.feature.food.SearchHit("3017620422003", "Nutella", "Ferrero", food = null)
        val found = adding().food(Msg.Food.Search("nutella")).food(Msg.Food.SubmitSearch)
            .food(Msg.Food.Searched("nutella", io.github.alexanderop.eisen.feature.food.SearchAnswer.Found(listOf(bare))))
        val looking = found.food(Msg.Food.PlateAdd(bare.id))
        assertEquals(FoodSheet.LookingUp(Lookup.Looking("3017620422003"), direct = true), looking.sheet)
        val plated = looking.food(Msg.Food.Looked("3017620422003", ProductAnswer.Found(nutella)), id = "p1")
        assertEquals(listOf("Nutella"), plated.plate.map { it.entry.name })
        assertNull(plated.sheet)
        // A product found is kept on the phone right away, as it always was.
        assertTrue(plated.state.foods.any { it.id == nutella.id })
    }

    // Kept across a restart

    @Test fun `the plate survives the end of the process, in its own file and in its limits`() {
        val plated = adding().food(Msg.Food.PlateAdd(oats.id), id = "p1").food(Msg.Food.OpenQuick).food(Msg.Food.SaveQuick(850, 40, "Pizza"), id = "q1")
        val file = encodePlate(plated.plate)!!
        assertEquals(plated.plate, decodePlate(file))
        assertNull(encodePlate(emptyList()))
        val (restarted, _) = init(Flags(plated.state, null, false, Notifications(granted = true, asked = true), now = noon + 60_000, savedPlate = decodePlate(file)))
        assertEquals(plated.plate, restarted.food.plate)
        assertNull(restarted.food.add)
        // A plate file of a build with more room is cut to what fits.
        val many = (1..MAX_PLATE_ITEMS + 5).map { plateItemOf(oats, 80.0, "p$it") }
        assertEquals(MAX_PLATE_ITEMS, decodePlate(encodePlate(many)!!).size)
    }

    @Test fun `Reset empties the plate with the rest`() {
        val plated = adding().food(Msg.Food.PlateAdd(oats.id), id = "p1")
        assertEquals(emptyList<Any>(), plated.food(Msg.Data.Reset).plate)
    }
}
