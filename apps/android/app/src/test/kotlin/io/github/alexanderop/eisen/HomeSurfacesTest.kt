package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.ExerciseProgress
import io.github.alexanderop.eisen.domain.Routine
import io.github.alexanderop.eisen.domain.Slot
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.elm.Cmd
import io.github.alexanderop.eisen.domain.elm.MAX_SHORTCUTS
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.ShortcutSpec
import io.github.alexanderop.eisen.domain.elm.WidgetRow
import io.github.alexanderop.eisen.domain.elm.refreshHomeSurfaces
import io.github.alexanderop.eisen.domain.elm.shortcutsOf
import io.github.alexanderop.eisen.domain.elm.widgetContent
import io.github.alexanderop.eisen.domain.freshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The widget and the launcher shortcuts, as `update` computes them for the adapters. */
class HomeSurfacesTest {
    private val fresh = freshState(WeightUnit.KG)

    private fun routines(count: Int) = (1..count).map { Routine("r$it", "Workout $it", listOf(Slot("bench", 5, 5))) }

    // Shortcuts

    @Test fun `a running workout has one shortcut, Resume`() {
        assertEquals(listOf(ShortcutSpec("resume", "Resume", "Resume workout", routineId = null)), shortcutsOf(started().state))
    }

    @Test fun `one shortcut per workout, the one offered next first`() {
        assertEquals(
            listOf(
                ShortcutSpec("routine-B", "Workout B", "Start Workout B", "B"),
                ShortcutSpec("routine-A", "Workout A", "Start Workout A", "A"),
            ),
            shortcutsOf(fresh.copy(nextRoutineId = "B")),
        )
    }

    @Test fun `at most four shortcuts, and the next workout is among them`() {
        val shortcuts = shortcutsOf(fresh.copy(routines = routines(6), nextRoutineId = "r6"))
        assertEquals(MAX_SHORTCUTS, shortcuts.size)
        assertEquals(listOf("r6", "r1", "r2", "r3"), shortcuts.map { it.routineId })
    }

    @Test fun `a workout without a name gets one, since Android refuses a shortcut without a label`() {
        val blank = fresh.copy(routines = listOf(Routine("A", "  ", emptyList())), nextRoutineId = "A")
        assertEquals(listOf(ShortcutSpec("routine-A", "Workout", "Start Workout", "A")), shortcutsOf(blank))
        assertTrue(shortcutsOf(fresh.copy(routines = emptyList())).isEmpty())
    }

    // Widget

    @Test fun `the widget shows the next workout as it is set up, with bodyweight loads`() {
        val state = fresh.copy(progress = fresh.progress + ("dips" to ExerciseProgress(weight = 10.0, fails = 0, increment = 2.5)))
        val content = widgetContent(state)
        assertFalse(content.running)
        assertEquals("A", content.routineId)
        assertEquals("Workout A", content.name)
        assertEquals(WidgetRow("Dips", 3, null, "BW + 10kg"), content.rows.last())
        assertEquals(WidgetRow("Dips", 3, null, "BW"), widgetContent(fresh).rows.last())
        assertTrue(content.canStart)
    }

    @Test fun `the widget shows the running workout and how far it got`() {
        val content = widgetContent(started().send(WorkoutEvent.TapSet(0, 0)).state)
        assertTrue(content.running)
        assertNull(content.routineId)
        assertEquals("Workout A", content.name)
        assertEquals(1, content.logged)
        assertEquals(content.rows.sumOf { it.sets }, content.total)
        assertTrue(content.canStart)
    }

    @Test fun `a workout without exercises cannot start, and no workout at all still draws`() {
        val empty = fresh.copy(routines = listOf(Routine("A", "Empty", emptyList())), nextRoutineId = "A")
        assertFalse(widgetContent(empty).canStart)
        val none = widgetContent(fresh.copy(routines = emptyList()))
        assertEquals("Eisen", none.name)
        assertNull(none.routineId)
        assertFalse(none.canStart)
    }

    // Through update

    @Test fun `update asks for a redraw only when the widget or the shortcuts change`() {
        // The bar changes no weight the widget shows: saved, not redrawn.
        val bar = stepOf().send(Msg.Settings.SetBar(15.0))
        assertTrue(bar.cmds.any { it is Cmd.Save })
        assertTrue(bar.cmds.none { it is Cmd.RefreshHomeSurfaces })
        // A new name shows in both.
        val renamed = stepOf().send(Msg.Routines.Rename("A", "Push"))
        assertTrue(refreshHomeSurfaces(renamed.state) in renamed.cmds)
        assertEquals("Push", (renamed.cmds.last() as Cmd.RefreshHomeSurfaces).widget.name)
        // A logged set changes the widget's count.
        val tapped = started().send(WorkoutEvent.TapSet(0, 0))
        assertTrue(refreshHomeSurfaces(tapped.state) in tapped.cmds)
    }
}
