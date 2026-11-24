package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.core.*
import io.github.alexanderop.eisen.app.state.*
import io.github.alexanderop.eisen.app.elm.*
import io.github.alexanderop.eisen.feature.share.*
import io.github.alexanderop.eisen.feature.progress.*
import io.github.alexanderop.eisen.feature.timer.*
import io.github.alexanderop.eisen.feature.timer.boxing.*
import io.github.alexanderop.eisen.feature.timer.wod.*
import io.github.alexanderop.eisen.feature.workout.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The same cases as `apps/web/src/domain/domain.test.ts`: both apps must behave the same. */
class DomainTest {
    private val kg = WeightUnit.KG
    private val lb = WeightUnit.LB
    private val all = listOf<Int?>(5, 5, 5, 5, 5)
    private val kgPlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    private fun initialProgress(): Progress = freshState(kg).progress

    private fun routine(id: String): Routine = defaultRoutines().first { it.id == id }

    private fun start(id: String, progress: Progress = initialProgress()): Workout =
        createWorkout(routine(id), ::catalogExercise, progress, kg, 0, "w1")

    private fun workoutWith(sets: Map<String, List<Int?>>, type: String = "A"): Workout {
        val workout = start(type)
        return workout.copy(exercises = workout.exercises.map { it.copy(sets = sets[it.id] ?: it.sets) })
    }

    private fun progress(weight: Number, fails: Int, increment: Number) =
        ExerciseProgress(weight.toDouble(), fails, increment.toDouble())

    @Test fun `nextReps cycles empty to target down to 0 and back to empty`() {
        val seen = mutableListOf<Int?>()
        var value: Int? = null
        repeat(7) {
            value = nextReps(value, 5)
            seen += value
        }
        assertEquals(listOf(5, 4, 3, 2, 1, 0, null), seen)
    }

    @Test fun `adds weight after a fully completed exercise`() {
        val workout = workoutWith(mapOf("hackSquat" to all, "bench" to all, "row" to all))
        val result = applyProgression(initialProgress(), workout, 20.0)
        assertEquals(progress(85, 0, 5), result.progress["hackSquat"])
        assertEquals(82.5, result.progress.getValue("bench").weight, 0.0)
        assertEquals(62.5, result.progress.getValue("row").weight, 0.0)
        assertEquals(listOf(ChangeKind.UP, ChangeKind.UP, ChangeKind.UP), result.changes.map { it.kind })
    }

    @Test fun `adds 5kg to the deadlift`() {
        val workout = workoutWith(mapOf("hackSquat" to all, "ohp" to all, "deadlift" to all), "B")
        assertEquals(105.0, applyProgression(initialProgress(), workout, 20.0).progress.getValue("deadlift").weight, 0.0)
    }

    @Test fun `repeats the weight after missed reps and leaves skipped exercises alone`() {
        val workout = workoutWith(mapOf("hackSquat" to listOf(5, 5, 5, 4, 3)))
        val result = applyProgression(initialProgress(), workout, 20.0)
        assertEquals(Outcome.FAIL, outcome(workout.exercises[0]))
        assertEquals(progress(80, 1, 5), result.progress["hackSquat"])
        assertEquals(progress(80, 0, 2.5), result.progress["bench"])
        assertEquals(1, result.changes.size)
    }

    @Test fun `counts an unfinished exercise as a miss`() {
        val workout = workoutWith(mapOf("hackSquat" to listOf(5, 5, null, null, null)))
        assertEquals(Outcome.FAIL, outcome(workout.exercises[0]))
    }

    @Test fun `deloads 10 percent on the third miss in a row`() {
        val before = initialProgress() + ("hackSquat" to progress(100, 2, 5))
        val started = start("A", before)
        val workout = started.copy(
            exercises = started.exercises.mapIndexed { index, log -> if (index == 0) log.copy(sets = listOf(5, 4, 4, 3, 3)) else log },
        )
        val result = applyProgression(before, workout, 20.0)
        assertEquals(progress(90, 0, 5), result.progress["hackSquat"])
        assertEquals(ChangeKind.DELOAD, result.changes[0].kind)
    }

