package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.data.moved
import io.github.alexanderop.eisen.domain.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/** Drag to reorder in the Workouts tab: the list move and the Store changes it calls. */
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

    @Before fun reset() {
        Store.resetAll()
    }

    private fun slotIds(routineId: String) = Store.state.routines.first { it.id == routineId }.slots.map { it.exerciseId }

    @Test fun `moving a workout keeps the same workout next`() {
        Store.addRoutine()
        Store.selectRoutine("B")
        Store.moveRoutine(1, 0)
        assertEquals(listOf("B", "A"), Store.state.routines.take(2).map { it.id })
        assertEquals("B", Store.state.nextRoutineId)
        Store.moveRoutine(0, 2)
        assertEquals("B", Store.state.routines.last().id)
        assertEquals("B", Store.nextRoutine.id)
    }

    @Test fun `a slot moves any distance in one change`() {
        val before = slotIds("A")
        Store.moveSlotTo("A", 0, before.lastIndex)
        assertEquals(before.drop(1) + before.first(), slotIds("A"))
        Store.moveSlotTo("A", 0, 99)
        assertEquals(before.drop(1) + before.first(), slotIds("A"))
    }

    @Test fun `undo puts a removed slot back at its place with its sets and reps`() {
        val before = Store.state.routines.first { it.id == "A" }.slots
        val removed = before[1].copy(sets = 3, reps = 8)
        Store.removeSlot("A", 1)
        Store.insertSlot("A", 1, removed)
        assertEquals(before.toMutableList().apply { set(1, removed) }, Store.state.routines.first { it.id == "A" }.slots)
        // A second undo of the same slot does not add it twice.
        Store.insertSlot("A", 0, removed)
        assertEquals(before.size, slotIds("A").size)
    }

    @Test fun `undo after the list got shorter puts the slot at the end`() {
        val slot = Slot("bench", 5, 5)
        Store.removeSlot("A", 1)
        Store.removeSlot("A", 0)
        Store.insertSlot("A", 10, slot)
        assertEquals("bench", slotIds("A").last())
    }
}
