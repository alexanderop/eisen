package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.AddScreen
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.DishFailure
import io.github.alexanderop.eisen.app.elm.DishStep
import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.PhotoFor
import io.github.alexanderop.eisen.app.elm.PhotoSource
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.DishAi
import io.github.alexanderop.eisen.feature.food.DishGuess
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.MAX_DISH_GRAMS
import io.github.alexanderop.eisen.feature.food.MAX_DISH_ITEMS
import io.github.alexanderop.eisen.feature.food.MAX_PLATE_ITEMS
import io.github.alexanderop.eisen.feature.food.matchDish
import io.github.alexanderop.eisen.feature.food.parsePlainFoods
import io.github.alexanderop.eisen.feature.food.plateItemOf
import io.github.alexanderop.eisen.feature.food.readDish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The photo of a dish: reading what the model wrote, finding its names among the foods the phone knows, and each
 * transition of [DishStep], with the answers that come when nothing waits for them.
 */
class DishTest {
    private val plain = parsePlainFoods(File("src/main/assets/foods/plain.json").readText())

    // Reading the model's answer

    @Test fun `an answer in the form asked for is read line by line`() {
        assertEquals(
            listOf(DishGuess("rice, cooked", 180.0), DishGuess("chicken breast, grilled", 150.0), DishGuess("broccoli, cooked", 90.0)),
            readDish("rice, cooked; 180\nchicken breast, grilled; 150\nbroccoli, cooked; 90\n"),
        )
    }

    @Test fun `bullets, numbers, bold marks, units and a line of talk around it are read past`() {
        val answer = """
            Here is what I see:
            - **Rice, cooked**; 180 g
            2. chicken breast, grilled; about 150g
            * broccoli: 90 grams
            • egg, fried - 50
            orange juice; 200-250 ml
            name; grams
            none
        """.trimIndent()
        assertEquals(
            listOf(DishGuess("Rice, cooked", 180.0), DishGuess("chicken breast, grilled", 150.0), DishGuess("broccoli", 90.0), DishGuess("egg, fried", 50.0), DishGuess("orange juice", 200.0)),
            readDish(answer),
        )
    }

    @Test fun `no food, no amount, an amount of nothing and a second line of the same name give no guess`() {
        assertEquals(emptyList<DishGuess>(), readDish("none"))
        assertEquals(emptyList<DishGuess>(), readDish(""))
        assertEquals(emptyList<DishGuess>(), readDish("rice\n; 100\n123; 100\nrice; 0\nrice; lots"))
        assertEquals(listOf(DishGuess("rice", 100.0)), readDish("rice; 100\nRice; 200"))
    }

    @Test fun `the grams and the number of foods stay within their limits`() {
        assertEquals(listOf(DishGuess("rice", MAX_DISH_GRAMS), DishGuess("salt", 1.0), DishGuess("oil", 13.0)), readDish("rice; 999999\nsalt; 0.2\noil; 12,5"))
        assertEquals(MAX_DISH_ITEMS, readDish((1..40).joinToString("\n") { "food $it; 50" }).size)
        assertTrue(readDish("x".repeat(500) + "; 50").single().name.length <= 60)
    }

    // Finding the food

    private fun found(name: String) = matchDish(name, plain)?.name

    @Test fun `a name finds the plain food it means, cooked before raw`() {
        assertEquals("Rice, white, cooked", found("rice, cooked"))
        assertEquals("Rice, white, cooked", found("rice"))
        assertEquals("Rice, brown, cooked", found("brown rice"))
        assertEquals("Rice, white, raw", found("rice, raw"))
        assertEquals("Chicken breast, grilled", found("chicken breast, grilled"))
        assertEquals("Chicken breast, grilled", found("Grilled chicken breast"))
        assertEquals("Broccoli, cooked", found("steamed broccoli"))
        assertEquals("Egg, fried", found("fried egg"))
        assertEquals("Eggs, scrambled", found("scrambled eggs"))
        assertEquals("Pasta, cooked", found("pasta"))
        assertEquals("French fries", found("french fries"))
        assertEquals("Tomato", found("tomatoes, sliced"))
        assertEquals("Salmon, farmed, cooked", found("salmon"))
        assertEquals("Avocado", found("avocado slices"))
    }

    @Test fun `a name is not a food that only shares a word with it`() {
        assertNull(found("chicken curry"))
        assertNull(found("rice pudding"))
        assertNull(found("caesar salad"))
        assertNull(found("cooked"))
        assertNull(found(""))
    }

