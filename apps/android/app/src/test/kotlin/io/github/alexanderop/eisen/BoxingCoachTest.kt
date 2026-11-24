package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.BOXING_BURNOUTS
import io.github.alexanderop.eisen.domain.BOXING_COMBOS
import io.github.alexanderop.eisen.domain.BOXING_CUES
import io.github.alexanderop.eisen.domain.BOXING_LINES
import io.github.alexanderop.eisen.domain.BOXING_MOVES
import io.github.alexanderop.eisen.domain.BOXING_SPLITS
import io.github.alexanderop.eisen.domain.BOXING_TEMPLATES
import io.github.alexanderop.eisen.domain.BUILD_LADDERS
import io.github.alexanderop.eisen.domain.BoxingCues
import io.github.alexanderop.eisen.domain.BoxingFocus
import io.github.alexanderop.eisen.domain.BoxingPlan
import io.github.alexanderop.eisen.domain.BoxingRoom
import io.github.alexanderop.eisen.domain.BoxingRound
import io.github.alexanderop.eisen.domain.BoxingStance
import io.github.alexanderop.eisen.domain.BoxingStyle
import io.github.alexanderop.eisen.domain.BoxingTheme
import io.github.alexanderop.eisen.domain.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.domain.MAX_OWN_COMBOS
import io.github.alexanderop.eisen.domain.OwnCombo
import io.github.alexanderop.eisen.domain.StateJson
import io.github.alexanderop.eisen.domain.TimerCalls
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerCue
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.boxingCues
import io.github.alexanderop.eisen.domain.boxingPlan
import io.github.alexanderop.eisen.domain.boxingRecordings
import io.github.alexanderop.eisen.domain.boxingTotals
import io.github.alexanderop.eisen.domain.boxingView
import io.github.alexanderop.eisen.domain.burnoutOf
import io.github.alexanderop.eisen.domain.comboCount
import io.github.alexanderop.eisen.domain.comboLength
import io.github.alexanderop.eisen.domain.comboPunches
import io.github.alexanderop.eisen.domain.cueCombo
import io.github.alexanderop.eisen.domain.decodeState
import io.github.alexanderop.eisen.domain.encodeState
import io.github.alexanderop.eisen.domain.freshState
import io.github.alexanderop.eisen.domain.isAgain
import io.github.alexanderop.eisen.domain.isCoachCue
import io.github.alexanderop.eisen.domain.ladderSteps
import io.github.alexanderop.eisen.domain.moveSound
import io.github.alexanderop.eisen.domain.ownCombo
import io.github.alexanderop.eisen.domain.roundCombos
import io.github.alexanderop.eisen.domain.roundSummary
import io.github.alexanderop.eisen.domain.sessionLine
import io.github.alexanderop.eisen.domain.sessionSummary
import io.github.alexanderop.eisen.domain.soundCombo
import io.github.alexanderop.eisen.domain.speedCombos
import io.github.alexanderop.eisen.domain.templatePlan
import io.github.alexanderop.eisen.domain.themeCombos
import io.github.alexanderop.eisen.domain.throwTime
import io.github.alexanderop.eisen.domain.timerConfig
import io.github.alexanderop.eisen.domain.withOwn
import io.github.alexanderop.eisen.domain.withOwnMoves
import io.github.alexanderop.eisen.domain.withRound
import io.github.alexanderop.eisen.domain.withoutOwn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coach's second round: the warm-up, the build-up, reminders, the non-stop finish, the lifter's
 * own combinations, the room to throw, and the totals. The schedules of plans without any of these
 * are pinned in [BoxingTest] and must not change.
 */
class BoxingCoachTest {
    private val quiet = TimerCalls(minutes = 0, halfway = false, final = false, phases = false, beeps = false)
    private val mine = listOf(OwnCombo("own-1", listOf("1", "2", "3b", "slip", "2")))

    private fun seconds(millis: Long): String = if (millis % 1000 == 0L) "${millis / 1000}" else "${millis / 1000.0}"

    /** One round of [work] seconds, after a lead-in of 10 s: the round starts at 10 s. */
    private fun one(round: BoxingRound, work: Int = 60, plan: BoxingPlan = BoxingPlan(emptyList(), BoxingStyle.NAMES)) =
        timerConfig(TimerConfig(TimerMode.BOXING, work, 10, 1, plan.copy(rounds = listOf(round))))

