package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.timer.ui.FinishHold
import io.github.alexanderop.eisen.feature.timer.ui.FinishHoldEvent
import io.github.alexanderop.eisen.feature.timer.ui.FinishHoldStep
import io.github.alexanderop.eisen.feature.timer.ui.finishHoldAfter
import org.junit.Assert.assertEquals
import org.junit.Test

/** For Time's "Hold to finish" (`FinishHold.kt`): one test per transition, then the bugs of the old press handler. */
class FinishHoldTest {
    private val idle = FinishHold.Idle
    private val filling = FinishHold.Filling
    private val armed = FinishHold.Armed
    private val inside = FinishHoldEvent.Release(inside = true)
    private val outside = FinishHoldEvent.Release(inside = false)

    private fun run(vararg events: FinishHoldEvent): List<FinishHoldStep> {
        var hold: FinishHold = idle
        return events.map { event -> finishHoldAfter(hold, event).also { hold = it.hold } }
    }

    @Test fun `a finger down starts filling, only from idle`() {
        assertEquals(FinishHoldStep(filling), finishHoldAfter(idle, FinishHoldEvent.Down))
        assertEquals(FinishHoldStep(filling), finishHoldAfter(filling, FinishHoldEvent.Down))
        assertEquals(FinishHoldStep(armed), finishHoldAfter(armed, FinishHoldEvent.Down))
    }

    @Test fun `a full bar arms the button while the finger is down`() {
        assertEquals(FinishHoldStep(armed), finishHoldAfter(filling, FinishHoldEvent.Full))
        assertEquals(FinishHoldStep(armed), finishHoldAfter(armed, FinishHoldEvent.Full))
        // The fill of a touch that already ended arms nothing.
        assertEquals(FinishHoldStep(idle), finishHoldAfter(idle, FinishHoldEvent.Full))
    }

    @Test fun `letting go too early on the button shows the hint and finishes nothing`() {
        assertEquals(FinishHoldStep(idle, hint = true), finishHoldAfter(filling, inside))
    }

    @Test fun `letting go early off the button or a cancel while filling goes back quietly`() {
        assertEquals(FinishHoldStep(idle), finishHoldAfter(filling, outside))
        assertEquals(FinishHoldStep(idle), finishHoldAfter(filling, FinishHoldEvent.Cancel))
    }

    @Test fun `letting go of a full bar on the button finishes`() {
        assertEquals(FinishHoldStep(idle, finish = true), finishHoldAfter(armed, inside))
    }

    @Test fun `a full bar let go off the button or cancelled backs out`() {
        assertEquals(FinishHoldStep(idle), finishHoldAfter(armed, outside))
        assertEquals(FinishHoldStep(idle), finishHoldAfter(armed, FinishHoldEvent.Cancel))
    }

    @Test fun `idle ignores the end of a touch it never saw`() {
        for (event in listOf(inside, outside, FinishHoldEvent.Cancel)) assertEquals(FinishHoldStep(idle), finishHoldAfter(idle, event))
    }

    // The bugs of the old handler, which finished whenever the bar was full, whatever the finger did.

    @Test fun `bug - hold until full, slide off and lift - the run goes on`() {
        // The old handler ignored that the press was cancelled: fill >= 1 finished the run.
        val steps = run(FinishHoldEvent.Down, FinishHoldEvent.Full, FinishHoldEvent.Cancel, outside)
        assertEquals(listOf(false, false, false, false), steps.map { it.finish })
        assertEquals(idle, steps.last().hold)
    }

    @Test fun `bug - after a finish the next short tap does not finish again`() {
        // Old: a finish the program ignored left the bar full, and the next tap's release finished at once.
        val steps = run(FinishHoldEvent.Down, FinishHoldEvent.Full, inside, FinishHoldEvent.Down, inside)
        assertEquals(listOf(false, false, true, false, false), steps.map { it.finish })
        assertEquals(FinishHoldStep(idle, hint = true), steps.last())
    }
}
