package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.AmountFor
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.IconPicker
import io.github.alexanderop.eisen.app.elm.LabelStep
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.PhotoFailure
import io.github.alexanderop.eisen.app.elm.PhotoSource
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.FoodUnit
import io.github.alexanderop.eisen.feature.food.LabelMark
import io.github.alexanderop.eisen.feature.food.LabelText
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The photo of a label ([LabelStep]): each transition, from the add screen and from a new food's form, the answers that
 * come when nothing waits for them, and that reading a photo saves and logs nothing.
 */
class LabelFlowTest {
    private val noon = ZonedDateTime.parse("2026-11-27T12:00:00Z").toInstant().toEpochMilli()
    private val ice = savedLabel("4311501070628")
    private val nutellaJar = savedLabel("3017620422003")

    private fun Step.food(msg: Msg, id: String = "n1"): Step = update(msg, model, Env(noon, zone = ZoneOffset.UTC) { id }).let { (next, cmds) -> Step(next, cmds) }

    private fun Step.ignores(msg: Msg) {
        val after = food(msg)
        assertSame("$msg changed the model", model, after.model)
        assertTrue("$msg asked for ${after.cmds}", after.cmds.isEmpty())
    }

    private val Step.sheet get() = model.food.sheet
    private val Step.photo get() = (model.food.sheet as FoodSheet.Photo).step

    private fun addScreen() = Step(Model(freshState(WeightUnit.KG), now = noon)).food(Msg.Food.OpenAdd)
    private fun choosing() = addScreen().food(Msg.Food.ReadLabel())
    private fun waiting(source: PhotoSource = PhotoSource.PICKER) = choosing().food(Msg.Food.ChoosePhoto(source))
    private fun reading() = waiting().food(Msg.Food.PhotoChosen)

    @Test fun `closed to Choose, Photo of the label on the add screen`() {
        val step = choosing()
        assertEquals(FoodSheet.Photo(LabelStep.Choose), step.sheet)
        assertTrue(step.cmds.isEmpty())
    }

    @Test fun `Choose to Waiting, the camera app or the photo picker opens`() {
        for (source in PhotoSource.entries) {
            val step = choosing().food(Msg.Food.ChoosePhoto(source))
            assertEquals(LabelStep.Waiting(source), step.photo)
            assertEquals(listOf(Cmd.Ui(UiEvent.TakePhoto(source))), step.cmds)
        }
    }

    @Test fun `Waiting to Reading, a photo came`() {
        assertEquals(LabelStep.Reading, reading().photo)
    }

    @Test fun `Waiting back to where it began, the camera or the picker left without a photo, nothing changed`() {
        val start = addScreen()
        val cancelled = waiting().food(Msg.Food.PhotoCancelled)
        assertEquals(start.model, cancelled.model)
        // From a new food's form: the form as it was, its barcode and the name typed.
        val form = Step(Model(freshState(WeightUnit.KG), now = noon)).food(Msg.Food.OpenAdd).food(Msg.Food.TypeBarcode).food(Msg.Food.LookUp("4260999999990"))
            .food(Msg.Food.Looked("4260999999990", ProductAnswer.NotFound))
        val own = form.sheet as FoodSheet.Own
        val back = form.food(Msg.Food.ReadLabel("Quark")).food(Msg.Food.ChoosePhoto(PhotoSource.CAMERA)).food(Msg.Food.PhotoCancelled)
        assertEquals(own.copy(name = "Quark"), back.sheet)
    }

    @Test fun `Reading to the own food form, filled, the name empty, each value with its mark`() {
        val filled = reading().food(Msg.Food.LabelRecognized(ice))
        val form = filled.sheet as FoodSheet.Own
        assertEquals("", form.name)
        assertNull(form.id)
        val read = form.read ?: error("not filled")
        assertEquals(listOf(200.0, 1.6, 25.5, 10.1), read.values.map { it?.value })
        assertTrue(read.values.all { it?.mark == LabelMark.READ })
        assertEquals(50.0, read.serving)
        assertTrue(filled.cmds.isEmpty())
    }

    @Test fun `Reading to NothingFound, a photo without a table`() {
        assertEquals(LabelStep.NothingFound, reading().food(Msg.Food.LabelRecognized(nutellaJar)).photo)
        assertEquals(LabelStep.NothingFound, reading().food(Msg.Food.LabelRecognized(LabelText(emptyList()))).photo)
    }

