package io.github.alexanderop.eisen.feature.food

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Calories and protein: the foods the phone knows, what was eaten when, and the goal of a day. Pure: the
 * clock, the time zone and new ids come in as values, the network and the camera are Cmds of the program.
 */

/** What the numbers of a food are per 100 of: grams, or millilitres for a drink. */
@Serializable
enum class FoodUnit(val label: String) {
    @SerialName("g") G("g"),
    @SerialName("ml") ML("ml"),
}

/** Where a food comes from: Open Food Facts (by its barcode or its search), the lifter, or the plain foods shipped in the app. */
@Serializable
enum class FoodSource {
    @SerialName("scanned") SCANNED,
    @SerialName("own") OWN,
    @SerialName("plain") PLAIN,
}

/**
 * A food with its numbers per 100 g (or 100 ml when [unit] is millilitres). [serving] and [pack] are
 * amounts in the same unit, when known: they give the chips of the add sheet. [carbs] and [fat] are kept,
 * not shown in the lifter's face; null when the source does not say. [icon] is the food's own picture (from its Open Food
 * Facts category, the plain foods' asset, or the lifter's choice); null shows the guess from its name, see [iconOf]. It
 * came after build 25, whose files have none; it is left out of the file when null, and a name this build does not know
 * reads as none.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Food(
    val id: String,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    val kcal: Double,
    val protein: Double = 0.0,
    val carbs: Double? = null,
    val fat: Double? = null,
    val unit: FoodUnit = FoodUnit.G,
    val serving: Double? = null,
    val pack: Double? = null,
    val source: FoodSource,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @Serializable(with = FoodIconOrNone::class) val icon: FoodIcon? = null,
)

/**
 * Something eaten. Its numbers are its own, worked out when it was logged: editing or deleting its food
 * later changes none of them. [foodId] is null for a quick entry of calories, which has no [amount].
 * [day] is the local calendar day it counts for (`2026-11-26`), fixed when it is logged: a flight to
 * another time zone does not move what was eaten to another day. [at] orders the entries of a day.
 */
@Serializable
data class FoodEntry(
    val id: String,
    val foodId: String? = null,
    val name: String,
    val amount: Double? = null,
    val unit: FoodUnit = FoodUnit.G,
    val kcal: Int,
    val protein: Double = 0.0,
    val carbs: Double? = null,
    val fat: Double? = null,
    val at: Long,
    val day: String,
)

/**
 * What a day should reach, in kcal and grams. Each may be missing: no goal shows what was eaten. [carbs] and [fat] came
 * after build 24; its files have neither and load as they were.
 */
@Serializable
data class FoodGoal(val kcal: Int? = null, val protein: Int? = null, val carbs: Int? = null, val fat: Int? = null)

// Limits: every number of the feature has its maximum here (LimitsTest sends the largest number through every Msg).

/** Pure fat: no food has more energy per 100 g. */
const val MAX_KCAL_PER_100 = 900.0

/** Protein, carbs or fat per 100 g. */
const val MAX_MACRO_PER_100 = 100.0

/** The least of a food that can be logged. */
const val MIN_AMOUNT = 1.0

/** The most of a food in one entry, and the largest serving or pack: five kilograms or litres. */
const val MAX_AMOUNT = 5_000.0

/** The most one entry can hold: the densest food at the largest amount. */
const val MAX_ENTRY_KCAL = 45_000

/** Protein, carbs or fat in one entry, in grams. */
const val MAX_ENTRY_GRAMS = 5_000.0

/** Quick kcal: a meal out, typed in. */
const val MAX_QUICK_KCAL = 10_000
const val MAX_QUICK_PROTEIN = 1_000

const val MAX_GOAL_KCAL = 10_000
const val MAX_GOAL_PROTEIN = 1_000
const val MAX_GOAL_CARBS = 2_500
const val MAX_GOAL_FAT = 1_100

const val MAX_FOOD_NAME = 60
const val MAX_BRAND = 40
const val MAX_FOOD_QUERY = 60

/** The name of a quick entry without one. */
const val QUICK_NAME = "Quick kcal"

/** One kilocalorie in kilojoules: what a label that only gives kJ is divided by. */
const val KJ_PER_KCAL = 4.184

fun kcalFromKj(kj: Double): Double = kj / KJ_PER_KCAL

private fun finite(value: Double): Double = if (value.isFinite()) value else 0.0

/** [value] to one decimal, halves up: 12.25 g is 12.3 g. */
fun round1(value: Double): Double = BigDecimal.valueOf(finite(value)).setScale(1, RoundingMode.HALF_UP).toDouble()

/** Whole kilocalories, halves up: 269.5 is 270. */
fun roundKcal(value: Double): Int = BigDecimal.valueOf(finite(value)).setScale(0, RoundingMode.HALF_UP).toInt()