    private fun spoken(config: TimerConfig, seed: Int, calls: TimerCalls = DEFAULT_TIMER_CALLS): String =
        boxingCues(config, calls, seed).filter { !it.sound.startsWith("beep") }.joinToString(" ") { "${seconds(it.at)}:${it.sound}" }

    /** The lines that are said in their place and that a call waits for. */
    private fun isLine(sound: String) = sound.startsWith("round-") || sound == "last-round" || sound.startsWith("box-theme-") ||
        sound == "box-speed" || sound == "box-power" || sound == "thirty-seconds" || sound == "ten-seconds" || sound == "box-nonstop"

    @Test fun `a warm-up calls only its three easy combinations and reminds you to move`() {
        val warmup = one(BoxingRound(BoxingTheme.WARMUP, 8, 0))
        assertEquals(
            "0:get-ready 10.7:round-1 12.4:box-theme-warmup 16:box-jab-n 24:box-double-jab-n 26.1:box-cue-angle 32:box-jab-cross-n " +
                "35.2:box-cue-snap 40:box-jab-n 42.1:box-cue-hands 48:box-jab-cross-n 56:box-jab-n 60:ten-seconds 64:box-double-jab-n 70.7:complete",
            spoken(warmup, 1),
        )
        assertEquals(listOf("jab", "double-jab", "jab-cross"), themeCombos(BoxingTheme.WARMUP).map { it.id })
        var reminders = 0
        for (seed in 1..40) {
            val cues = boxingCues(warmup, quiet, seed)
            assertTrue(cues.all { soundCombo(it.sound)?.id in setOf("jab", "double-jab", "jab-cross") || isCoachCue(it.sound) })
            reminders += cues.count { isCoachCue(it.sound) }
        }
        // Many: about every other combination, whatever the round says.
        assertTrue("$reminders", reminders in 120..220)
        assertEquals(spoken(warmup, 7), spoken(one(BoxingRound(BoxingTheme.WARMUP, 8, 0, cues = BoxingCues.SOME)), 7))
        assertEquals("Warm-up · Easy", roundSummary(BoxingRound(BoxingTheme.WARMUP, 8, 0, cues = BoxingCues.SOME)))
    }

    @Test fun `a build-up climbs one ladder at a time, every step thrown again`() {
        val build = one(BoxingRound(BoxingTheme.BUILD, 6, 0), 120)
        assertEquals(
            "16:box-jab-n 18.5:box-again 22:box-double-jab-n 24.5:box-again 28:box-jab-jab-cross-n 33.7:box-again " +
                "37:box-jab-jab-cross-hook-n 44.3:box-again 48:box-cross-hook-n 52.1:box-again 55:box-cross-hook-cross-n 60.7:box-again " +
                "64:box-cross-hook-cross-hook-n 71.3:box-again 75:box-hook-n 77.5:box-again 81:box-hook-cross-n 85.1:box-again " +
                "88:box-hook-cross-hook-n 93.7:box-again 97:box-jab-n 99.5:box-again 103:box-jab-cross-n 107.1:box-again " +
                "110:box-jab-cross-hook-n 115.7:box-again 119:box-jab-cross-hook-cross-n",
            spoken(build, 4, quiet),
        )
        assertEquals(19, themeCombos(BoxingTheme.BUILD).size)
        for (seed in 1..60) {
            for (work in listOf(60, 180, 300)) {
                val cues = boxingCues(one(BoxingRound(BoxingTheme.BUILD, 4 + seed % 6, 0), work), quiet, seed)
                val calls = cues.filter { soundCombo(it.sound) != null }
                // Each step is followed by "Again!" before anything else, unless the round is about to end.
                for (index in cues.indices) {
                    if (soundCombo(cues[index].sound) == null || cues[index] == calls.last()) continue
                    assertTrue("$seed/$work: ${cues.getOrNull(index + 1)}", isAgain(cues[index + 1].sound))
                }
                // The calls are whole ladders in order, one after the other, and only the last may be cut short.
                val steps = ladderSteps(calls.map { soundCombo(it.sound)!!.id })
                assertTrue(steps.all { it.first >= 1 })
                val ladders = mutableListOf<List<String>>()
                calls.forEachIndexed { at, call -> if (steps[at].first == 1) ladders += listOf(soundCombo(call.sound)!!.id) else ladders[ladders.lastIndex] = ladders.last() + soundCombo(call.sound)!!.id }
                ladders.dropLast(1).forEach { assertTrue("$seed/$work: $it", it in BUILD_LADDERS) }
                assertTrue(BUILD_LADDERS.any { it.take(ladders.last().size) == ladders.last() })
                // Never the same ladder twice in a row.
                ladders.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
            }
        }
        // The screen shows where the combination stands in its ladder: jab, double jab, ... is ladder 3 of four steps.
        val cues = boxingCues(build, quiet, 4)
        fun at(seconds: Int) = boxingView(build, cues, seconds * 1000L).let { Triple(it.combo?.id, it.step, it.steps) }
        assertEquals(Triple(null, 0, 0), at(15))
        assertEquals(Triple("jab", 1, 4), at(17))
        assertEquals(Triple("double-jab", 2, 4), at(23))
        assertEquals(Triple("jab-jab-cross-hook", 4, 4), at(45))
        assertEquals(Triple("cross-hook", 1, 3), at(49))
        assertEquals(Triple("jab", 1, 5), at(98))
        assertEquals(Triple("jab-cross-hook-cross", 4, 5), at(120))
        assertEquals("Build-up", roundSummary(BoxingRound(BoxingTheme.BUILD, 6, 0)))
        // Nothing to split or pick in a build-up.
        assertEquals(BoxingRound(BoxingTheme.BUILD, 6, 0), boxingPlan(BoxingPlan(listOf(BoxingRound(BoxingTheme.BUILD, 6, 30, listOf("jab"))), BoxingStyle.NAMES), 1).rounds[0])
    }

