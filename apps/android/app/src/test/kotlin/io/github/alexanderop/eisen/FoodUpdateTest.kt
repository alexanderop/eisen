package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.Failure
import io.github.alexanderop.eisen.app.elm.Flags
import io.github.alexanderop.eisen.app.elm.FoodModel
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.Lookup
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Notifications
import io.github.alexanderop.eisen.app.elm.Online
import io.github.alexanderop.eisen.app.elm.ScanFailure
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.againItems
import io.github.alexanderop.eisen.app.elm.exportJson
import io.github.alexanderop.eisen.app.elm.foodResults
import io.github.alexanderop.eisen.app.elm.foodToday
import io.github.alexanderop.eisen.app.elm.foodWeek
import io.github.alexanderop.eisen.app.elm.handlesBack
import io.github.alexanderop.eisen.app.elm.init
import io.github.alexanderop.eisen.app.elm.kcalHeadline
import io.github.alexanderop.eisen.app.elm.openFoodDay
import io.github.alexanderop.eisen.app.elm.showsFood
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.MAX_AMOUNT
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_KCAL
import io.github.alexanderop.eisen.feature.food.PRODUCT_REQUESTS_PER_MINUTE
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.QUICK_NAME
import io.github.alexanderop.eisen.feature.food.SEARCH_REQUESTS_PER_MINUTE
import io.github.alexanderop.eisen.feature.food.SearchAnswer
import io.github.alexanderop.eisen.feature.food.SearchHit
import io.github.alexanderop.eisen.feature.food.hitOf
import io.github.alexanderop.eisen.feature.food.entryOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Every transition of the Food screen, its sheet, the lookup and the online search, through `update` alone. */
class FoodUpdateTest {
    private val skyr = Food("off:4056489000006", "Skyr Natur", "Milbona", "4056489000006", kcal = 60.0, protein = 11.0, pack = 450.0, source = FoodSource.SCANNED)
    private val oats = Food("fdc:173904", "Oats", kcal = 379.0, protein = 13.2, serving = 80.0, source = FoodSource.PLAIN)
    private val nutella = Food("off:3017620422003", "Nutella", "Ferrero", "3017620422003", kcal = 539.0, protein = 6.3, pack = 400.0, source = FoodSource.SCANNED)

    /** Noon on Thursday, 26 November 2026, UTC. */
    private val noon = ZonedDateTime.parse("2026-11-26T12:00:00Z").toInstant().toEpochMilli()
    private val today = "2026-11-26"

    private fun step(state: AppState = freshState(WeightUnit.KG).copy(foods = listOf(skyr))): Step = Step(Model(state, plainFoods = listOf(oats), now = noon))

    private fun Step.food(msg: Msg, now: Long = noon, id: String = "n1", zone: ZoneId = ZoneOffset.UTC): Step =
        update(msg, model, Env(now, zone = zone) { id }).let { (next, cmds) -> Step(next, cmds) }

    private fun opened(state: AppState = freshState(WeightUnit.KG).copy(foods = listOf(skyr))) = step(state).food(Msg.Food.Open)

    private fun Step.ignores(msg: Msg, now: Long = noon) {
        val after = food(msg, now)
        assertSame("$msg changed the model", model, after.model)
        assertTrue("$msg asked for ${after.cmds}", after.cmds.isEmpty())
    }

    private val Step.sheet get() = model.food.sheet

    // The screen

    @Test fun `the card opens today, Back closes it, and it covers the tabs like the workout`() {
        val open = step().food(Msg.Food.Open)
        assertEquals(today, open.model.food.day)
        assertTrue(showsFood(open.model))
        assertTrue(handlesBack(open.model))
        val closed = open.food(Msg.Food.Close)
        assertNull(closed.model.food.day)
        step().ignores(Msg.Food.Close)
    }