    @Test fun `loads the heaviest plates first`() {
        assertEquals(listOf(25.0, 15.0), platesPerSide(100.0, 20.0, kgPlates).plates)
        assertEquals(listOf(20.0, 1.25), platesPerSide(62.5, 20.0, kgPlates).plates)
        assertEquals(emptyList<Double>(), platesPerSide(20.0, 20.0, kgPlates).plates)
        val short = platesPerSide(21.0, 20.0, kgPlates)
        assertEquals(emptyList<Double>(), short.plates)
        assertEquals(0.5, short.remainder, 1e-9)
    }

    @Test fun `adds 3 max-reps sets of dips to A and pull-ups to B`() {
        assertEquals(
            ExerciseLog("dips", "Dips", Equipment.BODYWEIGHT, 0.0, null, listOf(null, null, null)),
            start("A").exercises.last(),
        )
        assertEquals("pullups", start("B").exercises.last().id)
    }

    @Test fun `counts any logged reps as done and never changes a weight`() {
        val workout = workoutWith(mapOf("dips" to listOf(12, 9, 7)))
        assertEquals(Outcome.SUCCESS, outcome(workout.exercises[3]))
        val result = applyProgression(initialProgress(), workout, 20.0)
        assertEquals(initialProgress(), result.progress)
        assertTrue(result.changes.isEmpty())
    }

    @Test fun `a machine has no bar and warms up in whole plates`() {
        assertEquals(0.0, emptyWeight(Equipment.MACHINE, 20.0), 0.0)
        assertEquals(20.0, emptyWeight(Equipment.BARBELL, 20.0), 0.0)
        assertEquals(listOf(25.0, 5.0), platesPerSide(60.0, 0.0, kgPlates).plates)
        assertEquals(
            listOf(WarmupSet(30.0, 5, 1), WarmupSet(50.0, 3, 1), WarmupSet(65.0, 2, 1)),
            warmupSets(80.0, 0.0, warmupStep(Equipment.MACHINE, kg)),
        )
    }

    @Test fun `warmup ramps from the bar to below the work weight`() {
        assertEquals(emptyList<WarmupSet>(), warmupSets(20.0, 20.0, 2.5))
        assertEquals(
            listOf(WarmupSet(20.0, 5, 2), WarmupSet(40.0, 5, 1), WarmupSet(60.0, 3, 1), WarmupSet(80.0, 2, 1)),
            warmupSets(100.0, 20.0, 2.5),
        )
        assertEquals(listOf(WarmupSet(20.0, 5, 2), WarmupSet(25.0, 2, 1)), warmupSets(30.0, 20.0, 2.5))
    }

    @Test fun `catalog has unique ids and an image file for every exercise`() {
        assertEquals(CATALOG.size, CATALOG.map { it.id }.toSet().size)
        val folder = java.io.File("../../web/src/assets/exercises")
        for (exercise in CATALOG) {
            val file = java.io.File(folder, "${exercise.image}.webp")
            assertTrue(exercise.id, file.exists())
        }
    }

    @Test fun `starts bars on plate steps and dumbbells on finer ones in both units`() {
        assertEquals(progress(175, 0, 5), startProgress(catalogExercise("bench")!!, lb))
        assertEquals(6.0, startProgress(catalogExercise("lateralRaise")!!, kg).weight, 0.0)
        assertEquals(progress(12.5, 0, 2.5), startProgress(catalogExercise("lateralRaise")!!, lb))
        assertEquals(progress(102.5, 1, 5), convertProgress(progress(225, 1, 10), Equipment.BARBELL, lb, kg))
    }

    private val curl = customExercise("custom-1", "Cable Curl", Equipment.WEIGHT)
    private fun find(id: String) = if (id == curl.id) curl else catalogExercise(id)
    private val own = Routine("r1", "Pull day", listOf(defaultSlot(curl), Slot("pushups", 2, 15), Slot("gone", 3, 10)))

    @Test fun `builds a workout from any exercises and skips deleted ones`() {
        val workout = createWorkout(own, ::find, emptyMap(), kg, 0, "w")
        assertEquals("Pull day", workout.name)
        assertEquals(
            listOf(
                ExerciseLog("custom-1", "Cable Curl", Equipment.WEIGHT, 10.0, 10, listOf(null, null, null)),
                ExerciseLog("pushups", "Push-ups", Equipment.BODYWEIGHT, 0.0, 15, listOf(null, null)),
            ),
            workout.exercises,
        )
    }