    @Test fun `a freestyle round with reminders says nothing else, now and then`() {
        for ((level, range) in listOf(BoxingCues.SOME to 16_000L..27_500L, BoxingCues.MANY to 9_000L..17_500L)) {
            for (seed in 1..30) {
                val config = one(BoxingRound(BoxingTheme.FREESTYLE, 6, 0, cues = level), 180)
                val cues = boxingCues(config, DEFAULT_TIMER_CALLS, seed).filter { !it.sound.startsWith("beep") }
                assertEquals(0, comboCount(cues))
                assertFalse(cues.any { isAgain(it.sound) })
                val reminders = cues.filter { isCoachCue(it.sound) }
                assertTrue(reminders.size >= if (level == BoxingCues.SOME) 6 else 9)
                assertEquals(20_000L, reminders.first().at)
                reminders.zipWithNext().forEach { (a, b) ->
                    assertTrue("$level $seed: ${b.at - a.at}", b.at - a.at in range)
                    assertTrue(a.sound != b.sound)
                }
            }
        }
        assertEquals(
            "0:get-ready 10.7:round-1 12.4:box-theme-freestyle 20:box-cue-hands 33:box-cue-chin 43:box-cue-angle 57:box-cue-move " +
                "71:box-cue-hands 81:box-cue-chin 94:box-cue-angle 100:thirty-seconds 103:box-cue-snap 114:box-cue-breathe " +
                "120:ten-seconds 123:box-cue-snap 130.7:complete",
            spoken(one(BoxingRound(BoxingTheme.FREESTYLE, 6, 0, cues = BoxingCues.MANY), 120), 1),
        )
        assertEquals("Freestyle · reminders", roundSummary(BoxingRound(BoxingTheme.FREESTYLE, 6, 0, cues = BoxingCues.SOME)))
        // Off is the freestyle round it always was.
        assertEquals("0:get-ready 10.7:round-1 12.4:box-theme-freestyle 160:thirty-seconds 180:ten-seconds 190.7:complete", spoken(one(BoxingRound(BoxingTheme.FREESTYLE, 6, 0), 180), 1))
        // No non-stop finish in freestyle.
        assertEquals(0, boxingPlan(BoxingPlan(listOf(BoxingRound(BoxingTheme.FREESTYLE, 6, 0, burnout = 30)), BoxingStyle.NAMES), 1).rounds[0].burnout)
    }