    @Test fun `the food logged most often wins among the ones a name fits as well`() {
        val brown = plain.first { it.name == "Rice, brown, cooked" }
        val log = List(3) { io.github.alexanderop.eisen.feature.food.entryOf(brown, 100.0, "e$it", 0, "2026-11-27") }
        assertEquals("Rice, brown, cooked", matchDish("rice", plain, log)?.name)
    }

    @Test fun `an own food is found by its name`() {
        val skyr = Food("own:1", "Skyr Natur", kcal = 62.0, protein = 11.0, source = FoodSource.OWN)
        assertEquals(skyr, matchDish("skyr", listOf(skyr) + plain))
    }

    // The flow

    private val noon = ZonedDateTime.parse("2026-11-27T12:00:00Z").toInstant().toEpochMilli()
    private fun Step.food(msg: Msg, id: String = "n1"): Step = update(msg, model, Env(noon, zone = ZoneOffset.UTC) { id }).let { (next, cmds) -> Step(next, cmds) }
    private fun Step.ignores(msg: Msg) {
        val after = food(msg)
        assertSame("$msg changed the model", model, after.model)
        assertTrue("$msg asked for ${after.cmds}", after.cmds.isEmpty())
    }
    private val Step.dish get() = (model.food.sheet as FoodSheet.Dish).step

    private fun addScreen(ai: DishAi = DishAi.READY) = Step(Model(freshState(WeightUnit.KG), now = noon, plainFoods = plain)).food(Msg.Food.OpenAdd).food(Msg.Food.DishAiChecked(ai))
    private fun choosing() = addScreen().food(Msg.Food.ReadDish)
    private fun waiting() = choosing().food(Msg.Food.ChoosePhoto(PhotoSource.PICKER))
    private fun reading() = waiting().food(Msg.Food.PhotoChosen)
    private fun found() = reading().food(Msg.Food.DishRead("rice, cooked; 180\nchicken curry; 250\nbroccoli, cooked; 90"))

    @Test fun `opening the add screen asks the phone for its model, and without one nothing is offered`() {
        val opened = Step(Model(freshState(WeightUnit.KG), now = noon)).food(Msg.Food.OpenAdd)
        assertEquals(listOf<Cmd>(Cmd.CheckDishAi), opened.cmds)
        assertEquals(DishAi.MISSING, opened.model.food.dishAi)
        opened.ignores(Msg.Food.ReadDish)
        opened.ignores(Msg.Food.DishAiChecked(DishAi.MISSING))
        assertEquals(DishAi.READY, opened.food(Msg.Food.DishAiChecked(DishAi.READY)).model.food.dishAi)
    }

    @Test fun `a model that is not downloaded yet is downloaded first, and a download that fails says so`() {
        val step = addScreen(DishAi.DOWNLOADABLE).food(Msg.Food.ReadDish)
        assertEquals(DishStep.Choose, step.dish)
        step.ignores(Msg.Food.ChoosePhoto(PhotoSource.CAMERA))
        val downloading = step.food(Msg.Food.DownloadDishAi)
        assertEquals(DishAi.DOWNLOADING, downloading.model.food.dishAi)
        assertEquals(listOf<Cmd>(Cmd.DownloadDishAi), downloading.cmds)
        downloading.ignores(Msg.Food.DownloadDishAi)
        val failed = downloading.food(Msg.Food.DishAiDownloadFailed)
        assertEquals(DishAi.DOWNLOADABLE, failed.model.food.dishAi)
        assertEquals("The download did not finish. Try again, on Wi-Fi.", (failed.uiEvents.single() as UiEvent.Snackbar).text)
        val ready = downloading.food(Msg.Food.DishAiChecked(DishAi.READY))
        assertEquals(DishStep.Waiting(PhotoSource.CAMERA), ready.food(Msg.Food.ChoosePhoto(PhotoSource.CAMERA)).dish)
        step.ignores(Msg.Food.DishAiDownloadFailed)
    }

    @Test fun `Choose to Waiting to Reading, the photo is taken for a dish`() {
        for (source in PhotoSource.entries) {
            val step = choosing().food(Msg.Food.ChoosePhoto(source))
            assertEquals(DishStep.Waiting(source), step.dish)
            assertEquals(listOf<Cmd>(Cmd.Ui(UiEvent.TakePhoto(source, PhotoFor.DISH))), step.cmds)
        }
        assertEquals(DishStep.Reading, reading().dish)
        assertNull(waiting().food(Msg.Food.PhotoCancelled).model.food.sheet)
    }

