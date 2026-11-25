package io.github.alexanderop.eisen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the architecture promises and the compiler does not check: the domain is pure. It reads no
 * clock, rolls no dice, touches no file and no Android, and it has no way to blow up on a null.
 * Time, ids and randomness come in through `Env` and seeds; everything else is a `Cmd`.
 */
class RulesTest {
    private val root = File("src/main/kotlin/io/github/alexanderop/eisen")

    /** The pure packages: `core`, `feature` and `app` without their `ui`, `platform` and `runtime` parts (see `ArchitectureTest`). */
    private val domain = root.walk().filter { file ->
        val dirs = file.parentFile.relativeTo(root).invariantSeparatorsPath.split("/")
        file.extension == "kt" && dirs.first() in setOf("core", "feature", "app") && dirs.none { it in setOf("ui", "platform", "runtime") }
    }.toList()

    private val forbidden = listOf(
        Regex("!!") to "asserts a value is not null: say what happens when it is",
        Regex("""^import (android|androidx)\.""") to "imports Android: that belongs in runtime or ui",
        Regex("""^import java\.(io|net|nio\.file|nio\.channels)\.""") to "does input or output: that is a Cmd",
        Regex("""System\.(currentTimeMillis|nanoTime)|Instant\.now|LocalDate(Time)?\.now""") to "reads the clock: take the time from Env",
        Regex("""Math\.random|\bRandom\(\)|UUID\.randomUUID""") to "rolls dice of its own: take a seed or an id from Env",
        Regex("""\bthrow [A-Z]""") to "throws: give the failure back as a value",
    )

    @Test fun `the domain stays pure`() {
        val files = domain
        assertTrue("no domain files under ${root.absolutePath}", files.size > 10)
        val broken = files.flatMap { file ->
            file.readLines().flatMapIndexed { index, line ->
                forbidden.filter { (rule) -> rule.containsMatchIn(line) }.map { (_, why) -> "${file.relativeTo(root)}:${index + 1} $why\n    ${line.trim()}" }
            }
        }
        assertEquals("The domain has to stay pure:\n${broken.joinToString("\n")}\n", 0, broken.size)
    }