    @Test fun `Add on the card opens the search, Scan starts the scanner`() {
        assertEquals(FoodSheet.Search(), step().food(Msg.Food.OpenAdd).sheet)
        val scan = step().food(Msg.Food.OpenScan)
        assertEquals(today, scan.model.food.day)
        assertEquals(listOf(UiEvent.ScanBarcode), scan.uiEvents)
        assertEquals(listOf(UiEvent.ScanBarcode), scan.food(Msg.Food.Scan).uiEvents)
        step().ignores(Msg.Food.Scan)
    }

    @Test fun `the arrows step through the days, never past today`() {
        val back = opened().food(Msg.Food.PreviousDay).food(Msg.Food.PreviousDay)
        assertEquals("2026-11-24", back.model.food.day)
        assertEquals("2026-11-25", back.food(Msg.Food.NextDay).model.food.day)
        opened().ignores(Msg.Food.NextDay)
        assertEquals(false, openFoodDay(opened().model)!!.canGoForward)
        assertEquals(true, openFoodDay(back.model)!!.canGoForward)
        step().ignores(Msg.Food.PreviousDay)
        step().ignores(Msg.Food.NextDay)
    }

    @Test fun `Back closes the sheet first, then the screen`() {
        val searching = opened().food(Msg.Food.OpenSearch)
        val sheetGone = searching.food(Msg.Nav.Back)
        assertNull(sheetGone.sheet)
        assertEquals(today, sheetGone.model.food.day)
        assertNull(sheetGone.food(Msg.Nav.Back).model.food.day)
        assertEquals(Tab.HOME, sheetGone.food(Msg.Nav.Back).model.nav.tab)
    }

    @Test fun `after a restart the screen comes back on today, without its sheet or the lookup that was going`() {
        val looking = opened().food(Msg.Food.Scanned("3017620422003"))
        assertTrue(looking.sheet is FoodSheet.LookingUp)
        // The process ends: only the file is kept.
        val (restarted, _) = init(Flags(looking.state, null, false, Notifications(granted = true, asked = true), now = noon + 60_000))
        assertEquals(FoodModel(), restarted.food)
        val back = Step(restarted).food(Msg.Nav.Restore(Tab.HOME, workoutOpen = false, routine = null, food = true), now = noon + 61_000)
        assertEquals(today, back.model.food.day)
        assertNull(back.sheet)
        // The answer of the request the dead process sent never comes; one that did would change nothing.
        back.ignores(Msg.Food.Looked("3017620422003", ProductAnswer.Found(nutella)))
    }

    // The lookup

    @Test fun `a barcode the phone knows needs no network`() {
        val known = opened().food(Msg.Food.Scanned("4056489000006"))
        assertEquals(FoodSheet.Amount(skyr, 450.0), known.sheet)
        assertTrue(known.cmds.isEmpty())
    }

    @Test fun `an unknown barcode is looked up once`() {
        val looking = opened().food(Msg.Food.Scanned("3017620422003"))
        assertEquals(FoodSheet.LookingUp(Lookup.Looking("3017620422003")), looking.sheet)
        assertEquals(listOf(Cmd.LookUpProduct("3017620422003")), looking.cmds)
        looking.ignores(Msg.Food.Scanned("3017620422003"))
    }

    @Test fun `a found product is kept, needs no network again, and its add sheet opens`() {
        val found = opened().food(Msg.Food.Scanned("3017620422003")).food(Msg.Food.Looked("3017620422003", ProductAnswer.Found(nutella)))
        assertEquals(FoodSheet.Amount(nutella, 400.0), found.sheet)
        assertTrue(nutella in found.state.foods)
        assertTrue(found.cmds.contains(Cmd.Save(found.state)))
        val again = found.food(Msg.Food.CloseSheet).food(Msg.Food.Scanned("3017620422003"))
        assertTrue(again.cmds.isEmpty())
        assertEquals(FoodSheet.Amount(nutella, 400.0), again.sheet)
    }

