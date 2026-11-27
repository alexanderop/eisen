package io.github.alexanderop.eisen.feature.food

import io.github.alexanderop.eisen.core.WeightUnit
import java.time.ZoneId

/*
 * A day of food as Markdown, to paste into a chat or a note: the totals against the goal, a table of what was eaten
 * and when, and the weight of that day when there is one. Pure: the same day gives the same text.
 */

/** [entries] of [day] as Markdown. [weight] is that day's, in [unit]. */
fun dayMarkdown(day: String, entries: List<FoodEntry>, goal: FoodGoal, zone: ZoneId, weight: WeightEntry? = null, unit: WeightUnit = WeightUnit.KG): String {
    val totals = totalsOf(entries)
    val lines = mutableListOf("## Food · ${dayDate(day)}", "")
    lines += listOf(
        "**${kcalText(totals.kcal)} kcal**" + (goal.kcal?.let { " of ${kcalText(it)}" } ?: ""),
        "Protein ${against(totals.protein, goal.protein)}",
        "Carbs ${against(totals.carbs, goal.carbs)}",
        "Fat ${against(totals.fat, goal.fat)}",
    ).joinToString(" · ")
    if (entries.isNotEmpty()) {
        lines += ""
        lines += "| Time | Food | Amount | kcal | Protein | Carbs | Fat |"
        lines += "|---|---|--:|--:|--:|--:|--:|"
        for (entry in entries) {
            lines += row(
                entryMinute(entry, zone)?.let(::clockText) ?: "",
                cell(entry.name),
                entry.amount?.let { amountText(it, entry.unit) } ?: "",
                kcalText(entry.kcal),
                "${numberText(entry.protein)} g",
                entry.carbs?.let { "${numberText(it)} g" } ?: "",
                entry.fat?.let { "${numberText(it)} g" } ?: "",
            )
        }
        lines += row("", "**Total**", "", "**${kcalText(totals.kcal)}**", "**${numberText(totals.protein)} g**", "**${numberText(totals.carbs)} g**", "**${numberText(totals.fat)} g**")
    }
    if (weight != null) {
        lines += ""
        lines += "Weight: ${bodyText(weight.kg, unit)}"
    }
    return lines.joinToString("\n") + "\n"
}

/** "143 of 160 g" with a goal, "143 g" without. */
private fun against(eaten: Double, goal: Int?): String = if (goal == null) "${numberText(eaten)} g" else "${numberText(eaten)} of $goal g"

private fun row(vararg cells: String): String = cells.joinToString(" | ", prefix = "| ", postfix = " |")

/** A name inside a table: a bar would end the cell and a line break the row. */
private fun cell(text: String): String = text.replace("\\", "\\\\").replace("|", "\\|").replace(Regex("\\s+"), " ").trim()
