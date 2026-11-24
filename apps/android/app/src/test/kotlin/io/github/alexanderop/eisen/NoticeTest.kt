package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.MAX_NOTICE_SEGMENTS
import io.github.alexanderop.eisen.app.elm.NoticeProgress
import io.github.alexanderop.eisen.app.elm.NoticeSegment
import io.github.alexanderop.eisen.app.elm.NoticeTone.REST
import io.github.alexanderop.eisen.app.elm.NoticeTone.WORK
import io.github.alexanderop.eisen.app.elm.RestState
import io.github.alexanderop.eisen.app.elm.noticeChip
import io.github.alexanderop.eisen.app.elm.restCountdown
import io.github.alexanderop.eisen.app.elm.timerNotice
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerRun
import io.github.alexanderop.eisen.feature.timer.timerSeconds
import io.github.alexanderop.eisen.feature.timer.wod.WOD_LIBRARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Live Update of a timer and of a rest shows: the status bar chip and the segmented bar. */
class NoticeTest {
    private val short = TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 3)
    private fun run(config: TimerConfig = short) = TimerRun("t1", "Tabata", config, DEFAULT_TIMER_CALLS, startedAt = STARTED_AT)

    /** [STARTED_AT] plus [seconds] of the run: the lead-in is the first ten. */
    private fun at(seconds: Int) = STARTED_AT + seconds * 1000L

    @Test fun `a Tabata is a segment per work and rest, work lime and rest blue`() {
        val bar = timerNotice(run(), at(15), freshState()).progress
        assertEquals(
            listOf(NoticeSegment(20, WORK), NoticeSegment(10, REST), NoticeSegment(20, WORK), NoticeSegment(10, REST), NoticeSegment(20, WORK)),
            bar?.segments,
        )
    }

    @Test fun `the tracker stands at the start of the block the run is in`() {
        assertEquals(0, timerNotice(run(), at(3), freshState()).progress?.at)   // lead-in
        assertEquals(0, timerNotice(run(), at(25), freshState()).progress?.at)  // work of round 1
        assertEquals(20, timerNotice(run(), at(35), freshState()).progress?.at) // rest after it
        assertEquals(30, timerNotice(run(), at(45), freshState()).progress?.at) // work of round 2
        assertEquals(80, timerNotice(run(), at(95), freshState()).progress?.at) // done
    }

    @Test fun `the chip is the countdown while the clock runs and says Paused when it does not`() {
        assertNull(timerNotice(run(), at(3), freshState()).chip)
        assertNull(timerNotice(run(), at(25), freshState()).chip)
        assertNull(timerNotice(run(), at(35), freshState()).chip)
        val paused = run().copy(pausedAt = at(25))
        assertEquals("Paused", timerNotice(paused, at(60), freshState()).chip)
        // Paused there is no countdown to show: the chronometer stops.
        assertNull(timerNotice(paused, at(60), freshState()).base)
    }

    @Test fun `an open For Time of a WOD in rounds names the round in the chip, a plain one leaves it to the clock`() {
        val helen = WOD_LIBRARY.first { it.id == "wod-helen" }.let { it.copy(timer = it.timer.copy(work = 0)) }
        val wodRun = run(helen.timer).copy(wod = helen)
        assertEquals("R1/3", noticeChip(wodRun, paused = false, countDown = false))
        assertEquals("Paused", noticeChip(wodRun, paused = true, countDown = false))
        assertNull(noticeChip(run(TimerConfig(TimerMode.FOR_TIME, 0, 0, 1)), paused = false, countDown = false))
        // An open For Time has no end, so no bar.
        assertNull(timerNotice(run(TimerConfig(TimerMode.FOR_TIME, 0, 0, 1)), at(30), freshState()).progress)
    }

    @Test fun `a boxing run is a segment per round and rest`() {
        val boxing = DEFAULT_TIMERS.getValue(TimerMode.BOXING)
        val bar = timerNotice(run(boxing), at(15), freshState()).progress!!
        assertEquals(11, bar.segments.size)
        assertEquals(List(6) { NoticeSegment(180, WORK) }, bar.segments.filter { it.tone == WORK })
        assertEquals(List(5) { NoticeSegment(60, REST) }, bar.segments.filter { it.tone == REST })
    }

    @Test fun `more blocks than Android draws become a segment per round, and the bar still covers the whole timer`() {
        val long = TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 20)
        val bar = timerNotice(run(long), at(15), freshState()).progress!!
        assertTrue(bar.segments.size <= MAX_NOTICE_SEGMENTS)
        assertEquals(timerSeconds(long), bar.segments.sumOf { it.seconds })
        val emom = TimerConfig(TimerMode.EMOM, work = 60, rest = 0, rounds = 40)
        val many = timerNotice(run(emom), at(15), freshState()).progress!!
        assertTrue(many.segments.size <= MAX_NOTICE_SEGMENTS)
        assertEquals(timerSeconds(emom), many.segments.sumOf { it.seconds })
    }

    /**
     * Android throttles an app that posts too often. A tick sends the notification only when it
     * changed, so it may change at a new block and once a minute for the bar, not with the seconds.
     */
    @Test fun `within a block and a minute the notification stays the same, so it is not posted again`() {
        assertEquals(timerNotice(run(), at(21), freshState()), timerNotice(run(), at(29), freshState()))
        val amrap = run(TimerConfig(TimerMode.AMRAP, work = 600, rest = 0, rounds = 1))
        val first = timerNotice(amrap, at(70), freshState())
        val second = timerNotice(amrap, at(75), freshState())
        assertEquals(first, second)
        // A minute into the work the bar moves on: one post a minute.
        assertEquals(60, timerNotice(amrap, at(75), freshState()).progress?.at)
        assertEquals(0, timerNotice(amrap, at(65), freshState()).progress?.at)
    }

    @Test fun `the rest between sets is one segment with the tracker at the time gone`() {
        val rest = RestState(LATER, LATER + 90_000, "All reps done.")
        assertEquals(NoticeProgress(listOf(NoticeSegment(90, REST)), 0), restCountdown(rest, LATER).progress)
        assertEquals(NoticeProgress(listOf(NoticeSegment(90, REST)), 30), restCountdown(rest, LATER + 30_000).progress)
        // Extended past its first end it is one longer segment.
        val extended = rest.copy(endsAt = LATER + 120_000)
        assertEquals(NoticeProgress(listOf(NoticeSegment(120, REST)), 95), restCountdown(extended, LATER + 95_000).progress)
    }
}