    @Test fun `the last seconds of a round go non-stop with short combinations`() {
        val burn = one(BoxingRound(BoxingTheme.TWO, 4, 0, burnout = 15))
        assertEquals(
            "0:get-ready 10.7:round-1 12.4:box-theme-two 16:box-jab-hook-n 20:box-cross-uppercut-n 24:box-jab-hook-n 28:box-cross-hook-n " +
                "32:box-cross-uppercut-n 36:box-jab-uppercut-n 40:box-jab-cross-n 44:box-jab-uppercut-n 48:box-jab-cross-n 52:box-jab-hook-n " +
                "55:box-nonstop 60:ten-seconds 62.5:box-jab-cross-n 66:box-cross-uppercut-n 70.7:complete",
            spoken(burn, 1),
        )
        for (theme in listOf(BoxingTheme.MIX, BoxingTheme.THREE, BoxingTheme.BUILD, BoxingTheme.WARMUP)) {
            for (seed in 1..40) {
                for (split in listOf(0, 15)) {
                    val round = BoxingRound(theme, 4, split, cues = BoxingCues.MANY, burnout = 15)
                    val config = one(round)
                    val cues = boxingCues(config, DEFAULT_TIMER_CALLS, seed).filter { !it.sound.startsWith("beep") }
                    val short = speedCombos(if (theme == BoxingTheme.BUILD) themeCombos(BoxingTheme.BUILD) else roundCombos(config.boxing!!.rounds[0])).map { it.id }.toSet()
                    val after = cues.filter { it.at >= 55_000 && it.at < 70_000 }
                    assertTrue("$theme $seed: $after", after.all { it.sound == "box-nonstop" || it.sound == "ten-seconds" || soundCombo(it.sound)?.id in short })
                    assertTrue(after.none { isAgain(it.sound) || isCoachCue(it.sound) || it.sound == "box-speed" || it.sound == "box-power" })
                    assertTrue(after.any { soundCombo(it.sound) != null })
                    val nonstop = cues.single { it.sound == "box-nonstop" }.at
                    val ten = cues.single { it.sound == "ten-seconds" }.at
                    assertTrue(kotlin.math.abs(nonstop - ten) >= 2500)
                }
            }
        }
        // The band says it: the run is in its non-stop finish from 45 s on.
        val cues = boxingCues(burn, DEFAULT_TIMER_CALLS, 1)
        assertEquals(null, boxingView(burn, cues, 54_000).focus)
        assertEquals(BoxingFocus.BURNOUT, boxingView(burn, cues, 55_000).focus)
        assertEquals("Non-stop!", boxingView(burn, cues, 56_000).say)
        assertNull(boxingView(burn, cues, 57_500).say)
        // A round shorter than twice the finish has none.
        assertEquals(0, burnoutOf(BoxingRound(BoxingTheme.MIX, 6, 0, burnout = 30), 59))
        assertEquals(30, burnoutOf(BoxingRound(BoxingTheme.MIX, 6, 0, burnout = 30), 60))
        assertEquals(
            spoken(timerConfig(TimerConfig(TimerMode.BOXING, 50, 10, 1, BoxingPlan(listOf(BoxingRound(BoxingTheme.MIX, 6, 0)), BoxingStyle.NAMES))), 3),
            spoken(timerConfig(TimerConfig(TimerMode.BOXING, 50, 10, 1, BoxingPlan(listOf(BoxingRound(BoxingTheme.MIX, 6, 0, burnout = 30)), BoxingStyle.NAMES))), 3),
        )
        assertEquals("Mix · Fast · reminders · 30s non-stop", roundSummary(BoxingRound(BoxingTheme.MIX, 4, 0, cues = BoxingCues.SOME, burnout = 30)))
    }

