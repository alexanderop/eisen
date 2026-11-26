package io.github.alexanderop.eisen.feature.food

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * The timeline of a day: its entries grouped by when they were eaten. Pure: the time zone comes in.
 */

/** Entries logged within this many minutes of the first of a group belong to it. */
const val GROUP_MINUTES = 30

/**
 * Entries eaten together: at [minute] of the day (the first of them), or with no time ([minute] null: filled in later for
 * an earlier day, see [hasTime]), and what they add up to.
 */
data class TimeGroup(val minute: Int?, val entries: List<FoodEntry>, val totals: Totals)

/**
 * The [entries] of one day as groups in the order of the day: an entry within [GROUP_MINUTES] of the first entry of the
 * group before it joins that group. Entries without a time of their own are one group at the end. Every entry is in
 * exactly one group, so the groups add up to the day.
 */
fun timeline(entries: List<FoodEntry>, zone: ZoneId): List<TimeGroup> {
    val (timed, untimed) = entries.partition { hasTime(it, zone) }
    val groups = mutableListOf<MutableList<Pair<Int, FoodEntry>>>()
    for (pair in timed.map { minuteOfDay(it.at, zone) to it }.sortedWith(compareBy<Pair<Int, FoodEntry>> { it.first }.thenBy { it.second.at })) {
        val last = groups.lastOrNull()
        if (last != null && pair.first - last.first().first <= GROUP_MINUTES) last += pair else groups += mutableListOf(pair)
    }
    val timedGroups = groups.map { group -> TimeGroup(group.first().first, group.map { it.second }, totalsOf(group.map { it.second })) }
    val noTime = untimed.sortedBy { it.at }.takeIf { it.isNotEmpty() }?.let { TimeGroup(null, it, totalsOf(it)) }
    return timedGroups + listOfNotNull(noTime)
}

/** A minute of the day as "07:30". */
fun clockText(minute: Int): String = "%02d:%02d".format(minute.coerceIn(0, 24 * 60 - 1) / 60, minute.coerceIn(0, 24 * 60 - 1) % 60)

/** The moment of [minute] on [day] in [zone]; null for a day that is not a date. */
fun atOfDay(day: String, minute: Int, zone: ZoneId): Long? {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return null
    val clamped = minute.coerceIn(0, 24 * 60 - 1)
    return date.atTime(LocalTime.of(clamped / 60, clamped % 60)).atZone(zone).toInstant().toEpochMilli()
}

/** The minute of the day [entry] was eaten at, or null when it has no time of its own. */
fun entryMinute(entry: FoodEntry, zone: ZoneId): Int? = if (hasTime(entry, zone)) minuteOfDay(entry.at, zone) else null