    @Test fun `a barcode not found, or found without calories, becomes an own food with the barcode and the name`() {
        val looking = opened().food(Msg.Food.Scanned("4260999999990"))
        val missing = looking.food(Msg.Food.Looked("4260999999990", ProductAnswer.NotFound)).sheet as FoodSheet.Own
        assertEquals("4260999999990", missing.barcode)
        assertEquals("", missing.name)
        assertTrue(missing.note!!.contains("does not know"))
        val noKcal = looking.food(Msg.Food.Looked("4260999999990", ProductAnswer.NoKcal("Cornedbeef"))).sheet as FoodSheet.Own
        assertEquals("Cornedbeef", noKcal.name)
        assertEquals("4260999999990", noKcal.barcode)
    }

    @Test fun `no network fails with Retry, which asks again`() {
        val looking = opened().food(Msg.Food.Scanned("3017620422003"))
        val offline = looking.food(Msg.Food.Looked("3017620422003", ProductAnswer.Offline))
        assertEquals(FoodSheet.LookingUp(Lookup.Failed("3017620422003", Failure.OFFLINE)), offline.sheet)
        assertEquals(FoodSheet.LookingUp(Lookup.Failed("3017620422003", Failure.BUSY)), looking.food(Msg.Food.Looked("3017620422003", ProductAnswer.Busy)).sheet)
        val retried = offline.food(Msg.Food.Retry, now = noon + 5_000)
        assertEquals(FoodSheet.LookingUp(Lookup.Looking("3017620422003")), retried.sheet)
        assertEquals(listOf(Cmd.LookUpProduct("3017620422003")), retried.cmds)
        looking.ignores(Msg.Food.Retry)
    }

    @Test fun `an answer for another barcode or after the sheet went changes nothing`() {
        val looking = opened().food(Msg.Food.Scanned("3017620422003"))
        looking.ignores(Msg.Food.Looked("5449000000996", ProductAnswer.Found(nutella)))
        looking.food(Msg.Food.CloseSheet).ignores(Msg.Food.Looked("3017620422003", ProductAnswer.Found(nutella)))
    }

    @Test fun `over the rate limit of Open Food Facts a lookup waits without a request`() {
        var at = opened()
        repeat(PRODUCT_REQUESTS_PER_MINUTE) { i -> at = at.food(Msg.Food.Scanned("4260999999990"), now = noon + i).food(Msg.Food.CloseSheet, now = noon + i) }
        val over = at.food(Msg.Food.Scanned("4260999999990"), now = noon + 30_000)
        assertEquals(FoodSheet.LookingUp(Lookup.Failed("4260999999990", Failure.BUSY)), over.sheet)
        assertTrue(over.cmds.isEmpty())
        // A minute later it may go again.
        assertEquals(listOf(Cmd.LookUpProduct("4260999999990")), at.food(Msg.Food.Scanned("4260999999990"), now = noon + 61_000).cmds)
    }

    @Test fun `when the scanner gives nothing the barcode is typed, and only a real one is looked up`() {
        val failed = step().food(Msg.Food.ScanFailed(ScanFailure.CANCELLED))
        assertEquals(today, failed.model.food.day)
        assertTrue(failed.sheet is FoodSheet.Barcode)
        assertTrue((step().food(Msg.Food.ScanFailed(ScanFailure.UNAVAILABLE)).sheet as FoodSheet.Barcode).note!!.contains("not available"))
        failed.ignores(Msg.Food.LookUp("3017620422004"))
        failed.ignores(Msg.Food.LookUp("123"))
        assertEquals(listOf(Cmd.LookUpProduct("3017620422003")), failed.food(Msg.Food.LookUp(" 3017620422003 ")).cmds)
        assertEquals(FoodSheet.Barcode(), opened().food(Msg.Food.TypeBarcode).sheet)
        step().ignores(Msg.Food.LookUp("3017620422003"))
    }

    // The add sheet and the log

