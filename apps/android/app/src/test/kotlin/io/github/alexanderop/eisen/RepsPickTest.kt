package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.core.ui.RepsPickEvent
import io.github.alexanderop.eisen.core.ui.RepsPickPhase
import io.github.alexanderop.eisen.core.ui.RepsPickStep
import io.github.alexanderop.eisen.core.ui.pick
import io.github.alexanderop.eisen.core.ui.repsPickAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Holding a set to pick its reps (`RepsPickPhase.kt`): one test per transition, then the bug of the old lift. */
class RepsPickTest {
    private val set = "bench set 2"
    private val other = "squat set 1"
    private val closed: RepsPickPhase<String> = RepsPickPhase.Closed
    private val held = RepsPickPhase.Held(set)
    private val lifted = RepsPickPhase.Lifted(set)
    private fun sliding(under: Int?) = RepsPickPhase.Sliding(set, under)

    private fun after(phase: RepsPickPhase<String>, event: RepsPickEvent<String>) = repsPickAfter(phase, event)

    @Test fun `a hold opens the numbers with the finger down, TalkBack without one`() {
        assertEquals(RepsPickStep(held), after(closed, RepsPickEvent.Open(set, fingerDown = true)))
        assertEquals(RepsPickStep(lifted), after(closed, RepsPickEvent.Open(set, fingerDown = false)))
        // Another set's hold takes the place of the numbers open.
        assertEquals(RepsPickStep(RepsPickPhase.Held(other)), after(lifted, RepsPickEvent.Open(other, fingerDown = true)))
        assertEquals(set, held.pick)
        assertNull(closed.pick)
    }

    @Test fun `a held finger slides once it is past the slop`() {
        assertEquals(RepsPickStep(held), after(held, RepsPickEvent.Move(under = 5, slid = false)))
        assertEquals(RepsPickStep(sliding(4), tick = true), after(held, RepsPickEvent.Move(under = 4, slid = true)))
        assertEquals(RepsPickStep(sliding(null)), after(held, RepsPickEvent.Move(under = null, slid = true)))
    }

    @Test fun `sliding follows the number under the finger and ticks on each new one`() {
        assertEquals(RepsPickStep(sliding(3), tick = true), after(sliding(4), RepsPickEvent.Move(3, slid = true)))
        assertEquals(RepsPickStep(sliding(3)), after(sliding(3), RepsPickEvent.Move(3, slid = true)))
        assertEquals(RepsPickStep(sliding(null)), after(sliding(3), RepsPickEvent.Move(null, slid = true)))
        // Back near where it started it still slides.
        assertEquals(RepsPickStep(sliding(5), tick = true), after(sliding(4), RepsPickEvent.Move(5, slid = false)))
    }

    @Test fun `a lift without sliding leaves the numbers open for a tap`() {
        assertEquals(RepsPickStep(lifted), after(held, RepsPickEvent.Lift(5)))
    }

    @Test fun `a lift after sliding picks the number under the finger, or leaves them open beside the numbers`() {
        assertEquals(RepsPickStep(closed, picked = 3), after(sliding(3), RepsPickEvent.Lift(3)))
        assertEquals(RepsPickStep(lifted), after(sliding(3), RepsPickEvent.Lift(null)))
    }

    @Test fun `a cancel closes without a pick`() {
        assertEquals(RepsPickStep(closed), after(held, RepsPickEvent.Cancel))
        assertEquals(RepsPickStep(closed), after(sliding(3), RepsPickEvent.Cancel))
    }

    @Test fun `a tapped number is picked from every open state`() {
        for (phase in listOf(held, sliding(2), lifted)) assertEquals(RepsPickStep(closed, picked = 1), after(phase, RepsPickEvent.Tap(1)))
        assertEquals(RepsPickStep(closed), after(closed, RepsPickEvent.Tap(1)))
    }

    @Test fun `a tap outside, Back or moved cards close from every state`() {
        for (phase in listOf(closed, held, sliding(2), lifted)) assertEquals(RepsPickStep(closed), after(phase, RepsPickEvent.Close))
    }

    @Test fun `without a finger down the end of an old touch changes nothing`() {
        for (event in listOf(RepsPickEvent.Move(3, slid = true), RepsPickEvent.Lift(3), RepsPickEvent.Cancel)) {
            assertEquals(RepsPickStep(lifted), after(lifted, event))
            assertEquals(RepsPickStep(closed), after(closed, event))
        }
    }

    @Test fun `bug - the system taking the touch mid-slide logs nothing`() {
        // Compose reports a cancel as a lift. The old gesture treated it as one and picked the number under the finger.
        var phase = closed
        val picked = mutableListOf<Int>()
        for (event in listOf(RepsPickEvent.Open(set, true), RepsPickEvent.Move(4, true), RepsPickEvent.Move(3, true), RepsPickEvent.Cancel)) {
            val step = after(phase, event)
            phase = step.phase
            step.picked?.let(picked::add)
        }
        assertEquals(emptyList<Int>(), picked)
        assertEquals(closed, phase)
    }
}