private fun per100(value: Double, max: Double): Double = round1(finite(value).coerceIn(0.0, max))

private fun amountWithin(value: Double?): Double? = value?.takeIf { it.isFinite() && it > 0 }?.let { round1(it.coerceIn(MIN_AMOUNT, MAX_AMOUNT)) }

/** [food] in its limits: names cut, numbers per 100 in what a food can have, a serving and a pack where an entry can go. */
fun foodWithin(food: Food): Food = food.copy(
    name = food.name.trim().take(MAX_FOOD_NAME),
    brand = food.brand.trim().take(MAX_BRAND),
    barcode = food.barcode?.let(::normalBarcode)?.takeIf { barcodeProblem(it) == null },
    kcal = per100(food.kcal, MAX_KCAL_PER_100),
    protein = per100(food.protein, MAX_MACRO_PER_100),
    carbs = food.carbs?.let { per100(it, MAX_MACRO_PER_100) },
    fat = food.fat?.let { per100(it, MAX_MACRO_PER_100) },
    serving = amountWithin(food.serving),
    pack = amountWithin(food.pack),
)

private fun grams(value: Double): Double = round1(finite(value).coerceIn(0.0, MAX_ENTRY_GRAMS))

/** [entry] in its limits, for a file written by hand or by an older build. */
fun entryWithin(entry: FoodEntry): FoodEntry = entry.copy(
    name = entry.name.trim().take(MAX_FOOD_NAME).ifEmpty { QUICK_NAME },
    amount = amountWithin(entry.amount),
    kcal = entry.kcal.coerceIn(0, MAX_ENTRY_KCAL),
    protein = grams(entry.protein),
    carbs = entry.carbs?.let(::grams),
    fat = entry.fat?.let(::grams),
)

/** A goal of 0 is no goal. */
fun goalWithin(goal: FoodGoal): FoodGoal = FoodGoal(
    kcal = goal.kcal?.takeIf { it > 0 }?.coerceAtMost(MAX_GOAL_KCAL),
    protein = goal.protein?.takeIf { it > 0 }?.coerceAtMost(MAX_GOAL_PROTEIN),
    carbs = goal.carbs?.takeIf { it > 0 }?.coerceAtMost(MAX_GOAL_CARBS),
    fat = goal.fat?.takeIf { it > 0 }?.coerceAtMost(MAX_GOAL_FAT),
)

/** Energy per gram: protein and carbs 4 kcal, fat 9. */
const val KCAL_PER_GRAM_PROTEIN = 4
const val KCAL_PER_GRAM_CARBS = 4
const val KCAL_PER_GRAM_FAT = 9

/** The share of the calories "Fill carbs and fat" gives to fat. */
const val FAT_SHARE = 0.25

/**
 * Carbs and fat that fill a goal of [kcal] after [protein] grams, so nobody has to do the arithmetic: fat [FAT_SHARE] of
 * the calories, the rest carbs (none when protein and fat already take it all). Null without a calorie goal.
 */
fun carbsAndFatFor(kcal: Int, protein: Int): Pair<Int, Int>? {
    if (kcal <= 0) return null
    val fat = roundKcal(kcal * FAT_SHARE / KCAL_PER_GRAM_FAT).coerceIn(0, MAX_GOAL_FAT)
    val rest = kcal - protein.coerceAtLeast(0) * KCAL_PER_GRAM_PROTEIN - fat * KCAL_PER_GRAM_FAT
    val carbs = roundKcal(rest.coerceAtLeast(0) / KCAL_PER_GRAM_CARBS.toDouble()).coerceIn(0, MAX_GOAL_CARBS)
    return carbs to fat
}

/**
 * Grams of a macro eaten against its goal, calm either way: "96 g" without a goal, "96 / 160 g" under it, and
 * "170 / 160 g · 10 over" past it.
 */
fun macroText(eaten: Double, goal: Int?): String {
    val grams = roundKcal(eaten)
    if (goal == null) return "$grams g"
    return if (grams > goal) "$grams / $goal g · ${grams - goal} over" else "$grams / $goal g"
}

/** [amount] of [food], logged at [at] for [day]: kcal rounded to whole ones, protein, carbs and fat to a tenth. */
fun entryOf(food: Food, amount: Double, id: String, at: Long, day: String): FoodEntry {
    val eaten = amountWithin(amount) ?: MIN_AMOUNT
    val share = eaten / 100.0
    return FoodEntry(
        id = id,
        foodId = food.id,
        name = food.name.ifBlank { QUICK_NAME },
        amount = eaten,
        unit = food.unit,
        kcal = roundKcal(food.kcal * share).coerceIn(0, MAX_ENTRY_KCAL),
        protein = grams(food.protein * share),
        carbs = food.carbs?.let { grams(it * share) },
        fat = food.fat?.let { grams(it * share) },
        at = at,
        day = day,
    )
}