    @Test fun `Waiting or Reading to Failed`() {
        assertEquals(LabelStep.Failed(PhotoFailure.NO_APP), waiting(PhotoSource.CAMERA).food(Msg.Food.LabelFailed(PhotoFailure.NO_APP)).photo)
        assertEquals(LabelStep.Failed(PhotoFailure.UNREADABLE), reading().food(Msg.Food.LabelFailed(PhotoFailure.UNREADABLE)).photo)
    }

    @Test fun `NothingFound and Failed, Try again is the choice again, Type it in the form`() {
        val nothing = reading().food(Msg.Food.LabelRecognized(nutellaJar))
        val failed = reading().food(Msg.Food.LabelFailed(PhotoFailure.UNREADABLE))
        for (step in listOf(nothing, failed)) {
            assertEquals(LabelStep.Choose, step.food(Msg.Food.RetryPhoto).photo)
            assertEquals(FoodSheet.Own(), step.food(Msg.Food.TypeItIn).sheet)
        }
    }

    @Test fun `CloseSheet and Back close it from every step`() {
        val steps = listOf(choosing(), waiting(), reading(), reading().food(Msg.Food.LabelRecognized(nutellaJar)), reading().food(Msg.Food.LabelFailed(PhotoFailure.UNREADABLE)))
        for (step in steps) {
            assertNull(step.food(Msg.Food.CloseSheet).sheet)
            assertNull(step.food(Msg.Nav.Back).sheet)
        }
    }

    @Test fun `a photo, an answer or a failure nobody waits for changes nothing`() {
        val add = addScreen()
        for (msg in listOf(Msg.Food.PhotoChosen, Msg.Food.PhotoCancelled, Msg.Food.LabelRecognized(ice), Msg.Food.LabelFailed(PhotoFailure.UNREADABLE), Msg.Food.RetryPhoto, Msg.Food.TypeItIn, Msg.Food.ChoosePhoto(PhotoSource.CAMERA))) {
            add.ignores(msg)
        }
        // A second photo while one is read, an answer while the choice shows.
        reading().ignores(Msg.Food.PhotoChosen)
        reading().ignores(Msg.Food.ChoosePhoto(PhotoSource.PICKER))
        choosing().ignores(Msg.Food.LabelRecognized(ice))
        waiting().ignores(Msg.Food.LabelRecognized(ice))
        // Not over another sheet, not over a food that is edited, not over the grid of pictures.
        add.food(Msg.Food.OpenQuick).ignores(Msg.Food.ReadLabel())
        add.food(Msg.Food.NewOwn("Bread")).food(Msg.Food.OpenIcons).ignores(Msg.Food.ReadLabel("Bread"))
    }

    @Test fun `after a restart in the middle the flow is gone, and a photo coming back changes nothing`() {
        // The model starts over: no sheet. The camera app's answer after the process ended finds nothing waiting for it.
        val restarted = Step(Model(freshState(WeightUnit.KG), now = noon))
        assertNull(restarted.model.food.sheet)
        restarted.ignores(Msg.Food.PhotoChosen)
        restarted.ignores(Msg.Food.LabelRecognized(ice))
    }

    @Test fun `reading a photo saves and logs nothing, Save food does, per 100 ml when the label says so, then the plate`() {
        val milk = reading().food(Msg.Food.LabelRecognized(savedLabel("4311501065525")))
        assertEquals(milk.model.state, addScreen().model.state)
        assertEquals(FoodUnit.ML, (milk.sheet as FoodSheet.Own).read?.unit)
        val saved = milk.food(Msg.Food.SaveOwn("Milch", 64.0, 3.3, 4.8, 3.5, null), id = "m1")
        val food = saved.model.state.foods.single()
        assertEquals(FoodUnit.ML, food.unit)
        assertEquals(FoodSource.OWN, food.source)
        // As after any own food on the add screen: its amount for the plate.
        assertEquals(AmountFor.Plate, (saved.sheet as FoodSheet.Amount).target)
        assertTrue(saved.model.state.foodLog.isEmpty())
    }

    @Test fun `Read from a photo in a new food's form keeps its barcode and the name typed`() {
        val form = addScreen().food(Msg.Food.TypeBarcode).food(Msg.Food.LookUp("4260999999990")).food(Msg.Food.Looked("4260999999990", ProductAnswer.NotFound))
        val filled = form.food(Msg.Food.ReadLabel("Quark")).food(Msg.Food.ChoosePhoto(PhotoSource.PICKER)).food(Msg.Food.PhotoChosen).food(Msg.Food.LabelRecognized(ice))
        val own = filled.sheet as FoodSheet.Own
        assertEquals("4260999999990", own.barcode)
        assertEquals("Quark", own.name)
        assertEquals(IconPicker.Closed, own.picker)
        assertEquals(200.0, own.read?.kcal?.value)
    }
}
