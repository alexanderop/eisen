package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The domain rules of timed exercises (holds). The same cases as the PWA's tests of the shared contract:
 * the JSON, the share link and the texts must come out the same in both apps.
 */
class TimedTest {
    private val kg = WeightUnit.KG

    private fun log(id: String, sets: List<Int?>, target: Int? = 60, equipment: Equipment = Equipment.BODYWEIGHT, weight: Double = 0.0) =
        ExerciseLog(id, catalogExercise(id)?.name ?: id, equipment, weight, target, sets, timed = true)

    private fun workout(vararg logs: ExerciseLog, id: String = "w1") =
        Workout(id, "A", "Core", kg, 0, 12 * 60000L, logs.toList())

    @Test fun `holdText shows seconds under a minute and m-ss from a minute on`() {
        assertEquals(listOf("0s", "5s", "45s", "59s", "1:00", "1:05", "1:30", "10:00", "60:00"), listOf(0, 5, 45, 59, 60, 65, 90, 600, 3600).map(::holdText))
    }

    @Test fun `the catalog ends each group with its holds`() {
        val timed = CATALOG.filter { it.timed }
        assertEquals(
            listOf("wallSit", "gluteBridgeHold", "deadHang", "supermanHold", "farmersCarry", "plank", "sidePlank", "hollowHold", "lSit"),
            timed.map { it.id },
        )
        for (group in GROUPS) {
            val inGroup = CATALOG.filter { it.group == group }
            assertEquals(group, inGroup.sortedBy { it.timed }, inGroup)
        }
        assertEquals(Exercise("farmersCarry", "Farmer's Carry", "Arms", Equipment.WEIGHT, 20.0, 2.0, "farmers-carry", timed = true), catalogExercise("farmersCarry"))
        assertEquals(Exercise("plank", "Plank", "Core", Equipment.BODYWEIGHT, 0.0, 0.0, "plank", timed = true), catalogExercise("plank"))
        assertEquals("supermanHold", CATALOG.first { it.image == "plank" }.id)
    }

    @Test fun `a new timed exercise starts at 3 holds of a minute, a weighted one too`() {
        assertEquals(Slot("plank", 3, DEFAULT_HOLD_SECONDS), defaultSlot(catalogExercise("plank")!!))
        assertEquals(Slot("farmersCarry", 3, 60), defaultSlot(catalogExercise("farmersCarry")!!))
        assertEquals(Slot("dips", 3, null), defaultSlot(catalogExercise("dips")!!))
    }

    @Test fun `the lifter's own exercise can be timed`() {
        assertTrue(customExercise("c1", "Hang", Equipment.BODYWEIGHT, timed = true).timed)
        assertFalse(customExercise("c2", "Curl", Equipment.WEIGHT).timed)
    }

    @Test fun `a log copies timed from its exercise`() {
        val plank = createLog(catalogExercise("plank")!!, Slot("plank", 3, 45), emptyMap(), kg)
        assertEquals(ExerciseLog("plank", "Plank", Equipment.BODYWEIGHT, 0.0, 45, listOf(null, null, null), timed = true), plank)
        assertFalse(createLog(catalogExercise("dips")!!, Slot("dips", 3, null), emptyMap(), kg).timed)
    }

    @Test fun `timed is written only when true and a missing one reads as false`() {
        val fresh = freshState(kg)
        assertFalse(encodeState(fresh).contains("timed"))
        val custom = fresh.copy(
            customExercises = listOf(customExercise("c1", "Hang", Equipment.BODYWEIGHT, timed = true)),
            history = listOf(workout(log("plank", listOf(60, 45, null)))),
        )
        val json = encodeState(custom)
        assertEquals(2, Regex("\"timed\":true").findAll(json).count())
        assertEquals(custom, decodeState(json))
        assertEquals(custom.copy(customExercises = listOf(customExercise("c1", "Hang", Equipment.BODYWEIGHT))), decodeState(json.replaceFirst(",\"timed\":true", "")))
    }

    @Test fun `outcome and progression stay as they are`() {
        assertEquals(Outcome.SUCCESS, outcome(log("plank", listOf(60, 60, 60))))
        assertEquals(Outcome.FAIL, outcome(log("plank", listOf(60, 45, 60))))
        assertEquals(Outcome.SUCCESS, outcome(log("deadHang", listOf(40, null), target = null)))
    }

    @Test fun `a timed bodyweight exercise never progresses`() {
        val progress = mapOf("plank" to ExerciseProgress(10.0, 0, 0.0))
        val result = applyProgression(progress, workout(log("plank", listOf(60, 60, 60), weight = 10.0)), 20.0)
        assertEquals(progress, result.progress)
        assertTrue(result.changes.isEmpty())
    }