    @Test fun `a combination of the lifter's own is said move by move and drilled like any long one`() {
        val combo = ownCombo(mine.single())
        assertEquals("Jab · Cross · Hook to the body · Slip · Cross", combo.names)
        assertEquals("1 – 2 – 3B – Slip – 2", combo.numbers)
        assertEquals("One, Two, Three to the body, Slip, Two.", combo.sayNumbers)
        assertEquals(4, comboPunches(combo))
        assertEquals(3, comboPunches(BOXING_COMBOS.first { it.id == "jab-cross-hook-body" }))
        assertEquals(1, comboPunches(BOXING_COMBOS.first { it.id == "slip-cross" }))
        assertEquals(5300L, comboLength(combo, BoxingStyle.NAMES))
        val plan = BoxingPlan(emptyList(), BoxingStyle.NAMES, own = mine)
        val config = one(BoxingRound(BoxingTheme.OWN, 6, 0), 60, plan)
        val cues = boxingCues(config, quiet, 1)
        assertEquals(
            "16:box-move-1-n 16.95:box-move-2-n 18:box-move-3b-n 19.5:box-move-slip-n 20.25:box-move-2-n 24.3:box-again " +
                "28.5:box-move-1-n 29.45:box-move-2-n 30.5:box-move-3b-n 32:box-move-slip-n 32.75:box-move-2-n 36.8:box-again 40.8:box-one-more " +
                "45:box-move-1-n 45.95:box-move-2-n 47:box-move-3b-n 48.5:box-move-slip-n 49.25:box-move-2-n 53.3:box-again " +
                "57.5:box-move-1-n 58.45:box-move-2-n 59.5:box-move-3b-n 61:box-move-slip-n 61.75:box-move-2-n 65.8:box-again",
            cues.joinToString(" ") { "${seconds(it.at)}:${it.sound}" },
        )
        // Only the first move carries the combination: the others are not calls of their own.
        assertEquals(listOf("own-1", null, null, null, null), cues.take(5).map { it.combo })
        assertEquals(4, comboCount(cues))
        assertEquals("own-1", cueCombo(cues[0], mine)?.id)
        assertNull(cueCombo(cues[1], mine))
        assertNull(cueCombo(cues[0], emptyList()))
        // The screen shows it like any other, and the bar runs from the call to its "Again!".
        val view = boxingView(config, cues, 18_000)
        assertEquals(combo, view.combo)
        assertEquals(16_000L, view.since)
        assertEquals(24_300L, view.next)
        assertEquals(1 to 2, view.go to view.goes)
        assertEquals(28_500L, boxingView(config, cues, 25_000).next)
        // In numbers it says the numbers.
        assertEquals("box-move-3b-d", boxingCues(one(BoxingRound(BoxingTheme.OWN, 6, 0), 60, plan.copy(style = BoxingStyle.NUMBERS)), quiet, 1)[2].sound)
        // The mix calls the lifter's own too.
        assertTrue(themeCombos(BoxingTheme.MIX, mine).any { it.id == "own-1" })
        // An own combination is never started when its last move would come after the bell.
        val long = plan.copy(own = listOf(OwnCombo("own-1", listOf("1b", "2b", "3b", "4b", "1b", "2b"))))
        for (seed in 1..50) {
            for (work in 30..120 step 5) {
                val config2 = one(BoxingRound(BoxingTheme.OWN, 3, 0), work, long)
                for (cue in boxingCues(config2, quiet, seed)) assertTrue("$seed $work $cue", cue.at < 10_000 + work * 1000L)
            }
        }
        assertEquals("box-move-slip-n", moveSound("slip", BoxingStyle.NAMES))
    }

    @Test fun `a roomy plan gives more time to throw than a tight one`() {
        assertEquals(listOf(2750L, 2000L, 1550L), BoxingRoom.entries.map { throwTime(3, it) })
        fun calls(room: BoxingRoom, seed: Int) =
            comboCount(boxingCues(one(BoxingRound(BoxingTheme.THREE, 3, 0), 180, BoxingPlan(emptyList(), BoxingStyle.NAMES, room = room)), quiet, seed))
        assertEquals(listOf(15, 18, 20), BoxingRoom.entries.map { calls(it, 1) })
        for (seed in 1..20) assertTrue(calls(BoxingRoom.ROOMY, seed) < calls(BoxingRoom.TIGHT, seed))
    }