    @Test fun `a picked food opens at its start amount, the chips and grams stay in limits, and Add logs it on the day shown`() {
        val picked = opened().food(Msg.Food.OpenSearch).food(Msg.Food.Search("oat")).food(Msg.Food.Pick(oats.id))
        assertEquals(FoodSheet.Amount(oats, 80.0), picked.sheet)
        assertEquals(MAX_AMOUNT, (picked.food(Msg.Food.SetAmount(Double.MAX_VALUE)).sheet as FoodSheet.Amount).amount, 0.0)
        assertEquals(1.0, (picked.food(Msg.Food.SetAmount(-5.0)).sheet as FoodSheet.Amount).amount, 0.0)
        picked.ignores(Msg.Food.SetAmount(80.0))
        val yesterday = opened().food(Msg.Food.PreviousDay).food(Msg.Food.OpenSearch).food(Msg.Food.Search("oat")).food(Msg.Food.Pick(oats.id))
        val added = yesterday.food(Msg.Food.SetAmount(40.0)).food(Msg.Food.ConfirmAmount, id = "e1")
        assertNull(added.sheet)
        val entry = added.state.foodLog.single()
        assertEquals(entryOf(oats, 40.0, "e1", noon, "2026-11-25"), entry)
        // A plain food is the app's: it is not kept.
        assertEquals(listOf(skyr), added.state.foods)
        opened().ignores(Msg.Food.ConfirmAmount)
        opened().ignores(Msg.Food.Pick(oats.id))
    }

    @Test fun `a tap on an entry changes its amount and keeps the rest of it`() {
        val logged = opened().food(Msg.Food.Scanned("4056489000006")).food(Msg.Food.ConfirmAmount, id = "e1")
        val opened = logged.food(Msg.Food.OpenEntry("e1"), now = noon + 60_000)
        assertEquals(FoodSheet.Amount(skyr, 450.0, "e1"), opened.sheet)
        val changed = opened.food(Msg.Food.SetAmount(225.0)).food(Msg.Food.ConfirmAmount, now = noon + 70_000).state.foodLog.single()
        assertEquals(entryOf(skyr, 225.0, "e1", noon, today), changed)
        logged.ignores(Msg.Food.OpenEntry("gone"))
    }

    @Test fun `a swipe deletes an entry with Undo, which puts it back where it was`() {
        val two = opened().food(Msg.Food.Scanned("4056489000006")).food(Msg.Food.ConfirmAmount, id = "e1")
            .food(Msg.Food.Scanned("4056489000006")).food(Msg.Food.ConfirmAmount, id = "e2")
        val deleted = two.food(Msg.Food.DeleteEntry("e1"))
        assertEquals(listOf("e2"), deleted.state.foodLog.map { it.id })
        val snackbar = deleted.uiEvents.filterIsInstance<UiEvent.Snackbar>().single()
        assertEquals("Deleted Skyr Natur", snackbar.text)
        assertEquals(Msg.Food.UndoDeleteEntry, snackbar.onAction)
        val undone = deleted.food(Msg.Food.UndoDeleteEntry)
        assertEquals(two.state.foodLog, undone.state.foodLog)
        undone.ignores(Msg.Food.UndoDeleteEntry)
        deleted.ignores(Msg.Food.DeleteEntry("e1"))
    }

    @Test fun `one tap on Again logs the food at its last amount, with Undo`() {
        val logged = opened().food(Msg.Food.Scanned("4056489000006")).food(Msg.Food.SetAmount(300.0)).food(Msg.Food.ConfirmAmount, id = "e1")
        val item = againItems(logged.model).single()
        assertEquals(skyr to 300.0, item.food to item.amount)
        val again = logged.food(Msg.Food.AddAgain(skyr.id, 300.0), now = noon + 60_000, id = "e2")
        assertEquals(listOf(300.0, 300.0), again.state.foodLog.map { it.amount })
        val snackbar = again.uiEvents.filterIsInstance<UiEvent.Snackbar>().single()
        assertEquals("Added Skyr Natur, 300 g", snackbar.text)
        assertEquals(listOf("e1"), again.food(Msg.Food.UndoAgain).state.foodLog.map { it.id })
        again.food(Msg.Food.UndoAgain).ignores(Msg.Food.UndoAgain)
        logged.ignores(Msg.Food.AddAgain("gone", 100.0))
        step().ignores(Msg.Food.AddAgain(skyr.id, 300.0))
    }