    @Test fun `Reading to Found, the foods the phone knows at the grams guessed and the names it does not know`() {
        val step = found().dish as DishStep.Found
        assertEquals(listOf("Rice, white, cooked" to 180.0, "Broccoli, cooked" to 90.0), step.items.map { it.entry.name to it.entry.amount })
        assertEquals(listOf("n1", "n1.2"), step.items.map { it.entry.id })
        assertEquals(listOf("chicken curry"), step.missing)
        // The calories are the food's, worked out from its numbers per 100 g.
        val rice = plain.first { it.name == "Rice, white, cooked" }
        assertEquals(plateItemOf(rice, 180.0, "n1"), step.items[0])
        assertTrue(found().model.food.plate.isEmpty())
        assertEquals(found().model.state, addScreen().model.state)
    }

    @Test fun `two names that mean one food are that food once`() {
        val step = reading().food(Msg.Food.DishRead("rice; 100\nwhite rice, cooked; 80")).dish as DishStep.Found
        assertEquals(listOf(100.0), step.items.map { it.entry.amount })
    }

    @Test fun `Reading to NothingFound or Failed, and back to Choose`() {
        assertEquals(DishStep.NothingFound, reading().food(Msg.Food.DishRead("none")).dish)
        assertEquals(DishStep.Failed(DishFailure.MODEL), reading().food(Msg.Food.DishFailed(DishFailure.MODEL)).dish)
        assertEquals(DishStep.Failed(DishFailure.NO_APP), waiting().food(Msg.Food.DishFailed(DishFailure.NO_APP)).dish)
        for (step in listOf(reading().food(Msg.Food.DishRead("none")), reading().food(Msg.Food.DishFailed(DishFailure.UNREADABLE)), found())) {
            assertEquals(DishStep.Choose, step.food(Msg.Food.RetryPhoto).dish)
        }
    }

    @Test fun `PutDish puts the foods found on the plate and closes the sheet, and logs nothing`() {
        val step = found().food(Msg.Food.PutDish)
        assertNull(step.model.food.sheet)
        assertEquals(listOf("Rice, white, cooked", "Broccoli, cooked"), step.model.food.plate.map { it.entry.name })
        assertEquals(AddScreen(), step.model.food.add)
        assertTrue(step.state.foodLog.isEmpty())
    }

    @Test fun `SearchDish puts the foods found on the plate and searches for the name`() {
        val step = found().food(Msg.Food.SearchDish("chicken curry"))
        assertNull(step.model.food.sheet)
        assertEquals(2, step.model.food.plate.size)
        assertEquals("chicken curry", step.model.food.add?.query)
        found().ignores(Msg.Food.SearchDish("pizza"))
    }

    @Test fun `a plate too full for the foods found takes none of them and the sheet stays`() {
        val rice = plain.first { it.name == "Rice, white, cooked" }
        val full = found().let { step -> step.copy(model = step.model.copy(food = step.model.food.copy(plate = List(MAX_PLATE_ITEMS - 1) { plateItemOf(rice, 100.0, "p$it") }))) }
        val after = full.food(Msg.Food.PutDish)
        assertEquals(MAX_PLATE_ITEMS - 1, after.model.food.plate.size)
        assertTrue(after.model.food.sheet is FoodSheet.Dish)
        assertTrue(after.uiEvents.single() is UiEvent.Snackbar)
    }

    @Test fun `an answer, a photo or a button that comes when nothing waits for it changes nothing`() {
        val late = listOf(Msg.Food.DishRead("rice; 100"), Msg.Food.DishFailed(DishFailure.MODEL), Msg.Food.PutDish, Msg.Food.SearchDish("rice"), Msg.Food.DownloadDishAi)
        for (msg in late) {
            addScreen().ignores(msg)
            choosing().ignores(msg)
        }
        waiting().ignores(Msg.Food.DishRead("rice; 100"))
        reading().ignores(Msg.Food.PhotoChosen)
        reading().ignores(Msg.Food.PhotoCancelled)
        found().ignores(Msg.Food.DishRead("rice; 100"))
        reading().food(Msg.Food.DishRead("chicken curry; 200")).ignores(Msg.Food.PutDish)
        // The label's own answers do not reach the dish, nor the dish's the label.
        reading().ignores(Msg.Food.TypeItIn)
        addScreen().food(Msg.Food.ReadLabel()).food(Msg.Food.ChoosePhoto(PhotoSource.PICKER)).food(Msg.Food.PhotoChosen).ignores(Msg.Food.DishRead("rice; 100"))
    }

