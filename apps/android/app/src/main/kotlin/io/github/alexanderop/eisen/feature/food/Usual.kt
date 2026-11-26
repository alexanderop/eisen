package io.github.alexanderop.eisen.feature.food

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/*
 * The foods usual at this time of day: what the add screen offers first. Pure: the clock and the time zone come in.
 */

/** How far back "usual at this time" looks. */
const val USUAL_DAYS = 28

/** How far from now, either way, a time of day counts as "this time". */
const val USUAL_WINDOW_MINUTES = 120

/** How many foods "usual at this time" offers. */
const val USUAL_SIZE = 6

private const val DAY_MINUTES = 24 * 60
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** The minute of the day of [at] in [zone]: 0 at midnight, 450 at 07:30. */
fun minuteOfDay(at: Long, zone: ZoneId): Int = Instant.ofEpochMilli(at).atZone(zone).let { it.hour * 60 + it.minute }

/** How many minutes apart two times of day are, the short way round midnight: 23:30 and 00:30 are 60 apart. */
fun minutesApart(a: Int, b: Int): Int {
    val apart = abs(a - b) % DAY_MINUTES
    return minOf(apart, DAY_MINUTES - apart)
}

/**
 * True when [entry] was logged on its own day: then the time it carries is when it was eaten. An entry filled in later
 * for an earlier day carries the time it was added, which says nothing about when it was eaten.
 */
fun hasTime(entry: FoodEntry, zone: ZoneId): Boolean = localDay(entry.at, zone) == entry.day

/**
 * The foods logged within [USUAL_WINDOW_MINUTES] of the time of day of [now], over the last [USUAL_DAYS] days, the most
 * often logged first (the most recent first among equals), each at its usual amount (the one the add sheet starts at).
 * Only entries with a time of their own count, and only foods the phone still knows ([find]).
 */
fun usualAt(log: List<FoodEntry>, find: (String) -> Food?, now: Long, zone: ZoneId): List<AgainItem> {
    val minute = minuteOfDay(now, zone)
    val near = log.filter { entry ->
        entry.foodId != null && entry.amount != null && now - entry.at in 0 until USUAL_DAYS * DAY_MILLIS &&
            hasTime(entry, zone) && minutesApart(minuteOfDay(entry.at, zone), minute) <= USUAL_WINDOW_MINUTES
    }
    return near.groupBy { it.foodId }
        .mapNotNull { (id, entries) -> id?.let(find)?.let { food -> Triple(food, entries.size, entries.maxOf { it.at }) } }
        .sortedWith(compareByDescending<Triple<Food, Int, Long>> { it.second }.thenByDescending { it.third })
        .take(USUAL_SIZE)
        .map { (food, _, _) -> AgainItem(food, startAmount(food, log)) }
}

/** The plain foods a phone that has logged nothing lately offers, by their names in the app's list. */
val COMMON_FOODS = listOf("Oats, rolled", "Banana", "Egg, hard-boiled", "Rice, white, cooked", "Chicken breast, grilled", "Yogurt, plain, low fat", "Bread, whole wheat", "Milk, 2% fat")

/** [COMMON_FOODS] among [plain], in that order, each at its usual amount. */
fun commonFoods(plain: List<Food>, log: List<FoodEntry>): List<AgainItem> =
    COMMON_FOODS.mapNotNull { name -> plain.firstOrNull { it.name == name } }.map { AgainItem(it, startAmount(it, log)) }
