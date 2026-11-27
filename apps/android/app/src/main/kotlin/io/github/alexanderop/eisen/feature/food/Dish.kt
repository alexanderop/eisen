package io.github.alexanderop.eisen.feature.food

/*
 * A photo of a dish: Gemini Nano on the phone names the foods it sees and guesses their grams; this file reads its answer
 * and finds each name among the foods the phone knows. Pure. The model gives names and amounts only: every calorie comes
 * from the food found here, never from the model.
 */

/** Whether the phone has the model that names the foods on a photo: Gemini Nano, kept by Android (AICore), not by the app. */
enum class DishAi {
    /** The phone has no such model: the photo of a dish is not offered. */
    MISSING,

    /** The phone can have it, after a download Android makes once. */
    DOWNLOADABLE,
    DOWNLOADING,
    READY,
}

/** A food the model saw: its [name] as the model wrote it, and the [grams] it guessed. */
data class DishGuess(val name: String, val grams: Double)

/** The most foods read from one photo: more than a plate holds apart, and a model that runs on lists no more. */
const val MAX_DISH_ITEMS = 12

/** The most one food of a dish weighs: beyond it the model guessed a number, not a portion. */
const val MAX_DISH_GRAMS = 2_000.0

/**
 * What the model is asked. Short, with the form of the answer spelled out: a small model follows an example better than a
 * rule. The names are asked for the way the plain foods are named ("Rice, white, cooked"), so [matchDish] finds them.
 */
const val DISH_PROMPT = """List every food and drink you can see in this photo of a meal.
Write one line per food, in exactly this form:
name; grams
Use short plain English names that say how the food is cooked, like these:
rice, cooked; 180
chicken breast, grilled; 150
broccoli, cooked; 90
egg, fried; 50
Estimate the grams of each food as it lies on the plate. Write nothing else.
If there is no food in the photo, write: none"""

private val BULLET = Regex("""^\s*(?:[-*•·]+|\d{1,2}[.)])\s*""")
private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
private val LOOSE = Regex("""^(.*?\p{L})[\s:,|–-]+~?\s*(\d+(?:[.,]\d+)?)\s*(?:g|gr|grams?|ml)?\.?\s*$""", RegexOption.IGNORE_CASE)

/**
 * The foods in the model's [answer]: a line "name; grams" each, also with a bullet, a number before it, bold marks or
 * "150 g" at its end. A line without a name or without an amount is not a food (a heading, a sentence, "none"). At most
 * [MAX_DISH_ITEMS], each name once, the grams between [MIN_AMOUNT] and [MAX_DISH_GRAMS], whole.
 */
fun readDish(answer: String): List<DishGuess> = answer.lineSequence()
    .mapNotNull { raw ->
        val line = raw.replace("*", "").replace("`", "").replace(BULLET, "").trim()
        val (name, amount) = if (';' in line) {
            line.substringBefore(';') to NUMBER.find(line.substringAfter(';'))?.value
        } else {
            LOOSE.find(line)?.let { it.groupValues[1] to it.groupValues[2] } ?: return@mapNotNull null
        }
        val grams = amount?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
        val clean = name.trim().trim(',', ':', '.', '-').trim().take(MAX_FOOD_NAME).trim()
        if (clean.none { it.isLetter() }) return@mapNotNull null
        DishGuess(clean, Math.round(grams.coerceIn(MIN_AMOUNT, MAX_DISH_GRAMS)).toDouble())
    }
    .distinctBy { it.name.lowercase() }
    .take(MAX_DISH_ITEMS)
    .toList()

/** Words that say how a food is prepared, not what it is: they choose between two foods, they never make one match. */
private val PREPARED = setOf(
    "cooked", "boiled", "grilled", "roasted", "baked", "fried", "steamed", "raw", "dry", "dried", "mashed", "sauteed",
    "scrambled", "poached", "toasted", "canned", "frozen", "fresh", "plain", "sliced", "diced", "chopped", "whole",
)

/** Words that carry nothing. */
private val FILLER = setOf("a", "an", "the", "of", "with", "and", "in", "on", "some", "piece", "pieces", "slice", "slices", "side", "small", "large", "medium")

/** A food as it comes uncooked: on a plate the cooked one is meant, unless the model says otherwise. */
private val UNCOOKED = setOf("raw", "dry", "dried")

/** A word without its plural: "potatoes" and "potato" are one word, "peaches" and "peach". */
private fun singular(word: String): String = when {
    word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
    word.length > 4 && (word.endsWith("oes") || word.endsWith("ches") || word.endsWith("shes") || word.endsWith("sses") || word.endsWith("xes")) -> word.dropLast(2)
    word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
    else -> word
}

private fun wordsOf(text: String): List<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in FILLER }.map(::singular)

/**
 * The food among [foods] that [name] means, or null when none is it. Every word of the name that says what the food is
 * has to be in the food's name: "chicken curry" is not "Chicken breast", and a food nobody knows is better told than
 * guessed. Among the ones left: the food whose own first words the name covers ("rice" is "Rice, white, cooked" before
 * "Rice cakes"), then the one prepared as the name says, a cooked one before a raw one (a plate holds cooked food), the
 * one logged most often in [log], the one with the fewest words more, and the first of [foods].
 */
fun matchDish(name: String, foods: List<Food>, log: List<FoodEntry> = emptyList()): Food? {
    val asked = wordsOf(name).toSet()
    val what = asked - PREPARED
    if (what.isEmpty()) return null
    val counts = log.groupingBy { it.foodId }.eachCount()
    val wantsUncooked = asked.any { it in UNCOOKED }
    return foods.withIndex().mapNotNull { (index, food) ->
        val words = wordsOf(food.name).toSet()
        if (!words.containsAll(what)) return@mapNotNull null
        val head = wordsOf(food.name.substringBefore(',')).toSet()
        Candidate(
            food = food,
            headCovered = asked.containsAll(head),
            prepared = (asked intersect PREPARED).count { it in words },
            uncooked = !wantsUncooked && words.any { it in UNCOOKED },
            logged = counts[food.id] ?: 0,
            more = (words - asked).size,
            index = index,
        )
    }.minWithOrNull(
        compareByDescending<Candidate> { it.headCovered }
            .thenByDescending { it.prepared }
            .thenBy { it.uncooked }
            .thenByDescending { it.logged }
            .thenBy { it.more }
            .thenBy { it.index },
    )?.food
}

private class Candidate(val food: Food, val headCovered: Boolean, val prepared: Int, val uncooked: Boolean, val logged: Int, val more: Int, val index: Int)
