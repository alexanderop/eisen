package io.github.alexanderop.eisen.feature.food

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/*
 * What Open Food Facts answers, read into foods. Pure: the request is the runtime's (feature/food/platform),
 * which hands the status code and the body here. Read API v2 with a `fields=` list, see [PRODUCT_FIELDS]:
 * v3.6 moved the numbers per 100 g out of `nutriments` into a `nutrition` object, and v3 without a minor
 * version leaves out `product_quantity`. Saved real answers are in the tests' resources.
 */

/**
 * The fields asked for. `quantity` must be among them: without it the server leaves out `product_quantity`,
 * which it works out from it.
 */
const val PRODUCT_FIELDS = "code,product_name,product_name_en,generic_name,brands,quantity,product_quantity,product_quantity_unit," +
    "serving_quantity,serving_quantity_unit,nutrition_data_per,nutriments"

/** Products per search: one page, the first that fits on the sheet. */
const val SEARCH_PAGE_SIZE = 20

/** Requests per minute Open Food Facts allows one phone: 15 for products, 10 for searches. */
const val PRODUCT_REQUESTS_PER_MINUTE = 15
const val SEARCH_REQUESTS_PER_MINUTE = 10

/** What looking up a barcode came to. */
sealed interface ProductAnswer {
    data class Found(val food: Food) : ProductAnswer

    /** The product is there, but without its calories: the lifter adds them. [name] may be empty. */
    data class NoKcal(val name: String) : ProductAnswer

    data object NotFound : ProductAnswer

    /** No answer at all: no network, a timeout, or something that is not Open Food Facts. */
    data object Offline : ProductAnswer

    /** Open Food Facts asked to wait: too many requests (429), or too much load (503). */
    data object Busy : ProductAnswer
}

/** What a search came to. */
sealed interface SearchAnswer {
    data class Found(val foods: List<Food>) : SearchAnswer
    data object Offline : SearchAnswer
    data object Busy : SearchAnswer
}

private val Lenient = Json { ignoreUnknownKeys = true }