    @Test fun `progresses a custom exercise by its own increment and never a bodyweight one`() {
        val before = mapOf("custom-1" to progress(10, 0, 1))
        val started = createWorkout(own, ::find, before, kg, 0, "w")
        val workout = started.copy(
            exercises = listOf(started.exercises[0].copy(sets = listOf(10, 10, 10)), started.exercises[1].copy(sets = listOf(15, 12))),
        )
        val result = applyProgression(before, workout, 20.0)
        assertEquals(mapOf("custom-1" to progress(11, 0, 1)), result.progress)
        assertEquals(listOf("Cable Curl"), result.changes.map { it.name })
    }

    @Test fun `rotates through the workouts in order`() {
        val routines = listOf(routine("A"), routine("B"), own)
        assertEquals("B", nextRoutineId(routines, "A", "A"))
        assertEquals("A", nextRoutineId(routines, "r1", "A"))
        assertEquals("B", nextRoutineId(routines, "deleted", "B"))
    }

    private val v3 = """
        {"version":3,"settings":{"unit":"kg","bar":20,"restDone":90,"restMissed":300,"sound":true},
        "progress":{"hackSquat":{"weight":90,"fails":1},"bench":{"weight":82.5,"fails":0},"row":{"weight":60,"fails":0},
        "ohp":{"weight":40,"fails":0},"deadlift":{"weight":100,"fails":0}},
        "nextType":"B","active":null,
        "history":[{"id":"h1","type":"A","unit":"kg","startedAt":1,"finishedAt":2,"exercises":[
        {"id":"hackSquat","weight":85,"targetReps":5,"sets":[5,5,5,5,5]},
        {"id":"dips","weight":0,"targetReps":null,"sets":[10,8,6]}]}]}
    """.trimIndent()

    @Test fun `turns the fixed program into two editable workouts and keeps everything logged`() {
        val migrated = decodeState(v3)!!
        assertEquals(STATE_VERSION, migrated.version)
        assertEquals(defaultRoutines(), migrated.routines)
        assertEquals("B", migrated.nextRoutineId)
        assertEquals(progress(90, 1, 5), migrated.progress["hackSquat"])
        assertEquals(progress(82.5, 0, 2.5), migrated.progress["bench"])
        assertEquals("A" to "Workout A", migrated.history[0].routineId to migrated.history[0].name)
        assertEquals(
            listOf("Hack Squat" to Equipment.MACHINE, "Dips" to Equipment.BODYWEIGHT),
            migrated.history[0].exercises.map { it.name to it.equipment },
        )
        assertTrue(migrated.settings.haptics)
    }

    @Test fun `renames the version 1 squat to the hack squat`() {
        val v1 = v3.replace("\"version\":3", "\"version\":1").replace("hackSquat", "squat")
        val migrated = decodeState(v1)!!
        assertEquals(progress(90, 1, 5), migrated.progress["hackSquat"])
        assertEquals("hackSquat", migrated.history[0].exercises[0].id)
    }

    private fun v4(deadlift: Slot): String {
        val state = freshState(kg).copy(version = 4, routines = listOf(Routine("B", "Workout B", listOf(Slot("ohp", 5, 5), deadlift))))
        return encodeState(state)
    }

    @Test fun `gives a deadlift still on one set all five and leaves an edited one alone`() {
        assertEquals(listOf(Slot("ohp", 5, 5), Slot("deadlift", 5, 5)), decodeState(v4(Slot("deadlift", 1, 5)))!!.routines[0].slots)
        assertEquals(listOf(Slot("ohp", 5, 5), Slot("deadlift", 3, 3)), decodeState(v4(Slot("deadlift", 3, 3)))!!.routines[0].slots)
    }

    @Test fun `switches vibration on for an install from before it existed`() {
        val v5 = encodeState(freshState(kg).copy(version = 5)).replace(",\"haptics\":true", "")
        assertFalse(v5.contains("haptics"))
        assertEquals(freshState(kg).settings, decodeState(v5)!!.settings)
    }

    @Test fun `knows the hold was learnt, and a file from before it loads without it`() {
        val learnt = freshState(kg).copy(pickedReps = true)
        assertEquals(Loaded.Ok(learnt), loadState(encodeState(learnt)))
        val before = encodeState(freshState(kg)).replace(",\"pickedReps\":false", "")
        assertFalse(before.contains("pickedReps"))
        assertEquals(Loaded.Ok(freshState(kg)), loadState(before))
    }