    @Test fun `random plans with every option never talk over a line and stay in their round`() {
        for (seed in 1..200) {
            val random = java.util.Random(seed.toLong())
            fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]
            val own = List(random.nextInt(4)) { at -> OwnCombo("own-${at + 1 + random.nextInt(2)}", List(1 + random.nextInt(7)) { pick(BOXING_MOVES).id }) }
            val rounds = List(1 + random.nextInt(4)) {
                val theme = pick(BoxingTheme.entries)
                val pool = themeCombos(theme, own).map { it.id } + "nope"
                BoxingRound(
                    theme,
                    random.nextInt(18),
                    pick(BOXING_SPLITS + 7),
                    if (random.nextBoolean()) emptyList() else List(random.nextInt(4)) { pick(pool) },
                    pick(BoxingCues.entries),
                    pick(BOXING_BURNOUTS + 20),
                )
            }
            val input = BoxingPlan(rounds, pick(BoxingStyle.entries), pick(BoxingStance.entries), pick(BoxingRoom.entries), own)
            val count = rounds.size + random.nextInt(2)
            val plan = boxingPlan(input, count)
            assertEquals("$seed", plan, boxingPlan(plan, count))
            val work = pick(listOf(30, 35, 45, 60, 90, 120, 180))
            val config = timerConfig(TimerConfig(TimerMode.BOXING, work, 10 + random.nextInt(20), count, input))
            val calls = if (random.nextBoolean()) DEFAULT_TIMER_CALLS else TimerCalls(0, false, random.nextBoolean(), random.nextBoolean(), random.nextBoolean())
            val cues = boxingCues(config, calls, seed * 31)
            val voice = cues.filter { !it.sound.startsWith("beep") }
            voice.zipWithNext().forEach { (a, b) -> assertTrue("$seed: $a then $b", b.at - a.at >= 400) }
            fun length(cue: TimerCue): Long? = when {
                cueCombo(cue, config.boxing!!.own) != null -> comboLength(cueCombo(cue, config.boxing!!.own)!!, config.boxing!!.style)
                isAgain(cue.sound) -> 1500
                isCoachCue(cue.sound) -> 2000
                else -> null
            }
            val lines = voice.filter { isLine(it.sound) }
            for (cue in voice) {
                val length = length(cue) ?: continue
                val talkedOver = lines.firstOrNull { it.at > cue.at - 2500 && it.at < cue.at + length }
                assertNull("$seed: $cue over $talkedOver", talkedOver)
                // Nothing is called in the last seconds of a round.
                val into = (cue.at - 10_000) % ((work + config.rest) * 1000L)
                assertTrue("$seed: $cue", into <= (work - 4) * 1000L)
            }
            // Every move of the lifter's own combinations has its recording, in the plan's style.
            assertTrue(cues.filter { it.sound.startsWith("box-move-") }.all { it.sound in boxingRecordings() })
        }
    }

    @Test fun `the totals count each combination once and its punches for every go`() {
        val cues = listOf(
            TimerCue(1_000, "box-jab-cross-hook-n"),
            TimerCue(5_000, "box-again"),
            TimerCue(8_000, "box-one-more"),
            TimerCue(12_000, "box-move-1-n", "own-1"),
            TimerCue(12_650, "box-move-2-n"),
            TimerCue(13_300, "box-move-3b-n"),
            TimerCue(14_650, "box-move-slip-n"),
            TimerCue(15_300, "box-move-2-n"),
            TimerCue(18_000, "box-again"),
            TimerCue(21_000, "box-cue-move"),
            TimerCue(24_000, "box-slip-cross-d"),
            TimerCue(26_000, "ten-seconds"),
        )
        // 3 punches three times, 4 punches twice, the 1 of "Slip, two".
        assertEquals(3 to 18, boxingTotals(cues, mine, 30_000))
        assertEquals(2 to 17, boxingTotals(cues, mine, 23_000))
        assertEquals(1 to 6, boxingTotals(cues, mine, 7_000))
        val session = TimerSession("b1", "Boxing", one(BoxingRound(BoxingTheme.MIX, 6, 0)), 0, 1, 1320, 6, true, punches = 480, combos = 166)
        assertEquals("6 rounds · 22:00 · about 480 punches", sessionLine(session))
        assertEquals("6 rounds · 22:00", sessionSummary(session))
    }

    @Test fun `a state written before the coach's second round reads the same, and a plan with everything comes back whole`() {
        // Version 9 as it was: no reminders, no non-stop, no stance, room or own combinations anywhere.
        val boxing = """{"mode":"boxing","work":120,"rest":30,"rounds":2,"boxing":{"rounds":[{"theme":"jab","every":4,"split":0,"pick":[]},{"theme":"three","every":6,"split":15,"pick":["jab-cross-hook"]}],"style":"numbers"}}"""
        val session = """{"id":"s1","name":"Boxing","config":$boxing,"startedAt":1000,"finishedAt":300000,"seconds":270,"rounds":2,"completed":true}"""
        val current = StateJson.parseToJsonElement(encodeState(freshState())).jsonObject
        val configs = current.getValue("timerConfigs").jsonObject
        val old = JsonObject(
            current + mapOf(
                "version" to JsonPrimitive(9),
                "timerConfigs" to JsonObject(configs + ("boxing" to StateJson.parseToJsonElement(boxing))),
                "timerPresets" to StateJson.parseToJsonElement("""[{"id":"p1","name":"Old","config":$boxing}]"""),
                "timerSessions" to StateJson.parseToJsonElement("[$session]"),
            ),
        ).toString()
        val read = decodeState(old)!!
        val plan = read.timerConfigs.getValue(TimerMode.BOXING).boxing!!
        assertEquals(
            BoxingPlan(listOf(BoxingRound(BoxingTheme.JAB, 4, 0), BoxingRound(BoxingTheme.THREE, 6, 15, listOf("jab-cross-hook"))), BoxingStyle.NUMBERS),
            plan,
        )
        assertEquals(BoxingStance.ORTHODOX to BoxingRoom.NORMAL, plan.stance to plan.room)
        assertEquals(plan, read.timerPresets.single().config.boxing)
        assertNull(read.timerSessions.single().punches)
        // Its schedule is the one it had: a plan without the new options draws what it drew.
        assertEquals(boxingCues(read.timerConfigs.getValue(TimerMode.BOXING), DEFAULT_TIMER_CALLS, 5), boxingCues(timerConfig(read.timerPresets.single().config), DEFAULT_TIMER_CALLS, 5))
        // Written again it has the new fields, with their defaults.
        val again = encodeState(read)
        assertTrue(again.contains(""""rounds":[{"theme":"jab","every":4,"split":0,"pick":[],"cues":"off","burnout":0},"""))
        assertTrue(again.contains(""""style":"numbers","stance":"orthodox","room":"normal","own":[]}"""))
        assertFalse(again.contains("\"punches\""))
        assertEquals(read, decodeState(again))

        // A plan with every option, a session with its punches.
        val full = BoxingPlan(
            listOf(
                BoxingRound(BoxingTheme.WARMUP, 8, 0, cues = BoxingCues.MANY),
                BoxingRound(BoxingTheme.OWN, 4, 15, listOf("own-2"), BoxingCues.SOME, 30),
                BoxingRound(BoxingTheme.BUILD, 6, 0, cues = BoxingCues.SOME, burnout = 15),
                BoxingRound(BoxingTheme.FREESTYLE, 6, 0, cues = BoxingCues.MANY),
            ),
            BoxingStyle.NUMBERS,
            BoxingStance.SOUTHPAW,
            BoxingRoom.ROOMY,
            listOf(OwnCombo("own-1", listOf("1", "2", "3b")), OwnCombo("own-2", listOf("slip", "2", "pivot", "3"))),
        )
        val config = timerConfig(TimerConfig(TimerMode.BOXING, 180, 60, 4, full))
        assertEquals(full, config.boxing)
        val state = freshState().copy(
            timerConfigs = freshState().timerConfigs + (TimerMode.BOXING to config),
            timerSessions = listOf(TimerSession("s2", "Boxing", config, 0, 900_000, 900, 4, true, punches = 480, combos = 166)),
        )
        val json = encodeState(state)
        assertTrue(json.contains(""""own":[{"id":"own-1","moves":["1","2","3b"]},{"id":"own-2","moves":["slip","2","pivot","3"]}]"""))
        assertTrue(json.contains(""""punches":480,"combos":166"""))
        assertEquals(state, decodeState(json))
    }

    @Test fun `own combinations are added under the lowest free id, edited, and removed from every round`() {
        val empty = BoxingPlan(listOf(BoxingRound(BoxingTheme.MIX, 6, 0)), BoxingStyle.NAMES)
        val one = withOwn(empty, listOf("1", "2", "3b"))
        assertEquals(listOf(OwnCombo("own-1", listOf("1", "2", "3b"))), one.own)
        val two = withOwn(one, listOf("slip", "2"))
        assertEquals(listOf("own-1", "own-2"), two.own.map { it.id })
        // Not a combination: one move, seven moves, a move that does not exist.
        assertSame(two, withOwn(two, listOf("1")))
        assertSame(two, withOwn(two, List(7) { "1" }))
        assertSame(two, withOwn(two, listOf("1", "kick")))
        // Twelve at most.
        var full = two
        repeat(20) { full = withOwn(full, listOf("1", "1")) }
        assertEquals(MAX_OWN_COMBOS, full.own.size)
        // The lowest free number comes back after a removal.
        val gap = withoutOwn(two, "own-1")
        assertEquals(listOf("own-2", "own-1"), withOwn(gap, listOf("3", "2")).own.map { it.id })

        val edited = withOwnMoves(two, "own-1", listOf("1", "1", "2"))
        assertEquals(listOf("1", "1", "2"), edited.own.first().moves)
        assertSame(two, withOwnMoves(two, "own-9", listOf("1", "2")))
        assertSame(two, withOwnMoves(two, "own-1", listOf("1")))

        // Removed: gone from every pick, and a "My combos" round with nothing left becomes a mix.
        val rounds = listOf(
            BoxingRound(BoxingTheme.OWN, 6, 0, listOf("own-1")),
            BoxingRound(BoxingTheme.MIX, 6, 0, listOf("own-1", "jab")),
        )
        val picked = boxingPlan(two.copy(rounds = rounds), 2)
        assertEquals(listOf("own-1"), picked.rounds[0].pick)
        val without = withoutOwn(picked, "own-1")
        assertEquals(BoxingTheme.OWN, without.rounds[0].theme)
        assertEquals(emptyList<String>(), without.rounds[0].pick)
        assertEquals(listOf("jab"), without.rounds[1].pick)
        val none = withoutOwn(without, "own-2")
        assertEquals(BoxingRound(BoxingTheme.MIX, 6, 0), none.rounds[0])
        assertSame(none, withoutOwn(none, "own-2"))
        // A round edited onto "My combos" without any is a mix.
        assertEquals(BoxingTheme.MIX, withRound(empty, 0, BoxingRound(BoxingTheme.OWN, 6, 0)).rounds[0].theme)
        assertEquals(BoxingTheme.OWN, withRound(two, 0, BoxingRound(BoxingTheme.OWN, 6, 0)).rounds[0].theme)
        // A file's own combinations are cleaned: bad ids, doubles, unknown moves, too short.
        val cleaned = boxingPlan(
            BoxingPlan(
                emptyList(),
                BoxingStyle.NAMES,
                own = listOf(OwnCombo("own-1", listOf("1", "kick", "2")), OwnCombo("own-1", listOf("3", "3")), OwnCombo("mine", listOf("1", "2")), OwnCombo("own-7", listOf("1")), OwnCombo("own-8", List(8) { "2" })),
            ),
            1,
        )
        assertEquals(listOf(OwnCombo("own-1", listOf("1", "2")), OwnCombo("own-8", List(6) { "2" })), cleaned.own)
    }

    @Test fun `the two new templates keep the lifter's style, stance, room and own combinations`() {
        val plan = BoxingPlan(emptyList(), BoxingStyle.NUMBERS, BoxingStance.SOUTHPAW, BoxingRoom.TIGHT, mine)
        val pads = templatePlan(BOXING_TEMPLATES.first { it.name == "Pad work" }.rounds, 3, plan)
        assertEquals(List(3) { BoxingRound(BoxingTheme.BUILD, 6, 0) }, pads.rounds)
        assertEquals(plan.copy(rounds = pads.rounds), pads)
        val full = templatePlan(BOXING_TEMPLATES.first { it.name == "Full session" }.rounds, 6, plan)
        assertEquals(
            listOf(
                "Warm-up · Easy",
                "2-punch combos · Fast",
                "3-punch combos · Steady · reminders",
                "Build-up",
                "Mix · Steady · speed and power 30s",
                "Mix · Fast · reminders · 30s non-stop",
            ),
            full.rounds.map(::roundSummary),
        )
        assertEquals("Warm up, build, mix, burn out", BOXING_TEMPLATES.first { it.name == "Full session" }.hint)
        // A run of the full session: about 480 punches in six three-minute rounds is the kind of number the result shows.
        val config = timerConfig(TimerConfig(TimerMode.BOXING, 180, 60, 6, full))
        val (combos, punches) = boxingTotals(boxingCues(config, DEFAULT_TIMER_CALLS, 1), plan.own, Long.MAX_VALUE)
        assertTrue("$combos $punches", combos in 100..220 && punches in 250..700)
    }

    @Test fun `every line of the coach has its text`() {
        assertEquals(8, BOXING_CUES.size)
        assertEquals("Move!", BOXING_LINES["box-cue-move"])
        assertEquals("Non-stop!", BOXING_LINES["box-nonstop"])
        val recordings = boxingRecordings()
        assertEquals("Hook to the body.", recordings["box-move-3b-n"])
        assertEquals("Three to the body.", recordings["box-move-3b-d"])
        assertEquals("Step back.", recordings["box-move-back-n"])
        assertEquals(28, recordings.keys.count { it.startsWith("box-move-") })
        for (theme in BoxingTheme.entries) assertTrue(recordings.containsKey("box-theme-${theme.key}"))
    }
}
