package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.draft
import io.github.alexanderop.eisen.app.elm.restScreen
import io.github.alexanderop.eisen.app.elm.repsQuestion
import io.github.alexanderop.eisen.app.elm.hold
import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_TEMPLATES
import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_MOVES
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingCues
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRoom
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStance
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStyle
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.feature.workout.ExerciseProgress
import io.github.alexanderop.eisen.app.elm.Feel
import io.github.alexanderop.eisen.feature.workout.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_RIR
import io.github.alexanderop.eisen.feature.workout.canRate
import io.github.alexanderop.eisen.feature.workout.rirOf
import io.github.alexanderop.eisen.feature.workout.Routine
import io.github.alexanderop.eisen.app.elm.Session
import io.github.alexanderop.eisen.feature.workout.Slot
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.active
import io.github.alexanderop.eisen.app.elm.AddTarget
import io.github.alexanderop.eisen.app.elm.BACKUP_RESTORED
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Cue
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.DeletedWorkout
import io.github.alexanderop.eisen.app.elm.Flags
import io.github.alexanderop.eisen.app.elm.HISTORY_SNACKBAR
import io.github.alexanderop.eisen.app.elm.HealthAvailability
import io.github.alexanderop.eisen.app.elm.HealthJob
import io.github.alexanderop.eisen.app.elm.HealthModel
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.BACKUP_TOO_NEW
import io.github.alexanderop.eisen.app.elm.NOT_A_BACKUP
import io.github.alexanderop.eisen.app.elm.Navigation
import io.github.alexanderop.eisen.app.elm.Notifications
import io.github.alexanderop.eisen.app.elm.PermissionCheck
import io.github.alexanderop.eisen.app.elm.REST_OVER_VIBRATION
import io.github.alexanderop.eisen.app.elm.RemovedSlot
import io.github.alexanderop.eisen.app.elm.RepsQuestion
import io.github.alexanderop.eisen.app.elm.RestScreen
import io.github.alexanderop.eisen.app.elm.RestState
import io.github.alexanderop.eisen.app.elm.SLOT_SNACKBAR
import io.github.alexanderop.eisen.app.elm.Sub
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.backupFileName
import io.github.alexanderop.eisen.app.elm.exerciseNamed
import io.github.alexanderop.eisen.app.elm.exercises
import io.github.alexanderop.eisen.app.elm.exportJson
import io.github.alexanderop.eisen.app.elm.handlesBack
import io.github.alexanderop.eisen.app.elm.holdFits
import io.github.alexanderop.eisen.app.elm.init
import io.github.alexanderop.eisen.app.elm.refreshHomeSurfaces
import io.github.alexanderop.eisen.app.elm.restOver
import io.github.alexanderop.eisen.app.elm.restScreenFits
import io.github.alexanderop.eisen.app.elm.SETTLE_MS
import io.github.alexanderop.eisen.app.elm.Settling
import io.github.alexanderop.eisen.app.elm.showsWorkout
import io.github.alexanderop.eisen.app.elm.subscriptions
import io.github.alexanderop.eisen.app.elm.warmupKey
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.elm.order
import io.github.alexanderop.eisen.feature.timer.MAX_OPEN_TIMER_SECONDS
import io.github.alexanderop.eisen.feature.timer.TimerCalls
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.timerConfig
import io.github.alexanderop.eisen.feature.timer.countsRoundsByTap
import io.github.alexanderop.eisen.feature.timer.timerSeconds
import io.github.alexanderop.eisen.app.elm.HistoryView
import io.github.alexanderop.eisen.app.elm.TimerState
import io.github.alexanderop.eisen.app.elm.TAP_GUARD_MILLIS
import io.github.alexanderop.eisen.app.elm.timerRun
import io.github.alexanderop.eisen.app.elm.MAX_QUERY
import io.github.alexanderop.eisen.app.elm.RESULT_GUARD_MILLIS
import io.github.alexanderop.eisen.app.elm.SET_TAP_GUARD_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import io.github.alexanderop.eisen.feature.timer.wod.SCHEMES
import io.github.alexanderop.eisen.feature.timer.wod.WOD_LIBRARY
import io.github.alexanderop.eisen.feature.timer.wod.WodItem
import io.github.alexanderop.eisen.feature.timer.wod.WodUnit
import io.github.alexanderop.eisen.feature.timer.wod.findWod
import io.github.alexanderop.eisen.feature.timer.wod.isLibraryWod
import io.github.alexanderop.eisen.feature.timer.wod.wod
import io.github.alexanderop.eisen.app.elm.TimerPage
import io.github.alexanderop.eisen.app.elm.WodFilter
import io.github.alexanderop.eisen.app.elm.draftFile
import io.github.alexanderop.eisen.app.elm.fitting
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import io.github.alexanderop.eisen.app.elm.restCountdown
import io.github.alexanderop.eisen.app.elm.WodDraft
import io.github.alexanderop.eisen.app.elm.pending
import org.junit.Test

/**
 * Every Msg group besides the workout machine (see [WorkoutMachineTest]), through `update` alone:
 * for a Msg, the new Model and the Cmds it asks for.
 */
class UpdateTest {
    private fun routine(step: Step, id: String): Routine = step.state.routines.first { it.id == id }

    private val stopRest = listOf(Cmd.SaveRest(null), Cmd.CancelRestAlarms, Cmd.HideRestCountdown, Cmd.HideRestOver)

    private fun Step.snackbars(): List<UiEvent.Snackbar> = uiEvents.filterIsInstance<UiEvent.Snackbar>()

    // Routines

    @Test fun `Add names the new workout after the first free number and opens it`() {
        val added = stepOf().send(Msg.Routines.Add(), id = "r3")
        assertEquals(Routine("r3", "Workout 3", emptyList()), added.state.routines.last())
        assertEquals("r3", added.model.nav.routine)
        assertTrue(Cmd.Save(added.state) in added.cmds)

        val taken = stepOf().send(Msg.Routines.Rename("A", "Workout 3")).send(Msg.Routines.Add(openEditor = false), id = "r4")
        assertEquals("Workout 4", taken.state.routines.last().name)
        assertNull(taken.model.nav.routine)
    }

    @Test fun `Rename cuts the name and leaving a blank name gives it one`() {
        val long = stepOf().send(Msg.Routines.Rename("A", "x".repeat(40)))
        assertEquals(30, routine(long, "A").name.length)
        val blank = stepOf().send(Msg.Routines.Rename("A", "  "))
        assertEquals("  ", routine(blank, "A").name)
        assertEquals("Workout", routine(blank.send(Msg.Routines.CommitName("A")), "A").name)
        assertIgnored(stepOf(), Msg.Routines.CommitName("A"))
    }

    @Test fun `the last routine stays`() {
        val one = stepOf().send(Msg.Routines.Remove("B"))
        assertEquals(listOf("A"), one.state.routines.map { it.id })
        assertIgnored(one, Msg.Routines.Remove("A"))
        assertIgnored(stepOf(), Msg.Routines.Remove("nope"))
    }

    @Test fun `removing the routine offered next offers the first one and closes its editor`() {
        val open = stepOf().send(Msg.Routines.Select("B")).send(Msg.Nav.OpenRoutine("B"))
        val removed = open.send(Msg.Routines.Remove("B"))
        assertEquals("A", removed.state.nextRoutineId)
        assertNull(removed.model.nav.routine)
    }

    @Test fun `the next routine cannot change while a workout runs`() {
        assertEquals("B", stepOf().send(Msg.Routines.Select("B")).state.nextRoutineId)
        assertIgnored(started(), Msg.Routines.Select("B"))
    }

    @Test fun `a routine that is gone is not chosen as the next one`() {
        assertIgnored(stepOf(), Msg.Routines.Select("gone"))
    }

    @Test fun `AddSlot adds an exercise once with its default sets and a working weight`() {
        val added = stepOf().send(Msg.Routines.AddSlot("A", "dumbbellRow"))
        assertEquals(Slot("dumbbellRow", 3, 10), routine(added, "A").slots.last())
        assertTrue("dumbbellRow" in added.state.progress)
        assertIgnored(added, Msg.Routines.AddSlot("A", "dumbbellRow"))
        assertIgnored(stepOf(), Msg.Routines.AddSlot("A", "nope"))
        // Not even a working weight for an exercise added to a routine that is gone.
        assertIgnored(stepOf(), Msg.Routines.AddSlot("gone", "dumbbellRow"))
    }

    @Test fun `UpdateSlot changes sets and reps and ignores an index that is gone`() {
        val updated = stepOf().send(Msg.Routines.UpdateSlot("A", 0, Slot("hackSquat", 3, 8)))
        assertEquals(Slot("hackSquat", 3, 8), routine(updated, "A").slots[0])
        assertIgnored(stepOf(), Msg.Routines.UpdateSlot("A", 9, Slot("hackSquat", 3, 8)))
    }

    @Test fun `UpdateSlot from a card whose index went stale does not overwrite its neighbour`() {
        // Bench was at 1; after it moved down, index 1 holds Row. The old card's update is dropped.
        val moved = stepOf().send(Msg.Routines.MoveSlot("A", 1, 2))
        assertIgnored(moved, Msg.Routines.UpdateSlot("A", 1, Slot("bench", 3, 8)))
    }

    @Test fun `InsertSlot brings nothing back into a routine or for an exercise that is gone`() {
        assertIgnored(stepOf(), Msg.Routines.InsertSlot("gone", 0, Slot("dumbbellRow", 3, 10)))
        assertIgnored(stepOf(), Msg.Routines.InsertSlot("A", 0, Slot("custom-gone", 3, 10)))
    }

    @Test fun `removing a slot offers Undo and Undo puts it back once`() {
        val before = routine(stepOf(), "A").slots
        val removed = stepOf().send(Msg.Routines.RemoveSlot("A", "bench"))
        assertEquals(listOf("hackSquat", "row", "dips"), routine(removed, "A").slots.map { it.exerciseId })
        assertEquals(RemovedSlot("A", 1, before[1]), removed.model.undo.removedSlot)
        assertEquals(
            UiEvent.Snackbar("Removed Bench Press", actionLabel = "Undo", onAction = Msg.Routines.UndoRemoveSlot, tag = SLOT_SNACKBAR),
            removed.snackbars().single(),
        )
        val undone = removed.send(Msg.Routines.UndoRemoveSlot)
        assertEquals(before, routine(undone, "A").slots)
        assertNull(undone.model.undo.removedSlot)
        assertIgnored(undone, Msg.Routines.UndoRemoveSlot)
    }

    @Test fun `removing a slot without Undo or one that is gone`() {
        val quiet = stepOf().send(Msg.Routines.RemoveSlot("A", "bench", offerUndo = false))
        assertTrue(quiet.snackbars().isEmpty())
        assertNull(quiet.model.undo.removedSlot)
        assertIgnored(quiet, Msg.Routines.RemoveSlot("A", "bench"))
    }

    @Test fun `removing a slot is felt, unless haptics are off`() {
        assertEquals(listOf(Feel.TAP), stepOf().send(Msg.Routines.RemoveSlot("A", "bench")).feels)
        assertEquals(listOf(Feel.TAP), stepOf().send(Msg.Routines.RemoveSlot("A", "bench", offerUndo = false)).feels)
        val fresh = stepOf().state
        val off = stepOf(fresh.copy(settings = fresh.settings.copy(haptics = false)))
        assertTrue(off.send(Msg.Routines.RemoveSlot("A", "bench")).feels.isEmpty())
    }

    // Custom exercises

    @Test fun `AddCustom trims the name, finds a taken one and ignores an empty one`() {
        val created = stepOf().send(Msg.Exercises.AddCustom("  Cable Fly  ", Equipment.WEIGHT), id = "1")
        val fly = created.state.customExercises.single()
        assertEquals("custom-1", fly.id)
        assertEquals("Cable Fly", fly.name)
        assertEquals(fly, exerciseNamed(created.model, "cable fly"))

        assertIgnored(created, Msg.Exercises.AddCustom("CABLE FLY", Equipment.BARBELL))
        assertIgnored(stepOf(), Msg.Exercises.AddCustom("Bench Press", Equipment.BARBELL))
        assertIgnored(stepOf(), Msg.Exercises.AddCustom("   ", Equipment.BARBELL))
        assertEquals(40, stepOf().send(Msg.Exercises.AddCustom("y".repeat(50), Equipment.WEIGHT)).state.customExercises.single().name.length)
    }

