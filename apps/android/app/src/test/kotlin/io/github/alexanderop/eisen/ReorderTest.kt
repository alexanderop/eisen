package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.Slot
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.moved
import io.github.alexanderop.eisen.domain.elm.nextRoutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Drag to reorder in the Workouts tab: the list move and the Msgs it sends through `update`. */
class ReorderTest {
    private val list = listOf("a", "b", "c", "d")

    @Test fun `moves the first item to the end`() {
        assertEquals(listOf("b", "c", "d", "a"), list.moved(0, 3))
    }

    @Test fun `moves the last item to the front`() {
        assertEquals(listOf("d", "a", "b", "c"), list.moved(3, 0))
    }

    @Test fun `moves one place either way`() {
        assertEquals(listOf("a", "c", "b", "d"), list.moved(1, 2))
        assertEquals(listOf("a", "c", "b", "d"), list.moved(2, 1))
    }

    @Test fun `the same index changes nothing`() {
        assertSame(list, list.moved(2, 2))
    }

    @Test fun `indexes outside the list are ignored`() {
        assertSame(list, list.moved(-1, 2))
        assertSame(list, list.moved(4, 0))
        assertSame(list, list.moved(0, 4))
        assertSame(list, list.moved(1, -1))
        assertSame(emptyList<String>(), emptyList<String>().moved(0, 0))
    }

    private var at = stepOf()

    private fun send(msg: Msg) {
        at = at.send(msg)
    }

    private fun slotIds(routineId: String) = at.state.routines.first { it.id == routineId }.slots.map { it.exerciseId }

    @Test fun `moving a workout keeps the same workout next`() {
        send(Msg.Routines.Add())
        send(Msg.Routines.Select("B"))
        send(Msg.Routines.Move(1, 0))
        assertEquals(listOf("B", "A"), at.state.routines.take(2).map { it.id })
        assertEquals("B", at.state.nextRoutineId)
        send(Msg.Routines.Move(0, 2))
        assertEquals("B", at.state.routines.last().id)
        assertEquals("B", nextRoutine(at.model).id)
    }

    @Test fun `a slot moves any distance in one change`() {
        val before = slotIds("A")
        send(Msg.Routines.MoveSlot("A", 0, before.lastIndex))
        assertEquals(before.drop(1) + before.first(), slotIds("A"))
        send(Msg.Routines.MoveSlot("A", 0, 99))
        assertEquals(before.drop(1) + before.first(), slotIds("A"))
    }

    @Test fun `undo puts a removed slot back at its place with its sets and reps`() {
        val before = at.state.routines.first { it.id == "A" }.slots
        val removed = before[1].copy(sets = 3, reps = 8)
        send(Msg.Routines.RemoveSlot("A", before[1].exerciseId))
        send(Msg.Routines.InsertSlot("A", 1, removed))
        assertEquals(before.toMutableList().apply { set(1, removed) }, at.state.routines.first { it.id == "A" }.slots)
        // A second undo of the same slot does not add it twice.
        send(Msg.Routines.InsertSlot("A", 0, removed))
        assertEquals(before.size, slotIds("A").size)
    }

    @Test fun `undo after the list got shorter puts the slot at the end`() {
        val slot = Slot("bench", 5, 5)
        send(Msg.Routines.RemoveSlot("A", "bench"))
        send(Msg.Routines.RemoveSlot("A", "hackSquat"))
        send(Msg.Routines.InsertSlot("A", 10, slot))
        assertEquals("bench", slotIds("A").last())
    }
}