    @Test fun `round-trips a current state and rejects junk`() {
        val fresh = freshState(kg)
        assertEquals(fresh, decodeState(encodeState(fresh)))
        assertEquals(fresh, decodeState(encodeState(fresh, pretty = true)))
        assertNull(decodeState("""{"version":99}"""))
        assertNull(decodeState("\"nope\""))
        assertNull(decodeState("not json"))
        assertFalse(isAppState(migrate(JsonPrimitive("nope"))))
    }

    @Test fun `says why a file is not a state`() {
        val fresh = freshState(kg)
        assertEquals(Loaded.Ok(fresh), loadState(encodeState(fresh)))
        assertEquals(Loaded.NotJson, loadState("not json"))
        assertEquals(Loaded.NotJson, loadState(""))
        assertEquals(Loaded.NotEisen, loadState("\"nope\""))
        assertEquals(Loaded.NotEisen, loadState("""{"not":"eisen"}"""))
        assertEquals(Loaded.NotEisen, loadState("""{"version":9}"""))
        assertEquals(Loaded.TooNew(99), loadState("""{"version":99}"""))
        // A state of this app with a part that cannot be read names the part.
        val damaged = encodeState(fresh).replaceFirst("\"restDone\":", "\"restGone\":")
        val broken = loadState(damaged) as Loaded.Broken
        assertTrue(broken.reason, "restDone" in broken.reason)
        assertNull(decodeState(damaged))
    }

    @Test fun `reads a backup written by the PWA`() {
        // Whole numbers without a decimal point, the way JavaScript writes them.
        val pwa = """
            {"version":6,"settings":{"unit":"kg","bar":20,"restDone":90,"restMissed":300,"sound":true,"haptics":false},
            "progress":{"bench":{"weight":82.5,"fails":0,"increment":2.5}},
            "routines":[{"id":"A","name":"Workout A","slots":[{"exerciseId":"bench","sets":5,"reps":5},{"exerciseId":"dips","sets":3,"reps":null}]}],
            "nextRoutineId":"A",
            "customExercises":[{"id":"custom-1","name":"Cable Curl","group":"Custom","equipment":"weight","start":10,"increment":1}],
            "active":null,
            "history":[{"id":"h","routineId":"A","name":"Workout A","unit":"kg","startedAt":1763575200000,"finishedAt":1763578800000,
            "exercises":[{"id":"bench","name":"Bench Press","equipment":"barbell","weight":80,"targetReps":5,"sets":[5,5,5,4,null]}]}]}
        """.trimIndent()
        val state = decodeState(pwa)!!
        assertFalse(state.settings.haptics)
        assertEquals(listOf(5, 5, 5, 4, null), state.history[0].exercises[0].sets)
        assertNull(state.customExercises[0].image)
        assertEquals(state, decodeState(encodeState(state)))
    }

    private fun logged(id: String, weight: Number, sets: List<Int?>, startedAt: Long): Workout {
        val workout = workoutWith(mapOf("hackSquat" to sets))
        return workout.copy(
            id = id,
            startedAt = startedAt,
            exercises = workout.exercises.mapIndexed { index, log -> if (index == 0) log.copy(weight = weight.toDouble()) else log },
        )
    }

    // Newest first, like the history.
    private val history = listOf(
        logged("skipped", 95, listOf(null, null, null, null, null), 5),
        logged("missed", 95, listOf(5, 5, 5, 4, 3), 4),
        logged("second", 90, all, 3),
        logged("deload", 80, all, 2),
        logged("first", 85, all, 1),
    )

    @Test fun `finds the last workout that logged the exercise`() {
        assertEquals("missed", lastLogged(history, "hackSquat")?.workout?.id)
        assertNull(lastLogged(history, "bench"))
    }

    @Test fun `counts a weight as a record only when every rep was done and it beats what came before`() {
        assertEquals(
            listOf(false, false, true, false, false),
            history.indices.map { "hackSquat" in recordIds(history, it) },
        )
        assertEquals("second", recordOf(history, "hackSquat")?.workout?.id)
    }