private fun parse(body: String?): JsonObject? = body?.let {
    try {
        Lenient.parseToJsonElement(it) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
}

private fun busy(status: Int) = status == 429 || status == 503 || status == 502 || status == 504

/**
 * The answer to a lookup of [barcode]: the HTTP [status] and the [body]. Null [body]: no answer at all.
 * A product not found comes as 404 with `"status": 0`.
 */
fun productAnswer(barcode: String, status: Int, body: String?): ProductAnswer {
    if (busy(status)) return ProductAnswer.Busy
    val json = parse(body) ?: return if (status == 404) ProductAnswer.NotFound else ProductAnswer.Offline
    val product = json["product"] as? JsonObject
    if (json["status"].number()?.toInt() == 0 || product == null) return ProductAnswer.NotFound
    val food = foodOf(product, barcode)
    return if (food == null) ProductAnswer.NoKcal(nameOf(product)) else ProductAnswer.Found(food)
}

/** The answer to a search: the products with a name and their calories, the others left out. */
fun searchAnswer(status: Int, body: String?): SearchAnswer {
    if (busy(status)) return SearchAnswer.Busy
    val json = parse(body) ?: return SearchAnswer.Offline
    val products = json["products"] as? JsonArray ?: return SearchAnswer.Offline
    val foods = products.mapNotNull { element ->
        val product = element as? JsonObject ?: return@mapNotNull null
        val code = product["code"].text()?.let(::normalBarcode)?.takeIf { barcodeProblem(it) == null } ?: return@mapNotNull null
        foodOf(product, code)?.takeIf { nameOf(product).isNotEmpty() }
    }
    return SearchAnswer.Found(foods.distinctBy { it.id }.take(SEARCH_PAGE_SIZE))
}

/** A product as a food, or null without its calories. Per 100 ml when the product is sold by volume. */
private fun foodOf(product: JsonObject, barcode: String): Food? {
    val nutriments = product["nutriments"] as? JsonObject
    val kcal = nutriments?.get("energy-kcal_100g").number()
        ?: nutriments?.get("energy-kj_100g").number()?.let(::kcalFromKj)
        // `energy_100g` is in kilojoules.
        ?: nutriments?.get("energy_100g").number()?.let(::kcalFromKj)
        ?: return null
    val packUnit = product["product_quantity_unit"].text()?.lowercase()
    val servingUnit = product["serving_quantity_unit"].text()?.lowercase()
    val unit = if (packUnit == "ml" || servingUnit == "ml" || product["nutrition_data_per"].text() == "100ml") FoodUnit.ML else FoodUnit.G
    fun inUnit(amount: Double?, said: String?) = amount?.takeIf { it > 0 && (said == null || said == unit.label) }
    val code = normalBarcode(barcode)
    val name = nameOf(product).ifEmpty { brandOf(product).ifEmpty { "Product $code" } }
    return foodWithin(
        Food(
            id = "off:$code",
            name = name,
            brand = brandOf(product),
            barcode = code,
            kcal = kcal,
            protein = nutriments?.get("proteins_100g").number() ?: 0.0,
            carbs = nutriments?.get("carbohydrates_100g").number(),
            fat = nutriments?.get("fat_100g").number(),
            unit = unit,
            serving = inUnit(product["serving_quantity"].number(), servingUnit),
            pack = inUnit(product["product_quantity"].number(), packUnit),
            source = FoodSource.SCANNED,
        ),
    )
}

private fun nameOf(product: JsonObject): String =
    listOf("product_name", "product_name_en", "generic_name").firstNotNullOfOrNull { key -> product[key].text()?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()
        .take(MAX_FOOD_NAME)

/** The first brand: Open Food Facts lists them with commas ("Ferrero, Nutella"). */
private fun brandOf(product: JsonObject): String = product["brands"].text()?.split(',')?.firstOrNull()?.trim().orEmpty().take(MAX_BRAND)

private fun JsonElement?.prim(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

/** A number, also one written as text ("0", "12,5"), as Open Food Facts sometimes does. */
private fun JsonElement?.number(): Double? {
    val value = prim() ?: return null
    val number = if (value.isString) value.content.trim().replace(',', '.').toDoubleOrNull() else value.doubleOrNull ?: value.intOrNull?.toDouble()
    return number?.takeIf { it.isFinite() && it >= 0 }
}

private fun JsonElement?.text(): String? = prim()?.content

/** Whether one more request fits the limit of [perMinute], given when the earlier ones went out. */
fun underLimit(sent: List<Long>, now: Long, perMinute: Int): Boolean = sent.count { now - it in 0 until MINUTE_MILLIS } < perMinute

/** The request times still within the last minute, with [now] added. */
fun withRequest(sent: List<Long>, now: Long): List<Long> = listOf(now) + sent.filter { now - it in 0 until MINUTE_MILLIS }

private const val MINUTE_MILLIS = 60_000L

// The plain foods shipped in the app

/**
 * The plain foods of the app's asset `foods/plain.json`, made by `scripts/plain-foods.py` from USDA FoodData
 * Central. Each entry: `id`, `name`, `kcal`, `protein`, `carbs`, `fat` per 100 g and an optional `serving`.
 * A broken asset gives none, never a crash.
 */
fun parsePlainFoods(json: String): List<Food> {
    val array = try {
        Lenient.parseToJsonElement(json) as? JsonArray
    } catch (_: SerializationException) {
        null
    } ?: return emptyList()
    return array.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val id = item["id"].text() ?: return@mapNotNull null
        val name = item["name"].text() ?: return@mapNotNull null
        val kcal = item["kcal"].number() ?: return@mapNotNull null
        foodWithin(
            Food(
                id = "fdc:$id",
                name = name,
                kcal = kcal,
                protein = item["protein"].number() ?: 0.0,
                carbs = item["carbs"].number(),
                fat = item["fat"].number(),
                unit = if (item["unit"].text() == "ml") FoodUnit.ML else FoodUnit.G,
                serving = item["serving"].number(),
                source = FoodSource.PLAIN,
            ),
        )
    }.distinctBy { it.id }
}
