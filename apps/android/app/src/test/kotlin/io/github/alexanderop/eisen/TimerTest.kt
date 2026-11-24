package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.OUTCOME_LABELS
import io.github.alexanderop.eisen.feature.timer.TimerOutcome
import io.github.alexanderop.eisen.feature.timer.timerOutcome
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.feature.timer.TimerCalls
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerCue
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerPhase
import io.github.alexanderop.eisen.feature.timer.TimerRun
import io.github.alexanderop.eisen.feature.timer.TimerView
import io.github.alexanderop.eisen.feature.timer.activeTime
import io.github.alexanderop.eisen.feature.timer.sessionSummary
import io.github.alexanderop.eisen.feature.timer.timerConfig
import io.github.alexanderop.eisen.feature.timer.timerCues
import io.github.alexanderop.eisen.feature.timer.timerSeconds
import io.github.alexanderop.eisen.feature.timer.timerSession
import io.github.alexanderop.eisen.feature.timer.timerSummary
import io.github.alexanderop.eisen.feature.timer.timerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same inputs and expected values as the PWA's `timer.test.ts`: the cue schedules are the contract between the apps. */
class TimerTest {
    private fun tabata(rounds: Int) = TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = rounds)
    private val quiet = TimerCalls(minutes = 0, halfway = false, final = false, phases = false, beeps = false)
    private val open = TimerConfig(TimerMode.FOR_TIME, work = 0, rest = 0, rounds = 1)
    private fun defaults(mode: TimerMode) = DEFAULT_TIMERS.getValue(mode)

    /** "10.7:round-1 30:beep-long": seconds after Start and the sound. */
    private fun schedule(config: TimerConfig, calls: TimerCalls = DEFAULT_TIMER_CALLS): String =
        timerCues(config, calls).joinToString(" ") { "${seconds(it.at)}:${it.sound}" }

    /** Milliseconds as JavaScript prints `ms / 1000`: "10", "10.7". */
    private fun seconds(millis: Long): String = if (millis % 1000 == 0L) "${millis / 1000}" else "${millis / 1000.0}"

    @Test fun `knows how long each mode runs`() {
        assertEquals(230, timerSeconds(tabata(8)))
        assertEquals(600, timerSeconds(defaults(TimerMode.EMOM)))
        assertEquals(600, timerSeconds(defaults(TimerMode.AMRAP)))
        assertEquals(1200, timerSeconds(defaults(TimerMode.FOR_TIME)))
        assertNull(timerSeconds(open))
    }

    @Test fun `describes a timer in one line`() {
        assertEquals("8 × 20s / 10s", timerSummary(tabata(8)))
        assertEquals("10 × 1:00", timerSummary(defaults(TimerMode.EMOM)))
        assertEquals("10:00", timerSummary(defaults(TimerMode.AMRAP)))
        assertEquals("Cap 20:00", timerSummary(defaults(TimerMode.FOR_TIME)))
        assertEquals("No cap", timerSummary(open))
    }

    @Test fun `brings a config into the limits of its mode`() {
        assertEquals(TimerConfig(TimerMode.TABATA, 5, 5, 60), timerConfig(TimerConfig(TimerMode.TABATA, work = 1, rest = 0, rounds = 99)))
        assertEquals(TimerConfig(TimerMode.EMOM, 3600, 0, 1), timerConfig(TimerConfig(TimerMode.EMOM, work = 9999, rest = 30, rounds = 0)))
        assertEquals(TimerConfig(TimerMode.AMRAP, 90, 0, 1), timerConfig(TimerMode.AMRAP, work = 90.4, rest = 5.0, rounds = 7.0))
        assertEquals(0, timerConfig(TimerConfig(TimerMode.FOR_TIME, work = -3, rest = 5, rounds = 7)).work)
        assertEquals(5, timerConfig(TimerConfig(TimerMode.FOR_TIME, work = 2, rest = 5, rounds = 7)).work)
    }

    @Test fun `walks through the lead-in, work, rest and the end`() {
        val config = tabata(3)
        timerView(config, 4000).let {
            assertEquals(TimerPhase.READY, it.phase)
            assertEquals(6000L, it.phaseLeft)
            assertEquals(0L, it.elapsed)
            assertEquals(80000L, it.totalLeft)
        }
        timerView(config, 10000).let {
            assertEquals(TimerPhase.WORK, it.phase)
            assertEquals(1, it.round)
            assertEquals(0, it.roundsDone)
            assertEquals(20000L, it.phaseLeft)
        }
        assertEquals(
            TimerView(
                phase = TimerPhase.REST,
                round = 1,
                rounds = 3,
                roundsDone = 1,
                phaseLeft = 5000,
                phaseElapsed = 5000,
                phaseTotal = 10000,
                elapsed = 25000,
                totalLeft = 55000,
                progress = 0.5,
            ),
            timerView(config, 35000),
        )
        timerView(config, 75000).let {
            assertEquals(TimerPhase.WORK, it.phase)
            assertEquals(3, it.round)
            assertEquals(2, it.roundsDone)
            assertEquals(15000L, it.totalLeft)
        }
        timerView(config, 90000).let {
            assertEquals(TimerPhase.DONE, it.phase)
            assertEquals(3, it.roundsDone)
            assertEquals(0L, it.totalLeft)
            assertEquals(80000L, it.elapsed)
        }
    }

    @Test fun `counts an open For Time up with nothing to count down to`() {
        timerView(open, 70000).let {
            assertEquals(TimerPhase.WORK, it.phase)
            assertNull(it.rounds)
            assertNull(it.phaseLeft)
            assertNull(it.totalLeft)
            assertEquals(60000L, it.elapsed)
            assertEquals(0.0, it.progress, 0.0)
        }
        assertEquals(TimerPhase.DONE, timerView(open, 10000L + 3600000).phase)
    }

    @Test fun `beeps into every change of phase and names the rounds`() {
        // Rounds of 20 seconds are too short for any call about the time left.
        assertEquals(
            "0:get-ready 7:beep 8:beep 9:beep 10:beep-long 10.7:round-1 " +
                "27:beep 28:beep 29:beep 30:beep-long 30.7:rest " +
                "37:beep 38:beep 39:beep 40:beep-long 40.7:round-2 " +
                "57:beep 58:beep 59:beep 60:beep-long 60.7:rest " +
                "67:beep 68:beep 69:beep 70:beep-long 70.7:last-round " +
                "87:beep 88:beep 89:beep 90:beep-long 90.7:complete",
            schedule(tabata(3)),
        )
    }

    @Test fun `says the time left in the round, never in the whole timer`() {
        // Two rounds of three minutes: every round counts down by itself, and nothing is said about the eight minutes in all.
        assertEquals(
            "0:get-ready 10.7:round-1 70:minutes-left-2 100:halfway 130:minutes-left-1 160:thirty-seconds 180:ten-seconds 190.7:rest " +
                "250.7:last-round 310:minutes-left-2 340:halfway 370:minutes-left-1 400:thirty-seconds 420:ten-seconds 430.7:complete",
            schedule(TimerConfig(TimerMode.TABATA, work = 180, rest = 60, rounds = 2), DEFAULT_TIMER_CALLS.copy(beeps = false)),
        )
        // A round under two minutes has no halfway, one under 90 seconds no thirty seconds, one under 30 no ten.
        assertEquals(
            "25:minutes-left-1 75:ten-seconds 115:minutes-left-1 165:ten-seconds",
            schedule(TimerConfig(TimerMode.TABATA, work = 75, rest = 15, rounds = 2), quiet.copy(halfway = true, final = true, minutes = 1)),
        )
        assertEquals("", schedule(tabata(8), quiet.copy(halfway = true, final = true, minutes = 1)))
    }

    @Test fun `lets a time call wait for the line about the phase`() {
        // One minute left comes two seconds into a round of 62: the round is named first.
        assertEquals(
            "0:get-ready 10.7:round-1 13.2:minutes-left-1 62:ten-seconds 72.7:rest 82.7:last-round 85.2:minutes-left-1 134:ten-seconds 144.7:complete",
            schedule(TimerConfig(TimerMode.TABATA, work = 62, rest = 10, rounds = 2), DEFAULT_TIMER_CALLS.copy(beeps = false)),
        )
    }

    @Test fun `leaves out a time call that lands on a round of an EMOM`() {
        // The same call in an EMOM is left out: the round number says where the lifter is.
        assertEquals(
            "0:get-ready 10.7:round-1 62:ten-seconds 72.7:round-2 124:ten-seconds 134.7:last-round 186:ten-seconds 196.7:complete",
            schedule(TimerConfig(TimerMode.EMOM, work = 62, rest = 0, rounds = 3), DEFAULT_TIMER_CALLS.copy(beeps = false)),
        )
    }

    @Test fun `calls the minutes left as often as asked`() {
        val amrap = TimerConfig(TimerMode.AMRAP, work = 600, rest = 0, rounds = 1)
        fun only(minutes: Int) = schedule(amrap, quiet.copy(minutes = minutes))
        assertEquals("", only(0))
        assertEquals("310:minutes-left-5", only(5))
        assertEquals("130:minutes-left-8 250:minutes-left-6 370:minutes-left-4 490:minutes-left-2", only(2))
        assertEquals(9, only(1).split(" ").size)
        assertEquals("310:halfway 580:thirty-seconds 600:ten-seconds", schedule(amrap, quiet.copy(halfway = true, final = true)))
        // Halfway and 5 minutes left are the same moment: halfway is said.
        assertEquals("310:halfway", schedule(amrap, quiet.copy(halfway = true, minutes = 5)))
    }

    @Test fun `counts the minutes up in a For Time without a cap`() {
        val cues = timerCues(open, quiet.copy(minutes = 5, halfway = true, final = true))
        assertEquals(TimerCue(310000, "minutes-5"), cues.first())
        assertEquals(TimerCue(3310000, "minutes-55"), cues.last())
        assertEquals("0:get-ready 7:beep 8:beep 9:beep 10:beep-long 10.7:go", schedule(open, quiet.copy(phases = true, beeps = true)))
    }

    @Test fun `says Go where there is one block, and keeps short timers quiet`() {
        assertEquals(
            "0:get-ready 10.7:go 30.7:complete",
            schedule(TimerConfig(TimerMode.AMRAP, work = 20, rest = 0, rounds = 1), DEFAULT_TIMER_CALLS.copy(beeps = false)),
        )
    }

    private fun run(config: TimerConfig, pausedAt: Long? = null, paused: Long = 0, taps: Int = 0) =
        TimerRun(id = "t1", name = "Tabata", config = config, calls = DEFAULT_TIMER_CALLS, startedAt = 1000, pausedAt = pausedAt, paused = paused, taps = taps)

    @Test fun `does not count pauses`() {
        assertEquals(30000L, activeTime(run(tabata(3)), 31000))
        assertEquals(25000L, activeTime(run(tabata(3), paused = 5000), 31000))
        assertEquals(15000L, activeTime(run(tabata(3), pausedAt = 21000, paused = 5000), 99000))
    }

    @Test fun `turns a run into a history entry`() {
        // Stopped in the second rest: two rounds done, 55 seconds in.
        val stopped = timerSession(run(tabata(3)), 1000L + 65000, false)!!
        assertEquals(Triple(55, 2, false), Triple(stopped.seconds, stopped.rounds, stopped.completed))
        assertEquals(66000L, stopped.finishedAt)
        assertEquals("2 rounds · 55s", sessionSummary(stopped))

        val done = timerSession(run(tabata(3)), 1000L + 200000, true)!!
        assertEquals(Triple(80, 3, true), Triple(done.seconds, done.rounds, done.completed))

        val forTime = timerSession(run(defaults(TimerMode.FOR_TIME), taps = 4), 1000L + 10000 + 372000, true)!!
        assertEquals("4 rounds · 6:12", sessionSummary(forTime))
        val noTaps = timerSession(run(defaults(TimerMode.AMRAP)), 1000L + 10000 + 600000, true)!!
        assertEquals("10:00", sessionSummary(noTaps))

        assertNull(timerSession(run(tabata(3)), 1000L + 9000, false))
    }

    @Test fun `tells a For Time that hit its cap from one that was finished`() {
        val capped = timerSession(run(defaults(TimerMode.FOR_TIME)), 1000L + 10000 + 1200000, true)!!
        assertEquals(TimerOutcome.CAPPED, timerOutcome(capped))
        val finished = timerSession(run(defaults(TimerMode.FOR_TIME)), 1000L + 10000 + 372000, true)!!
        assertEquals(TimerOutcome.COMPLETED, timerOutcome(finished))
        assertEquals(TimerOutcome.STOPPED, timerOutcome(timerSession(run(tabata(3)), 1000L + 65000, false)!!))
        assertEquals(TimerOutcome.COMPLETED, timerOutcome(timerSession(run(tabata(3)), 1000L + 200000, true)!!))
        assertEquals(TimerOutcome.COMPLETED, timerOutcome(timerSession(run(open), 1000L + 10000 + 372000, true)!!))
        assertEquals("Time cap", OUTCOME_LABELS[TimerOutcome.CAPPED])
    }
}