    @Test fun `a timed weighted exercise goes up when every hold lasted, and repeats when one was cut short`() {
        val progress = mapOf("farmersCarry" to ExerciseProgress(20.0, 0, 2.0))
        fun carry(vararg sets: Int?) = log("farmersCarry", sets.toList(), equipment = Equipment.WEIGHT, weight = 20.0)
        val up = applyProgression(progress, workout(carry(60, 60, 60)), 20.0)
        assertEquals(ExerciseProgress(22.0, 0, 2.0), up.progress["farmersCarry"])
        assertEquals(listOf(ChangeKind.UP), up.changes.map { it.kind })
        val short = applyProgression(progress, workout(carry(60, 40, 60)), 20.0)
        assertEquals(ExerciseProgress(20.0, 1, 2.0), short.progress["farmersCarry"])
    }

    @Test fun `a timed bodyweight record is the longest single hold`() {
        assertEquals(75.0, recordValue(log("plank", listOf(60, 75, null)), kg))
        assertNull(recordValue(log("plank", listOf(null, null, null)), kg))
        val history = listOf(workout(log("plank", listOf(50, 80, 40)), id = "new"), workout(log("plank", listOf(75, 75, 75)), id = "old"))
        // 165 seconds in all is less than 225, but the longest hold is the record.
        assertTrue("plank" in recordIds(history, 0))
        assertEquals("new", recordOf(history, "plank")?.workout?.id)
    }

    @Test fun `a timed weighted record is the weight, as for other weighted exercises`() {
        val carry = log("farmersCarry", listOf(60, 60, 60), equipment = Equipment.WEIGHT, weight = 24.0)
        assertEquals(24.0, recordValue(carry, kg))
        assertNull(recordValue(carry.copy(sets = listOf(60, 30, 60)), kg))
    }

    @Test fun `reps text writes holds`() {
        assertEquals("3×1:00", repsText(listOf(60, 60, 60), 60, timed = true))
        assertEquals("1:00, 45s, –", repsText(listOf(60, 45, null), 60, timed = true))
        assertEquals("1:20, 55s", repsText(listOf(80, 55), null, timed = true))
        assertEquals("5, 5, 4", repsText(listOf(5, 5, 4), 5))
    }

    @Test fun `the Markdown lists holds`() {
        val markdown = workoutMarkdown(workout(log("plank", listOf(60, 45, null))), "Thu, Nov 19")
        assertEquals("## Core\n\nThu, Nov 19 · 12 min\n\n- **Plank** BW: 1:00, 45s, –", markdown)
    }

    @Test fun `holds count as sets, never as reps or volume`() {
        val shared = SharedWorkout(
            "Core", kg, 0, 12,
            listOf(
                SharedExercise("Plank", true, 10.0, 60, listOf(60, 45, null), false, timed = true),
                SharedExercise("Bench Press", false, 80.0, 5, listOf(5, 5, 5), false),
            ),
        )
        assertEquals(SharedStats(volume = 1200.0, sets = 5, reps = 15), sharedStats(shared))
    }

    private val vector = SharedWorkout(
        name = "Core",
        unit = kg,
        startedAt = 29_000_000L * 60_000,
        minutes = 12,
        exercises = listOf(
            SharedExercise("Plank", true, 0.0, 60, listOf(60, 45, null), false, timed = true),
            SharedExercise("Bench Press", false, 80.0, 5, listOf(5, 5, 5), false),
        ),
    )

    @Test fun `a timed exercise carries a 7th element in the link, others stay at 6`() {
        val json = String(Base64.getUrlDecoder().decode(encodeShared(vector)))
        assertEquals("""[1,"Core",0,29000000,12,[["Plank",1,0,60,0,[60,45,-1],1],["Bench Press",0,80,5,0,[5,5,5]]]]""", json)
        assertEquals(vector, decodeShared(encodeShared(vector)))
    }

    @Test fun `a shared workout keeps whether its exercises are timed`() {
        val shared = toShared(workout(log("plank", listOf(60, 45, null)), ExerciseLog("bench", "Bench Press", Equipment.BARBELL, 80.0, 5, listOf(5, null))))
        assertEquals(listOf(true, false), shared.exercises.map { it.timed })
        assertEquals(shared, decodeShared(encodeShared(shared)))
    }

    @Test fun `a link allows long targets and holds only for timed exercises`() {
        fun code(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
        assertEquals(600, decodeShared(code("""[1,"A",0,1,1,[["Plank",1,0,600,0,[3600,-1],1]]]"""))?.exercises?.single()?.targetReps)
        assertNull(decodeShared(code("""[1,"A",0,1,1,[["Plank",1,0,601,0,[60],1]]]""")))
        assertNull(decodeShared(code("""[1,"A",0,1,1,[["Plank",1,0,60,0,[3601],1]]]""")))
        assertNull(decodeShared(code("""[1,"A",0,1,1,[["Squat",0,80,60,0,[5]]]]""")))
        assertNull(decodeShared(code("""[1,"A",0,1,1,[["Squat",0,80,5,0,[1000]]]]""")))
        assertEquals(false, decodeShared(code("""[1,"A",0,1,1,[["Squat",0,80,5,0,[5],0]]]"""))?.exercises?.single()?.timed)
    }
}
