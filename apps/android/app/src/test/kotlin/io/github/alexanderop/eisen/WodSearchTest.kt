package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.timer.wod.SuggestionKind
import io.github.alexanderop.eisen.feature.timer.wod.WOD_LIBRARY
import io.github.alexanderop.eisen.feature.timer.wod.WodSuggestion
import io.github.alexanderop.eisen.core.catalogExercise
import io.github.alexanderop.eisen.feature.timer.wod.matchRank
import io.github.alexanderop.eisen.feature.timer.wod.searchText
import io.github.alexanderop.eisen.feature.timer.wod.searchWods
import io.github.alexanderop.eisen.feature.timer.wod.suggestWods
import io.github.alexanderop.eisen.feature.timer.wod.wod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The same inputs and expected values as the PWA's `wodSearch.test.ts`. */
class WodSearchTest {
    private val find = ::catalogExercise
    private fun names(typed: String) = searchWods(WOD_LIBRARY, typed, find).map { it.name }

    @Test fun `compares without case, accents or punctuation`() {
        assertEquals("pull ups", searchText("  Pull-ups "))
        assertEquals("cafe roll", searchText("Café  Röll"))
        assertEquals("21 15 9 for time", searchText("21-15-9 · For Time"))
    }

    @Test fun `ranks a start above a word start above somewhere inside`() {
        assertEquals(0, matchRank("clean and jerk", "clean"))
        assertEquals(1, matchRank("clean and jerk", "jerk"))
        assertEquals(2, matchRank("cable crunch", "run"))
        assertNull(matchRank("push ups", "pull"))
        // Every word has to match; the weakest one decides.
        assertEquals(1, matchRank("hang power clean", "pow cl"))
        assertNull(matchRank("hang power clean", "pow x"))
        assertNull(matchRank("anything", ""))
    }

    @Test fun `answers an empty search with everything`() {
        assertSame(WOD_LIBRARY, searchWods(WOD_LIBRARY, "  ", find))
    }

    @Test fun `finds a WOD by its name, best match first`() {
        assertEquals(listOf("Fran"), names("fran"))
        assertEquals(listOf("Murph"), names("MUR"))
        // "ar": Ares starts with it, Mary and Karen and Barbara have it inside.
        assertEquals(listOf("Ares", "Karen", "Barbara", "Mary"), names("ar").take(4))
    }

    @Test fun `finds WODs by what kind they are and by what is in them`() {
        assertEquals(listOf("Cindy", "Mary"), names("amrap"))
        assertEquals(listOf("Fran", "Diane"), names("21-15-9"))
        assertEquals(listOf("Chelsea"), names("emom"))
        assertEquals(listOf("Fran", "Jackie"), names("thruster"))
        assertEquals(listOf("Aphrodite", "Helios"), names("burpee"))
        assertEquals(listOf("Helen"), names("kettle"))
        assertEquals(emptyList<String>(), names("zzz"))
    }

    @Test fun `puts a name match above a movement match`() {
        val own = wod(WOD_LIBRARY[0].copy(id = "mine", name = "Pull day"))
        assertEquals(listOf("Pull day", "Cindy"), searchWods(listOf(WOD_LIBRARY[1], own), "pull", find).map { it.name })
    }

    @Test fun `suggests WODs by name, then movements with how many WODs use them`() {
        assertEquals(emptyList<WodSuggestion>(), suggestWods(WOD_LIBRARY, "", find))
        assertEquals(listOf(WodSuggestion(SuggestionKind.WOD, "wod-fran", "Fran", "21-15-9 · For Time")), suggestWods(WOD_LIBRARY, "fr", find))
        val pull = suggestWods(WOD_LIBRARY, "pu", find)
        assertEquals(listOf("Pull-ups", "Push-ups", "Push Jerk", "Handstand Push-up"), pull.map { it.text })
        assertEquals(WodSuggestion(SuggestionKind.MOVEMENT, "pullups", "Pull-ups", "11 WODs"), pull[0])
        val a = suggestWods(WOD_LIBRARY, "a", find)
        // "Fran" has an a inside: that is a search hit, not a suggestion.
        assertEquals(listOf("Annie", "Angie", "Aphrodite", "Ares", "Air Squat", "Clean and Jerk"), a.map { it.text })
    }
}