    @Test fun `AddCustom adds the exercise where the picker was opened`() {
        val toRoutine = stepOf().send(Msg.Exercises.AddCustom("Cable Fly", Equipment.WEIGHT, AddTarget.Routine("B")), id = "1")
        assertEquals("custom-1", routine(toRoutine, "B").slots.last().exerciseId)

        val toWorkout = started().send(Msg.Exercises.AddCustom("Cable Fly", Equipment.WEIGHT, AddTarget.Workout), id = "1")
        assertEquals("custom-1", toWorkout.workout.exercises.last().id)
        assertEquals(toWorkout.workout, toWorkout.state.active)
    }

    @Test fun `DeleteCustom removes it from every routine and its weight, finished workouts keep it`() {
        val created = stepOf().send(Msg.Exercises.AddCustom("Cable Fly", Equipment.WEIGHT, AddTarget.Routine("A")), id = "1")
        assertTrue("custom-1" in created.state.progress)
        val deleted = created.send(Msg.Exercises.DeleteCustom("custom-1"))
        assertTrue(deleted.state.customExercises.isEmpty())
        assertTrue(routine(deleted, "A").slots.none { it.exerciseId == "custom-1" })
        assertFalse("custom-1" in deleted.state.progress)
    }

    @Test fun `DeleteCustom of a built-in exercise changes nothing`() {
        assertIgnored(stepOf(), Msg.Exercises.DeleteCustom("bench"))
    }

    // History