    @Test fun `quick kcal logs calories without a food, and changes them`() {
        val sheet = opened().food(Msg.Food.OpenQuick)
        assertEquals(FoodSheet.Quick(), sheet.sheet)
        sheet.ignores(Msg.Food.SaveQuick(0, 30, "Pizza"))
        val saved = sheet.food(Msg.Food.SaveQuick(850, 40, "", ), id = "q1")
        val entry = saved.state.foodLog.single()
        assertEquals(QUICK_NAME to 850, entry.name to entry.kcal)
        assertNull(entry.foodId)
        assertEquals(40.0, entry.protein, 0.0)
        val edit = saved.food(Msg.Food.OpenEntry("q1"))
        assertEquals(FoodSheet.Quick("q1"), edit.sheet)
        val changed = edit.food(Msg.Food.SaveQuick(900, null, "Burger")).state.foodLog.single()
        assertEquals(Triple("q1", "Burger", 900), Triple(changed.id, changed.name, changed.kcal))
        opened().ignores(Msg.Food.SaveQuick(500, null, ""))
    }

    // Own foods

    @Test fun `an own food is saved from its form and opens to be logged`() {
        val form = opened().food(Msg.Food.OpenSearch).food(Msg.Food.Search("Protein bread")).food(Msg.Food.NewOwn("Protein bread "))
        assertEquals(FoodSheet.Own(name = "Protein bread"), form.sheet)
        form.ignores(Msg.Food.SaveOwn(" ", 250.0, 20.0, null, null, null))
        val saved = form.food(Msg.Food.SaveOwn("Protein bread", 250.0, 20.0, 0.0, 3.0, 50.0), id = "b1")
        val bread = saved.state.foods.last()
        assertEquals(Food("own:b1", "Protein bread", kcal = 250.0, protein = 20.0, fat = 3.0, serving = 50.0, source = FoodSource.OWN), bread)
        assertEquals(FoodSheet.Amount(bread, 50.0), saved.sheet)
    }

    @Test fun `a lookup that found nothing keeps its barcode on the own food, which the next scan finds`() {
        val form = opened().food(Msg.Food.Scanned("4260999999990")).food(Msg.Food.Looked("4260999999990", ProductAnswer.NotFound))
        val saved = form.food(Msg.Food.SaveOwn("Tante Emma Quark", 70.0, 12.0, null, null, null), id = "q")
        assertEquals("4260999999990", saved.state.foods.last().barcode)
        val again = saved.food(Msg.Food.CloseSheet).food(Msg.Food.Scanned("4260999999990"))
        assertTrue(again.cmds.isEmpty())
        assertEquals("Tante Emma Quark", (again.sheet as FoodSheet.Amount).food.name)
    }

    @Test fun `editing or deleting an own food leaves what was logged of it as it was`() {
        val saved = opened().food(Msg.Food.NewOwn("Bread")).food(Msg.Food.SaveOwn("Bread", 250.0, 9.0, null, null, null), id = "b1")
        val logged = saved.food(Msg.Food.ConfirmAmount, id = "e1")
        val log = logged.state.foodLog
        val editing = logged.food(Msg.Food.EditOwn("own:b1"))
        assertEquals(FoodSheet.Own(id = "own:b1"), editing.sheet)
        val edited = editing.food(Msg.Food.SaveOwn("Rye bread", 220.0, 7.0, null, null, null))
        assertEquals("Rye bread", edited.state.foods.last().name)
        assertEquals(log, edited.state.foodLog)
        val deleted = edited.food(Msg.Food.DeleteOwn("own:b1"))
        assertTrue(deleted.state.foods.none { it.id == "own:b1" })
        assertEquals(log, deleted.state.foodLog)
        assertNull(deleted.sheet)
        // A scanned food is no own food: it is neither edited nor deleted this way.
        opened().ignores(Msg.Food.EditOwn(skyr.id))
        opened().ignores(Msg.Food.DeleteOwn(skyr.id))
    }