/** Calories typed in for a meal out, with protein if known and a name if given. */
fun quickEntry(kcal: Int, protein: Int?, name: String, id: String, at: Long, day: String): FoodEntry = FoodEntry(
    id = id,
    name = name.trim().take(MAX_FOOD_NAME).ifEmpty { QUICK_NAME },
    kcal = kcal.coerceIn(0, MAX_QUICK_KCAL),
    protein = (protein ?: 0).coerceIn(0, MAX_QUICK_PROTEIN).toDouble(),
    at = at,
    day = day,
)

/**
 * The per-100 numbers an entry was logged with, as a food: what the add sheet shows when an entry's amount
 * changes and its food is gone. Null for a quick entry, which has no amount.
 */
fun foodOfEntry(entry: FoodEntry): Food? {
    val amount = entry.amount ?: return null
    val per = 100.0 / amount
    return Food(
        id = entry.foodId ?: entry.id,
        name = entry.name,
        kcal = entry.kcal * per,
        protein = entry.protein * per,
        carbs = entry.carbs?.let { it * per },
        fat = entry.fat?.let { it * per },
        unit = entry.unit,
        source = FoodSource.OWN,
    )
}

// Days

/** The local calendar day of [at] in [zone], as `2026-11-26`. */
fun localDay(at: Long, zone: ZoneId): String = Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString()

private fun date(day: String): LocalDate? = runCatching { LocalDate.parse(day) }.getOrNull()

fun dayBefore(day: String): String = date(day)?.minusDays(1)?.toString() ?: day

fun dayAfter(day: String): String = date(day)?.plusDays(1)?.toString() ?: day

/** [day] moved by [days], back for a negative number. */
fun plusDays(day: String, days: Long): String = date(day)?.plusDays(days)?.toString() ?: day

/** The seven days of the week of [day], Monday first; empty for a day that is not a date. */
fun weekOf(day: String): List<String> {
    val at = date(day) ?: return emptyList()
    val monday = at.minusDays((at.dayOfWeek.value - 1).toLong())
    return (0L until 7L).map { monday.plusDays(it).toString() }
}

/** The day of the month of [day], 26 for `2026-11-26`. */
fun dayOfMonth(day: String): Int = date(day)?.dayOfMonth ?: 0

/** True for a day that is a real date and not after [today]. */
fun isPastOrToday(day: String, today: String): Boolean {
    val at = date(day) ?: return false
    val now = date(today) ?: return false
    return !at.isAfter(now)
}

/** "Today", "Yesterday", or the date as "Tue, Nov 24". */
fun dayTitle(day: String, today: String): String = when (day) {
    today -> "Today"
    dayBefore(today) -> "Yesterday"
    else -> date(day)?.let { "${it.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)}, ${it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)} ${it.dayOfMonth}" } ?: day
}

/** The entries of [day], in the order they were eaten. */
fun entriesOf(log: List<FoodEntry>, day: String): List<FoodEntry> = log.filter { it.day == day }.sortedBy { it.at }

/** What the entries add up to. Protein, carbs and fat to a tenth. */
data class Totals(val kcal: Int, val protein: Double, val carbs: Double, val fat: Double)

fun totalsOf(entries: List<FoodEntry>): Totals = Totals(
    kcal = entries.sumOf { it.kcal },
    protein = round1(entries.sumOf { it.protein }),
    carbs = round1(entries.sumOf { it.carbs ?: 0.0 }),
    fat = round1(entries.sumOf { it.fat ?: 0.0 }),
)

// The add sheet

/**
 * What the add sheet starts at: the amount last logged of this food, else its serving, else its pack, else 100.
 */
fun startAmount(food: Food, log: List<FoodEntry>): Double =
    log.filter { it.foodId == food.id && it.amount != null }.maxByOrNull { it.at }?.amount ?: food.serving ?: food.pack ?: 100.0

/** A chip of the add sheet: its text and the amount it sets. */
data class AmountChip(val label: String, val amount: Double)

/**
 * ½, 1 and 2 of the serving, or of the pack when there is no serving. A pack that is not the serving gets a chip
 * of its own. None when neither is known: the grams are typed.
 */
fun amountChips(food: Food): List<AmountChip> {
    val serving = food.serving
    val pack = food.pack
    val base = serving ?: pack ?: return emptyList()
    val name = if (serving != null) "serving" else "pack"
    val chips = mutableListOf(
        AmountChip("½", base / 2),
        AmountChip("1 $name ${amountText(base, food.unit)}", base),
        AmountChip("2", base * 2),
    )
    if (serving != null && pack != null && pack != serving) chips += AmountChip("1 pack ${amountText(pack, food.unit)}", pack)
    return chips.filter { it.amount in MIN_AMOUNT..MAX_AMOUNT }.map { it.copy(amount = round1(it.amount)) }.distinctBy { it.amount }
}

