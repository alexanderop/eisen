package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.RoundSheet
import io.github.alexanderop.eisen.app.elm.SheetEvent
import io.github.alexanderop.eisen.app.elm.TemplateUndo
import io.github.alexanderop.eisen.app.elm.sheetAfter
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The boxing setup's round sheet and template Undo (`RoundSheet`, `TemplateUndo` in `Model.kt`): one test
 * per transition of [sheetAfter], then the bugs of the loose fields they replaced, through `update`.
 */
class BoxingSheetTest {
    private val default = DEFAULT_TIMERS.getValue(TimerMode.BOXING)
    private val sheet = RoundSheet(2, copied = true, heard = "jab-cross")

    private fun setup(): Step = stepOf().send(Msg.Timer.Open(TimerMode.BOXING), now = STARTED_AT)

    @Test fun `a sheet opens fresh, stays open for its own round, and closes to nothing`() {
        assertEquals(RoundSheet(3), sheetAfter(null, SheetEvent.Open(3)))
        assertEquals(RoundSheet(3), sheetAfter(sheet, SheetEvent.Open(3)))
        assertEquals(sheet, sheetAfter(sheet, SheetEvent.Open(2)))
        assertNull(sheetAfter(sheet, SheetEvent.Close))
        assertNull(sheetAfter(null, SheetEvent.Close))
    }

    @Test fun `fewer rounds than the sheet's round close it, otherwise it stays as it is`() {
        assertNull(sheetAfter(sheet, SheetEvent.Rounds(2)))
        assertEquals(sheet, sheetAfter(sheet, SheetEvent.Rounds(3)))
        assertNull(sheetAfter(null, SheetEvent.Rounds(3)))
    }

    @Test fun `Listen names what it played, copying says so, and an edit takes it back`() {
        assertEquals(sheet.copy(heard = "jab-hook"), sheetAfter(sheet, SheetEvent.Heard("jab-hook")))
        assertEquals(RoundSheet(2, copied = true), sheetAfter(RoundSheet(2), SheetEvent.CopiedToAll))
        assertEquals(sheet.copy(copied = false), sheetAfter(sheet, SheetEvent.Edited()))
        // Another theme for its own round: what it played is not in the round any more.
        assertEquals(RoundSheet(2), sheetAfter(sheet, SheetEvent.Edited(retheme = 2)))
        assertEquals(sheet.copy(copied = false), sheetAfter(sheet, SheetEvent.Edited(retheme = 4)))
        for (event in listOf(SheetEvent.Heard("x"), SheetEvent.CopiedToAll, SheetEvent.Edited())) assertNull(sheetAfter(null, event))
    }

    @Test fun `a sheet follows its round when rounds move or are copied`() {
        assertEquals(RoundSheet(4, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Moved(2, 4)))
        assertEquals(RoundSheet(1, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Moved(0, 3)))
        assertEquals(RoundSheet(3, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Moved(4, 1)))
        assertEquals(RoundSheet(2, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Moved(4, 5)))
        assertEquals(RoundSheet(3, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Duplicated(2)))
        assertEquals(RoundSheet(3, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Duplicated(0)))
        assertEquals(RoundSheet(2, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Duplicated(4)))
    }

    @Test fun `removing the sheet's round shows the next one as a fresh sheet, removing another one moves it`() {
        assertEquals(RoundSheet(2), sheetAfter(sheet, SheetEvent.Removed(2, left = 5)))
        assertEquals(RoundSheet(1), sheetAfter(RoundSheet(2, heard = "x"), SheetEvent.Removed(2, left = 2)))
        assertEquals(RoundSheet(1, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Removed(0, left = 5)))
        assertEquals(RoundSheet(2, heard = "jab-cross"), sheetAfter(sheet, SheetEvent.Removed(4, left = 5)))
        assertNull(sheetAfter(null, SheetEvent.Removed(0, left = 5)))
    }

    // The bugs of the loose fields, through update.

    @Test fun `removing a round after Listen forgets what Listen played in it`() {
        // Rounds 0 and 1 are the same, so the combination heard in round 0 is also one of round 1's.
        val same = setup().send(Msg.Timer.OpenRound(0)).send(Msg.Timer.CopyToAll(0))
        val heard = same.send(Msg.Timer.Listen(0))
        val first = (heard.cmds.single() as Cmd.PlayTimerSound).sound
        val removed = heard.send(Msg.Timer.RemoveRound(0))
        assertEquals(RoundSheet(0), removed.model.timerSetup!!.sheet)
        // The next Listen starts at the round's first combination, not after the removed round's.
        assertEquals(first, (removed.send(Msg.Timer.Listen(0)).cmds.single() as Cmd.PlayTimerSound).sound)
    }

    @Test fun `the rounds wheel takes a template's Undo away whole`() {
        val applied = setup().send(Msg.Timer.ApplyTemplate(1), now = LATER)
        assertEquals(TemplateUndo(default.boxing!!, LATER), applied.model.timerSetup!!.template)
        val moved = applied.send(Msg.Timer.Edit(applied.model.timerSetup!!.config.copy(rounds = 8)))
        assertNull(moved.model.timerSetup!!.template)
        assertIgnored(moved, Msg.Timer.UndoTemplate)
        assertIgnored(moved, Msg.Timer.ForgetTemplate(LATER))
    }

    @Test fun `the rounds wheel that closes the sheet leaves nothing of it behind`() {
        val open = setup().send(Msg.Timer.OpenRound(4)).send(Msg.Timer.CopyToAll(4)).send(Msg.Timer.Listen(4))
        assertEquals(RoundSheet(4, copied = true, heard = open.model.timerSetup!!.sheet?.heard), open.model.timerSetup!!.sheet)
        val fewer = open.send(Msg.Timer.Edit(open.model.timerSetup!!.config.copy(rounds = 3)))
        assertNull(fewer.model.timerSetup!!.sheet)
        // Opened again later, the sheet of that round starts fresh.
        val again = fewer.send(Msg.Timer.Edit(fewer.model.timerSetup!!.config.copy(rounds = 6))).send(Msg.Timer.OpenRound(4))
        assertEquals(RoundSheet(4), again.model.timerSetup!!.sheet)
    }

    @Test fun `a round of my combos made from the sheet forgets what Listen played`() {
        val open = setup().send(Msg.Timer.OpenRound(0)).send(Msg.Timer.Listen(0))
        val own = open.send(Msg.Timer.SaveOwn(null, listOf("1", "2"), round = 0))
        assertEquals(BoxingTheme.OWN, own.model.timerSetup!!.config.boxing!!.rounds[0].theme)
        assertEquals(RoundSheet(0), own.model.timerSetup!!.sheet)
        // A theme kept keeps what was heard: only the plan's "copied" goes.
        val round = open.model.timerSetup!!.config.boxing!!.rounds[0]
        val kept = open.send(Msg.Timer.EditRound(0, round.copy(every = round.every + 1)))
        assertEquals(round.every + 1, kept.model.timerSetup!!.config.boxing!!.rounds[0].every)
        assertEquals(open.model.timerSetup!!.sheet, kept.model.timerSetup!!.sheet)
    }
}