    // The goal

    @Test fun `the goal is set from the screen, 0 is none and every number has a limit`() {
        val goal = opened().food(Msg.Food.OpenGoal)
        assertEquals(FoodSheet.Goal, goal.sheet)
        assertEquals(FoodGoal(2400, 160), goal.food(Msg.Food.SaveGoal(2400, 160)).state.foodGoal)
        assertEquals(FoodGoal(MAX_GOAL_KCAL, null), goal.food(Msg.Food.SaveGoal(Int.MAX_VALUE, 0)).state.foodGoal)
        assertNull(goal.food(Msg.Food.SaveGoal(2400, 160)).sheet)
        opened().ignores(Msg.Food.SaveGoal(2400, 160))
    }

    @Test fun `the headline says what is left, how far over, or what was eaten`() {
        val logged = opened().food(Msg.Food.Scanned("4056489000006")).food(Msg.Food.ConfirmAmount, id = "e1")
        assertEquals("270 kcal eaten", kcalHeadline(openFoodDay(logged.model)!!))
        val withGoal = logged.food(Msg.Food.OpenGoal).food(Msg.Food.SaveGoal(2400, 160))
        assertEquals("2,130 kcal left", kcalHeadline(openFoodDay(withGoal.model)!!))
        assertEquals("70 kcal over", kcalHeadline(openFoodDay(logged.food(Msg.Food.OpenGoal).food(Msg.Food.SaveGoal(200, 0)).model)!!))
        assertEquals(270, foodToday(withGoal.model).totals.kcal)
    }

    // The search

    @Test fun `typing filters what the phone knows at once and sends nothing`() {
        val typed = opened().food(Msg.Food.OpenSearch).food(Msg.Food.Search("sky"))
        assertTrue(typed.cmds.isEmpty())
        assertEquals(listOf(skyr), foodResults(typed.model))
        assertEquals(listOf(oats), foodResults(typed.food(Msg.Food.Search("oats")).model))
        opened().ignores(Msg.Food.Search("sky"))
    }

    @Test fun `a submit asks Open Food Facts once, and its answer fills the results`() {
        val typed = opened().food(Msg.Food.OpenSearch).food(Msg.Food.Search("nutella "))
        val sent = typed.food(Msg.Food.SubmitSearch)
        assertEquals(listOf(Cmd.SearchFoods("nutella")), sent.cmds)
        assertEquals(Online.Searching("nutella"), (sent.sheet as FoodSheet.Search).online)
        sent.ignores(Msg.Food.SubmitSearch)
        sent.ignores(Msg.Food.Searched("skyr", SearchAnswer.Found(listOf(hitOf(nutella)))))
        val found = sent.food(Msg.Food.Searched("nutella", SearchAnswer.Found(listOf(hitOf(nutella)))))
        assertEquals(Online.Results("nutella", listOf(hitOf(nutella))), (found.sheet as FoodSheet.Search).online)
        assertEquals(Online.Failed("nutella", Failure.OFFLINE), (sent.food(Msg.Food.Searched("nutella", SearchAnswer.Offline)).sheet as FoodSheet.Search).online)
        assertEquals(Online.Failed("nutella", Failure.BUSY), (sent.food(Msg.Food.Searched("nutella", SearchAnswer.Busy)).sheet as FoodSheet.Search).online)
        // A result opens its add sheet and is kept once it is logged.
        val picked = found.food(Msg.Food.Pick(nutella.id))
        assertEquals(FoodSheet.Amount(nutella, 400.0), picked.sheet)
        assertTrue(nutella in picked.food(Msg.Food.ConfirmAmount).state.foods)
        opened().food(Msg.Food.OpenSearch).food(Msg.Food.Search("n")).ignores(Msg.Food.SubmitSearch)
    }

