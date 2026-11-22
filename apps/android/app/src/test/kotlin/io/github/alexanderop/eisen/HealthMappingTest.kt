package io.github.alexanderop.eisen

import androidx.health.connect.client.records.ExerciseSegment
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.health.SegmentFields
import io.github.alexanderop.eisen.health.segmentTypeFor
import io.github.alexanderop.eisen.health.sessionFields
import io.github.alexanderop.eisen.health.workoutNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class HealthMappingTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    // 2026-11-21 10:00 UTC, one hour long.
    private val start = 1_795_255_200_000L
    private val hour = 3_600_000L

    private fun log(id: String, name: String, weight: Double, sets: List<Int?>, target: Int? = 5, equipment: Equipment = Equipment.BARBELL) =
        ExerciseLog(id, name, equipment, weight, target, sets)

    private fun workout(vararg logs: ExerciseLog, finishedAt: Long? = start + hour) =
        Workout("w1", "A", "Workout A", WeightUnit.KG, start, finishedAt, logs.toList())

    @Test fun `times come from the workout with the zone offset of that moment`() {
        val fields = sessionFields(workout(log("squat", "Squat", 100.0, listOf(5, 5, 5, 5, 5))), berlin)!!
        assertEquals(Instant.ofEpochMilli(start), fields.start)
        assertEquals(Instant.ofEpochMilli(start + hour), fields.end)
        // November in Berlin is winter time.
        assertEquals(ZoneOffset.ofHours(1), fields.startOffset)
        assertEquals(ZoneOffset.ofHours(1), fields.endOffset)
        assertEquals("Workout A", fields.title)
    }

    @Test fun `a summer workout gets the summer offset`() {
        val july = 1_783_850_400_000L // 2026-07-12 10:00 UTC
        val fields = sessionFields(workout(log("squat", "Squat", 100.0, listOf(5))).copy(startedAt = july, finishedAt = july + hour), berlin)!!
        assertEquals(ZoneOffset.ofHours(2), fields.startOffset)
    }

    @Test fun `a running workout or one that ends before it starts gives no record`() {
        assertNull(sessionFields(workout(log("squat", "Squat", 100.0, listOf(5)), finishedAt = null), berlin))
        assertNull(sessionFields(workout(log("squat", "Squat", 100.0, listOf(5)), finishedAt = start), berlin))
        assertNull(sessionFields(workout(log("squat", "Squat", 100.0, listOf(5)), finishedAt = start - 1), berlin))
    }

    @Test fun `notes list each logged exercise with weight and sets, without Markdown`() {
        val notes = workoutNotes(
            workout(
                log("squat", "Squat", 100.0, listOf(5, 5, 5, 5, 5)),
                log("bench", "Bench Press", 62.5, listOf(5, 5, 4, null, 3)),
                log("ohp", "Overhead Press", 40.0, listOf(null, null, null)),
                log("pullups", "Pull-ups", 0.0, listOf(8, 6), target = null, equipment = Equipment.BODYWEIGHT),
            ),
        )
        assertEquals("Squat 100kg: 5×5\nBench Press 62.5kg: 5, 5, 4, –, 3\nPull-ups bodyweight: 8, 6", notes)
        assertTrue('*' !in notes && '#' !in notes)
    }

    @Test fun `pounds stay pounds in the notes`() {
        val notes = workoutNotes(workout(log("squat", "Squat", 225.0, listOf(5, 5, 5))).copy(unit = WeightUnit.LB))
        assertEquals("Squat 225lb: 3×5", notes)
    }

    @Test fun `clear matches get a segment type, everything else none`() {
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, segmentTypeFor("squat"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS, segmentTypeFor("bench"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT, segmentTypeFor("deadlift"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP, segmentTypeFor("pullups"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS, segmentTypeFor("ohp"))
        assertNull(segmentTypeFor("row"))
        assertNull(segmentTypeFor("calfRaise"))
        assertNull(segmentTypeFor("custom-1234"))
    }

    @Test fun `segments split the session in order and carry the logged reps`() {
        val fields = sessionFields(
            workout(
                log("squat", "Squat", 100.0, listOf(5, 5, 5, 5, 5)),
                // Barbell row has no Health Connect type: its slot stays empty.
                log("row", "Barbell Row", 60.0, listOf(5, 5, 5, 5, 5)),
                // Not done at all: takes no slot.
                log("ohp", "Overhead Press", 40.0, listOf(null, null)),
                log("bench", "Bench Press", 80.0, listOf(5, 5, 4, null, 3)),
            ),
            berlin,
        )!!
        val third = hour / 3
        assertEquals(
            listOf(
                SegmentFields(Instant.ofEpochMilli(start), Instant.ofEpochMilli(start + third), ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, 25),
                SegmentFields(
                    Instant.ofEpochMilli(start + 2 * third),
                    Instant.ofEpochMilli(start + 3 * third),
                    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
                    17,
                ),
            ),
            fields.segments,
        )
    }

    @Test fun `segments never leave the session and never overlap`() {
        val logs = (1..7).map { log("squat", "Squat", 100.0, listOf(5)) }.toTypedArray()
        val fields = sessionFields(workout(*logs, finishedAt = start + 1_000_003), berlin)!!
        assertEquals(7, fields.segments.size)
        assertTrue(fields.segments.first().start >= fields.start)
        assertTrue(fields.segments.last().end <= fields.end)
        fields.segments.zipWithNext { a, b -> assertTrue(a.end <= b.start) }
        fields.segments.forEach { assertTrue(it.start < it.end) }
    }

    @Test fun `a session too short to split gets no segments`() {
        val fields = sessionFields(workout(log("squat", "Squat", 100.0, listOf(5)), log("bench", "Bench Press", 80.0, listOf(5)), finishedAt = start + 1), berlin)!!
        assertEquals(emptyList<SegmentFields>(), fields.segments)
    }

    @Test fun `a workout with nothing logged has empty notes and no segments`() {
        val fields = sessionFields(workout(log("squat", "Squat", 100.0, listOf(null, null))), berlin)!!
        assertEquals("", fields.notes)
        assertEquals(emptyList<SegmentFields>(), fields.segments)
    }
}
