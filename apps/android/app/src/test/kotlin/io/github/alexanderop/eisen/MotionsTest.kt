package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.core.CATALOG
import io.github.alexanderop.eisen.core.MOTIONS
import io.github.alexanderop.eisen.core.MOTION_BACKGROUND
import io.github.alexanderop.eisen.core.MOTION_FRAMES
import io.github.alexanderop.eisen.core.MOTION_FRAME_MS
import io.github.alexanderop.eisen.core.MOTION_STILL_FRAME
import io.github.alexanderop.eisen.core.motionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The moving pictures: the same map and numbers as `apps/web/src/domain/motions.ts`, and the strips that are there. */
class MotionsTest {
    private val web = File("../../web/src/domain/motions.ts").readText()

    @Test fun `an exercise has its strip, some share one, the others have none`() {
        assertEquals("burpee", motionOf("burpee"))
        assertEquals("pull-up", motionOf("pullups"))
        assertEquals("pull-up", motionOf("chinups"))
        assertEquals("run", motionOf("sprint"))
        assertNull(motionOf("bench"))
        assertNull(motionOf("custom-1"))
    }

    @Test fun `the map and the numbers are the PWA's`() {
        val body = web.substringAfter("MOTIONS").substringAfter("{").substringBefore("}")
        val entries = Regex("""(\w+):\s*'([^']+)'""").findAll(body).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(entries, MOTIONS)
        fun number(name: String) = Regex("""$name = '?([#\w]+)'?""").find(web)!!.groupValues[1]
        assertEquals(number("MOTION_FRAMES"), MOTION_FRAMES.toString())
        assertEquals(number("MOTION_FRAME_MS"), MOTION_FRAME_MS.toString())
        assertEquals(number("MOTION_STILL_FRAME"), MOTION_STILL_FRAME.toString())
        assertEquals(number("MOTION_BACKGROUND").removePrefix("#").uppercase(), "%06X".format(MOTION_BACKGROUND and 0xFFFFFF))
    }

    @Test fun `every exercise with a strip is in the catalog`() {
        val ids = CATALOG.map { it.id }.toSet()
        assertEquals(emptySet<String>(), MOTIONS.keys - ids)
    }

    @Test fun `every strip is there and six square frames wide`() {
        val folder = File("../../web/src/assets/motions")
        for (file in MOTIONS.values.toSet().map { File(folder, "$it.webp") }) {
            assertTrue("${file.name} is there", file.exists())
            val (width, height) = webpSize(file.readBytes())
            assertEquals("${file.name} is $width×$height", height * MOTION_FRAMES, width)
        }
    }

    /** The canvas size from a WebP header: lossy, lossless or extended. */
    private fun webpSize(bytes: ByteArray): Pair<Int, Int> {
        fun u(at: Int) = bytes[at].toInt() and 0xFF
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WEBP", String(bytes, 8, 4))
        return when (String(bytes, 12, 4)) {
            "VP8X" -> (1 + (u(24) or (u(25) shl 8) or (u(26) shl 16))) to (1 + (u(27) or (u(28) shl 8) or (u(29) shl 16)))
            "VP8L" -> {
                val bits = u(21) or (u(22) shl 8) or (u(23) shl 16) or (u(24) shl 24)
                (1 + (bits and 0x3FFF)) to (1 + ((bits shr 14) and 0x3FFF))
            }
            "VP8 " -> ((u(26) or (u(27) shl 8)) and 0x3FFF) to ((u(28) or (u(29) shl 8)) and 0x3FFF)
            else -> error("Not a WebP picture")
        }
    }
}
