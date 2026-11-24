package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.BOXING_COMBOS
import io.github.alexanderop.eisen.domain.DRILL_FROM
import io.github.alexanderop.eisen.domain.roundCombos
import io.github.alexanderop.eisen.domain.BOXING_THEMES
import io.github.alexanderop.eisen.domain.BoxingCombo
import io.github.alexanderop.eisen.domain.BoxingRound
import io.github.alexanderop.eisen.domain.BoxingStyle
import io.github.alexanderop.eisen.domain.BoxingTheme
import io.github.alexanderop.eisen.domain.THEME_EXAMPLES
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.roundSummary
import io.github.alexanderop.eisen.domain.themeCombos
import io.github.alexanderop.eisen.domain.timerSeconds
import io.github.alexanderop.eisen.domain.BoxingCues
import io.github.alexanderop.eisen.domain.BoxingStance
import io.github.alexanderop.eisen.domain.OwnCombo
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.burnoutOf
import io.github.alexanderop.eisen.domain.ownCombo

/*
 * What the boxing setup and run say, the same words as the PWA's `domain/boxingScreens.ts`.
 */

/** "6 rounds · 22:00 total". */
fun planTotal(config: TimerConfig): String =
    "${config.rounds} ${if (config.rounds == 1) "round" else "rounds"} · ${holdText(timerSeconds(config) ?: 0)} total"

/** A round's line under its theme name: `roundSummary` without the theme. "Fast · 2 picked", "no calls", "punch by punch". */
fun roundDetail(round: BoxingRound): String = when (round.theme) {
    BoxingTheme.FREESTYLE -> "no calls${if (round.cues != BoxingCues.OFF) " · reminders" else ""}"
    BoxingTheme.BUILD -> "punch by punch${roundSummary(round).removePrefix(BoxingTheme.BUILD.title)}"
    else -> roundSummary(round).removePrefix("${round.theme.title} · ")
}

/**
 * What a theme's tile shows: "Double jab" or "1 – 1", "Any combination" for the mix, "No calls" for
 * freestyle, the start of a ladder for the build-up, and the lifter's first own combination, or
 * "Make one first" while there is none.
 */
fun themeExample(theme: BoxingTheme, style: BoxingStyle, own: List<OwnCombo> = emptyList()): String {
    val numbers = style == BoxingStyle.NUMBERS
    return when (theme) {
        BoxingTheme.MIX -> "Any combination"
        BoxingTheme.BUILD -> if (numbers) "1 → 1 – 2 → …" else "Jab → Jab · Cross → …"
        BoxingTheme.OWN -> own.firstOrNull()?.let { shownCombo(ownCombo(it), style) } ?: "Make one first"
        else -> BOXING_COMBOS.firstOrNull { it.id == THEME_EXAMPLES[theme] }?.let { shownCombo(it, style) } ?: "No calls"
    }
}

/** A combination as the chosen style shows it: "Jab · Cross" or "1 – 2". */
fun shownCombo(combo: BoxingCombo, style: BoxingStyle): String = if (style == BoxingStyle.NUMBERS) combo.numbers else combo.names

/** "6 combinations", "43 combinations", "No combinations". */
fun comboTotal(theme: BoxingTheme, own: List<OwnCombo> = emptyList()): String {
    val count = themeCombos(theme, own).size
    return if (count == 0) "No combinations" else "$count ${if (count == 1) "combination" else "combinations"}"
}

/** "Combinations: all 7", "Combinations: 2 of 7". */
fun pickText(round: BoxingRound, own: List<OwnCombo> = emptyList()): String {
    val all = themeCombos(round.theme, own).size
    return if (round.pick.isEmpty()) "Combinations: all $all" else "Combinations: ${round.pick.size} of $all"
}

/** The picks with one combination switched on or off. All of them on is the same as none. */
fun toggledPick(round: BoxingRound, id: String, own: List<OwnCombo> = emptyList()): List<String> {
    val all = themeCombos(round.theme, own).map { it.id }
    val current = round.pick.ifEmpty { all }
    val next = if (id in current) current - id else current + id
    return if (next.size == all.size) emptyList() else all.filter { it in next }
}

/**
 * The combinations to pick from, in groups: one group for a theme, one per theme for the mix, with
 * the lifter's own last.
 */
