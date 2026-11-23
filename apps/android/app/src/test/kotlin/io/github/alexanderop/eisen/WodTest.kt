package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.CATALOG
import io.github.alexanderop.eisen.domain.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.domain.StateJson
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.TimerRun
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.WOD_LIBRARY
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Wod
import io.github.alexanderop.eisen.domain.WodItem
import io.github.alexanderop.eisen.domain.WodUnit
import io.github.alexanderop.eisen.domain.TimerOutcome
import io.github.alexanderop.eisen.domain.sameWork
import io.github.alexanderop.eisen.domain.timerOutcome
import io.github.alexanderop.eisen.domain.wodDone
import io.github.alexanderop.eisen.domain.wodName
import io.github.alexanderop.eisen.domain.wodStep
import io.github.alexanderop.eisen.domain.wodSteps
import io.github.alexanderop.eisen.domain.bestText
import io.github.alexanderop.eisen.domain.catalogExercise
import io.github.alexanderop.eisen.domain.findWod
import io.github.alexanderop.eisen.domain.isBodyweightWod
import io.github.alexanderop.eisen.domain.isLibraryWod
import io.github.alexanderop.eisen.domain.isWodRecord
import io.github.alexanderop.eisen.domain.itemAmount
import io.github.alexanderop.eisen.domain.loadFrom
import io.github.alexanderop.eisen.domain.loadIn
import io.github.alexanderop.eisen.domain.roundLines
import io.github.alexanderop.eisen.domain.timerSession
import io.github.alexanderop.eisen.domain.wod
import io.github.alexanderop.eisen.domain.wodBest
import io.github.alexanderop.eisen.domain.wodFormat
import io.github.alexanderop.eisen.domain.wodImages
import io.github.alexanderop.eisen.domain.wodLines
import io.github.alexanderop.eisen.domain.wodRound
import io.github.alexanderop.eisen.domain.wodRounds
import io.github.alexanderop.eisen.domain.gapText
import io.github.alexanderop.eisen.domain.liveGap
import io.github.alexanderop.eisen.domain.roundsGap
import io.github.alexanderop.eisen.domain.roundsGapText
import io.github.alexanderop.eisen.domain.splitTime
import io.github.alexanderop.eisen.domain.isRest
import io.github.alexanderop.eisen.domain.wodRest
import io.github.alexanderop.eisen.domain.WodRest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same inputs and expected values as the PWA's `wod.test.ts`. */
class WodTest {
    private fun lib(id: String): Wod = WOD_LIBRARY.first { it.id == id }
    private val find = ::catalogExercise
    private val kg = WeightUnit.KG
    private val lb = WeightUnit.LB

    @Test fun `ships twenty-one benchmark workouts whose exercises all exist`() {
        assertEquals(
            listOf(
                "Fran", "Cindy", "Helen", "Grace", "Annie", "Karen", "Angie", "Barbara", "Chelsea", "Mary", "Jackie", "Diane", "Isabel", "DT", "Murph",
                "Venus", "Aphrodite", "Helios", "Zeus", "Ares", "Gaia",
            ),
            WOD_LIBRARY.map { it.name },
        )
        assertEquals(21, WOD_LIBRARY.map { it.id }.toSet().size)
        for (entry in WOD_LIBRARY) {
            assertEquals(entry.name, wod(entry), entry)
            for (item in entry.items) assertNotNull(item.exerciseId, find(item.exerciseId))
        }
    }

    @Test fun `adds the movements the workouts need to the catalog`() {
        val ids = CATALOG.map { it.id }
        for (id in listOf(
            "airSquat", "pistolSquat", "cleanAndJerk", "snatch", "hangPowerClean", "kettlebellSwing", "thruster", "pushJerk",
            "handstandPushup", "toesToBar", "burpee", "boxJump", "doubleUnder", "wallBall", "run", "rowErg",
            "bodyweightLunge", "climber", "jumpingJack", "jump", "standup", "sprint", "rest",
        )) {
            assertTrue(id, id in ids)
        }
        assertEquals(
            listOf("Burpee", "Box Jump", "Double-Under", "Wall Ball", "Run", "Row", "Climbers", "Jumping Jacks", "Jumps", "Standups", "Sprint", "Rest"),
            CATALOG.filter { it.group == "Conditioning" }.map { it.name },
        )
    }

