package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_COMBOS
import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_LINES
import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_TEMPLATES
import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_THEMES
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingFocus
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingPlan
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStyle
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingView
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.feature.timer.TimerCalls
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerPhase
import io.github.alexanderop.eisen.feature.timer.boxing.boxingCues
import io.github.alexanderop.eisen.feature.timer.boxing.boxingPlan
import io.github.alexanderop.eisen.feature.timer.boxing.boxingRecordings
import io.github.alexanderop.eisen.feature.timer.boxing.boxingView
import io.github.alexanderop.eisen.feature.timer.boxing.comboCount
import io.github.alexanderop.eisen.feature.timer.boxing.comboSound
import io.github.alexanderop.eisen.feature.timer.boxing.copiedToAll
import io.github.alexanderop.eisen.feature.timer.boxing.focusAt
import io.github.alexanderop.eisen.feature.timer.boxing.roundSummary
import io.github.alexanderop.eisen.feature.timer.boxing.sayLength
import io.github.alexanderop.eisen.feature.timer.boxing.DRILL_FROM
import io.github.alexanderop.eisen.feature.timer.boxing.isAgain
import io.github.alexanderop.eisen.feature.timer.boxing.throwTime
import io.github.alexanderop.eisen.feature.timer.boxing.seededRandom
import io.github.alexanderop.eisen.feature.timer.boxing.soundCombo
import io.github.alexanderop.eisen.feature.timer.boxing.themeCombos
import io.github.alexanderop.eisen.feature.timer.timerConfig
import io.github.alexanderop.eisen.feature.timer.timerCues
import io.github.alexanderop.eisen.feature.timer.timerSeconds
import io.github.alexanderop.eisen.feature.timer.timerSummary
import io.github.alexanderop.eisen.feature.timer.timerView
import io.github.alexanderop.eisen.feature.timer.boxing.withRound
import io.github.alexanderop.eisen.feature.timer.boxing.duplicatedRound
import io.github.alexanderop.eisen.feature.timer.boxing.movedRound
import io.github.alexanderop.eisen.feature.timer.boxing.paceOf
import io.github.alexanderop.eisen.feature.timer.boxing.roundCombos
import io.github.alexanderop.eisen.feature.timer.boxing.speedCombos
import io.github.alexanderop.eisen.feature.timer.boxing.templatePlan
import io.github.alexanderop.eisen.feature.timer.boxing.withoutRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same inputs and expected values as the PWA's `boxing.test.ts`: the schedules with their seeds
 * are the contract between the apps, so both call the same combinations at the same moments.
 */
class BoxingTest {
    private fun round(theme: BoxingTheme, every: Int = 4, split: Int = 0) = BoxingRound(theme, every, split)
    private fun short(style: BoxingStyle = BoxingStyle.NAMES) = timerConfig(
        TimerConfig(TimerMode.BOXING, work = 30, rest = 10, rounds = 2, boxing = BoxingPlan(listOf(round(BoxingTheme.JAB), round(BoxingTheme.TWO, 5, 15)), style)),
    )
    private val quiet = TimerCalls(minutes = 0, halfway = false, final = false, phases = false, beeps = false)
    private val default = DEFAULT_TIMERS.getValue(TimerMode.BOXING)

    /** Milliseconds as JavaScript prints `ms / 1000`: "16", "10.7". */
    private fun seconds(millis: Long): String = if (millis % 1000 == 0L) "${millis / 1000}" else "${millis / 1000.0}"

    /** "16:box-jab-n": seconds after Start and the sound, without the beeps. */
    private fun spoken(config: TimerConfig, seed: Int, calls: TimerCalls = DEFAULT_TIMER_CALLS): String =
        boxingCues(config, calls, seed).filter { !it.sound.startsWith("beep") }.joinToString(" ") { "${seconds(it.at)}:${it.sound}" }