    @Test fun `compares records across units and counts total reps for bodyweight exercises`() {
        val inPounds = logged("lb", 200, all, 6).copy(unit = lb)
        assertTrue("hackSquat" in recordIds(listOf(inPounds) + history, 0))
        val lighter = logged("lb", 195, all, 6).copy(unit = lb)
        assertFalse("hackSquat" in recordIds(listOf(lighter) + history, 0))

        fun dips(vararg sets: Int) = workoutWith(mapOf("dips" to sets.toList()))
        assertTrue("dips" in recordIds(listOf(dips(10, 9, 8), dips(10, 8, 8)), 0))
        assertFalse("dips" in recordIds(listOf(dips(10, 8, 8), dips(10, 9, 8)), 0))
    }

    @Test fun `lists the logged exercises as markdown and leaves out skipped ones`() {
        val workout = workoutWith(mapOf("hackSquat" to all, "bench" to listOf(5, 5, 4, null, null), "dips" to listOf(12, 9, 7)))
            .copy(finishedAt = 50 * 60000L)
        assertEquals(
            listOf(
                "## Workout A", "", "Thu, Nov 19 · 50 min", "",
                "- **Hack Squat** 80kg: 5×5",
                "- **Bench Press** 80kg: 5, 5, 4, –, –",
                "- **Dips** BW: 12, 9, 7",
            ).joinToString("\n"),
            workoutMarkdown(workout, "Thu, Nov 19"),
        )
    }

    @Test fun `formats weights without trailing zeros`() {
        assertEquals("80kg", formatWeight(80.0, kg))
        assertEquals("82.5kg", formatWeight(82.5, kg))
        assertEquals("1.25kg", formatWeight(1.25, kg))
        assertEquals("100lb", formatWeight(100.0, lb))
        assertEquals("0kg", formatWeight(0.0, kg))
    }

    private val zone = ZoneId.of("Europe/Berlin")
    private fun day(year: Int, month: Int, date: Int): Long =
        LocalDateTime.of(year, month, date, 18, 0).atZone(zone).toInstant().toEpochMilli()
    private fun dateOf(time: Long): Int = java.time.Instant.ofEpochMilli(time).atZone(zone).dayOfMonth

    @Test fun `picks round axis values that cover the data`() {
        assertEquals(listOf(80.0, 85.0, 90.0, 95.0), niceTicks(80.0, 95.0))
        assertEquals(listOf(62.5, 65.0, 67.5, 70.0), niceTicks(62.5, 70.0))
        assertEquals(listOf(20.0, 25.0, 30.0, 35.0), niceTicks(23.0, 31.0))
        assertEquals(listOf(0.0, 50.0, 100.0, 150.0), niceTicks(40.0, 125.0))
        assertEquals(listOf(50.0, 100.0), niceTicks(80.0, 80.0))
    }

    @Test fun `starts the week on Monday`() {
        // 21 November 2026 is a Saturday.
        assertEquals(16, dateOf(weekStart(day(2026, 11, 21), zone)))
        assertEquals(16, dateOf(weekStart(day(2026, 11, 16), zone)))
        assertEquals(16, dateOf(weekStart(day(2026, 11, 22), zone)))
    }

    @Test fun `counts weeks in a row and lets the running week still be open`() {
        val now = day(2026, 11, 18)
        val lastThree = listOf(day(2026, 11, 13), day(2026, 11, 3), day(2026, 10, 30))
        assertEquals(3, weekStreak(lastThree, now, zone))
        assertEquals(4, weekStreak(listOf(now) + lastThree, now, zone))
        assertEquals(0, weekStreak(listOf(day(2026, 11, 3)), now, zone))
        assertEquals(0, weekStreak(emptyList(), now, zone))
    }

