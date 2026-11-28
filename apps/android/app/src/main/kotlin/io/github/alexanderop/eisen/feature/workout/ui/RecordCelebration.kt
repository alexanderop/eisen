package io.github.alexanderop.eisen.feature.workout.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alexanderop.eisen.feature.workout.Workout
import kotlinx.coroutines.delay

/** How long "New record" stays for TalkBack before the word goes again. */
private const val SAID_MS = 4_000L

/** A burst to play ([burst] goes up by one each time) and what TalkBack is told ([said], empty when nothing). */
internal class Celebration {
    var burst by mutableIntStateOf(0)
    var said by mutableStateOf("")
}

/**
 * Celebrates each exercise of [workout] that joins [records] while the screen shows. The ones that already held a
 * record when it opened (the app came back, the workout was reopened) are not celebrated again.
 */
@Composable
internal fun rememberCelebration(records: Set<String>, workout: Workout): Celebration {
    val celebration = remember(workout.id) { Celebration() }
    val celebrated = remember(workout.id) { records.toMutableSet() }
    LaunchedEffect(records) {
        val fresh = records - celebrated
        if (fresh.isEmpty()) return@LaunchedEffect
        celebrated += fresh
        celebration.burst++
        val names = workout.exercises.filter { it.id in fresh }.joinToString(", ") { it.name }
        celebration.said = "New record: $names"
        delay(SAID_MS)
        celebration.said = ""
    }
    return celebration
}

/**
 * The word for TalkBack: a polite live region outside the workout's cards, so it is heard also while the rest screen
 * covers them. Nothing on screen shows it.
 */
@Composable
internal fun RecordAnnouncement(said: String) {
    if (said.isEmpty()) return
    Box(
        Modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = said
        },
    )
}