    @Test fun `has combinations for every theme and a recording for each in both styles`() {
        assertEquals(
            "warmup:3 jab:6 two:6 three:7 four:7 body:9 hooks:7 defense:6 build:19 own:0 mix:43 freestyle:0",
            BOXING_THEMES.joinToString(" ") { "${it.key}:${themeCombos(it).size}" },
        )
        assertEquals(43, BOXING_COMBOS.map { it.id }.toSet().size)
        val recordings = boxingRecordings()
        // 43 combinations twice, twelve themes, speed and power, again and one more, non-stop,
        // eight reminders, and the 14 moves of the lifter's own combinations twice.
        assertEquals(86 + 12 + 4 + 1 + 8 + 28, recordings.size)
        assertEquals("Jab, cross, hook.", recordings["box-jab-cross-hook-n"])
        assertEquals("One, two, three.", recordings["box-jab-cross-hook-d"])
        assertEquals("Long combos.", recordings["box-theme-four"])
        for (theme in BOXING_THEMES) assertNotNull(recordings["box-theme-${theme.key}"])
    }

    @Test fun `has a recording in the shared assets for every line the coach says`() {
        // The APK takes its assets from the PWA's folder, see `app/build.gradle.kts`.
        val folder = java.io.File("../../web/src/assets/voice/timer")
        val missing = boxingRecordings().keys.filterNot { java.io.File(folder, "$it.mp3").isFile }
        // The lines of the second round of the coach are being recorded; the player skips one until it is there.
        val coming = BOXING_LINES.keys.filter { it.startsWith("box-cue-") || it in listOf("box-nonstop", "box-theme-warmup", "box-theme-build", "box-theme-own") } +
            boxingRecordings().keys.filter { it.startsWith("box-move-") }
        assertTrue("no recording of ${missing - coming.toSet()}", (missing - coming.toSet()).isEmpty())
    }

    @Test fun `names the recording of a combination and finds it again`() {
        val combo = BOXING_COMBOS.first { it.id == "jab-cross" }
        assertEquals("box-jab-cross-n", comboSound(combo, BoxingStyle.NAMES))
        assertEquals("box-jab-cross-d", comboSound(combo, BoxingStyle.NUMBERS))
        assertSame(combo, soundCombo("box-jab-cross-d"))
        assertNull(soundCombo("box-theme-jab"))
        assertNull(soundCombo("box-speed"))
        assertNull(soundCombo("round-1"))
    }

    @Test fun `gives a plan one round per round of the timer, repeating the last one`() {
        val plan = boxingPlan(BoxingPlan(listOf(round(BoxingTheme.JAB), round(BoxingTheme.BODY, 99, 7)), BoxingStyle.NUMBERS), 4)
        assertEquals(BoxingStyle.NUMBERS, plan.style)
        assertEquals(listOf(BoxingTheme.JAB, BoxingTheme.BODY, BoxingTheme.BODY, BoxingTheme.BODY), plan.rounds.map { it.theme })
        // Out of range comes back in: every 3 to 15, a split only of the offered lengths.
        assertEquals(BoxingRound(BoxingTheme.BODY, 15, 0), plan.rounds[1])
        assertEquals(1, boxingPlan(plan, 1).rounds.size)
        // A freestyle round cannot be split: there is no coach to call speed and power.
        assertEquals(0, boxingPlan(BoxingPlan(listOf(round(BoxingTheme.FREESTYLE, 5, 30)), BoxingStyle.NAMES), 1).rounds[0].split)
        assertEquals(BoxingPlan(listOf(round(BoxingTheme.MIX, 6), round(BoxingTheme.MIX, 6)), BoxingStyle.NAMES), boxingPlan(null, 2))
    }