fun comboGroups(theme: BoxingTheme, own: List<OwnCombo> = emptyList()): List<Pair<BoxingTheme, List<BoxingCombo>>> {
    if (theme != BoxingTheme.MIX) return listOf(theme to themeCombos(theme, own))
    val catalog = BOXING_THEMES.map { entry -> entry to BOXING_COMBOS.filter { it.themes.first() == entry } }.filter { it.second.isNotEmpty() }
    return if (own.isEmpty()) catalog else catalog + (BoxingTheme.OWN to own.map(::ownCombo))
}

/** What the numbers mean, in one line. */
const val NUMBERS_KEY = "1 jab · 2 cross · 3 hook · 4 rear hook · 5 and 6 uppercut · B to the body"

/** Which hand throws what, the line under the stance. */
fun stanceKey(stance: BoxingStance): String = when (stance) {
    BoxingStance.ORTHODOX -> "Jab and lead hook: left hand. Cross: right hand."
    BoxingStance.SOUTHPAW -> "Jab and lead hook: right hand. Cross: left hand."
}

/** The line under the reminders of a round. */
fun remindersText(theme: BoxingTheme): String = when (theme) {
    BoxingTheme.WARMUP -> "A warm-up always has them"
    BoxingTheme.FREESTYLE -> "The only thing the coach says in a freestyle round"
    else -> "Between combinations the coach reminds you: Move! Hands up! Breathe!"
}

/** The line under the non-stop finish of a round [work] seconds long. */
fun burnoutText(round: BoxingRound, work: Int): String = when {
    round.burnout > 0 && burnoutOf(round, work) == 0 -> "Needs a round of at least ${holdText(round.burnout * 2).let { if (it.endsWith("s")) "0:${it.dropLast(1)}" else it }}"
    round.burnout == 0 -> "The end of the round: short combinations back to back"
    else -> "The last ${round.burnout} seconds: short combinations back to back"
}

/** "About 480 punches", under the combinations of a boxing result. */
fun punchesText(count: Int): String = if (count == 1) "About one punch" else "About $count punches"

/**
 * "62 more than last time", "40 fewer than last time", "Same as last time": the punches of [session]
 * against the boxing session before it in the history, when that one counted them. Only a workout of
 * the same length is a fair comparison: two rounds are not fewer punches than six.
 */
fun punchesGap(session: TimerSession, sessions: List<TimerSession>): String? {
    val punches = session.punches ?: return null
    val at = sessions.indexOfFirst { it.id == session.id }
    val before = (if (at == -1) sessions else sessions.drop(at + 1)).firstOrNull { it.config.mode == TimerMode.BOXING && it.id != session.id && sameLength(it, session) } ?: return null
    val diff = punches - (before.punches ?: return null)
    return when {
        diff > 0 -> "$diff more than last time"
        diff < 0 -> "${-diff} fewer than last time"
        else -> "Same as last time"
    }
}

/** Which of a round's combinations the coach drills, "Again!" after the first go: all of them, only the long ones, or none. */
enum class Drill { ALL, LONG, NONE }

fun drillOf(round: BoxingRound, own: List<OwnCombo> = emptyList()): Drill {
    // A build-up drills every step, a single jab too.
    if (round.theme == BoxingTheme.BUILD) return Drill.ALL
    val pool = if (round.theme == BoxingTheme.FREESTYLE) emptyList() else roundCombos(round, own)
    val long = pool.count { (it.moves?.size ?: it.say.split(",").size) >= DRILL_FROM }
    return when (long) {
        0 -> Drill.NONE
        pool.size -> Drill.ALL
        else -> Drill.LONG
    }
}

/**
 * "About 28 calls this round, one every 6s or so", and with a drill "About 18 combinations this
 * round, each thrown 2 or 3 times" or "..., long ones thrown 2 or 3 times".
 */
fun callsText(count: Int, work: Int, drill: Drill = Drill.NONE): String {
    if (count == 0) return "No calls this round"
    if (drill != Drill.NONE) {
        val combos = if (count == 1) "One combination this round" else "About $count combinations this round"
        return "$combos, ${if (drill == Drill.ALL) "each" else "long ones"} thrown 2 or 3 times"
    }
    if (count == 1) return "One call this round"
    return "About $count calls this round, one every ${Math.floor(work.toDouble() / count + 0.5).toInt()}s or so"
}

/** "166 combinations called", "One combination called". */
fun combosText(count: Int): String = if (count == 1) "One combination called" else "$count combinations called"

/** "Up next · Round 3", for the lead-in and the rest before a round. */
fun upNext(roundNumber: Int): String = "Up next · Round $roundNumber"

private fun sameLength(a: TimerSession, b: TimerSession): Boolean =
    a.config.rounds == b.config.rounds && a.config.work == b.config.work && a.completed == b.completed
