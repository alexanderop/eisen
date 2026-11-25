package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.ComboBuild
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.PickChoice
import io.github.alexanderop.eisen.app.elm.Picks
import io.github.alexanderop.eisen.app.elm.RoundSheet
import io.github.alexanderop.eisen.app.elm.SheetEvent
import io.github.alexanderop.eisen.app.elm.TimerSetup
import io.github.alexanderop.eisen.app.elm.pickChoice
import io.github.alexanderop.eisen.app.elm.sheetAfter
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.feature.timer.boxing.themeCombos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The boxing setup's list of a round's combinations (`Picks`, `PickChoice`) and its combo builder
 * (`ComboBuild`), both in `Model.kt`: one test per transition, through `update`, then the bugs of the
 * view's `fromNone`, `building` and `buildingFor`.
 */
class BoxingPicksTest {
    private fun setup(): Step = stepOf().send(Msg.Timer.Open(TimerMode.BOXING), now = STARTED_AT)
    private val Step.setup: TimerSetup get() = model.timerSetup!!
    private val Step.round: BoxingRound get() = setup.config.boxing!!.rounds[setup.sheet!!.index]
    private val Step.choice: PickChoice get() = pickChoice(round, setup.sheet!!.picks)

    /** Round 2 of the default plan, three-punch combinations, with its list open. */
    private fun list(): Step = setup().send(Msg.Timer.OpenRound(2)).send(Msg.Timer.OpenPicks)
    private val three = themeCombos(BoxingTheme.THREE).map { it.id }

    @Test fun `what the list shows comes from the round's pick, and None only while nothing is picked`() {
        val round = BoxingRound(BoxingTheme.THREE, 6, 0)
        assertEquals(PickChoice.All, pickChoice(round, null))
        assertEquals(PickChoice.All, pickChoice(round, Picks.SHOWN))
        assertEquals(PickChoice.NoneYet, pickChoice(round, Picks.NONE_YET))
        assertEquals(PickChoice.Some(listOf("a")), pickChoice(round.copy(pick = listOf("a")), Picks.NONE_YET))
    }

    @Test fun `the list opens and closes over the sheet, and nothing else changes`() {
        val list = list()
        assertEquals(RoundSheet(2, picks = Picks.SHOWN), list.setup.sheet)
        assertIgnored(list, Msg.Timer.OpenPicks)
        val closed = list.send(Msg.Timer.ClosePicks)
        assertEquals(RoundSheet(2), closed.setup.sheet)
        assertIgnored(closed, Msg.Timer.ClosePicks)
        assertIgnored(closed, Msg.Timer.PickNone)
        assertIgnored(setup(), Msg.Timer.OpenPicks)
    }

    @Test fun `None unchecks every box and keeps the round calling all, the next check is the only one`() {
        val none = list().send(Msg.Timer.PickNone)
        assertEquals(PickChoice.NoneYet, none.choice)
        assertEquals(emptyList<String>(), none.round.pick)
        val one = none.send(Msg.Timer.TogglePick(three[1]))
        assertEquals(PickChoice.Some(listOf(three[1])), one.choice)
        assertEquals(Picks.SHOWN, one.setup.sheet!!.picks)
    }

    @Test fun `a check takes one off all, All brings them back, and all checked is all`() {
        val fewer = list().send(Msg.Timer.TogglePick(three[0]))
        assertEquals(PickChoice.Some(three - three[0]), fewer.choice)
        assertEquals(PickChoice.All, fewer.send(Msg.Timer.TogglePick(three[0])).choice)
        assertEquals(emptyList<String>(), fewer.send(Msg.Timer.PickAll).round.pick)
        assertIgnored(list(), Msg.Timer.PickAll)
        assertIgnored(list(), Msg.Timer.TogglePick("not-a-combo"))
    }

    @Test fun `closing the list after None saves nothing, the round calls all`() {
        val closed = list().send(Msg.Timer.TogglePick(three[0])).send(Msg.Timer.PickNone).send(Msg.Timer.ClosePicks)
        assertEquals(emptyList<String>(), closed.round.pick)
        assertEquals(PickChoice.All, closed.choice)
    }