    @Test fun `a hit the search found without its calories is looked up by its barcode when it is picked`() {
        val bare = SearchHit("4337185761638", "Skyr natur", "K Classic", food = null)
        val found = opened().food(Msg.Food.OpenSearch).food(Msg.Food.Search("skyr")).food(Msg.Food.SubmitSearch)
            .food(Msg.Food.Searched("skyr", SearchAnswer.Found(listOf(bare, hitOf(nutella)))))
        val looking = found.food(Msg.Food.Pick(bare.id))
        assertEquals(FoodSheet.LookingUp(Lookup.Looking("4337185761638")), looking.sheet)
        assertEquals(listOf(Cmd.LookUpProduct("4337185761638")), looking.cmds)
        val kClassic = Food("off:4337185761638", "Skyr natur", "K-Classic", "4337185761638", kcal = 64.0, protein = 11.0, pack = 500.0, source = FoodSource.SCANNED)
        assertEquals(FoodSheet.Amount(kClassic, 500.0), looking.food(Msg.Food.Looked("4337185761638", ProductAnswer.Found(kClassic))).sheet)
        // One the phone knows by now needs no request.
        val known = opened(freshState(WeightUnit.KG).copy(foods = listOf(kClassic))).food(Msg.Food.OpenSearch).food(Msg.Food.Search("x")).food(Msg.Food.SubmitSearch)
            .food(Msg.Food.Searched("x", SearchAnswer.Found(listOf(bare)))).food(Msg.Food.Pick(bare.id))
        assertEquals(FoodSheet.Amount(kClassic, 500.0), known.sheet)
        assertTrue(known.cmds.none { it is Cmd.LookUpProduct })
        found.ignores(Msg.Food.Pick("off:0000000000000"))
    }

    @Test fun `over the rate limit of the search a submit waits without a request`() {
        var at = opened().food(Msg.Food.OpenSearch)
        repeat(SEARCH_REQUESTS_PER_MINUTE) { i -> at = at.food(Msg.Food.Search("q$i"), now = noon + i).food(Msg.Food.SubmitSearch, now = noon + i) }
        val over = at.food(Msg.Food.Search("more")).food(Msg.Food.SubmitSearch, now = noon + 20_000)
        assertEquals(Online.Failed("more", Failure.BUSY), (over.sheet as FoodSheet.Search).online)
        assertTrue(over.cmds.isEmpty())
    }

    // The week in Progress

    @Test fun `the week shows the last seven days against the goal, averages over the days logged, and the protein days`() {
        assertNull(foodWeek(step().model))
        fun quick(at: Step, day: Int, kcal: Int, protein: Int): Step =
            at.food(Msg.Food.OpenDay("2026-11-%02d".format(day))).food(Msg.Food.OpenQuick).food(Msg.Food.SaveQuick(kcal, protein, ""), id = "q$day")
        val goal = opened().food(Msg.Food.OpenGoal).food(Msg.Food.SaveGoal(2400, 160))
        // Nov 19 is eight days back: not in the week.
        val logged = listOf(Triple(19, 9000, 300), Triple(21, 2000, 170), Triple(24, 2600, 120), Triple(26, 1000, 160))
            .fold(goal) { at, (day, kcal, protein) -> quick(at, day, kcal, protein) }
        val week = foodWeek(logged.model)!!
        assertEquals(listOf("2026-11-20", "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25", "2026-11-26"), week.days.map { it.day })
        assertEquals(listOf("Fri", "Sat", "Sun", "Mon", "Tue", "Wed", "Thu"), week.days.map { it.name })
        assertEquals(listOf(0, 2000, 0, 0, 2600, 0, 1000), week.days.map { it.kcal })
        assertEquals(3, week.loggedDays)
        // (2000 + 2600 + 1000) / 3 and (170 + 120 + 160) / 3: the empty days do not count.
        assertEquals(1867, week.averageKcal)
        assertEquals(150, week.averageProtein)
        assertEquals(2, week.proteinDays)
        assertEquals(2400, week.goalKcal)
        // Without a protein goal there is nothing to reach; logged long ago, the week is there but empty.
        assertNull(foodWeek(logged.food(Msg.Food.OpenGoal).food(Msg.Food.SaveGoal(2400, 0)).model)!!.proteinDays)
        val later = foodWeek(logged.model.copy(now = noon + 30 * 86_400_000L))!!
        assertEquals(0, later.loggedDays)
        assertNull(later.averageKcal)
    }

