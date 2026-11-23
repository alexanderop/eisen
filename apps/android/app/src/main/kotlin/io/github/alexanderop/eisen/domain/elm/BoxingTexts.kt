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

/*
 * What the boxing setup and run say, the same words as the PWA's `domain/boxingScreens.ts`.
 */

/** "6 rounds · 22:00 total". */
fun planTotal(config: TimerConfig): String =
    "${config.rounds} ${if (config.rounds == 1) "round" else "rounds"} · ${holdText(timerSeconds(config) ?: 0)} total"

/** A round's line under its theme name: `roundSummary` without the theme. "Fast · 2 picked", "no calls". */
fun roundDetail(round: BoxingRound): String =
    if (round.theme == BoxingTheme.FREESTYLE) "no calls" else roundSummary(round).removePrefix("${round.theme.title} · ")

/** What a theme's tile shows: "Double jab" or "1 – 1", "Any combination" for the mix, "No calls" for freestyle. */
fun themeExample(theme: BoxingTheme, style: BoxingStyle): String {
    if (theme == BoxingTheme.MIX) return "Any combination"
    val combo = BOXING_COMBOS.firstOrNull { it.id == THEME_EXAMPLES[theme] } ?: return "No calls"
    return if (style == BoxingStyle.NUMBERS) combo.numbers else combo.names
}

/** "6 combinations", "43 combinations", "No combinations". */
fun comboTotal(theme: BoxingTheme): String {
    val count = themeCombos(theme).size
    return if (count == 0) "No combinations" else "$count ${if (count == 1) "combination" else "combinations"}"
}

/** "Combinations: all 7", "Combinations: 2 of 7". */
fun pickText(round: BoxingRound): String {
    val all = themeCombos(round.theme).size
    return if (round.pick.isEmpty()) "Combinations: all $all" else "Combinations: ${round.pick.size} of $all"
}

/** The picks with one combination switched on or off. All of them on is the same as none. */
fun toggledPick(round: BoxingRound, id: String): List<String> {
    val all = themeCombos(round.theme).map { it.id }
    val current = round.pick.ifEmpty { all }
    val next = if (id in current) current - id else current + id
    return if (next.size == all.size) emptyList() else all.filter { it in next }
}

/** The combinations to pick from, in groups: one group for a theme, one per theme for the mix. */
fun comboGroups(theme: BoxingTheme): List<Pair<BoxingTheme, List<BoxingCombo>>> {
    if (theme != BoxingTheme.MIX) return listOf(theme to themeCombos(theme))
    return BOXING_THEMES.filter { it != BoxingTheme.MIX && it != BoxingTheme.FREESTYLE }
        .map { entry -> entry to BOXING_COMBOS.filter { it.themes.first() == entry } }
}

/** What the numbers mean, in one line. */
const val NUMBERS_KEY = "1 jab · 2 cross · 3 hook · 5 and 6 uppercut · B to the body"

/** Which of a round's combinations the coach drills, "Again!" after the first go: all of them, only the long ones, or none. */
enum class Drill { ALL, LONG, NONE }

fun drillOf(round: BoxingRound): Drill {
    val pool = if (round.theme == BoxingTheme.FREESTYLE) emptyList() else roundCombos(round)
    val long = pool.count { it.say.split(",").size >= DRILL_FROM }
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