    /**
     * How a timer run ended has one reader, `timerOutcome`: a screen, Health Connect or a comparison that
     * read the raw `completed` flag would call a For Time that ran into its cap "completed" or "stopped
     * early" where the app says "Time cap". Every source of the app is checked, the Android side too.
     */
    @Test fun `only timerOutcome reads whether a timer run was completed`() {
        val reads = Regex("""\.completed\b""")
        val owner = File(root, "feature/timer/Timer.kt")
        val ownerLines = owner.readLines()
        val start = ownerLines.indexOfFirst { it.startsWith("fun timerOutcome(") }
        val end = (start until ownerLines.size).first { ownerLines[it] == "}" }
        val broken = root.walk().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val allowed = file == owner && index in start..end
                if (reads.containsMatchIn(line) && !allowed) "${file.relativeTo(root)}:${index + 1}\n    ${line.trim()}" else null
            }
        }.toList()
        assertTrue("timerOutcome is gone from ${owner.path}", start >= 0)
        assertEquals("Read timerOutcome(session) instead of session.completed:\n${broken.joinToString("\n")}\n", 0, broken.size)
    }

    private val sources = root.walk().filter { it.extension == "kt" }.toList()

    /** The text of the call that starts at [open], the index of its "(", up to its matching ")". */
    private fun callAt(text: String, open: Int): String {
        var depth = 0
        for (at in open until text.length) {
            when (text[at]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return text.substring(open, at + 1)
            }
        }
        return text.substring(open)
    }

    private fun lineOf(text: String, at: Int) = text.substring(0, at).count { it == '\n' } + 1

    /**
     * A number without a limit (QA build 19: 9999999 reps typed into the reps question crashed the app).
     * Every stepper says how far it goes: the default of none lets any number through to the model.
     */
    @Test fun `every number entry names its maximum`() {
        val call = Regex("""(?<![\w.])(IntStepper|Stepper)\(""")
        val broken = sources.flatMap { file ->
            val text = file.readText()
            call.findAll(text).filterNot { text.substring(maxOf(0, it.range.first - 4), it.range.first) == "fun " }.mapNotNull { match ->
                val args = callAt(text, match.range.last)
                if (Regex("""\bmax\s*=""").containsMatchIn(args)) null else "${file.relativeTo(root)}:${lineOf(text, match.range.first)} ${match.value} without max ="
            }.toList()
        }
        assertEquals("Give each of these a max (a constant of the domain):\n${broken.joinToString("\n")}\n", 0, broken.size)
    }

    /** What a wheel's values may be built with: functions `LimitsTest` checks stay short for any value, or a range up to a constant. */
    private val boundedWheels = Regex("""\b(repsChoices|weightChoices|timerWheelSeconds|timerCapSeconds|wodAmounts|wodLoads)\(|\(\d+\.\.[A-Z][A-Z_]+\)|coerceAtMost\([A-Z][A-Z_]+\)""")

    /**
     * A wheel holds a row per value. Its values come from a builder that stays short whatever the value it
     * shows (`LimitsTest`), or from a range up to a constant: never from a range up to a value of the model.
     * A wheel that takes its values as a parameter is checked where it is called.
     */
    @Test fun `every wheel is built from a bounded list`() {
        val wheel = Regex("""(?<![\w.])Wheel\(""")
        val broken = mutableListOf<String>()
        for (file in sources) {
            val text = file.readText()
            for (match in wheel.findAll(text)) {
                if (text.substring(maxOf(0, match.range.first - 4), match.range.first) == "fun ") continue
                val args = callAt(text, match.range.last)
                val name = Regex("""labels\s*=\s*remember(\([^)]*\))?\s*\{\s*(\w+)\.map""").find(args)?.groupValues?.get(2)
                val where = "${file.relativeTo(root)}:${lineOf(text, match.range.first)}"
                if (name == null) { broken += "$where: labels not as remember(...) { values.map ... }"; continue }
                val before = text.substring(0, match.range.first)
                val local = Regex("""val $name = (.*)""").findAll(before).lastOrNull()?.groupValues?.get(1)
                val enclosing = Regex("""fun (\w+)\(""").findAll(before).lastOrNull()?.groupValues?.get(1)
                val parameter = enclosing != null && Regex("""fun $enclosing\([^{]*\b$name: List<""").containsMatchIn(text)
                when {
                    local != null -> if (!boundedWheels.containsMatchIn(local)) broken += "$where: $name = $local"
                    parameter -> for (other in sources) {
                        val otherText = other.readText()
                        for (use in Regex("""(?<![\w.])$enclosing\(""").findAll(otherText)) {
                            if (otherText.substring(maxOf(0, use.range.first - 4), use.range.first) == "fun ") continue
                            val passed = callAt(otherText, use.range.last)
                            if (!boundedWheels.containsMatchIn(passed)) broken += "${other.relativeTo(root)}:${lineOf(otherText, use.range.first)}: $enclosing gets $passed"
                        }
                    }
                    else -> broken += "$where: cannot tell where $name comes from"
                }
            }
        }
        assertTrue("checked no wheel", sources.any { wheel.containsMatchIn(it.readText()) })
        assertEquals("Build these wheels from a bounded list:\n${broken.joinToString("\n")}\n", 0, broken.size)
    }

    /**
     * A count with a fixed plural (QA build 19: TalkBack said "1 reps in reserve"). A number of a value
     * goes through `counted`, unless the line itself tells 1 apart.
     */
    @Test fun `a count in a text says 1 rep, not 1 reps`() {
        val fixed = Regex(""""[^"\n]*\$\{?([a-z][\w.!]*(\([^)]*\))?)\}? (reps|rounds|seconds|minutes|sets|workouts|holds|punches|combinations|movements)\b""")
        val broken = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (fixed.containsMatchIn(line) && !line.contains("== 1") && !line.contains("> 1 ->")) "${file.relativeTo(root)}:${index + 1}\n    ${line.trim()}" else null
            }
        }
        assertEquals("Say these counts with counted(n, \"rep\"):\n${broken.joinToString("\n")}\n", 0, broken.size)
    }

    /**
     * A cue nobody hears (QA build 19: the end of a rest after the process died was dropped as a vibration
     * from the background, `usage: UNKNOWN`). Android lets an app in the background vibrate only with the
     * usage of an alarm, a ringtone or a notification, and restarts a killed foreground service only when it
     * is sticky.
     */
    @Test fun `cues vibrate as an alarm and the services that play them come back`() {
        val vibrations = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (Regex("""\.vibrate\(VibrationEffect""").containsMatchIn(line) && !line.contains("VibrationAttributes")) "${file.relativeTo(root)}:${index + 1}\n    ${line.trim()}" else null
            }
        }
        assertEquals("Vibrate with VibrationAttributes (USAGE_ALARM for a cue):\n${vibrations.joinToString("\n")}\n", 0, vibrations.size)
        val services = sources.filter { it.readText().contains(": Service()") }
        assertTrue("no services under ${root.absolutePath}", services.size >= 2)
        val notSticky = services.filterNot { it.readText().contains("return START_STICKY") }.map { it.relativeTo(root) }
        assertEquals("A foreground service that keeps a cue must return START_STICKY: $notSticky", emptyList<File>(), notSticky)
    }

    @Test fun `a failing walk is cut down to the steps that matter`() {
        val walk = (1..300).toList()
        // Fails whenever 7 comes before 213, whatever else is there.
        val fails = { part: List<Int> -> part.indexOf(7) in 0 until part.indexOf(213) }
        assertEquals(listOf(7, 213), shrink(walk, fails))
        // One step is enough, at the start, in the middle and at the end.
        for (culprit in listOf(1, 150, 300)) assertEquals(listOf(culprit), shrink(walk) { culprit in it })
        // Three that all have to stay, in their order.
        assertEquals(listOf(2, 99, 298), shrink(walk) { it.containsAll(listOf(2, 99, 298)) })
        // Nothing can go: the walk stays as it is.
        assertEquals(listOf(4, 5), shrink(listOf(4, 5)) { it.size == 2 })
    }
}