    @Test fun `a bar opens its day's Food screen, never a day to come`() {
        val opened = step().food(Msg.Food.OpenDay("2026-11-23"))
        assertEquals("2026-11-23", opened.model.food.day)
        assertNull(opened.sheet)
        assertEquals(today, step().food(Msg.Food.OpenDay(today)).model.food.day)
        step().ignores(Msg.Food.OpenDay("2026-11-27"))
        step().ignores(Msg.Food.OpenDay("not a day"))
        // Back goes to where the bar was.
        assertNull(opened.food(Msg.Nav.Back).model.food.day)
    }

    // Days and time zones

    @Test fun `food logged a second before midnight counts for that day, at midnight for the next`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val before = ZonedDateTime.parse("2026-11-26T23:59:59+01:00[Europe/Berlin]").toInstant().toEpochMilli()
        val open = step().food(Msg.Food.Open, now = before, zone = berlin)
        assertEquals("2026-11-26", open.model.food.day)
        val late = open.food(Msg.Food.OpenQuick, now = before, zone = berlin).food(Msg.Food.SaveQuick(300, null, ""), now = before, zone = berlin, id = "late")
        assertEquals("2026-11-26", late.state.foodLog.single().day)
        val midnight = step().food(Msg.Food.Open, now = before + 1_000, zone = berlin)
        assertEquals("2026-11-27", midnight.model.food.day)
    }

    @Test fun `in another time zone today moves, what was eaten stays on its day`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val newYork = ZoneId.of("America/New_York")
        val night = ZonedDateTime.parse("2026-11-27T00:30:00+01:00[Europe/Berlin]").toInstant().toEpochMilli()
        val logged = step().food(Msg.Food.Open, now = night, zone = berlin).food(Msg.Food.OpenQuick, now = night, zone = berlin)
            .food(Msg.Food.SaveQuick(400, null, ""), now = night, zone = berlin, id = "q")
        assertEquals("2026-11-27", logged.state.foodLog.single().day)
        // Back in New York an hour later it is still the evening before there.
        val landed = logged.food(Msg.App.Foreground(true), now = night + 3_600_000, zone = newYork)
        assertEquals(newYork, landed.model.zone)
        assertEquals(0, foodToday(landed.model).totals.kcal)
        assertEquals("2026-11-27", landed.state.foodLog.single().day)
    }

    // The data

    @Test fun `Reset erases the foods, the log and the goal, a backup brings them back`() {
        val full = opened().food(Msg.Food.Scanned("4056489000006")).food(Msg.Food.ConfirmAmount, id = "e1").food(Msg.Food.OpenGoal).food(Msg.Food.SaveGoal(2400, 160))
        val backup = exportJson(full.model)
        val reset = full.food(Msg.Data.Reset)
        assertEquals(emptyList<Food>(), reset.state.foods)
        assertEquals(emptyList<Any>(), reset.state.foodLog)
        assertEquals(FoodGoal(), reset.state.foodGoal)
        val restored = reset.food(Msg.Data.Import(backup))
        assertEquals(full.state.foodLog, restored.state.foodLog)
        assertEquals(full.state.foods, restored.state.foods)
        assertEquals(full.state.foodGoal, restored.state.foodGoal)
    }
}