    @Test fun `the list goes with its sheet`() {
        val sheet = RoundSheet(2, picks = Picks.NONE_YET)
        assertEquals(RoundSheet(3), sheetAfter(sheet, SheetEvent.Open(3)))
        assertNull(sheetAfter(sheet, SheetEvent.Close))
        assertEquals(RoundSheet(3, picks = Picks.NONE_YET), sheetAfter(sheet, SheetEvent.Moved(2, 3)))
        assertEquals(RoundSheet(2, picks = Picks.SHOWN), sheetAfter(sheet.copy(copied = true), SheetEvent.Picked(Picks.SHOWN)))
        assertEquals(RoundSheet(2, copied = true), sheetAfter(sheet.copy(copied = true), SheetEvent.PickList(null)))
    }

    // The combo builder.

    @Test fun `the builder opens new from a round's tile or the list, or for one of the lifter's own, one at a time`() {
        val fromTile = setup().send(Msg.Timer.NewCombo(2))
        assertEquals(ComboBuild.New(2), fromTile.setup.combo)
        assertIgnored(fromTile, Msg.Timer.NewCombo(null))
        assertEquals(ComboBuild.New(null), setup().send(Msg.Timer.NewCombo(null)).setup.combo)
        assertIgnored(setup(), Msg.Timer.NewCombo(9))
        assertIgnored(setup(), Msg.Timer.EditOwn("own-1"))
        val saved = setup().send(Msg.Timer.SaveOwn(null, listOf("1", "2")))
        val id = saved.setup.config.boxing!!.own.single().id
        assertEquals(ComboBuild.Edit(id), saved.send(Msg.Timer.EditOwn(id)).setup.combo)
    }

    @Test fun `the builder closes on Save, on close, and when its combination is deleted`() {
        val open = setup().send(Msg.Timer.NewCombo(null))
        assertNull(open.send(Msg.Timer.CloseCombo).setup.combo)
        assertIgnored(setup(), Msg.Timer.CloseCombo)
        val saved = open.send(Msg.Timer.SaveOwn(null, listOf("1", "2")))
        assertNull(saved.setup.combo)
        val id = saved.setup.config.boxing!!.own.single().id
        val editing = saved.send(Msg.Timer.EditOwn(id))
        assertNull(editing.send(Msg.Timer.DeleteOwn(id)).setup.combo)
        // A Save that changes nothing keeps it open.
        val same = editing.send(Msg.Timer.SaveOwn(id, listOf("1", "2")))
        assertEquals(ComboBuild.Edit(id), same.setup.combo)
    }

    // The bugs of the view's flags.

    @Test fun `bug - after None the header does not say all are called while every box is unchecked`() {
        // Old: `fromNone` unchecked every box, while the header read the empty pick as "All 6 are called".
        val none = list().send(Msg.Timer.PickNone)
        assertEquals(PickChoice.NoneYet, none.choice)
        // And Done keeps all, not none: the round always calls something.
        assertEquals(PickChoice.All, none.send(Msg.Timer.ClosePicks).choice)
    }

    @Test fun `bug - a combination made from a round's tile makes that round a My combos round, one from the list none`() {
        val fromTile = setup().send(Msg.Timer.OpenRound(2)).send(Msg.Timer.NewCombo(2))
        val combo = fromTile.setup.combo as ComboBuild.New
        val saved = fromTile.send(Msg.Timer.SaveOwn(null, listOf("1", "2"), combo.round))
        assertEquals(BoxingTheme.OWN, saved.setup.config.boxing!!.rounds[2].theme)
        val fromList = setup().send(Msg.Timer.NewCombo(null))
        val plain = fromList.send(Msg.Timer.SaveOwn(null, listOf("1", "2"), (fromList.setup.combo as ComboBuild.New).round))
        assertEquals(setup().setup.config.boxing!!.rounds.map { it.theme }, plain.setup.config.boxing!!.rounds.map { it.theme })
    }
}