    @Test fun `is a timer of rounds with rest between them`() {
        val config = timerConfig(TimerConfig(TimerMode.BOXING, work = 9999, rest = 0, rounds = 99))
        assertEquals(listOf(600, 10, 15), listOf(config.work, config.rest, config.rounds))
        assertEquals(15, config.boxing?.rounds?.size)
        assertEquals(6 * 180 + 5 * 60, timerSeconds(default))
        assertEquals("6 × 3:00 / 1:00", timerSummary(default))
        val view = timerView(short(), 10_000L + 35_000)
        assertEquals(listOf(TimerPhase.REST, 1, 2), listOf(view.phase, view.round, view.rounds))
        // The other modes carry no plan.
        assertNull(timerConfig(TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 8)).boxing)
    }

    @Test fun `draws the same numbers from the same seed`() {
        val a = seededRandom(42)
        val b = seededRandom(42)
        val first = listOf(a(), a(), a())
        assertEquals(first, listOf(b(), b(), b()))
        assertEquals(listOf(601, 448, 852), first.map { Math.floor(it * 1000).toInt() })
        assertTrue(first.all { it >= 0 && it < 1 })
    }

    @Test fun `calls the round, its theme, the combinations and the end of the round`() {
        assertEquals(
            "0:get-ready 10.7:round-1 12.4:box-theme-jab 16:box-double-jab-n 20:box-jab-jab-body-n " +
                "24:box-double-jab-n 28:box-triple-jab-n 30:ten-seconds 32.5:box-jab-jab-body-n 40.7:rest " +
                "50.7:last-round 52.4:box-theme-two 54.5:box-speed 57:box-cross-hook-n 61:box-hook-cross-n " +
                "65:box-power 70:ten-seconds 72.5:box-cross-uppercut-n 80.7:complete",
            spoken(short(), 1),
        )
    }

    @Test fun `says the same plan with numbers, and other combinations with another seed`() {
        assertEquals(
            "0:get-ready 10.7:round-1 12.4:box-theme-jab 16:box-jab-d 20:box-triple-jab-d " +
                "24.5:box-jab-body-d 30:ten-seconds 32.5:box-jab-jab-body-d 40.7:rest " +
                "50.7:last-round 52.4:box-theme-two 54.5:box-speed 57:box-cross-uppercut-d 61:box-jab-cross-d " +
                "65:box-power 70:ten-seconds 72.5:box-jab-uppercut-d 80.7:complete",
            spoken(short(BoxingStyle.NUMBERS), 2),
        )
        assertEquals(boxingCues(short(), DEFAULT_TIMER_CALLS, 1), timerCues(short(), DEFAULT_TIMER_CALLS, 1))
    }

    @Test fun `keeps calling combinations when everything else is switched off, and none in a freestyle round`() {
        val silent = spoken(short(), 1, quiet)
        assertTrue(silent, silent.startsWith("16:box-double-jab-n 20:box-jab-jab-body-n"))
        assertFalse(silent.contains("round-1"))
        assertTrue(silent.contains("box-speed"))
        val free = timerConfig(short().copy(boxing = BoxingPlan(listOf(round(BoxingTheme.FREESTYLE), round(BoxingTheme.FREESTYLE)), BoxingStyle.NAMES)))
        assertEquals(0, comboCount(boxingCues(free, DEFAULT_TIMER_CALLS, 1)))
        assertTrue(spoken(free, 1).contains("12.4:box-theme-freestyle"))
    }

    @Test fun `never calls the same combination twice in a row and never talks over another line`() {
        for (seed in listOf(1, 2, 3, 99, 12345)) {
            val cues = boxingCues(default, DEFAULT_TIMER_CALLS, seed)
            val voice = cues.filter { !it.sound.startsWith("beep") }
            for (index in 1 until voice.size) {
                val gap = voice[index].at - voice[index - 1].at
                assertTrue("${voice[index - 1].sound} then ${voice[index].sound}", gap >= 1500)
                if (soundCombo(voice[index].sound) != null && soundCombo(voice[index - 1].sound) != null) {
                    assertNotEquals(voice[index - 1].sound, voice[index].sound)
                }
            }
        }
        assertEquals(139, comboCount(boxingCues(default, DEFAULT_TIMER_CALLS, 1)))
    }

    @Test fun `says the time left after Speed or Power when both fall on the same moment`() {
        fun split(work: Int, every: Int) =
            timerConfig(TimerConfig(TimerMode.BOXING, work, 10, 1, BoxingPlan(listOf(round(BoxingTheme.MIX, 5, every)), BoxingStyle.NAMES)))
        // 3:00 in 30s stretches: "Power" at 2:30 into the round, which is also thirty seconds before its end.
        assertTrue(spoken(split(180, 30), 1).contains("160:box-power 162.5:thirty-seconds"))
        for (every in listOf(15, 20, 30, 60)) {
            for (work in 30..600 step 5) {
                val voice = boxingCues(split(work, every), DEFAULT_TIMER_CALLS, 1).filter { !it.sound.startsWith("beep") }
                for (index in 1 until voice.size) {
                    val gap = voice[index].at - voice[index - 1].at
                    assertTrue("${every}s stretches, ${work}s: ${voice[index - 1].sound} then ${voice[index].sound}", gap >= 1500)
                }
            }
        }
    }

    @Test fun `lets a long combination be said before the next one is called`() {
        assertEquals(1500L, sayLength("Jab."))
        assertEquals(2600L, sayLength("Jab, cross."))
        assertEquals(5900L, sayLength("Jab, cross, hook, cross, jab."))
        // Few parts but many words: the words decide.
        assertEquals(4080L, sayLength("Jab to the body, cross to the head."))
        // Asked for a call every 3 seconds, long combinations still get their time.
        val fast = timerConfig(TimerConfig(TimerMode.BOXING, 40, 10, 1, BoxingPlan(listOf(round(BoxingTheme.FOUR, 3)), BoxingStyle.NAMES)))
        assertEquals(
            "0:get-ready 10.7:round-1 12.4:box-theme-four 16:box-jab-jab-cross-hook-n 23.3:box-again " +
                "26.8:box-one-more 30.5:box-jab-cross-uppercut-cross-n 37.8:box-again 40:ten-seconds " +
                "42.5:box-cross-hook-cross-hook-n 50.7:complete",
            spoken(fast, 5),
        )
        for (seed in listOf(1, 2, 3)) {
            val calls = boxingCues(fast.copy(work = 180), quiet, seed).filter { soundCombo(it.sound) != null }
            for (index in 1 until calls.size) {
                val said = soundCombo(calls[index - 1].sound)!!.say
                assertTrue(calls[index].at - calls[index - 1].at >= sayLength(said) + 600)
            }
        }
    }

    @Test fun `calls faster in a speed stretch than in a power stretch`() {
        val split = timerConfig(TimerConfig(TimerMode.BOXING, work = 180, rest = 60, rounds = 1, boxing = BoxingPlan(listOf(round(BoxingTheme.MIX, 6, 30)), BoxingStyle.NAMES)))
        val combos = boxingCues(split, quiet, 7).filter { soundCombo(it.sound) != null }
        fun inStretch(from: Int, to: Int) = combos.count { it.at >= 10_000 + from * 1000L && it.at < 10_000 + to * 1000L }
        assertTrue(inStretch(30, 60) < inStretch(60, 90))
        assertEquals(BoxingFocus.SPEED, focusAt(round(BoxingTheme.MIX, 6, 30), 0))
        assertEquals(BoxingFocus.POWER, focusAt(round(BoxingTheme.MIX, 6, 30), 30_000))
        assertEquals(BoxingFocus.SPEED, focusAt(round(BoxingTheme.MIX, 6, 30), 60_000))
        assertNull(focusAt(round(BoxingTheme.MIX, 6, 0), 30_000))
    }

    @Test fun `shows the round, the stretch and the combination called last`() {
        val config = short()
        val cues = boxingCues(config, DEFAULT_TIMER_CALLS, 1)
        // The lead-in shows what round one will be.
        assertEquals(BoxingView(round(BoxingTheme.JAB), null, null), boxingView(config, cues, 3_000))
        // Before the first call of a round there is nothing to throw yet.
        assertNull(boxingView(config, cues, 14_000).combo)
        assertEquals("double-jab", boxingView(config, cues, 17_000).combo?.id)
        assertEquals("jab-jab-body", boxingView(config, cues, 23_000).combo?.id)
        // The rest shows the round that comes next.
        assertEquals(BoxingView(round(BoxingTheme.TWO, 5, 15), null, null), boxingView(config, cues, 45_000))
        assertEquals(BoxingFocus.SPEED, boxingView(config, cues, 58_000).focus)
        assertEquals("cross-hook", boxingView(config, cues, 58_000).combo?.id)
        assertEquals(BoxingFocus.POWER, boxingView(config, cues, 66_000).focus)
    }

    @Test fun `describes a round and edits a plan`() {
        assertEquals("Jab only · Fast", roundSummary(round(BoxingTheme.JAB)))
        assertEquals("Mix · Steady · speed and power 30s", roundSummary(round(BoxingTheme.MIX, 6, 30)))
        assertEquals("3-punch combos · Fast · 2 picked", roundSummary(round(BoxingTheme.THREE).copy(pick = listOf("jab-cross-hook", "jab-jab-cross"))))
        assertEquals("Easy", paceOf(8))
        assertEquals("All out", paceOf(3))
        assertEquals("Easy", paceOf(15))
        assertEquals("Freestyle", roundSummary(round(BoxingTheme.FREESTYLE)))
        val plan = boxingPlan(BoxingPlan(listOf(round(BoxingTheme.JAB), round(BoxingTheme.TWO), round(BoxingTheme.THREE)), BoxingStyle.NAMES), 3)
        assertEquals(listOf(BoxingTheme.JAB, BoxingTheme.BODY, BoxingTheme.THREE), withRound(plan, 1, round(BoxingTheme.BODY, 7)).rounds.map { it.theme })
        assertEquals(List(3) { round(BoxingTheme.THREE) }, copiedToAll(plan, 2).rounds)
        assertSame(plan, copiedToAll(plan, 9))
    }

    @Test fun `lays a template over any number of rounds by repeating it, in the style the lifter chose`() {
        assertEquals(listOf("Ladder", "Speed and power", "Head and body", "Classic", "Pad work", "Full session"), BOXING_TEMPLATES.map { it.name })
        val ladder = templatePlan(BOXING_TEMPLATES[0].rounds, 6, BoxingStyle.NUMBERS)
        assertEquals(BoxingStyle.NUMBERS, ladder.style)
        assertEquals(listOf(BoxingTheme.JAB, BoxingTheme.TWO, BoxingTheme.THREE, BoxingTheme.FOUR, BoxingTheme.JAB, BoxingTheme.TWO), ladder.rounds.map { it.theme })
        assertTrue(templatePlan(BOXING_TEMPLATES[1].rounds, 3, BoxingStyle.NAMES).rounds.all { it.split == 30 })
    }

    @Test fun `calls only the picked combinations of a theme`() {
        val picked = round(BoxingTheme.THREE).copy(pick = listOf("jab-cross-hook", "jab-jab-cross"))
        assertEquals(listOf("jab-cross-hook", "jab-jab-cross"), roundCombos(picked).map { it.id })
        assertEquals(7, roundCombos(round(BoxingTheme.THREE)).size)
        // A pick from another theme does not survive: the round would have nothing to call.
        val cleaned = boxingPlan(BoxingPlan(listOf(round(BoxingTheme.JAB).copy(pick = listOf("jab", "jab-cross-hook", "nope"))), BoxingStyle.NAMES), 1)
        assertEquals(listOf("jab"), cleaned.rounds[0].pick)
        val config = timerConfig(TimerConfig(TimerMode.BOXING, 30, 10, 1, BoxingPlan(listOf(picked), BoxingStyle.NAMES)))
        val called = boxingCues(config, quiet, 3).map { it.sound }.filter { soundCombo(it) != null }
        assertEquals(setOf("box-jab-jab-cross-n", "box-jab-cross-hook-n"), called.toSet())
        // One picked combination is called again and again.
        val one = config.copy(boxing = BoxingPlan(listOf(round(BoxingTheme.THREE).copy(pick = listOf("jab-cross-hook"))), BoxingStyle.NAMES))
        assertEquals(setOf("box-jab-cross-hook-n", "box-again", "box-one-more"), boxingCues(one, quiet, 1).map { it.sound }.toSet())
    }

    @Test fun `drills a long combination - says it once, then calls for it again once or twice`() {
        assertEquals(3, DRILL_FROM)
        assertEquals(2000L, throwTime(3))
        assertEquals(2500L, throwTime(4))
        val config = timerConfig(
            TimerConfig(TimerMode.BOXING, 60, 10, 1, BoxingPlan(listOf(round(BoxingTheme.THREE).copy(pick = listOf("jab-cross-hook", "jab-jab-cross"))), BoxingStyle.NAMES)),
        )
        val cues = boxingCues(config, quiet, 3)
        assertEquals(
            "16:box-jab-jab-cross-n 21.7:box-again 25:box-jab-cross-hook-n 30.7:box-again " +
                "34:box-jab-jab-cross-n 39.7:box-again 42.7:box-one-more 46:box-jab-cross-hook-n 51.7:box-again " +
                "55:box-jab-jab-cross-n 60.7:box-again 64:box-jab-cross-hook-n",
            cues.joinToString(" ") { "${seconds(it.at)}:${it.sound}" },
        )
        assertTrue(isAgain("box-again"))
        assertTrue(isAgain("box-one-more"))
        assertFalse(isAgain("box-jab-n"))
        // "Again" is not another combination.
        assertEquals(6, comboCount(cues))
        // The screen counts the goes: one of two, then two of two.
        fun goes(active: Long) = boxingView(config, cues, active).let { Triple(it.combo?.id, it.go, it.goes) }
        assertEquals(Triple(null, 0, 0), goes(15_000))
        assertEquals(1 to 2, goes(17_000).let { it.second to it.third })
        assertEquals(2 to 2, goes(23_000).let { it.second to it.third })
        assertEquals(1 to 3, goes(35_000).let { it.second to it.third })
        assertEquals(3 to 3, goes(43_000).let { it.second to it.third })
        // Short combinations are thrown once and move on.
        assertFalse(boxingCues(short(), quiet, 1).any { isAgain(it.sound) })
        val view = boxingView(short(), boxingCues(short(), DEFAULT_TIMER_CALLS, 1), 17_000)
        assertEquals(1 to 1, view.go to view.goes)
        // A three-minute round of three-punch combinations: 18 of them instead of a call every few seconds.
        val long = timerConfig(TimerConfig(TimerMode.BOXING, 180, 10, 1, BoxingPlan(listOf(round(BoxingTheme.THREE)), BoxingStyle.NAMES)))
        assertEquals(18, comboCount(boxingCues(long, quiet, 1)))
    }

    @Test fun `keeps calling through speed and power, with the short combinations in speed`() {
        fun plain(split: Int) = comboCount(
            boxingCues(timerConfig(TimerConfig(TimerMode.BOXING, 180, 60, 1, BoxingPlan(listOf(round(BoxingTheme.JAB, 5, split)), BoxingStyle.NAMES))), DEFAULT_TIMER_CALLS, 1),
        )
        assertEquals(listOf(33, 31, 35), listOf(plain(0), plain(15), plain(30)))
        assertTrue(speedCombos(themeCombos(BoxingTheme.FOUR)).all { it.say.split(",").size <= 5 })
        assertTrue(speedCombos(themeCombos(BoxingTheme.MIX)).all { it.say.split(",").size <= 2 })
    }

    @Test fun `moves, copies and removes rounds`() {
        val plan = boxingPlan(BoxingPlan(listOf(round(BoxingTheme.JAB), round(BoxingTheme.TWO), round(BoxingTheme.THREE)), BoxingStyle.NAMES), 3)
        fun themes(edited: BoxingPlan) = edited.rounds.map { it.theme }
        assertEquals(listOf(BoxingTheme.TWO, BoxingTheme.JAB, BoxingTheme.THREE), themes(movedRound(plan, 0, 1)))
        assertEquals(listOf(BoxingTheme.JAB, BoxingTheme.THREE, BoxingTheme.TWO), themes(movedRound(plan, 2, -1)))
        assertSame(plan, movedRound(plan, 0, -1))
        assertEquals(listOf(BoxingTheme.JAB, BoxingTheme.TWO, BoxingTheme.TWO, BoxingTheme.THREE), themes(duplicatedRound(plan, 1)))
        assertEquals(listOf(BoxingTheme.TWO, BoxingTheme.THREE), themes(withoutRound(plan, 0)))
        val single = boxingPlan(BoxingPlan(listOf(round(BoxingTheme.JAB)), BoxingStyle.NAMES), 1)
        assertSame(single, withoutRound(single, 0))
        assertEquals(15, duplicatedRound(boxingPlan(BoxingPlan(listOf(round(BoxingTheme.JAB)), BoxingStyle.NAMES), 15), 0).rounds.size)
    }
}