// Again

/** How far back "Again" looks. */
const val AGAIN_DAYS = 30

/** How many foods "Again" offers. */
const val AGAIN_SIZE = 6

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** A food of "Again" and the amount it was logged with last time: one tap logs that again. */
data class AgainItem(val food: Food, val amount: Double)

/**
 * The foods logged most often in the last [AGAIN_DAYS] days before [now], the most recent first among equals,
 * each with its last amount. Only foods the phone still knows ([find]); quick entries have no food.
 */
fun again(log: List<FoodEntry>, find: (String) -> Food?, now: Long): List<AgainItem> {
    val recent = log.filter { it.foodId != null && it.amount != null && now - it.at in 0 until AGAIN_DAYS * DAY_MILLIS }
    return recent.groupBy { it.foodId }
        .mapNotNull { (id, entries) ->
            val food = id?.let(find) ?: return@mapNotNull null
            val last = entries.maxBy { it.at }
            Triple(food, entries.size, last)
        }
        .sortedWith(compareByDescending<Triple<Food, Int, FoodEntry>> { it.second }.thenByDescending { it.third.at })
        .take(AGAIN_SIZE)
        .map { (food, _, last) -> AgainItem(food, last.amount ?: startAmount(food, log)) }
}

// Search

private val SOURCE_ORDER = listOf(FoodSource.OWN, FoodSource.SCANNED, FoodSource.PLAIN)

/**
 * The foods whose name or brand holds every word typed, best first: a word that starts a word of the name before
 * one inside it, then the foods logged most often, then the lifter's own before scanned ones before the plain
 * ones. Empty while nothing is typed.
 */
fun searchFoods(foods: List<Food>, query: String, log: List<FoodEntry>, limit: Int = 50): List<Food> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val counts = log.groupingBy { it.foodId }.eachCount()
    return foods.mapNotNull { food ->
        val text = "${food.name} ${food.brand}".lowercase()
        if (words.any { it !in text }) return@mapNotNull null
        val starts = words.count { Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(it)).containsMatchIn(text) }
        food to starts
    }
        .sortedWith(
            compareByDescending<Pair<Food, Int>> { it.second }
                .thenByDescending { counts[it.first.id] ?: 0 }
                .thenBy { SOURCE_ORDER.indexOf(it.first.source) }
                .thenBy { it.first.name.length },
        )
        .map { it.first }
        .distinctBy { it.id }
        .take(limit)
}

// Barcodes

/** A barcode as Open Food Facts files it: digits only, a UPC-A of 12 digits with the leading 0 of its EAN-13. */
fun normalBarcode(code: String): String {
    val digits = code.filter(Char::isDigit)
    return if (digits.length == 12) "0$digits" else digits
}

/**
 * Why a typed barcode cannot be one of a product, or null when it can: 8 digits (EAN-8, UPC-E), 12 (UPC-A),
 * 13 (EAN-13) or 14 (GTIN-14), and from 12 digits on a last digit that checks the others.
 */
fun barcodeProblem(code: String): String? {
    val digits = code.trim()
    if (digits.isEmpty()) return "Type the digits under the bars."
    if (!digits.all(Char::isDigit)) return "A barcode has digits only."
    if (digits.length !in setOf(8, 12, 13, 14)) return "A barcode has 8, 12 or 13 digits."
    if (digits.length >= 12 && !checks(digits)) return "The last digit does not match: check for a typo."
    return null
}

/** The GTIN check digit: the digits from the right weighted 3, 1, 3, ... add up to a multiple of 10. */
private fun checks(digits: String): Boolean {
    val body = digits.dropLast(1).reversed()
    val sum = body.mapIndexed { index, char -> (char - '0') * if (index % 2 == 0) 3 else 1 }.sum()
    return (10 - sum % 10) % 10 == digits.last() - '0'
}

// Texts

/** Whole kilocalories with a comma for the thousands: "1,240". */
fun kcalText(kcal: Int): String = "%,d".format(java.util.Locale.US, kcal)

/** An amount as "450 g", "330 ml" or "12.5 g". */
fun amountText(amount: Double, unit: FoodUnit): String = "${numberText(amount)} ${unit.label}"

/** Grams of protein as a whole number: "96 g". */
fun gramsText(grams: Double): String = "${roundKcal(grams)} g"

/** At most one decimal, none when it is zero: 450, 12.5. */
fun numberText(value: Double): String {
    val rounded = BigDecimal.valueOf(finite(value)).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
    return if (rounded.signum() == 0) "0" else rounded.toPlainString()
}