    @Test fun `lays the last weeks out as columns of seven days ending this week`() {
        val now = day(2026, 11, 21)
        val workout = start("A").copy(startedAt = day(2026, 11, 19))
        val weeks = calendarWeeks(listOf(workout), now, 12, zone)
        assertEquals(12, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        val thisWeek = weeks[11]
        assertEquals(listOf(16, 17, 18, 19, 20, 21, 22), thisWeek.map { dateOf(it.time) })
        assertEquals(listOf(0, 0, 0, 1, 0, 0, 0), thisWeek.map { it.workouts.size })
        assertTrue(thisWeek[5].today && !thisWeek[5].future)
        assertTrue(!thisWeek[6].today && thisWeek[6].future)
    }

    // Added weight on bodyweight exercises: the same cases as `describe('added weight on bodyweight exercises')` in the PWA.

    private val dips = catalogExercise("dips")!!
    private val dipsSlot = defaultSlot(dips)
    private fun added(weight: Number) = progress(weight, 0, 0)
    private fun AppState.withAdded(weight: Double) = editProgress("dips") { it.copy(weight = weight) }

    @Test fun `starts with no added weight when there is no progress entry`() {
        assertEquals(0.0, createLog(dips, dipsSlot, emptyMap(), kg).weight, 0.0)
        assertEquals(0.0, start("A").exercises.last().weight, 0.0)
    }

    @Test fun `starts with the added weight stored in progress`() {
        assertEquals(10.0, createLog(dips, dipsSlot, mapOf("dips" to added(10)), kg).weight, 0.0)
    }

    @Test fun `stores an added weight as an entry with no fails and no increment`() {
        assertEquals(added(10), freshState(kg).withAdded(10.0).progress["dips"])
    }

    @Test fun `removes the entry when the added weight goes back to none`() {
        assertEquals(initialProgress(), freshState(kg).withAdded(10.0).withAdded(0.0).progress)
    }

    @Test fun `steps an added weight like a dumbbell, 1kg or 2,5lb`() {
        assertEquals(1.0, stepFor(Equipment.BODYWEIGHT, kg), 0.0)
        assertEquals(2.5, stepFor(Equipment.BODYWEIGHT, lb), 0.0)
    }

    @Test fun `never raises or deloads an added weight by itself`() {
        val before = initialProgress() + ("dips" to added(10))
        val started = start("A", before)
        val workout = started.copy(exercises = started.exercises.map { if (it.id == "dips") it.copy(sets = listOf(12, 9, 7)) else it })
        val result = applyProgression(before, workout, 20.0)
        assertEquals(added(10), result.progress["dips"])
        assertTrue(result.changes.isEmpty())
    }

    @Test fun `keeps no added weight at none when switching units`() {
        assertEquals(added(0), convertProgress(added(0), Equipment.BODYWEIGHT, kg, lb))
    }

    @Test fun `converts an added weight and rounds it to the dumbbell step`() {
        assertEquals(added(22.5), convertProgress(added(10), Equipment.BODYWEIGHT, kg, lb))
        assertEquals(11.0, convertProgress(added(25), Equipment.BODYWEIGHT, lb, kg).weight, 0.0)
    }

    @Test fun `still counts total reps as the record`() {
        val log = start("A", mapOf("dips" to added(10))).exercises[3].copy(sets = listOf(12, 9, 7))
        assertEquals(28.0, recordValue(log, kg)!!, 0.0)
    }

    @Test fun `shows the load as BW, BW plus the added weight, or the weight`() {
        assertEquals("BW", loadText(true, 0.0, kg))
        assertEquals("BW + 10kg", loadText(true, 10.0, kg))
        assertEquals("BW + 22.5lb", loadText(true, 22.5, lb))
        assertEquals("80kg", loadText(false, 80.0, kg))
    }

    private fun weighted(): Workout {
        val workout = workoutWith(mapOf("dips" to listOf(12, 9, 7)))
        return workout.copy(
            finishedAt = 50 * 60000L,
            exercises = workout.exercises.map { if (it.id == "dips") it.copy(weight = 10.0) else it },
        )
    }

    @Test fun `writes the added weight into the Markdown`() {
        assertEquals("- **Dips** BW + 10kg: 12, 9, 7", workoutMarkdown(weighted(), "Thu, Nov 19").lines().last())
    }

    @Test fun `carries the added weight through a share link`() {
        val shared = toShared(weighted())
        assertEquals(listOf(SharedExercise("Dips", bodyweight = true, weight = 10.0, targetReps = null, sets = listOf(12, 9, 7), record = false)), shared.exercises)
        assertEquals(shared, sharedFromHash("#" + java.net.URI(sharedUrl("https://example.com/", shared)).rawFragment))
    }

    @Test fun `counts only the added weight of a bodyweight exercise in the volume`() {
        // The app does not know what the lifter weighs: 10kg × 28 reps, the body itself is not counted.
        assertEquals(SharedStats(volume = 280.0, sets = 3, reps = 28), sharedStats(toShared(weighted())))
    }
}