    /**
     * Whatever comes in, in whatever order: reading a photo of a dish saves and logs nothing, only "Put on the plate" and
     * "Search" of what it found fill the plate, never beyond its limit, and its sheet lies only over the add screen.
     */
    @Test fun `the dish keeps its rules whatever Msgs come in`() {
        val answers = listOf("rice, cooked; 180\nchicken curry; 250\negg, fried; 50", "none", "", "- **Banana**; 99999 g\nmilk; 200", "pizza; 300", "x".repeat(400))
        var plated = 0
        var found = 0
        for (seed in 1L..60L) {
            val dice = java.util.Random(seed)
            fun <T> pick(items: List<T>): T = items[dice.nextInt(items.size)]
            var step = Step(Model(freshState(WeightUnit.KG), now = noon, plainFoods = plain))
            repeat(200) { turn ->
                val any: Msg = pick(
                    listOf(
                        Msg.Food.OpenAdd, Msg.Food.ReadDish, Msg.Food.ReadDish, Msg.Food.DishAiChecked(pick(DishAi.entries)), Msg.Food.DishAiChecked(DishAi.READY),
                        Msg.Food.DownloadDishAi, Msg.Food.DishAiDownloadFailed, Msg.Food.ChoosePhoto(pick(PhotoSource.entries)), Msg.Food.ChoosePhoto(pick(PhotoSource.entries)),
                        Msg.Food.PhotoChosen, Msg.Food.PhotoChosen, Msg.Food.PhotoCancelled, Msg.Food.DishRead(pick(answers)), Msg.Food.DishRead(pick(answers)),
                        Msg.Food.DishFailed(pick(DishFailure.entries)), Msg.Food.RetryPhoto, Msg.Food.PutDish, Msg.Food.PutDish, Msg.Food.SearchDish("chicken curry"),
                        Msg.Food.TypeItIn, Msg.Food.ReadLabel(), Msg.Food.CloseSheet, Msg.Food.CloseAdd, Msg.Food.ClearPlate, Msg.Nav.Back,
                    ),
                )
                // Most of the time the step is answered as the lifter, the camera and the model would.
                val next: Msg? = when (val dish = (step.model.food.sheet as? FoodSheet.Dish)?.step) {
                    null -> if (step.model.food.add == null) Msg.Food.OpenAdd else if (step.model.food.dishAi == DishAi.MISSING) Msg.Food.DishAiChecked(DishAi.READY) else Msg.Food.ReadDish
                    DishStep.Choose -> if (step.model.food.dishAi == DishAi.READY) Msg.Food.ChoosePhoto(pick(PhotoSource.entries)) else Msg.Food.DishAiChecked(DishAi.READY)
                    is DishStep.Waiting -> Msg.Food.PhotoChosen
                    DishStep.Reading -> Msg.Food.DishRead(pick(answers))
                    is DishStep.Found -> if (dish.items.isEmpty()) Msg.Food.SearchDish(dish.missing.first()) else Msg.Food.PutDish
                    DishStep.NothingFound, is DishStep.Failed -> Msg.Food.RetryPhoto
                }
                val msg: Msg = if (next != null && dice.nextInt(3) > 0) next else any
                val after = step.food(msg, id = "s$seed-$turn")
                val where = "seed $seed, turn $turn, $msg"
                assertEquals(where, step.state, after.state)
                assertTrue(where, after.model.food.plate.size <= MAX_PLATE_ITEMS)
                assertEquals(where, after.model.food.plate.size, after.model.food.plate.map { it.entry.id }.toSet().size)
                if (after.model.food.plate.size > step.model.food.plate.size) {
                    assertTrue(where, msg == Msg.Food.PutDish || msg is Msg.Food.SearchDish)
                    assertTrue(where, step.model.food.sheet is FoodSheet.Dish && after.model.food.sheet == null)
                    plated++
                }
                (after.model.food.sheet as? FoodSheet.Dish)?.let { sheet ->
                    assertTrue(where, after.model.food.add != null)
                    // A photo is only asked for while the phone has the model.
                    if (sheet.step is DishStep.Waiting && sheet != step.model.food.sheet) assertEquals(where, DishAi.READY, step.model.food.dishAi)
                    if (sheet.step is DishStep.Found && sheet != step.model.food.sheet) found++
                }
                step = after
            }
        }
        assertTrue("only $found photos of dishes were read, $plated reached the plate", found >= 20 && plated >= 10)
    }
}