    private fun withHistory(): Step = started().send(WorkoutEvent.TapSet(0, 0)).send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)

    @Test fun `Edit corrects a finished workout and keeps the working weights`() {
        val done = withHistory()
        val workout = done.state.history[0]
        val fixed = workout.exercises.map { it.copy(sets = it.sets.map { 5 }) }
        val edited = done.send(Msg.History.Edit(workout.id, fixed))
        assertEquals(fixed, edited.state.history[0].exercises)
        assertEquals(done.state.progress, edited.state.progress)
    }

    @Test fun `a swiped delete offers Undo, which puts the workout back where it was`() {
        val done = withHistory()
        val workout = done.state.history[0]
        val deleted = done.send(Msg.History.Delete(workout.id, offerUndo = true))
        assertTrue(deleted.state.history.isEmpty())
        assertEquals(DeletedWorkout(workout, 0), deleted.model.undo.deletedWorkout)
        assertEquals(
            UiEvent.Snackbar("Workout deleted", actionLabel = "Undo", onAction = Msg.History.UndoDelete, tag = HISTORY_SNACKBAR),
            deleted.snackbars().single(),
        )
        val undone = deleted.send(Msg.History.UndoDelete)
        assertEquals(done.state.history, undone.state.history)
        assertIgnored(undone, Msg.History.UndoDelete)
        assertIgnored(undone, Msg.History.Restore(workout, 0))
    }

    @Test fun `a delete from the dialog offers no Undo`() {
        val done = withHistory()
        val deleted = done.send(Msg.History.Delete(done.state.history[0].id))
        assertTrue(deleted.state.history.isEmpty())
        assertTrue(deleted.snackbars().isEmpty())
        assertNull(deleted.model.undo.deletedWorkout)
        assertIgnored(deleted, Msg.History.Delete("nope"))
    }

    @Test fun `Health Connect follows the history only while sync is on`() {
        val off = withHistory()
        assertTrue(off.cmds.none { it is Cmd.Health })

        val on = started().let { it.copy(model = it.model.copy(health = HealthModel(enabled = true))) }
            .send(WorkoutEvent.TapSet(0, 0)).send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)
        val finished = on.state.history[0]
        assertTrue(Cmd.Health(HealthJob.Upsert(listOf(finished))) in on.cmds)

        val edited = on.send(Msg.History.Edit(finished.id, emptyList()))
        assertTrue(Cmd.Health(HealthJob.Upsert(listOf(edited.state.history[0]))) in edited.cmds)
        val deleted = edited.send(Msg.History.Delete(finished.id))
        assertTrue(Cmd.Health(HealthJob.Delete(listOf(finished.id))) in deleted.cmds)
        // A tapped set does not touch the history, so Health Connect is left alone.
        assertTrue(started().let { it.copy(model = it.model.copy(health = HealthModel(enabled = true))) }
            .send(WorkoutEvent.TapSet(0, 0)).cmds.none { it is Cmd.Health })
    }

    // Settings

    @Test fun `switching the unit converts the working weights and the bar`() {
        val dips = freshState(WeightUnit.KG).let { it.copy(progress = it.progress + ("dips" to ExerciseProgress(10.0, 0, 0.0))) }
        val lb = stepOf(dips).send(Msg.Settings.SetUnit(WeightUnit.LB))
        assertEquals(WeightUnit.LB, lb.state.settings.unit)
        assertEquals(45.0, lb.state.settings.bar, 0.0)
        assertEquals(175.0, lb.state.progress.getValue("hackSquat").weight, 0.0)
        assertEquals(22.5, lb.state.progress.getValue("dips").weight, 0.0)
        assertIgnored(lb, Msg.Settings.SetUnit(WeightUnit.LB))
    }

    @Test fun `an added weight of 0 stays 0 when the unit changes`() {
        val none = freshState(WeightUnit.KG).let { it.copy(progress = it.progress + ("dips" to ExerciseProgress(0.0, 0, 0.0))) }
        assertEquals(0.0, stepOf(none).send(Msg.Settings.SetUnit(WeightUnit.LB)).state.progress.getValue("dips").weight, 0.0)
    }

    @Test fun `the unit is locked during a workout and while a discard can be undone`() {
        assertIgnored(started(), Msg.Settings.SetUnit(WeightUnit.LB))
        val discarded = started().send(WorkoutEvent.RequestDiscard)
        assertIgnored(discarded, Msg.Settings.SetUnit(WeightUnit.LB))
        val forgotten = discarded.send(WorkoutEvent.Forget)
        assertEquals(WeightUnit.LB, forgotten.send(Msg.Settings.SetUnit(WeightUnit.LB)).state.settings.unit)
    }

    @Test fun `switching the sound on plays the cue once, off plays nothing`() {
        val off = stepOf().send(Msg.Settings.SetSound(false))
        assertFalse(off.state.settings.sound)
        assertTrue(off.cmds.none { it is Cmd.PlayCue })
        val on = off.send(Msg.Settings.SetSound(true))
        assertTrue(on.state.settings.sound)
        assertTrue(Cmd.PlayCue(Cue.REST_OVER) in on.cmds)
    }

    @Test fun `bar, rest times, haptics and increments`() {
        val edited = stepOf()
            .send(Msg.Settings.SetBar(15.0))
            .send(Msg.Settings.SetRestDone(120))
            .send(Msg.Settings.SetRestMissed(240))
            .send(Msg.Settings.SetHaptics(false))
            .send(Msg.Settings.SetIncrement("bench", 1.25))
        val settings = edited.state.settings
        assertEquals(listOf(15.0, 120.0, 240.0), listOf(settings.bar, settings.restDone.toDouble(), settings.restMissed.toDouble()))
        assertFalse(settings.haptics)
        assertEquals(1.25, edited.state.progress.getValue("bench").increment, 0.0)
        // Haptics off: a tapped set asks for no vibration.
        assertTrue(Step(edited.model).send(WorkoutEvent.Start).send(WorkoutEvent.TapSet(0, 0)).feels.isEmpty())
    }

    // Backups

    @Test fun `a valid backup replaces everything and stops the rest`() {
        val backup = freshState(WeightUnit.LB).copy(nextRoutineId = "B")
        val resting = started().send(WorkoutEvent.TapSet(0, 0))
        assertNotNull(resting.model.rest)
        val imported = resting.send(Msg.Data.Import(encodeState(backup)))
        assertEquals(backup, imported.state)
        assertEquals(Session.Idle, imported.session)
        assertNull(imported.model.rest)
        assertEquals(BACKUP_RESTORED, imported.model.dataNotice)
        assertTrue(imported.cmds.containsAll(stopRest + Cmd.Save(backup)))
    }

    @Test fun `a backup with a workout running brings it back running`() {
        val running = started().send(WorkoutEvent.TapSet(0, 0)).state
        val imported = stepOf().send(Msg.Data.Import(encodeState(running)))
        assertEquals(running.active, (imported.session as Session.Running).workout)
    }

    @Test fun `text that is no backup changes no data and says so`() {
        val before = stepOf()
        val refused = before.send(Msg.Data.Import("{\"not\":\"eisen\"}"))
        assertSame(before.state, refused.state)
        assertEquals(NOT_A_BACKUP, refused.model.dataNotice)
        assertTrue(refused.cmds.isEmpty())
        assertEquals(NOT_A_BACKUP, before.send(Msg.Data.BackupRead(null)).model.dataNotice)
    }

    @Test fun `a backup that is too new or damaged changes no data and says which`() {
        val before = stepOf()
        val newer = before.send(Msg.Data.Import("{\"version\":99}"))
        assertSame(before.state, newer.state)
        assertEquals(BACKUP_TOO_NEW, newer.model.dataNotice)
        val damaged = before.send(Msg.Data.Import(encodeState(before.state).replaceFirst("\"restDone\":", "\"restGone\":")))
        assertSame(before.state, damaged.state)
        assertTrue(damaged.model.dataNotice, damaged.model.dataNotice!!.startsWith("That backup is damaged: ") && "restDone" in damaged.model.dataNotice!!)
        assertTrue(damaged.cmds.isEmpty())
    }

    @Test fun `backup files are read, written and shared through Cmds`() {
        val step = stepOf()
        assertEquals(listOf(Cmd.ReadBackup("content://a")), step.send(Msg.Data.Restore("content://a")).cmds)
        assertEquals(BACKUP_RESTORED, step.send(Msg.Data.BackupRead(encodeState(freshState()))).model.dataNotice)
        assertEquals(listOf(Cmd.WriteBackup("content://b", exportJson(step.model))), step.send(Msg.Data.WriteBackup("content://b")).cmds)
        assertEquals("Backup saved.", step.send(Msg.Data.BackupWritten(true)).model.dataNotice)
        assertEquals("Could not save the backup.", step.send(Msg.Data.BackupWritten(false)).model.dataNotice)
        val shared = step.send(Msg.Data.SendBackup, now = 0)
        assertEquals(listOf(UiEvent.ShareBackup("eisen-backup-1970-01-01.json", exportJson(step.model))), shared.uiEvents)
        assertEquals("eisen-backup-1970-01-01.json", backupFileName(0))
        assertEquals("Could not create the backup.", step.send(Msg.Data.SendFailed).model.dataNotice)
    }

    @Test fun `Reset erases everything but the unit`() {
        val lb = stepOf(freshState(WeightUnit.LB)).send(Msg.Routines.Add(), id = "r3")
        val resting = lb.send(WorkoutEvent.Start).send(WorkoutEvent.TapSet(0, 0))
        val reset = resting.send(Msg.Data.Reset)
        assertEquals(freshState(WeightUnit.LB), reset.state)
        assertEquals(Session.Idle, reset.session)
        assertNull(reset.model.rest)
        assertFalse(reset.model.nav.workoutOpen)
        assertNull(reset.model.nav.routine)
        assertEquals("All data erased.", reset.model.dataNotice)
        assertTrue(reset.cmds.containsAll(stopRest))
    }

    // Navigation

    @Test fun `back closes the workout, then the routine editor, then goes home`() {
        val running = started()
        assertTrue(showsWorkout(running.model))
        assertTrue(handlesBack(running.model))
        val closed = running.send(Msg.Nav.Back)
        assertFalse(showsWorkout(closed.model))
        assertNotNull(closed.state.active)

        val editing = stepOf().send(Msg.Nav.SelectTab(Tab.WORKOUTS)).send(Msg.Nav.OpenRoutine("B"))
        val list = editing.send(Msg.Nav.Back)
        assertEquals(Navigation(tab = Tab.WORKOUTS), list.model.nav)
        val home = list.send(Msg.Nav.Back)
        assertEquals(Tab.HOME, home.model.nav.tab)
        assertFalse(handlesBack(home.model))
        assertIgnored(home, Msg.Nav.Back)
    }

    @Test fun `the workout only shows while there is one`() {
        val open = stepOf().send(Msg.Nav.OpenWorkout)
        assertTrue(open.model.nav.workoutOpen)
        assertFalse(showsWorkout(open.model))
        assertIgnored(stepOf(), Msg.Nav.OpenRoutine("nope"))
    }

    @Test fun `saved navigation comes back, without a routine that is gone`() {
        val restored = stepOf().send(Msg.Nav.Restore(Tab.WORKOUTS, workoutOpen = false, routine = "gone"))
        assertEquals(Navigation(tab = Tab.WORKOUTS), restored.model.nav)
        assertEquals("B", stepOf().send(Msg.Nav.Restore(Tab.WORKOUTS, false, "B")).model.nav.routine)
    }

    @Test fun `a change of screen reads the clock, so Home and Progress date themselves with today`() {
        val progress = stepOf().send(Msg.Nav.SelectTab(Tab.HISTORY), now = 5_000)
        assertEquals(Tab.HISTORY, progress.model.nav.tab)
        assertEquals(5_000, progress.model.now)
        assertTrue(progress.cmds.isEmpty())
        // Selecting the tab that shows reads it too.
        assertEquals(6_000, progress.send(Msg.Nav.SelectTab(Tab.HISTORY), now = 6_000).model.now)
        val home = progress.send(Msg.Nav.Back, now = 9_000)
        assertEquals(Tab.HOME, home.model.nav.tab)
        assertEquals(9_000, home.model.now)
        // A move that changes nothing still hands back the same model.
        assertIgnored(home, Msg.Nav.Back)
        assertIgnored(home, Msg.Nav.CloseWorkout)
    }

    @Test fun `the discard question reads the clock when it is asked, for its minutes`() {
        val logged = started().send(WorkoutEvent.TapSet(0, 0))
        val asking = logged.send(WorkoutEvent.RequestDiscard, now = LATER + 17 * 60_000)
        assertTrue(asking.session is Session.ConfirmingDiscard)
        assertEquals(LATER + 17 * 60_000, asking.model.now)
        // The home screen after a finish dates itself with the end of the workout.
        val home = logged.send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm, now = LATER + 40 * 60_000)
        assertEquals(Session.Idle, home.session)
        assertEquals(LATER + 40 * 60_000, home.model.now)
    }

    @Test fun `a state file that cannot be read starts fresh, with the clock of the start`() {
        val (model, cmds) = init(Flags(null, savedRest = null, healthEnabled = false, notifications = Notifications(true, true), now = 7_000))
        assertEquals(freshState(), model.state)
        assertEquals(Session.Idle, model.session)
        assertEquals(7_000, model.now)
        assertEquals(listOf(refreshHomeSurfaces(freshState())), cmds)
    }

    @Test fun `the summary is dismissed, warmups ticked and the reps entry closed`() {
        val saved = started().send(WorkoutEvent.TapSet(0, 0)).send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)
        assertNotNull(saved.model.lastChanges)
        assertNull(saved.send(Msg.Home.DismissSummary).model.lastChanges)

        val key = warmupKey("hackSquat", 0)
        val ticked = started().send(Msg.WorkoutScreen.ToggleWarmup(key))
        assertEquals(setOf("hackSquat:0"), ticked.model.warmupDone)
        assertTrue(ticked.send(Msg.WorkoutScreen.ToggleWarmup(key)).model.warmupDone.isEmpty())
        // A new workout starts with nothing ticked.
        assertTrue(ticked.send(WorkoutEvent.RequestDiscard).send(WorkoutEvent.Start).model.warmupDone.isEmpty())

        val asked = started().send(WorkoutEvent.TapSet(3, 0))
        assertEquals(RepsQuestion(3, 0, "dips", 8), asked.model.repsQuestion)
        assertNull(asked.send(Msg.WorkoutScreen.CloseRepsEntry).model.repsQuestion)
    }

    // The exercise tapped last (Workout A: hackSquat, bench, row, dips)

    private fun Step.order(): List<String> = order(run, model.settling?.id).map { it.log.id }

    @Test fun `the exercise tapped last holds its place, then rises once its wait is over`() {
        val tapped = started().send(WorkoutEvent.TapSet(2, 0))
        assertEquals(Settling("row", LATER), tapped.model.settling)
        assertTrue(Cmd.After(SETTLE_MS, Msg.WorkoutScreen.Settle("row", LATER)) in tapped.cmds)
        assertEquals(listOf("hackSquat", "bench", "row", "dips"), tapped.order())
        // Another tap starts the wait again: the first timer changes nothing.
        val again = tapped.send(WorkoutEvent.TapSet(2, 1), now = LATER + 1_000)
        assertEquals(Settling("row", LATER + 1_000), again.model.settling)
        assertIgnored(again, Msg.WorkoutScreen.Settle("row", LATER))
        val risen = again.send(Msg.WorkoutScreen.Settle("row", LATER + 1_000))
        assertNull(risen.model.settling)
        assertEquals(listOf("row", "hackSquat", "bench", "dips"), risen.order())
    }

    @Test fun `a finished exercise stays open while it settles, then sinks`() {
        val done = (0 until 5).fold(started()) { at, set -> at.send(WorkoutEvent.TapSet(0, set)) }
        assertEquals("hackSquat", done.order().first())
        val settled = done.send(Msg.WorkoutScreen.Settle("hackSquat", LATER))
        assertEquals(listOf("bench", "row", "dips", "hackSquat"), settled.order())
    }

    @Test fun `a tap on another exercise lets the one before take its place at once`() {
        val second = started().send(WorkoutEvent.TapSet(2, 0)).send(WorkoutEvent.TapSet(1, 0))
        assertEquals("bench", second.model.settling?.id)
        assertEquals(listOf("row"), second.run.ranking)
    }

    @Test fun `the first number picked on a set ends the hint for good, and logs like LogReps`() {
        val running = started()
        assertFalse(running.state.pickedReps)
        val picked = running.send(Msg.WorkoutScreen.PickReps(0, 0, 4))
        assertEquals(running.send(WorkoutEvent.LogReps(0, 0, 4)).workout, picked.workout)
        assertTrue(picked.state.pickedReps)
        assertEquals(1, picked.cmds.filterIsInstance<Cmd.Save>().size)
        assertEquals(Settling("hackSquat", LATER), picked.model.settling)
        // It stays after the workout, and a picker with nothing running changes nothing.
        val finished = picked.send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)
        assertTrue(finished.state.pickedReps)
        assertIgnored(finished, Msg.WorkoutScreen.PickReps(0, 0, 4))
        assertIgnored(stepOf(), Msg.WorkoutScreen.PickReps(0, 0, 4))
    }

    @Test fun `a max-reps set settles when its count is logged, not when it is tapped`() {
        val asked = started().send(WorkoutEvent.TapSet(3, 0))
        assertNull(asked.model.settling)
        assertTrue(asked.cmds.none { it is Cmd.After })
        assertEquals(Settling("dips", LATER), asked.send(WorkoutEvent.LogReps(3, 0, 8)).model.settling)
    }

    @Test fun `a drag or a TalkBack move settles first, and a move goes by the settled order`() {
        val tapped = started().send(WorkoutEvent.TapSet(2, 0))
        val now = tapped.send(Msg.WorkoutScreen.SettleNow)
        assertNull(now.model.settling)
        assertEquals(listOf("row"), now.run.ranking)
        assertIgnored(now, Msg.WorkoutScreen.SettleNow)

        // Settled, row is on top; one place down puts it second.
        val moved = tapped.send(Msg.WorkoutScreen.MoveBy("row", 1))
        assertNull(moved.model.settling)
        assertTrue(moved.run.arranged)
        assertEquals(listOf("hackSquat", "row", "bench", "dips"), moved.order())
        assertIgnored(stepOf(), Msg.WorkoutScreen.MoveBy("row", 1))
    }

    @Test fun `a discarded workout forgets the exercise tapped last`() {
        val discarded = started().send(WorkoutEvent.TapSet(2, 0)).send(WorkoutEvent.RequestDiscard).send(WorkoutEvent.Confirm)
        assertNull(discarded.model.settling)
        assertIgnored(discarded, Msg.WorkoutScreen.Settle("row", LATER))
    }

    // The rest timer

    private fun resting(seconds: Int = 90): Step =
        started().send(Msg.Settings.SetRestDone(seconds)).send(WorkoutEvent.TapSet(0, 0))

    @Test fun `a rest sets both alarms, a short one only the end`() {
        val long = resting(20).model.rest!!
        assertTrue(Cmd.ScheduleRestAlarms(tenAt = long.endsAt - 10_000, overAt = long.endsAt) in resting(20).cmds)
        val short = resting(15)
        val rest = short.model.rest!!
        assertEquals(15_000, rest.endsAt - rest.startedAt)
        assertTrue(Cmd.ScheduleRestAlarms(tenAt = null, overAt = rest.endsAt) in short.cmds)
    }

    @Test fun `Extend adds 30 seconds, counted from now once the rest is over`() {
        val rest = resting().model.rest!!
        val extended = resting().send(Msg.Rest.Extend)
        assertEquals(rest.copy(endsAt = rest.endsAt + 30_000), extended.model.rest)
        assertTrue(Cmd.ScheduleRestAlarms(tenAt = rest.endsAt + 20_000, overAt = rest.endsAt + 30_000) in extended.cmds)

        val late = rest.endsAt + 5_000
        assertEquals(late + 30_000, resting().send(Msg.Rest.Extend, now = late).model.rest!!.endsAt)
        assertIgnored(started(), Msg.Rest.Extend)
    }

    @Test fun `the end of a rest plays the cue, shakes, notifies and lets the service go after the cue`() {
        val step = resting()
        val rest = step.model.rest!!
        val over = step.send(Msg.Rest.AlarmOver, now = rest.endsAt)
        assertEquals(
            listOf(
                Cmd.PlayCue(Cue.REST_OVER),
                Cmd.Vibrate(REST_OVER_VIBRATION),
                Cmd.ShowRestOver(restOver(rest)),
                Cmd.After(4_000, Msg.Rest.CueFinished(rest)),
            ),
            over.cmds,
        )
        // The rest stays until it is dismissed.
        assertEquals(rest, over.model.rest)
        assertEquals(listOf(Cmd.HideRestCountdown), over.send(Msg.Rest.CueFinished(rest)).cmds)
    }

    @Test fun `only the end of a rest beeps when its recording cannot play`() {
        assertTrue(Cue.REST_OVER.beepWhenSilent)
        assertFalse(Cue.TEN_SECONDS.beepWhenSilent)
    }

    @Test fun `with the sound off and no notifications the end only shakes`() {
        val quiet = resting().send(Msg.Settings.SetSound(false))
        val step = quiet.copy(model = quiet.model.copy(notifications = Notifications(granted = false, asked = true)))
        val rest = step.model.rest!!
        val over = step.send(Msg.Rest.AlarmOver, now = rest.endsAt)
        assertEquals(listOf(Cmd.Vibrate(REST_OVER_VIBRATION), Cmd.After(4_000, Msg.Rest.CueFinished(rest))), over.cmds)
    }

    @Test fun `an alarm left over from before an extend is ignored`() {
        val rest = resting().model.rest!!
        val extended = resting().send(Msg.Rest.Extend)
        assertIgnored(extended, Msg.Rest.AlarmOver)
        val stale = extended.send(Msg.Rest.AlarmOver, now = rest.endsAt)
        assertSame(extended.model, stale.model)
        assertTrue(stale.cmds.isEmpty())
        // The cue of a rest that was extended meanwhile does not stop the new countdown.
        assertTrue(extended.send(Msg.Rest.CueFinished(rest)).cmds.isEmpty())
        assertIgnored(started(), Msg.Rest.AlarmOver)
    }

    @Test fun `ten seconds before the end the warning plays, not at any other time`() {
        val step = resting()
        val rest = step.model.rest!!
        assertEquals(listOf(Cmd.PlayCue(Cue.TEN_SECONDS)), step.send(Msg.Rest.AlarmTen, now = rest.endsAt - 10_000).cmds)
        assertTrue(step.send(Msg.Rest.AlarmTen, now = rest.endsAt - 30_000).cmds.isEmpty())
        assertTrue(step.send(Msg.Rest.AlarmTen, now = rest.endsAt).cmds.isEmpty())
        assertTrue(step.send(Msg.Settings.SetSound(false)).send(Msg.Rest.AlarmTen, now = rest.endsAt - 10_000).cmds.isEmpty())
        assertIgnored(started(), Msg.Rest.AlarmTen)
    }

    @Test fun `dismissing a rest takes everything of it away`() {
        val dismissed = resting().send(Msg.Rest.Dismiss)
        assertNull(dismissed.model.rest)
        assertEquals(stopRest, dismissed.cmds)
        assertIgnored(dismissed, Msg.Rest.Dismiss)
    }

    @Test fun `a rest comes back after the process ended, but not without its workout`() {
        val rest = RestState(1_000, 91_000, "All reps done.")
        val running = started().state
        val (back, _) = init(Flags(running, rest, healthEnabled = false, notifications = Notifications(true, true)))
        assertEquals(rest, back.rest)
        assertTrue(back.nav.workoutOpen)

        val (gone, stale) = init(Flags(freshState(), rest, healthEnabled = true, notifications = Notifications(true, true)))
        assertNull(gone.rest)
        assertEquals(stopRest + Cmd.Health(HealthJob.CheckPermission(PermissionCheck.STARTUP)) + refreshHomeSurfaces(freshState()), stale)
    }

    // Restart: `init` is pure, so for every state that survives the end of the process one test says what it
    // puts back around it. A force-stop takes the alarms and the countdown, a kill the countdown and the
    // service; the commands are the same ones that set them, so they are harmless when those survived.

    /** What a restart must never do again: a cue, a vibration, or a rest that starts over. */
    private fun assertHarmless(cmds: List<Cmd>) =
        assertTrue("$cmds", cmds.none { it is Cmd.PlayCue || it is Cmd.Vibrate || it is Cmd.Beep || it is Cmd.SaveRest || it is Cmd.PlayTimerSound })

    @Test fun `restart during a rest puts back its alarms, its countdown and its screen`() {
        // QA build 19: after a force-stop during a rest nothing signalled its end.
        val resting = started().send(WorkoutEvent.TapSet(0, 0))
        val rest = resting.model.rest!!
        val now = rest.startedAt + 30_000
        val (back, cmds) = init(Flags(resting.state, rest, healthEnabled = false, notifications = Notifications(true, true), now = now))
        assertEquals(rest, back.rest)
        assertEquals(
            listOf(Cmd.ScheduleRestAlarms(tenAt = rest.endsAt - 10_000, overAt = rest.endsAt), Cmd.ShowRestCountdown(restCountdown(rest, now)), refreshHomeSurfaces(resting.state)),
            cmds,
        )
        assertHarmless(cmds)
        // The rest bar opens its screen: the rest names its set.
        assertEquals(RestScreen.Minimised(0, 0, "hackSquat"), back.restScreen)
        assertEquals(RestScreen.Shown(0, 0, "hackSquat"), Step(back).send(Msg.Rest.Open).model.restScreen)
        // Its clock ticks once the app shows.
        assertEquals(setOf(Sub.RestClock(rest.endsAt)), subscriptions(Step(back).send(Msg.App.Foreground(true)).model))
        // Inside its last ten seconds there is no warning left to set.
        val (late, lateCmds) = init(Flags(resting.state, rest, healthEnabled = false, notifications = Notifications(true, true), now = rest.endsAt - 5_000))
        assertEquals(rest, late.rest)
        assertTrue(Cmd.ScheduleRestAlarms(tenAt = null, overAt = rest.endsAt) in lateCmds)
    }

    @Test fun `restart after a rest ran out shows that it is over, once, without a cue`() {
        val resting = started().send(WorkoutEvent.TapSet(0, 0))
        val rest = resting.model.rest!!
        val (over, cmds) = init(Flags(resting.state, rest, healthEnabled = false, notifications = Notifications(true, true), now = rest.endsAt + 60_000))
        assertEquals(rest, over.rest)
        assertEquals(listOf(Cmd.HideRestCountdown, Cmd.ShowRestOver(restOver(rest)), refreshHomeSurfaces(resting.state)), cmds)
        assertHarmless(cmds)
        // Without notifications only the countdown goes.
        val (_, quiet) = init(Flags(resting.state, rest, healthEnabled = false, notifications = Notifications(false, true), now = rest.endsAt + 60_000))
        assertEquals(listOf(Cmd.HideRestCountdown, refreshHomeSurfaces(resting.state)), quiet)
    }

    @Test fun `restart with a rest kept by build 19 goes on without knowing its set`() {
        val resting = started().send(WorkoutEvent.TapSet(0, 0))
        val old = resting.model.rest!!.copy(exerciseId = null, set = null)
        val (back, cmds) = init(Flags(resting.state, old, healthEnabled = false, notifications = Notifications(true, true), now = old.startedAt + 1_000))
        assertEquals(old, back.rest)
        assertTrue(cmds.any { it is Cmd.ScheduleRestAlarms } && cmds.any { it is Cmd.ShowRestCountdown })
        assertNull(back.overlay)
    }

    @Test fun `restart during an interval timer puts back its sounds and its notification, paused only the notification`() {
        val config = io.github.alexanderop.eisen.feature.timer.TimerConfig(TimerMode.TABATA, 20, 10, 3)
        val run = io.github.alexanderop.eisen.feature.timer.TimerRun("t1", "Tabata", config, io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMER_CALLS, startedAt = STARTED_AT)
        val flags = Flags(freshState(), savedRest = null, healthEnabled = false, notifications = Notifications(true, true), savedTimer = run, now = STARTED_AT + 30_000)
        val (going, cmds) = init(flags)
        assertEquals(TimerState.Running(run), going.timer)
        assertTrue(cmds.any { it is Cmd.ScheduleTimer } && cmds.any { it is Cmd.ShowTimer })
        assertHarmless(cmds)
        assertTrue(subscriptions(going).any { it is Sub.TimerClock })
        val (paused, quiet) = init(flags.copy(savedTimer = run.copy(pausedAt = STARTED_AT + 20_000)))
        assertTrue(quiet.any { it is Cmd.ShowTimer })
        assertTrue(quiet.none { it is Cmd.ScheduleTimer })
        assertTrue(subscriptions(paused).none { it is Sub.TimerClock })
    }

    @Test fun `restart during a workout opens it, and a kept WOD draft is offered, without writing anything`() {
        val running = started().send(WorkoutEvent.TapSet(0, 0)).state
        val (back, cmds) = init(Flags(running, savedRest = null, healthEnabled = false, notifications = Notifications(true, true)))
        assertTrue(back.nav.workoutOpen)
        assertEquals(running.active, back.session.active)
        assertEquals(listOf(refreshHomeSurfaces(running)), cmds)
        assertNull(back.overlay)

        val draft = WodDraft(WOD_LIBRARY.first(), WOD_LIBRARY.first())
        val (drafted, draftCmds) = init(Flags(freshState(), savedRest = null, healthEnabled = false, notifications = Notifications(true, true), savedDrafts = listOf(draft)))
        assertEquals(draft, drafted.wods.pending)
        assertTrue(draftCmds.none { it is Cmd.SaveDraft || it is Cmd.Save })
    }

    @Test fun `the first rest asks once to allow notifications and an answer shows the countdown`() {
        val unasked = started().let { it.copy(model = it.model.copy(notifications = Notifications(granted = false, asked = false))) }
        val asked = unasked.send(WorkoutEvent.TapSet(0, 0))
        assertEquals(listOf(Cmd.MarkNotificationsAsked, Cmd.Ui(UiEvent.RequestNotificationPermission)), asked.cmds.drop(1).take(2))
        assertTrue(asked.model.notifications.asked)
        assertTrue(asked.send(WorkoutEvent.TapSet(0, 1)).uiEvents.none { it == UiEvent.RequestNotificationPermission })

        val rest = asked.model.rest!!
        val allowed = asked.send(Msg.Rest.NotificationPermission(true), now = rest.startedAt + 1_000)
        assertTrue(allowed.model.notifications.granted)
        assertEquals(rest, allowed.model.rest)
        assertTrue(allowed.cmds.any { it is Cmd.ShowRestCountdown })
        // Almost over: running it again would change its cues.
        assertTrue(asked.send(Msg.Rest.NotificationPermission(true), now = rest.endsAt - 5_000).cmds.isEmpty())
        assertTrue(asked.send(Msg.Rest.NotificationPermission(false)).cmds.isEmpty())
    }

    @Test fun `the countdown clock runs only while a rest runs and the app shows`() {
        val visible = resting().send(Msg.App.Foreground(notificationsGranted = true))
        val rest = visible.model.rest!!
        assertEquals(setOf(Sub.RestClock(rest.endsAt)), subscriptions(visible.model))
        assertEquals(rest.startedAt + 5_000, visible.send(Msg.Rest.Tick, now = rest.startedAt + 5_000).model.now)
        assertTrue(subscriptions(visible.send(Msg.App.Background).model).isEmpty())
        assertTrue(subscriptions(visible.send(Msg.Rest.Dismiss).model).isEmpty())
        assertIgnored(started(), Msg.Rest.Tick)
    }

    // Launch from the widget or a shortcut

    @Test fun `a launch starts the routine it names and shows it`() {
        val launched = stepOf().send(Msg.App.LaunchWorkout("B", restored = false, fromHistory = false))
        assertEquals("B", launched.workout.routineId)
        assertTrue(showsWorkout(launched.model))
        // An unknown routine starts the one offered next.
        assertEquals("A", stepOf().send(Msg.App.LaunchWorkout("gone", false, false)).workout.routineId)
    }

    @Test fun `a launch counts once`() {
        assertIgnored(stepOf(), Msg.App.LaunchWorkout("B", restored = true, fromHistory = false))
        assertIgnored(stepOf(), Msg.App.LaunchWorkout("B", restored = false, fromHistory = true))
    }

    @Test fun `a launch during a workout resumes it`() {
        val closed = started().send(Msg.Nav.CloseWorkout)
        val resumed = closed.send(Msg.App.LaunchWorkout("B", false, false))
        assertSame(closed.session, resumed.session)
        assertTrue(showsWorkout(resumed.model))
    }

    @Test fun `a launch of a routine without exercises selects it and stays home`() {
        val empty = stepOf().send(Msg.Routines.Add(openEditor = false), id = "r3")
        val launched = empty.send(Msg.App.LaunchWorkout("r3", false, false))
        assertEquals("r3", launched.state.nextRoutineId)
        assertEquals(Session.Idle, launched.session)
        assertFalse(showsWorkout(launched.model))
    }

    // Health Connect switch

    @Test fun `turning sync on asks Health Connect only when it is not allowed yet`() {
        val checking = stepOf().send(Msg.Health.Toggle)
        assertEquals(listOf(Cmd.Health(HealthJob.CheckPermission(PermissionCheck.TOGGLE))), checking.cmds)
        val on = checking.send(Msg.Health.PermissionChecked(PermissionCheck.TOGGLE, granted = true))
        assertTrue(on.model.health.enabled)
        assertEquals(listOf(Cmd.SaveHealthEnabled(true)), on.cmds)

        val asking = checking.send(Msg.Health.PermissionChecked(PermissionCheck.TOGGLE, granted = false))
        assertEquals(listOf(UiEvent.RequestHealthPermission), asking.uiEvents)
        val refused = asking.send(Msg.Health.PermissionResult(false))
        assertFalse(refused.model.health.enabled)
        assertEquals("Health Connect did not allow Eisen to write workouts.", refused.model.health.notice)
        assertEquals("Could not open Health Connect.", asking.send(Msg.Health.PermissionRequestFailed).model.health.notice)

        val off = on.send(Msg.Health.Toggle)
        assertFalse(off.model.health.enabled)
        assertEquals(listOf(Cmd.SaveHealthEnabled(false)), off.cmds)
    }

    @Test fun `sync turns off when the permission was taken away`() {
        val on = stepOf().let { it.copy(model = it.model.copy(health = HealthModel(enabled = true))) }
        assertFalse(on.send(Msg.Health.PermissionChecked(PermissionCheck.STARTUP, granted = false)).model.health.enabled)
        assertIgnored(on, Msg.Health.PermissionChecked(PermissionCheck.STARTUP, granted = true))
        assertFalse(on.send(Msg.Health.Revoked).model.health.enabled)
    }

    @Test fun `syncing past workouts runs once at a time and says how it went`() {
        val on = withHistory().let { it.copy(model = it.model.copy(health = HealthModel(enabled = true))) }
        val syncing = on.send(Msg.Health.SyncPast)
        assertEquals(HealthModel(enabled = true, syncing = true, notice = "Syncing…"), syncing.model.health)
        assertEquals(listOf(Cmd.Health(HealthJob.SyncPast(on.state.history))), syncing.cmds)
        assertIgnored(syncing, Msg.Health.SyncPast)
        assertEquals("1 workout synced.", syncing.send(Msg.Health.SyncedPast(1)).model.health.notice)
        assertEquals("3 workouts synced.", syncing.send(Msg.Health.SyncedPast(3)).model.health.notice)
        assertEquals("Could not reach Health Connect.", syncing.send(Msg.Health.SyncedPast(null)).model.health.notice)
        val revoked = syncing.send(Msg.Health.Revoked).send(Msg.Health.SyncedPast(0))
        assertEquals("Health Connect no longer allows Eisen to write workouts.", revoked.model.health.notice)
        assertFalse(revoked.model.health.syncing)
    }

    @Test fun `whether Health Connect is there is asked on every return to the app and when its rows show`() {
        val check = Cmd.Health(HealthJob.CheckAvailability)
        assertTrue(check in stepOf().send(Msg.App.Foreground(notificationsGranted = true)).cmds)
        assertEquals(listOf(check), stepOf().send(Msg.Health.RowsShown).cmds)

        val there = stepOf().send(Msg.Health.Availability(HealthAvailability.AVAILABLE))
        assertEquals(HealthAvailability.AVAILABLE, there.model.health.availability)
        assertTrue(there.cmds.isEmpty())
        assertIgnored(there, Msg.Health.Availability(HealthAvailability.AVAILABLE))
        assertEquals(
            HealthAvailability.NEEDS_INSTALL,
            there.send(Msg.Health.Availability(HealthAvailability.NEEDS_INSTALL)).model.health.availability,
        )
    }

    @Test fun `the haptics of a tap reach the UI as events`() {
        assertEquals(listOf(Feel.TAP), started().send(WorkoutEvent.TapSet(0, 0)).feels)
    }

    // Invariants

    /**
     * Thousands of Msgs in a random order, ones that make sense and ones that do not, and the rules
     * that must hold after every one of them. Seeded, so a failure comes back the same way.
     */
    @Test fun `the model keeps its rules whatever Msgs come in`() {
        // Counts what the walk reached, so a generator that only ever sends ignored Msgs fails too.
        var finished = 0
        var changed = 0
        var held = 0
        var screens = 0
        var corrected = 0
        var picked = 0
        var rated = 0
        var doneScreens = 0
        var questions = 0
        var timed = 0
        var filed = 0
        var ranOut = 0
        var wodsSaved = 0
        var wodRuns = 0
        var wodsFiled = 0
        var drafted = 0
        var undone = 0
        var guarded = 0
        var boxingRuns = 0
        var boxingEdits = 0
        var ownSaved = 0
        // Both workouts start with a hold and a max hold, so the hold timer comes up often.
        val fresh = freshState(WeightUnit.KG)
        val withHolds = fresh.copy(routines = fresh.routines.map { it.copy(slots = listOf(Slot("plank", 3, 30), Slot("deadHang", 2, null)) + it.slots) })
        // The timer's and the WODs' Msgs and the ratings dilute the walk, so it takes more seeds to reach everything as often as before.
        var bounced = 0
        /** One Msg and every rule that must hold after it. Gives back where the walk stands then. */
        fun check(walk: Walk, sent: Sent): Walk {
            val step = walk.step
            val (msg, now, id) = sent
            val after = step.send(msg, now = now, id = id)
            val where = "$id, $msg"
            // Orders of input nobody scripted (QA build 19: a double tap saved 4 instead of 5).
            // A set that was open is never left below its target by a tap that came faster than the guard.
            val event0 = (msg as? Msg.Workout)?.event
            var opened = walk.opened
            if (event0 is WorkoutEvent.TapSet && step.session is Session.Running) {
                val log = step.workout.exercises.getOrNull(event0.exercise)
                val before = log?.sets?.getOrNull(event0.set)
                val logged = after.session.active?.exercises?.getOrNull(event0.exercise)?.sets?.getOrNull(event0.set)
                if (log != null && before == null && logged != null) opened = opened + ((log.id to event0.set) to now)
                val since = log?.let { opened[it.id to event0.set] }
                if (log != null && before != null && since != null && now - since in 0 until SET_TAP_GUARD_MILLIS) {
                    assertEquals("$where: a set logged ${now - since} ms ago was counted down", before, logged)
                    bounced++
                }
            }
            // A set's count only changes through a Msg that names that set, or the hold timer that holds it.
            val activeBefore = step.session.active
            val activeAfter = after.session.active
            if (activeBefore != null && activeAfter != null && activeBefore.id == activeAfter.id && msg !is Msg.Data) {
                for (log in activeAfter.exercises) {
                    val old = activeBefore.exercises.firstOrNull { it.id == log.id } ?: continue
                    for (set in old.sets.indices) {
                        if (old.sets[set] == log.sets.getOrNull(set)) continue
                        fun names(exercise: Int, at: Int) = activeBefore.exercises.getOrNull(exercise)?.id == log.id && at == set
                        val named = when {
                            msg is Msg.WorkoutScreen.PickReps -> names(msg.exercise, msg.set)
                            msg is Msg.Hold || msg is Msg.App.Foreground -> step.model.hold?.let { names(it.exercise, it.set) } ?: false
                            else -> when (val event = event0) {
                                is WorkoutEvent.TapSet -> names(event.exercise, event.set)
                                is WorkoutEvent.LogReps -> names(event.exercise, event.set)
                                is WorkoutEvent.CorrectReps -> names(event.exercise, event.set)
                                is WorkoutEvent.LogHold -> names(event.exercise, event.set)
                                else -> false
                            }
                        }
                        assertTrue("$where changed set $set of ${log.id} from ${old.sets[set]} to ${log.sets.getOrNull(set)}", named)
                    }
                }
            }
            // A running rest of a logged set can always be opened: something lies over the workout for it,
            // and a rest screen there is the one of that set.
            val running = after.model.rest
            if (running != null && after.model.timer !is TimerState.Running) {
                val restId = running.exerciseId
                val restSet = running.set
                val restLog = after.session.active?.exercises?.firstOrNull { it.id == restId }
                if (restLog != null && restSet != null && restLog.sets.getOrNull(restSet) != null) assertNotNull("$where: a rest runs with no screen to open", after.model.overlay)
                (after.model.overlay as? RestScreen)?.takeIf { it !is RestScreen.SetDone }?.let { screen ->
                    assertEquals(where, restId to restSet, screen.exerciseId to screen.set)
                    assertEquals(where, after.session.active?.exercises?.indexOfFirst { it.id == screen.exerciseId }, screen.exercise)
                }
            }

            // Pure: the same Msg on the same model at the same time gives the same answer.
            assertEquals(where, after, step.send(msg, now = now, id = id))
            // The workout belongs to the session.
            assertEquals(where, after.session.active, after.state.active)
            // The file is written exactly when the saved state changed, with that state.
            val saves = after.cmds.filterIsInstance<Cmd.Save>()
            assertEquals(where, if (after.state != step.state) listOf(Cmd.Save(after.state)) else emptyList<Cmd>(), saves)
            // The widget and the shortcuts are redrawn exactly when what they show changed, with that.
            val surfaces = refreshHomeSurfaces(after.state)
            val redraws = after.cmds.filterIsInstance<Cmd.RefreshHomeSurfaces>()
            assertEquals(where, if (surfaces != refreshHomeSurfaces(step.state)) listOf(surfaces) else emptyList<Cmd>(), redraws)
            // There is always a next workout, and it exists.
            val ids = after.state.routines.map { it.id }
            assertTrue(where, after.state.nextRoutineId in ids)
            // The editor never shows a routine that is gone.
            assertTrue(where, after.model.nav.routine == null || after.model.nav.routine in ids)
            // Every slot is a known exercise, at most once per routine.
            val known = exercises(after.model).map { it.id }.toSet()
            for (routine in after.state.routines) {
                assertTrue(where, routine.slots.all { it.exerciseId in known })
                assertEquals(where, routine.slots.size, routine.slots.map { it.exerciseId }.toSet().size)
            }
            // A hold timer only exists over an open set of a timed exercise of the running workout on
            // screen, and its clock runs as long as it does.
            after.model.hold?.let { hold ->
                assertTrue(where, after.session is Session.Running)
                assertTrue(where, holdFits(after.model, hold))
                assertTrue(where, showsWorkout(after.model))
                assertTrue(where, Sub.HoldClock(hold.startsAt) in subscriptions(after.model))
            }
            if (after.model.hold == null) assertTrue(where, subscriptions(after.model).none { it is Sub.HoldClock })
            // An open rest screen belongs to the running rest and a logged set of its exercise, and
            // never shows with a hold timer.
            // The "set done" screen has no rest and never minimises.
            after.model.restScreen?.let { screen ->
                if (screen is RestScreen.SetDone) {
                    assertNull(where, after.model.rest)
                    if (step.model.restScreen != screen) doneScreens++
                } else {
                    assertNotNull(where, after.model.rest)
                }
                assertTrue(where, restScreenFits(after.model, screen))
                if (step.model.restScreen == null) screens++
            }
            // A reps question belongs to a max-reps set of the workout on screen; what lies over the workout always fits it.
            after.model.repsQuestion?.let { question ->
                assertTrue(where, showsWorkout(after.model))
                assertEquals(where, question.exerciseId, after.model.session.active?.exercises?.getOrNull(question.exercise)?.id)
                if (step.model.repsQuestion == null) questions++
            }
            assertEquals(where, fitting(after.model, after.model.overlay), after.model.overlay)
            // A rating only ever sits on a logged set with a target count of reps, and never past the sets.
            for (log in after.state.history.flatMap { it.exercises } + after.state.active?.exercises.orEmpty()) {
                assertTrue(where, log.rir.size <= log.sets.size)
                for (set in log.rir.indices) if (log.rir[set] != null) assertTrue(where, canRate(log, set) && rirOf(log, set) != null)
            }
            // The wheels, the chips and putting the screen away or back never touch the rest, its alarms or its notification.
            val event = (msg as? Msg.Workout)?.event
            if (event is WorkoutEvent.CorrectReps || event is WorkoutEvent.RateSet || event is WorkoutEvent.SetWeight || msg == Msg.Rest.Minimise || msg == Msg.Rest.Open) {
                assertEquals(where, step.model.rest, after.model.rest)
                assertTrue(where, after.cmds.none { it is Cmd.SaveRest || it is Cmd.ScheduleRestAlarms || it is Cmd.CancelRestAlarms || it is Cmd.ShowRestCountdown })
                assertEquals(where, step.model.repsQuestion, after.model.repsQuestion)
            }
            if (event is WorkoutEvent.RateSet && after.model != step.model) {
                rated++
                assertEquals(where, event.rir, rirOf(after.workout.exercises[event.exercise], event.set))
                assertEquals(where, step.workout.exercises[event.exercise].sets, after.workout.exercises[event.exercise].sets)
            }
            // A number picked on a logged set only corrects it, like the wheel: the rest runs on untouched.
            if (msg is Msg.WorkoutScreen.PickReps && step.session is Session.Running) {
                val before = step.workout.exercises.getOrNull(msg.exercise)
                assertTrue(where, after.state.pickedReps)
                if (before != null && before.sets.getOrNull(msg.set) != null) {
                    assertEquals(where, step.model.rest, after.model.rest)
                    assertTrue(where, after.cmds.none { it is Cmd.SaveRest || it is Cmd.ScheduleRestAlarms || it is Cmd.CancelRestAlarms || it is Cmd.ShowRestCountdown })
                } else if (before != null && before.targetReps != null && !before.timed && msg.set in before.sets.indices && msg.reps in 0..before.targetReps!!) {
                    assertEquals(where, msg.reps, after.workout.exercises[msg.exercise].sets[msg.set])
                    picked++
                }
            }
            if (event is WorkoutEvent.CorrectReps && after.model != step.model) {
                corrected++
                assertEquals(where, step.model.restScreen, after.model.restScreen)
                assertEquals(where, event.reps, after.workout.exercises[event.exercise].sets[event.set])
            }
            // The timer logs a set of its own exercise only, never more than its target.
            val before = step.model.hold
            if (before != null && after.model.hold == null && msg is Msg.Hold && after.session is Session.Running) {
                val logged = after.workout.exercises.getOrNull(before.exercise)?.sets?.getOrNull(before.set)
                if (logged != null) {
                    held++
                    assertTrue(where, logged in 1..(before.target ?: MAX_HELD_SECONDS))
                }
            }
            // An interval timer: a run covers the rest and everything over the workout, and its clock runs while it goes.
            val run = after.model.timerRun
            if (run != null) {
                assertNull(where, after.model.rest)
                assertNull(where, after.model.overlay)
                val tick = if (run.config.mode == TimerMode.BOXING) 500L else 1000L
                assertEquals(where, run.pausedAt == null, Sub.TimerClock(run.startedAt + run.paused, tick) in subscriptions(after.model))
            } else {
                assertTrue(where, subscriptions(after.model).none { it is Sub.TimerClock })
            }
            // A WOD run keeps the time of every tap, one per tap and in order; a plain timer keeps none.
            for (kept in listOfNotNull(run, (after.model.timer as? TimerState.Finished)?.undo)) {
                if (kept.wod != null) {
                    assertEquals(where, kept.taps, kept.splits.size)
                    assertEquals(where, kept.splits.sorted(), kept.splits)
                } else {
                    assertTrue(where, kept.splits.isEmpty())
                }
            }
            // A skipped rest never adds a split or a tap.
            if (msg == Msg.Timer.SkipRest && run != null && wasRunningRun(step) != null) {
                assertEquals(where, wasRunningRun(step)!!.splits, run.splits)
                assertEquals(where, wasRunningRun(step)!!.taps, run.taps)
            }
            // The result does not answer within a second of showing: that is the thumb of the last tap.
            val shown = step.model.timer as? TimerState.Finished
            if (shown != null && msg is Msg.Timer && now - shown.at in 0 until RESULT_GUARD_MILLIS &&
                (msg == Msg.Timer.UndoFinish || msg == Msg.Timer.Done || msg == Msg.Timer.Again || msg == Msg.Timer.DontCount || msg is Msg.Timer.CorrectRounds)
            ) {
                assertSame(where, step.model, after.model)
                guarded++
            }
            // The run is kept in its file exactly when it changed, with that run.
            val kept = after.cmds.filterIsInstance<Cmd.SaveTimer>()
            assertEquals(where, if (run != step.model.timerRun) listOf(Cmd.SaveTimer(run)) else emptyList<Cmd>(), kept)
            if (run != null && step.model.timerRun == null) timed++
            // A round counts only when the one before is at least the tap guard ago.
            val wasRunning = step.model.timer as? TimerState.Running
            if (msg == Msg.Timer.Tap && run != null && wasRunning != null && run.taps > wasRunning.run.taps) {
                val last = wasRunning.tappedAt
                assertTrue(where, last == null || now - last >= TAP_GUARD_MILLIS)
            }
            // A corrected result is the same session as the one in the history.
            val finishedTimer = after.model.timer as? TimerState.Finished
            if (msg is Msg.Timer.CorrectRounds && finishedTimer != null && after.model != step.model) {
                assertTrue(where, finishedTimer.session.rounds >= 0)
                assertTrue(where, finishedTimer.session in after.state.timerSessions)
            }
            // The history of timers only grows by a run that ended, with numbers it can have.
            if (msg is Msg.Timer && msg != Msg.Timer.UndoDeleteSession && after.state.timerSessions.size != step.state.timerSessions.size) {
                if (after.state.timerSessions.size > step.state.timerSessions.size) {
                    val ended = step.model.timerRun
                    val session = after.state.timerSessions.first()
                    assertNotNull(where, ended)
                    assertEquals(where, ended!!.id, session.id)
                    assertEquals(where, session, (after.model.timer as? TimerState.Finished)?.session)
                    // The last tap of a WOD is filed with the run, so the run its result can bring back has it.
                    val filedRun = (after.model.timer as? TimerState.Finished)?.undo ?: ended
                    assertEquals(where, filedRun.splits.takeIf { filedRun.wod != null && it.isNotEmpty() }, session.splits)
                    assertTrue(where, session.seconds in 0..(timerSeconds(session.config) ?: MAX_OPEN_TIMER_SECONDS))
                    assertTrue(where, session.finishedAt >= session.startedAt && session.rounds >= 0)
                    if (!countsRoundsByTap(session.config.mode)) assertTrue(where, session.rounds <= session.config.rounds)
                    filed++
                    if (session.completed && msg != Msg.Timer.Finish && msg != Msg.Timer.Tap) ranOut++
                } else {
                    assertTrue(where, msg is Msg.Timer.DeleteSession || msg == Msg.Timer.UndoFinish || msg == Msg.Timer.DontCount)
                }
            }
            // The lifter's WODs: ids of their own, never a benchmark's, each in its limits; the draft too, but for its name.
            val own = after.state.wods.map { it.id }
            assertEquals(where, own.size, own.toSet().size)
            assertTrue(where, own.none(::isLibraryWod))
            for (saved in after.state.wods) assertEquals(where, wod(saved), saved)
            after.model.wods.draft?.let { draft ->
                assertEquals(where, wod(draft.wod).copy(name = draft.wod.name), draft.wod)
                if (step.model.wods.draft == null) drafted++
            }
            after.model.wods.detail?.let { assertNotNull(where, findWod(after.state.wods, it)) }
            // The suggestions only open over something typed, and the search fits its field.
            assertTrue(where, !after.model.wods.suggesting || after.model.wods.query.isNotBlank())
            assertTrue(where, after.model.wods.query.length <= MAX_QUERY)
            // The draft file is written exactly when what it keeps changed, with that.
            val keptAfter = draftFile(after.model.wods.builder)
            assertEquals(where, if (keptAfter != draftFile(step.model.wods.builder)) listOf(Cmd.SaveDraft(keptAfter)) else emptyList<Cmd>(), after.cmds.filterIsInstance<Cmd.SaveDraft>())
            // A finished WOD run is complete only with every step tapped off; Undo brings back the run its last tap ended.
            val ended = (step.model.timer as? TimerState.Finished)?.undo
            if (msg == Msg.Timer.UndoFinish && ended != null && after.model != step.model) {
                undone++
                assertEquals(where, ended.taps - 1, after.model.timerRun!!.taps)
                assertTrue(where, after.state.timerSessions.none { it.id == ended.id })
            }
            // A boxing plan always has one round per round and stays in its limits, wherever it is kept,
            // and a boxing run always has the seed that picks its combinations.
            val setup = after.model.timerSetup
            val configs = listOfNotNull(setup?.config, run?.config, (after.model.timer as? TimerState.Finished)?.session?.config) +
                after.state.timerConfigs.values + after.state.timerPresets.map { it.config } + after.state.timerSessions.map { it.config }
            for (config in configs.filter { it.mode == TimerMode.BOXING }) {
                assertEquals(where, timerConfig(config), config)
                assertEquals(where, config.rounds, config.boxing!!.rounds.size)
            }
            if (run?.config?.mode == TimerMode.BOXING) {
                assertNotNull(where, run.seed)
                if (run.id != step.model.timerRun?.id) boxingRuns++
            }
            setup?.sheet?.let { assertTrue(where, it.index in 0 until setup.config.rounds && setup.config.mode == TimerMode.BOXING) }
            // A run waits for the notification question only while none goes, and a boxing setup's edits are its mode's last config.
            if (after.model.pendingStart != null) assertNull(where, run)
            if (setup?.config?.mode == TimerMode.BOXING && msg is Msg.Timer && step.model.timerSetup?.config != setup.config && msg !is Msg.Timer.Open && msg !is Msg.Timer.OpenPreset && msg !is Msg.Timer.StartPreset) {
                assertEquals(where, setup.config, after.state.timerConfigs[TimerMode.BOXING])
            }
            if (setup?.config?.mode == TimerMode.BOXING && step.model.timerSetup?.config?.boxing != setup.config.boxing && step.model.timerSetup?.config?.mode == TimerMode.BOXING) boxingEdits++
            // A boxing plan is always within its limits: the lifter's own combinations valid and unique, every pick one the round can call.
            setup?.config?.boxing?.let { plan ->
                assertEquals(where, plan, io.github.alexanderop.eisen.feature.timer.boxing.boxingPlan(plan, plan.rounds.size))
                assertEquals(where, plan.own.map { it.id }.distinct(), plan.own.map { it.id })
                assertTrue(where, plan.own.size <= io.github.alexanderop.eisen.feature.timer.boxing.MAX_OWN_COMBOS)
                if (msg is Msg.Timer.SaveOwn && plan.own != step.model.timerSetup?.config?.boxing?.own) ownSaved++
            }
            if (msg == Msg.Wods.Save && after.state != step.state) wodsSaved++
            // A run carries a WOD exactly when it was started from one, and leaves the setups of the modes alone.
            if (run != null && run.id != step.model.timerRun?.id) {
                val expected = when {
                    msg is Msg.Wods.Start -> findWod(step.state.wods, msg.id)
                    msg == Msg.Timer.Again -> (step.model.timer as? TimerState.Finished)?.wod?.let { findWod(step.state.wods, it.id) ?: it }
                    msg == Msg.Timer.UndoFinish -> (step.model.timer as? TimerState.Finished)?.undo?.wod
                    // A run that waited for the notification question starts with its answer.
                    msg is Msg.Rest.NotificationPermission -> step.model.pendingStart?.wod
                    else -> null
                }
                assertEquals(where, expected, run.wod)
                if (run.wod != null) {
                    wodRuns++
                    assertEquals(where, step.state.timerConfigs, after.state.timerConfigs)
                }
            }
            // A filed run of a WOD is filed under its id, and its result shows the WOD.
            if (after.state.timerSessions.size > step.state.timerSessions.size && msg is Msg.Timer && msg != Msg.Timer.UndoDeleteSession) {
                val session = after.state.timerSessions.first()
                assertEquals(where, step.model.timerRun?.wod?.id, session.wodId)
                assertEquals(where, step.model.timerRun?.wod, (after.model.timer as TimerState.Finished).wod)
                if (session.wodId != null) wodsFiled++
            }
            if (after.state.history.size > step.state.history.size && after.session.active == null) finished++
            if (after.model != step.model) changed++
            return Walk(after, opened)
        }
        for (seed in 1L..320L) {
            val random = java.util.Random(seed)
            var walk = Walk(stepOf(withHolds))
            var now = STARTED_AT
            val trail = mutableListOf<Sent>()
            repeat(300) { turn ->
                // Now and then the same Msg again right after it, as a bouncing thumb or a double tap sends it.
                val again = trail.isNotEmpty() && random.nextInt(5) == 0
                now += if (again) 30L + random.nextInt(700) else random.nextInt(120_000).toLong()
                val sent = Sent(if (again) trail.last().msg else randomMsg(walk.step.model, random), now, "r$seed-$turn")
                trail += sent
                walk = runCatching { check(walk, sent) }.getOrElse { failure ->
                    // The walk is cut down to the Msgs the failure needs, so the report is a handful of lines and not three hundred.
                    val shortest = shrink(trail) { part -> runCatching { part.fold(Walk(stepOf(withHolds)), ::check) }.isFailure }
                    throw AssertionError("${failure.message}\nShortest walk that fails, ${shortest.size} of ${trail.size} Msgs:\n${shortest.joinToString("\n") { "  ${it.id} at ${it.now}: ${it.msg}" }}", failure)
                }
            }
        }
        assertTrue("only $finished workouts finished", finished >= 10)
        assertTrue("only $changed Msgs changed the model", changed >= 1_000)
        assertTrue("only $held holds were logged by the timer", held >= 5)
        assertTrue("only $screens rest screens opened", screens >= 20)
        assertTrue("only $corrected sets were corrected", corrected >= 5)
        assertTrue("only $picked open sets were logged by a picked number", picked >= 5)
        assertTrue("only $rated sets were rated", rated >= 20)
        assertTrue("only $doneScreens set done screens opened", doneScreens >= 5)
        assertTrue("only $questions reps questions were asked", questions >= 5)
        assertTrue("only $timed timers started", timed >= 30)
        assertTrue("only $filed timers were filed", filed >= 15)
        assertTrue("only $ranOut timers ran out by themselves", ranOut >= 3)
        assertTrue("only $drafted builders opened", drafted >= 50)
        assertTrue("only $wodsSaved WODs were saved", wodsSaved >= 20)
        assertTrue("only $wodRuns WODs started", wodRuns >= 20)
        assertTrue("only $wodsFiled runs of WODs were filed", wodsFiled >= 5)
        assertTrue("only $undone finished runs were undone", undone >= 3)
        assertTrue("only $guarded taps on a result were too early", guarded >= 1)
        assertTrue("only $boxingRuns boxing runs started", boxingRuns >= 5)
        assertTrue("only $boxingEdits boxing plans were edited", boxingEdits >= 50)
        assertTrue("only $ownSaved own combinations were saved", ownSaved >= 20)
        assertTrue("only $bounced taps came within the guard after the tap that logged their set", bounced >= 20)
    }

    /** A Msg of the walk with the time and the id it was sent with, so a part of the walk can be sent again. */
    private data class Sent(val msg: Msg, val now: Long, val id: String)

    /** Where a walk stands: the step, and when each set (exercise id and index) was last logged from open by a tap. */
    private data class Walk(val step: Step, val opened: Map<Pair<String, Int>, Long> = emptyMap())

    private fun wasRunningRun(step: Step) = (step.model.timer as? TimerState.Running)?.run

    /** A Msg about the current model, or about things that are not there (an index too far, an id that is gone). */
    private fun randomMsg(model: Model, random: java.util.Random): Msg {
        fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]
        fun index(size: Int): Int = random.nextInt(size + 2) - 1
        val state = model.state
        val routine = pick(state.routines)
        val routineId = if (random.nextInt(8) == 0) "gone" else routine.id
        val exerciseId = pick(exercises(model).map { it.id }.take(12) + listOf("plank", "deadHang") + state.customExercises.map { it.id } + "nope")
        val slotIndex = index(routine.slots.size)
        val slot = routine.slots.getOrNull(slotIndex) ?: Slot(exerciseId, 3, 5)
        val logs = model.session.active?.exercises.orEmpty()
        val exercise = index(logs.size)
        val set = random.nextInt(7) - 1
        val workoutId = state.history.getOrNull(index(state.history.size))?.id ?: "gone"
        val tap = { Msg.Workout(WorkoutEvent.TapSet(exercise, set)) }
        // A tap on a set of a timed exercise, which opens the hold timer when the set is open.
        val timed = logs.indices.filter { logs[it].timed }
        val tapHold = { Msg.Workout(WorkoutEvent.TapSet(if (timed.isEmpty()) exercise else pick(timed), random.nextInt(4))) }
        // The reps wheel corrects a logged set (the rest screen's most of the time), or a set that is not there.
        val logged = logs.indices.filterNot { logs[it].timed }.flatMap { i -> logs[i].sets.indices.filter { logs[i].sets[it] != null }.map { i to it } }
        val corrected = model.restScreen?.let { it.exercise to it.set }?.takeIf { random.nextInt(4) > 0 }
            ?: logged.takeIf { it.isNotEmpty() && random.nextBoolean() }?.let { pick(it) } ?: (exercise to set)
        val correct = { Msg.Workout(WorkoutEvent.CorrectReps(corrected.first, corrected.second, random.nextInt(8) - 1)) }
        // The next open set of the exercise with a target count closest to done, on the workout screen, so exercises
        // get to their last set, which opens the "set done" screen. Random taps rarely get that far.
        val open = logs.indices.filter { i -> !logs[i].timed && logs[i].targetReps != null && null in logs[i].sets }
        val tapNext = {
            when {
                open.isEmpty() -> tap()
                !model.nav.workoutOpen -> Msg.Nav.OpenWorkout
                else -> open.minBy { i -> logs[i].sets.count { it == null } }.let { i -> Msg.Workout(WorkoutEvent.TapSet(i, logs[i].sets.indexOf(null))) }
            }
        }
        // The chips rate the rest screen's set most of the time, with ratings in and out of range.
        val rate = { Msg.Workout(WorkoutEvent.RateSet(corrected.first, corrected.second, (random.nextInt(8) - 2).takeIf { it >= -1 })) }
        // While an interval timer runs, its own Msgs come most of the time: it covers everything else.
        if (model.timer != null && random.nextInt(4) > 0) {
            return pick(
                listOf(
                    Msg.Timer.Tick, Msg.Timer.Tick, Msg.Timer.Tick, Msg.Timer.Pause, Msg.Timer.Resume, Msg.Timer.Resume, Msg.Timer.End,
                    Msg.Timer.KeepGoing, Msg.Timer.SaveAndEnd, Msg.Timer.Discard, Msg.Timer.Finish, Msg.Timer.Tap, Msg.Timer.Tap,
                    Msg.Timer.Untap, Msg.Timer.CorrectRounds(1), Msg.Timer.CorrectRounds(-1),
                    Msg.Timer.Done, Msg.Timer.Again, Msg.Nav.Back, Msg.Timer.Release(model.timerRun?.id ?: "gone"),
                    Msg.Timer.UndoFinish, Msg.Timer.Tap, Msg.Timer.SkipRest, Msg.Timer.DontCount,
                ),
            )
        }
        // A boxing setup's own Msgs come most of the time while it is open: its round sheet, templates, style and listening.
        val boxing = model.timerSetup?.takeIf { it.config.mode == TimerMode.BOXING }
        if (boxing != null && model.timer == null && random.nextInt(3) > 0) {
            val rounds = boxing.config.rounds
            val ownIds = boxing.config.boxing?.own.orEmpty().map { it.id } + "own-99"
            val at = boxing.sheet?.index ?: index(rounds)
            return pick(
                listOf(
                    { Msg.Timer.OpenRound(index(rounds)) },
                    { Msg.Timer.CloseRound },
                    { Msg.Timer.EditRound(at, BoxingRound(pick(BoxingTheme.entries), random.nextInt(20) - 2, pick(listOf(0, 7, 15, 20, 30, 60)))) },
                    { Msg.Timer.EditRound(at, BoxingRound(pick(BoxingTheme.entries), random.nextInt(20) - 2, pick(listOf(0, 7, 15, 20, 30, 60)))) },
                    { Msg.Timer.CopyToAll(at) },
                    { Msg.Timer.ApplyTemplate(random.nextInt(BOXING_TEMPLATES.size + 1)) },
                    { Msg.Timer.UndoTemplate },
                    { Msg.Timer.SetStyle(pick(BoxingStyle.entries)) },
                    { Msg.Timer.Listen(at) },
                    { Msg.Timer.PlayCombo(pick(listOf("six", "jab", "nope"))) },
                    { Msg.Timer.MoveRound(index(rounds), index(rounds)) },
                    { Msg.Timer.DuplicateRound(index(rounds)) },
                    { Msg.Timer.RemoveRound(index(rounds)) },
                    { Msg.Timer.ForgetTemplate(boxing.template?.at ?: 0) },
                    { Msg.Timer.EditRound(at, (boxing.config.boxing?.rounds?.getOrNull(at) ?: BoxingRound(BoxingTheme.MIX, 6, 0)).copy(pick = listOf(pick(listOf("jab", "jab-cross-hook", "six", "nope"))))) },
                    { Msg.Timer.Edit(boxing.config.copy(rounds = random.nextInt(18) - 1, work = random.nextInt(700), rest = random.nextInt(320))) },
                    { Msg.Timer.Edit(boxing.config.copy(boxing = null)) },
                    { Msg.Timer.SavePreset(pick(listOf("", "Sparring"))) },
                    { Msg.Timer.UpdatePreset },
                    { Msg.Timer.Start },
                    { Msg.Nav.Back },
                    // The coach's second round: reminders, the non-stop finish, stance, room and the lifter's own combinations.
                    { Msg.Timer.EditRound(at, (boxing.config.boxing?.rounds?.getOrNull(at) ?: BoxingRound(BoxingTheme.MIX, 6, 0)).copy(cues = pick(BoxingCues.entries), burnout = pick(listOf(0, 15, 30, 20)))) },
                    { Msg.Timer.SetStance(pick(BoxingStance.entries)) },
                    { Msg.Timer.SetRoom(pick(BoxingRoom.entries)) },
                    { Msg.Timer.SaveOwn(null, List(random.nextInt(8)) { pick(BOXING_MOVES).id + if (random.nextInt(20) == 0) "x" else "" }, pick(listOf(null, at))) },
                    { Msg.Timer.SaveOwn(pick(ownIds), List(1 + random.nextInt(6)) { pick(BOXING_MOVES).id }) },
                    { Msg.Timer.DeleteOwn(pick(ownIds)) },
                    { Msg.Timer.UndoDeleteOwn },
                    { Msg.Timer.ListenMoves(List(random.nextInt(4)) { pick(BOXING_MOVES).id }) },
                    { Msg.Timer.PlayCombo(pick(ownIds)) },
                    { Msg.Timer.EditRound(at, BoxingRound(pick(listOf(BoxingTheme.OWN, BoxingTheme.BUILD, BoxingTheme.WARMUP)), 6, 0, ownIds.take(random.nextInt(2)))) },
                ),
            )()
        }
        // The builder's own Msgs come most of the time while it is open, as they would from the lifter.
        val draft = model.wods.draft
        val wodIds = WOD_LIBRARY.map { it.id }.take(4) + state.wods.map { it.id } + "gone"
        val ownId = state.wods.getOrNull(index(state.wods.size))?.id ?: "gone"
        if (draft != null && model.timer == null && random.nextInt(3) > 0) {
            val items = draft.wod.items
            val at = index(items.size)
            val item = items.getOrNull(at) ?: WodItem(exerciseId, 5, WodUnit.REPS, 0.0)
            return pick(
                listOf(
                    { Msg.Wods.Rename(pick(listOf("", "  ", "Burner", "z".repeat(60)))) },
                    { Msg.Wods.SetMode(pick(TimerMode.entries)) },
                    { Msg.Wods.SetTimer(TimerConfig(if (random.nextInt(4) > 0) draft.wod.timer.mode else pick(TimerMode.entries), random.nextInt(900) - 10, random.nextInt(60), random.nextInt(70) - 2)) },
                    { Msg.Wods.SetRounds(random.nextInt(70) - 2) },
                    { Msg.Wods.SetScheme(pick(SCHEMES + listOf(emptyList(), listOf(0, 5, 2000), List(14) { it }))) },
                    { Msg.Wods.AddItem(exerciseId) },
                    { Msg.Wods.AddItems(listOf(exerciseId, pick(WOD_LIBRARY).items.first().exerciseId, "nope")) },
                    { Msg.Wods.UpdateItem(at, item.copy(amount = random.nextInt(12_000) - 5, unit = pick(WodUnit.entries), load = random.nextInt(1200) * 0.5 - 20)) },
                    { Msg.Wods.MoveItem(index(items.size), index(items.size)) },
                    { Msg.Wods.RemoveItem(at, item.exerciseId) },
                    { Msg.Wods.Save },
                    { Msg.Wods.Save },
                    { Msg.Wods.Leave },
                    { Msg.Wods.KeepEditing },
                    { Msg.Wods.Discard },
                    { Msg.Nav.Back },
                    { Msg.Exercises.AddCustom(pick(listOf("Sled Push", "burpee")), pick(Equipment.entries), AddTarget.Wod) },
                ),
            )()
        }
        // While a hold timer is open, its own Msgs come half the time, as they would from the lifter and the clock.
        if (model.hold != null && random.nextBoolean()) return pick(listOf(Msg.Hold.SkipLeadIn, Msg.Hold.Stop, Msg.Hold.Cancel, Msg.Hold.Tick, Msg.Hold.Tick))
        val choices: List<() -> Msg> = listOf(
            { Msg.Workout(WorkoutEvent.Start) }, tap, tap, tap, tap, tap, tap, tapHold, tapHold, tapHold,
            { Msg.Workout(WorkoutEvent.LogReps(exercise, set, random.nextInt(12).takeIf { it > 0 })) },
            { Msg.Workout(WorkoutEvent.LogHold(exercise, set, random.nextInt(100))) },
            correct, correct, correct, correct, correct, correct,
            rate, rate, rate, rate, tapNext, tapNext, tapNext,
            { Msg.Rest.Rated(model.restScreen ?: RestScreen.Shown(0, 0, "nope"), random.nextInt(MAX_RIR + 1)) },
            { Msg.Settings.SetTrackRir(random.nextInt(4) > 0) },
            { Msg.Hold.SkipLeadIn },
            { Msg.Hold.Stop },
            { Msg.Hold.Stop },
            { Msg.Hold.Cancel },
            { Msg.Hold.Tick },
            { Msg.Hold.Tick },
            { Msg.Hold.Tick },
            { Msg.Workout(WorkoutEvent.SetWeight(exercise, random.nextInt(200) * 2.5 - 10)) },
            { Msg.Workout(WorkoutEvent.SetWorkingWeight(exerciseId, random.nextInt(100) * 2.5)) },
            { Msg.Workout(WorkoutEvent.AddExercise(exerciseId)) },
            { Msg.Workout(WorkoutEvent.RemoveExercise(exercise)) },
            { Msg.Workout(WorkoutEvent.Defer(exercise)) },
            { Msg.Workout(WorkoutEvent.Move(exercise, index(logs.size))) },
            { Msg.Workout(WorkoutEvent.Rise(exerciseId)) },
            // Finishing takes two Msgs in a row, so they come more often than the rest.
            { Msg.Workout(WorkoutEvent.RequestFinish) },
            { Msg.Workout(WorkoutEvent.RequestFinish) },
            { Msg.Workout(WorkoutEvent.RequestFinish) },
            { Msg.Workout(WorkoutEvent.RequestDiscard) },
            { Msg.Workout(WorkoutEvent.Confirm) },
            { Msg.Workout(WorkoutEvent.Confirm) },
            { Msg.Workout(WorkoutEvent.Confirm) },
            { Msg.Workout(WorkoutEvent.Confirm) },
            { Msg.Workout(WorkoutEvent.Cancel) },
            { Msg.Workout(WorkoutEvent.Undo) },
            { Msg.Workout(WorkoutEvent.Forget) },
            { Msg.WorkoutScreen.ToggleWarmup(warmupKey(exerciseId, set)) },
            { Msg.WorkoutScreen.CloseRepsEntry },
            // Holding a set: a number picked (the rest screen's set, a logged one or any), or the reps question.
            { Msg.WorkoutScreen.PickReps(corrected.first, corrected.second, random.nextInt(8) - 1) },
            { Msg.WorkoutScreen.PickReps(exercise, set, random.nextInt(8) - 1) },
            { Msg.Workout(WorkoutEvent.EnterReps(exercise, set)) },
            { Msg.WorkoutScreen.Settle(exerciseId, model.settling?.since ?: 0) },
            { Msg.WorkoutScreen.SettleNow },
            { Msg.WorkoutScreen.MoveBy(exerciseId, if (random.nextBoolean()) 1 else -1) },
            { Msg.Nav.SelectTab(pick(Tab.entries)) },
            { Msg.Nav.OpenWorkout },
            { Msg.Nav.CloseWorkout },
            { Msg.Nav.OpenRoutine(routineId) },
            { Msg.Nav.CloseRoutine },
            { Msg.Nav.Back },
            { Msg.Nav.Restore(pick(Tab.entries), random.nextBoolean(), routineId) },
            { Msg.Home.DismissSummary },
            { Msg.Routines.Select(routineId) },
            { Msg.Routines.Add(random.nextBoolean()) },
            { Msg.Routines.Rename(routineId, pick(listOf("", "  ", "Push", "x".repeat(40)))) },
            { Msg.Routines.CommitName(routineId) },
            { Msg.Routines.Move(index(state.routines.size), index(state.routines.size)) },
            { Msg.Routines.Remove(routineId) },
            { Msg.Routines.AddSlot(routineId, exerciseId) },
            { Msg.Routines.UpdateSlot(routineId, slotIndex, slot.copy(sets = 1 + random.nextInt(6))) },
            { Msg.Routines.MoveSlot(routineId, index(routine.slots.size), index(routine.slots.size)) },
            { Msg.Routines.RemoveSlot(routineId, slot.exerciseId, random.nextBoolean()) },
            { Msg.Routines.InsertSlot(routineId, index(routine.slots.size), Slot(exerciseId, 3, 5)) },
            { Msg.Routines.UndoRemoveSlot },
            { Msg.Exercises.AddCustom(pick(listOf("", "Cable Fly", "bench press", "Curl", "Hang")), pick(Equipment.entries), pick(listOf(null, AddTarget.Routine(routineId), AddTarget.Workout)), random.nextBoolean()) },
            { Msg.Exercises.DeleteCustom(exerciseId) },
            { Msg.History.Edit(workoutId, state.history.firstOrNull { it.id == workoutId }?.exercises.orEmpty().reversed()) },
            { Msg.History.Delete(workoutId, random.nextBoolean()) },
            { Msg.History.UndoDelete },
            { model.undo.deletedWorkout?.let { Msg.History.Restore(it.workout, index(state.history.size)) } ?: Msg.History.UndoDelete },
            { Msg.Settings.SetUnit(pick(WeightUnit.entries)) },
            { Msg.Settings.SetBar(random.nextInt(30).toDouble()) },
            { Msg.Settings.SetRestDone(random.nextInt(400)) },
            { Msg.Settings.SetRestMissed(random.nextInt(400)) },
            { Msg.Settings.SetSound(random.nextBoolean()) },
            { Msg.Settings.SetHaptics(random.nextBoolean()) },
            { Msg.Settings.SetRestFullScreen(random.nextBoolean()) },
            { Msg.Settings.SetIncrement(exerciseId, random.nextInt(5) * 1.25) },
            { Msg.Data.Import(exportJson(model)) },
            { Msg.Data.Import("not a backup") },
            { Msg.Data.Restore("content://backup") },
            { Msg.Data.BackupRead(if (random.nextBoolean()) null else "{}") },
            { Msg.Data.WriteBackup("content://backup") },
            { Msg.Data.BackupWritten(random.nextBoolean()) },
            { Msg.Data.SendBackup },
            { Msg.Data.SendFailed },
            { Msg.Data.Reset },
            { Msg.Rest.Extend },
            { Msg.Rest.Dismiss },
            { Msg.Rest.Shorten },
            { Msg.Rest.Lengthen },
            { Msg.Rest.Minimise },
            { Msg.Rest.Open },
            { Msg.Rest.AlarmTen },
            { Msg.Rest.AlarmOver },
            { Msg.Rest.CueFinished(model.rest ?: RestState(0, 0, "")) },
            { Msg.Rest.Tick },
            { Msg.Rest.NotificationPermission(random.nextBoolean()) },
            { Msg.Health.Toggle },
            { Msg.Health.PermissionChecked(pick(PermissionCheck.entries), random.nextBoolean()) },
            { Msg.Health.PermissionResult(random.nextBoolean()) },
            { Msg.Health.PermissionRequestFailed },
            { Msg.Health.SyncPast },
            { Msg.Health.SyncedPast(if (random.nextBoolean()) null else random.nextInt(5)) },
            { Msg.Health.Revoked },
            { Msg.Health.RowsShown },
            { Msg.Health.Availability(pick(HealthAvailability.entries)) },
            { Msg.App.Foreground(random.nextBoolean()) },
            { Msg.App.Background },
            { Msg.Timer.Open(pick(TimerMode.entries)) },
            { Msg.Timer.Open(pick(TimerMode.entries)) },
            { Msg.Timer.OpenPreset(state.timerPresets.getOrNull(index(state.timerPresets.size))?.id ?: "gone") },
            { Msg.Timer.Edit(TimerConfig(model.timerSetup?.config?.mode ?: pick(TimerMode.entries), random.nextInt(90) - 5, random.nextInt(40), random.nextInt(5))) },
            { Msg.Timer.CloseSetup },
            { Msg.Timer.SetCalls(TimerCalls(random.nextInt(7) - 1, random.nextBoolean(), random.nextBoolean(), random.nextBoolean(), random.nextBoolean())) },
            { Msg.Timer.PlaySample },
            { Msg.Timer.SavePreset(pick(listOf("", "Finisher", "x".repeat(60)))) },
            { Msg.Timer.UpdatePreset },
            { Msg.Timer.EditName(pick(listOf("", "Burner", "y".repeat(60)))) },
            { Msg.Timer.StartPreset(state.timerPresets.getOrNull(index(state.timerPresets.size))?.id ?: "gone") },
            { Msg.Timer.DeletePreset(state.timerPresets.getOrNull(index(state.timerPresets.size))?.id ?: "gone") },
            { Msg.Timer.UndoDeletePreset },
            { Msg.Timer.Start },
            { Msg.Timer.Start },
            { Msg.Timer.Start },
            { Msg.Timer.DeleteSession(state.timerSessions.getOrNull(index(state.timerSessions.size))?.id ?: "gone") },
            { Msg.Timer.UndoDeleteSession },
            { Msg.Timer.Tick },
            { Msg.Nav.ShowHistory(pick(HistoryView.entries)) },
            { Msg.Nav.ShowTimerPage(pick(TimerPage.entries)) },
            { Msg.Wods.Filter(pick(WodFilter.entries)) },
            {
                pick(
                    listOf(
                        Msg.Wods.Search(pick(listOf("", " ", "pu", "fran", "x".repeat(80), model.wods.query))),
                        Msg.Wods.CloseSuggestions,
                        Msg.Wods.SearchMovement(pick(listOf("Pull-ups", "Thruster"))),
                        Msg.Wods.ClearSearch,
                    ),
                )
            },
            { Msg.Wods.Open(pick(wodIds)) },
            { Msg.Wods.Close },
            { Msg.Wods.Start(pick(wodIds)) },
            { Msg.Wods.Start(pick(wodIds)) },
            { Msg.Wods.Start(pick(wodIds)) },
            { Msg.Wods.New },
            { Msg.Wods.New },
            { Msg.Wods.Edit(ownId) },
            { Msg.Wods.Copy(pick(wodIds)) },
            { Msg.Wods.Delete(ownId) },
            { Msg.Wods.UndoDelete },
            { Msg.Wods.ContinueDraft },
            { Msg.Wods.DiscardDraft },
            { Msg.App.LaunchWorkout(if (random.nextBoolean()) null else routineId, random.nextInt(4) == 0, random.nextInt(4) == 0) },
        )
        return pick(choices)()
    }
}
