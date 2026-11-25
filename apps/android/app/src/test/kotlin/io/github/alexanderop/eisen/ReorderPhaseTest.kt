package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.core.ui.ReorderEvent
import io.github.alexanderop.eisen.core.ui.ReorderPhase
import io.github.alexanderop.eisen.core.ui.reorderPhaseAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drag to reorder as one phase (`ReorderPhase.kt`): one test per transition. */
class ReorderPhaseTest {
    private val dragging = ReorderPhase.Dragging("b", grab = 12f, delta = 5f)

    @Test fun `a list at rest starts a drag, and only then`() {
        assertEquals(ReorderPhase.Dragging("b", 12f), reorderPhaseAfter(ReorderPhase.Idle, ReorderEvent.Start("b", 12f)))
        assertEquals(dragging, reorderPhaseAfter(dragging, ReorderEvent.Start("c", 0f)))
        assertEquals(ReorderPhase.Dropping("b"), reorderPhaseAfter(ReorderPhase.Dropping("b"), ReorderEvent.Start("c", 0f)))
        assertTrue(ReorderPhase.Idle.canStart)
        assertFalse(dragging.canStart)
        assertFalse(ReorderPhase.Dropping("b").canStart)
    }

    @Test fun `the dragged item adds up the finger's moves`() {
        assertEquals(dragging.copy(delta = -3f), reorderPhaseAfter(dragging, ReorderEvent.Move(-8f)))
        assertEquals(ReorderPhase.Idle, reorderPhaseAfter(ReorderPhase.Idle, ReorderEvent.Move(8f)))
    }

    @Test fun `a drop glides the item into place, then the list is at rest`() {
        assertEquals(ReorderPhase.Dropping("b"), reorderPhaseAfter(dragging, ReorderEvent.Drop))
        assertEquals(ReorderPhase.Idle, reorderPhaseAfter(ReorderPhase.Dropping("b"), ReorderEvent.Settled))
        assertEquals(ReorderPhase.Idle, reorderPhaseAfter(ReorderPhase.Idle, ReorderEvent.Drop))
        assertEquals(dragging, reorderPhaseAfter(dragging, ReorderEvent.Settled))
    }
}