    @Test fun `says what kind of workout it is`() {
        assertEquals("21-15-9 · For Time", wodFormat(lib("wod-fran")))
        assertEquals("3 rounds · For Time", wodFormat(lib("wod-helen")))
        assertEquals("For Time", wodFormat(lib("wod-grace")))
        assertEquals("4 rounds · For Time", wodFormat(lib("wod-venus")))
        assertEquals("50-40-30-20-10 · For Time", wodFormat(lib("wod-aphrodite")))
        assertEquals("2:00 Rest", roundLines(lib("wod-zeus"), 1, find, kg).last())
        assertEquals(listOf("7 × Pull-ups", "7 × Sit-ups", "40 m Sprint", "40 m Sprint", "1:00 Rest"), roundLines(lib("wod-ares"), 2, find, kg))
        assertEquals(7, wodLines(lib("wod-helios"), find, kg).size)
        assertEquals(listOf("50 × Push-ups", "20 × Sit-ups", "50 × Air Squat"), wodLines(lib("wod-venus"), find, kg))
        assertEquals("AMRAP 20:00", wodFormat(lib("wod-cindy")))
        assertEquals("EMOM 30 × 1:00", wodFormat(lib("wod-chelsea")))
        assertEquals("Tabata 8 × 20s / 10s", wodFormat(wod(lib("wod-cindy").copy(timer = TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 8)))))
    }

    @Test fun `lists what to do, in the unit the app shows`() {
        assertEquals(listOf("Thruster 43kg", "Pull-ups"), wodLines(lib("wod-fran"), find, kg))
        assertEquals(listOf("Thruster 95lb", "Pull-ups"), wodLines(lib("wod-fran"), find, lb))
        assertEquals(listOf("400 m Run", "21 × Kettlebell Swing 24kg", "12 × Pull-ups"), wodLines(lib("wod-helen"), find, kg))
        assertEquals(listOf("1.6 km Run", "100 × Pull-ups", "200 × Push-ups", "300 × Air Squat", "1.6 km Run"), wodLines(lib("wod-murph"), find, kg))
        assertEquals(listOf("30 × Clean and Jerk 135lb"), wodLines(lib("wod-grace"), find, lb))
    }

    @Test fun `gives every round of a scheme its own reps`() {
        val fran = lib("wod-fran")
        assertEquals(listOf("21 × Thruster 43kg", "21 × Pull-ups"), roundLines(fran, 1, find, kg))
        assertEquals(listOf("15 × Thruster 43kg", "15 × Pull-ups"), roundLines(fran, 2, find, kg))
        assertEquals(listOf("9 × Thruster 43kg", "9 × Pull-ups"), roundLines(fran, 3, find, kg))
        // Past the last round it stays on the last one.
        assertEquals(9, itemAmount(fran, fran.items[0], 9))
        // Metres are never replaced by the scheme.
        val mixed = wod(fran.copy(items = fran.items + WodItem("run", 200, WodUnit.METERS, 0.0)))
        assertEquals("200 m Run", roundLines(mixed, 2, find, kg)[2])
    }

    @Test fun `takes the items of a Tabata in turns and shows a missing amount as max`() {
        val turns = wod(
            Wod(
                id = "w",
                name = "Turns",
                timer = TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 8),
                rounds = 3,
                scheme = listOf(21, 15, 9),
                items = listOf(WodItem("pushups", 0, WodUnit.REPS, 0.0), WodItem("plank", 20, WodUnit.SECONDS, 0.0)),
            ),
        )
        // Rounds and scheme belong to For Time only.
        assertEquals(1, turns.rounds)
        assertEquals(emptyList<Int>(), turns.scheme)
        assertEquals(listOf("Max Push-ups"), roundLines(turns, 1, find, kg))
        assertEquals(listOf("20s Plank"), roundLines(turns, 2, find, kg))
        assertEquals(listOf("Max Push-ups"), roundLines(turns, 3, find, kg))
    }

    @Test fun `brings a draft into its limits`() {
        val draft = wod(
            Wod(
                id = "w",
                name = "   ",
                timer = TimerConfig(TimerMode.FOR_TIME, work = 0, rest = 9, rounds = 9),
                rounds = 99,
                scheme = emptyList(),
                items = listOf(WodItem("thruster", -4, WodUnit.REPS, 43.26)),
            ),
        )
        assertEquals("", draft.name)
        assertEquals("60 rounds · For Time", wodName(draft))
        assertEquals("Fran", wodName(lib("wod-fran")))
        assertEquals(60, draft.rounds)
        assertEquals(TimerConfig(TimerMode.FOR_TIME, work = 0, rest = 0, rounds = 1), draft.timer)
        assertEquals(WodItem("thruster", 0, WodUnit.REPS, 43.5), draft.items[0])
        // A scheme sets the number of rounds.
        val schemed = wod(draft.copy(scheme = listOf(5, 4, 0)))
        assertEquals(3, schemed.rounds)
        assertEquals(listOf(5, 4, 1), schemed.scheme)
    }

    @Test fun `counts rounds by taps where the lifter counts and by the clock elsewhere`() {
        val fran = lib("wod-fran")
        assertEquals(3, wodRounds(fran))
        assertNull(wodRounds(lib("wod-cindy")))
        assertEquals(30, wodRounds(lib("wod-chelsea")))
        assertEquals(1, wodRound(fran, 0, 1))
        // Two items a round: the second tap finishes round one.
        assertEquals(1, wodRound(fran, 1, 1))
        assertEquals(2, wodRound(fran, 2, 1))
        assertEquals(3, wodRound(fran, 5, 1))
        assertEquals(3, wodRound(fran, 9, 1))
        assertFalse(wodDone(fran, 5))
        assertTrue(wodDone(fran, 6))
        assertEquals(8, wodRound(lib("wod-cindy"), 7, 1))
        assertFalse(wodDone(lib("wod-cindy"), 99))
        assertEquals(12, wodRound(lib("wod-chelsea"), 7, 12))
    }

    @Test fun `ticks the items of a single round off one by one`() {
        val murph = lib("wod-murph")
        assertEquals(5, wodSteps(murph))
        assertEquals(1, wodSteps(lib("wod-grace")))
        assertEquals(6, wodSteps(lib("wod-fran")))
        assertEquals(12, wodSteps(lib("wod-venus")))
        // Venus: three items a round. The fifth tap is the second item of round two.
        assertEquals(1, wodStep(lib("wod-venus"), 4))
        assertEquals(2, wodRound(lib("wod-venus"), 4, 1))
        assertEquals(2, wodStep(lib("wod-venus"), 12))
        assertNull(wodSteps(lib("wod-cindy")))
        assertEquals(0, wodStep(murph, 0))
        assertEquals(3, wodStep(murph, 3))
        assertEquals(4, wodStep(murph, 9))
        assertFalse(wodDone(murph, 4))
        assertTrue(wodDone(murph, 5))
        assertEquals(1, wodRound(murph, 3, 1))
    }

    @Test fun `lets the clock count a rest instead of asking for a tap`() {
        val zeus = lib("wod-zeus")
        // Five movements and a rest a round: five taps, four rounds.
        assertEquals(20, wodSteps(zeus))
        assertTrue(isRest(zeus.items[5]))
        assertEquals(4, wodStep(zeus, 4))
        // The fifth tap ends round one: on to the first movement of round two, past the rest.
        assertEquals(0, wodStep(zeus, 5))
        assertEquals(2, wodRound(zeus, 5, 1))
        assertFalse(wodDone(zeus, 19))
        assertTrue(wodDone(zeus, 20))

        val five = listOf(60.0, 120.0, 180.0, 240.0, 300.0)
        // No rest in the middle of a round.
        assertNull(wodRest(zeus, five.take(3), 185.0))
        // The fifth tap starts the two minutes by itself.
        assertEquals(WodRest(120, 120), wodRest(zeus, five, 300.0))
        assertEquals(WodRest(59, 120), wodRest(zeus, five, 361.5))
        assertNull(wodRest(zeus, five, 420.0))
        // After the very last tap there is nothing to rest for.
        val all = List(20) { (it + 1) * 60.0 }
        assertNull(wodRest(zeus, all, 1200.0))
        assertNull(wodRest(lib("wod-fran"), listOf(40.0, 75.0), 76.0))
        assertNull(wodRest(lib("wod-cindy"), listOf(60.0), 61.0))

        // Ares: two sprints, then a minute.
        val ares = lib("wod-ares")
        assertEquals(20, wodSteps(ares))
        assertEquals(WodRest(50, 60), wodRest(ares, listOf(20.0, 40.0, 50.0, 60.0), 70.0))
        timerSession(run(zeus, 20), 10_000L + 1_300_000L, true)!!.let { assertEquals(true to 4, it.completed to it.rounds) }
        timerSession(run(zeus, 7), 10_000L + 500_000L, false)!!.let { assertEquals(false to 1, it.completed to it.rounds) }
    }

    @Test fun `knows when an edit changed the work and not only the name or the cap`() {
        val fran = lib("wod-fran")
        assertTrue(sameWork(fran, fran.copy(name = "Other")))
        assertTrue(sameWork(fran, fran.copy(timer = fran.timer.copy(work = 300))))
        assertFalse(sameWork(fran, wod(fran.copy(scheme = listOf(15, 12, 9)))))
        assertFalse(sameWork(fran, fran.copy(items = listOf(fran.items[0].copy(load = 30.0), fran.items[1]))))
        val cindy = lib("wod-cindy")
        assertFalse(sameWork(cindy, cindy.copy(timer = cindy.timer.copy(work = 600))))
    }

    @Test fun `converts weights both ways`() {
        assertEquals(43.0, loadIn(43.0, kg), 0.0)
        assertEquals(135.0, loadIn(61.0, lb), 0.0)
        assertEquals(225.0, loadIn(102.0, lb), 0.0)
        assertEquals(43.0, loadFrom(95.0, lb), 0.0)
        assertEquals(42.5, loadFrom(42.5, kg), 0.0)
    }

    @Test fun `picks up to three different pictures and knows what needs no weights`() {
        assertEquals(listOf("run", "pull-up", "push-up"), wodImages(lib("wod-murph"), find))
        assertEquals(listOf("clean-and-jerk"), wodImages(lib("wod-grace"), find))
        val free = WOD_LIBRARY.filter { isBodyweightWod(it, find) }.map { it.name }
        assertEquals(listOf("Cindy", "Annie", "Angie", "Barbara", "Chelsea", "Mary", "Murph", "Venus", "Aphrodite", "Helios", "Zeus", "Ares", "Gaia"), free)
    }

    @Test fun `finds library and own workouts by id`() {
        val own = wod(lib("wod-fran").copy(id = "mine", name = "My Fran"))
        assertEquals("My Fran", findWod(listOf(own), "mine")?.name)
        assertEquals("Fran", findWod(listOf(own), "wod-fran")?.name)
        assertNull(findWod(emptyList(), "nope"))
        assertTrue(isLibraryWod("wod-fran"))
        assertFalse(isLibraryWod("mine"))
    }

    private fun run(workout: Wod, taps: Int = 0) = TimerRun(
        id = "r",
        name = workout.name,
        config = workout.timer,
        calls = DEFAULT_TIMER_CALLS,
        startedAt = 0,
        pausedAt = null,
        paused = 0,
        taps = taps,
        wod = workout,
    )

    private fun session(workout: Wod, seconds: Int, taps: Int, finished: Boolean, id: String): TimerSession =
        timerSession(run(workout, taps), 10_000L + seconds * 1000L, finished)!!.copy(id = id)

    @Test fun `files a run of a workout under its id`() {
        val filed = session(lib("wod-fran"), 292, 6, true, "a")
        assertEquals(Triple("wod-fran", 292, 3), Triple(filed.wodId, filed.seconds, filed.rounds))
        val plain = timerSession(run(lib("wod-fran")).copy(wod = null), 20_000, true)!!
        assertNull(plain.wodId)
        // Left out of the file, like the PWA's `'wodId' in plain`.
        assertFalse("wodId" in StateJson.encodeToString(TimerSession.serializer(), plain))
    }

    @Test fun `counts a For Time only as finished with every step tapped off`() {
        // The clock stopped after one of three rounds: saved, but not a time to beat.
        val early = session(lib("wod-fran"), 3, 2, true, "early")
        assertEquals(false to 1, early.completed to early.rounds)
        session(lib("wod-fran"), 3, 1, true, "item").let { assertEquals(false to 0, it.completed to it.rounds) }
        assertEquals(TimerOutcome.STOPPED, timerOutcome(early))
        assertNull(wodBest(listOf(early), "wod-fran"))
        // A single round counts its items: all five ticked off is one round.
        session(lib("wod-murph"), 2400, 5, true, "m").let { assertEquals(true to 1, it.completed to it.rounds) }
        session(lib("wod-murph"), 900, 2, true, "m2").let { assertEquals(false to 0, it.completed to it.rounds) }
        session(lib("wod-grace"), 200, 1, true, "g").let { assertEquals(true to 1, it.completed to it.rounds) }
        // The clock decides in the other modes.
        session(lib("wod-cindy"), 1200, 0, true, "c").let { assertEquals(true to 0, it.completed to it.rounds) }
    }

    @Test fun `keeps the fastest finished For Time and the AMRAP with the most rounds`() {
        val fran = lib("wod-fran")
        val cindy = lib("wod-cindy")
        val sessions = listOf(
            session(fran, 310, 6, true, "slow"),
            session(fran, 292, 6, true, "fast"),
            session(fran, 120, 1, false, "stopped"),
            session(fran, 600, 2, true, "capped"),
            session(cindy, 1200, 14, true, "c14"),
            session(cindy, 1200, 18, true, "c18"),
            session(cindy, 400, 30, false, "cstopped"),
            session(lib("wod-chelsea"), 1800, 0, true, "emom"),
        )
        assertEquals("fast", wodBest(sessions, "wod-fran")?.id)
        assertEquals("4:52", bestText(wodBest(sessions, "wod-fran")!!))
        assertEquals("c18", wodBest(sessions, "wod-cindy")?.id)
        assertEquals("18 rounds", bestText(wodBest(sessions, "wod-cindy")!!))
        assertNull(wodBest(sessions, "wod-chelsea"))
        assertNull(wodBest(sessions, "wod-grace"))

        assertTrue(isWodRecord(sessions, session(fran, 280, 6, true, "new")))
        assertFalse(isWodRecord(sessions, session(fran, 292, 6, true, "same")))
        assertFalse(isWodRecord(sessions, session(fran, 100, 1, false, "quit")))
        assertTrue(isWodRecord(sessions, session(cindy, 1200, 19, true, "more")))
        // A first run has nothing to beat.
        assertFalse(isWodRecord(emptyList(), session(fran, 280, 6, true, "first")))
    }

    @Test fun `keeps the time of every tap and files it with the run`() {
        val fran = lib("wod-fran")
        assertEquals(41.3, splitTime(run(fran), 10_000L + 41_260L), 0.0)
        // In the lead-in nothing has started.
        assertEquals(0.0, splitTime(run(fran), 4_000), 0.0)
        val done = timerSession(run(fran, 6).copy(splits = listOf(40.0, 75.0, 120.0, 150.0, 190.0, 212.5)), 10_000L + 212_500L, true)!!
        assertEquals(listOf(40.0, 75.0, 120.0, 150.0, 190.0, 212.5), done.splits)
        assertFalse("splits" in StateJson.encodeToString(TimerSession.serializer(), timerSession(run(fran, 0), 30_000, false)!!))
        val plain = timerSession(run(fran, 2).copy(wod = null, splits = listOf(1.0, 2.0)), 30_000, true)!!
        assertFalse("splits" in StateJson.encodeToString(TimerSession.serializer(), plain))
    }

    @Test fun `measures a run against the best while it is still going`() {
        val best = session(lib("wod-fran"), 213, 6, true, "best").copy(splits = listOf(40.0, 75.0, 120.0, 150.0, 190.0, 212.5))
        // Level until the first item takes longer than it did in the best run.
        assertEquals(0, liveGap(best, emptyList(), 20.0))
        assertEquals(7, liveGap(best, emptyList(), 47.0))
        // Tapped 5 s early: ahead by that, for as long as the next item stays within its time.
        assertEquals(-5, liveGap(best, listOf(35.0), 50.0))
        assertEquals(0, liveGap(best, listOf(35.0), 75.0))
        assertEquals(8, liveGap(best, listOf(35.0), 83.0))
        // Behind at the last tap stays behind.
        assertEquals(15, liveGap(best, listOf(40.0, 90.0), 95.0))
        // The finish: only the last tap counts.
        // Whole seconds as the screen shows them: the best reads 3:33, this run 3:20.
        assertEquals(-13, liveGap(best, listOf(38.0, 70.0, 110.0, 140.0, 180.0, 200.0), 200.0))
        // Nothing to measure against.
        assertNull(liveGap(null, listOf(10.0), 20.0))
        assertNull(liveGap(session(lib("wod-fran"), 213, 6, true, "old"), listOf(10.0), 20.0))

        assertEquals("12s ahead", gapText(-12))
        assertEquals("1:05 behind", gapText(65))
        assertEquals("On pace", gapText(0))
    }

    @Test fun `counts an AMRAP against the rounds the best run had by now`() {
        val best = session(lib("wod-cindy"), 1200, 4, true, "best").copy(splits = listOf(60.0, 125.0, 200.0, 290.0))
        assertEquals(0, roundsGap(best, 0, 30.0))
        assertEquals(-1, roundsGap(best, 0, 61.0))
        assertEquals(1, roundsGap(best, 3, 130.0))
        assertEquals(0, roundsGap(best, 4, 300.0))
        assertNull(roundsGap(null, 2, 100.0))
        assertEquals("2 rounds ahead", roundsGapText(2))
        assertEquals("1 round behind", roundsGapText(-1))
        assertEquals("On pace", roundsGapText(0))
    }
}
