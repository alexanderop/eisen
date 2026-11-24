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
