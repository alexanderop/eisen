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
 * which hands the status code and the body here. A product by its barcode: API v2 with a `fields=` list, see
 * [PRODUCT_FIELDS]: v3.6 moved the numbers per 100 g out of `nutriments` into a `nutrition` object, and v3
 * without a minor version leaves out `product_quantity`. A search: Search-a-licious at
 * `search.openfoodfacts.org`, the full-text search their API docs point to (the legacy `/cgi/search.pl` is "not
 * recommended for new integrations" and answered 503 to every request in November 2026), see [searchAnswer].
 * Saved real answers are in the tests' resources.
 */

/**
 * The fields asked for. `quantity` must be among them: without it the server leaves out `product_quantity`,
 * which it works out from it.
 */
const val PRODUCT_FIELDS = "code,product_name,product_name_en,generic_name,brands,quantity,product_quantity,product_quantity_unit," +
    "serving_quantity,serving_quantity_unit,nutrition_data_per,nutriments,categories_tags"

/** Products per search: one page, the first that fits on the sheet. */
const val SEARCH_PAGE_SIZE = 20

/**
 * Requests per minute Open Food Facts allows one phone: 15 for products, 10 for searches. Search-a-licious states no
 * limit of its own; the search keeps the one their API docs give for every search.
 */
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

/**
 * A product the search found. [food] is null when the search does not carry its calories (its index leaves out
 * `nutriments` for many products): a tap then looks the product up by its [barcode], which has them. [icon]: the
 * picture of its categories, when the search carries them, also without its calories.
 */
data class SearchHit(val barcode: String, val name: String, val brand: String, val food: Food?, val icon: FoodIcon? = null) {
    val id: String get() = "off:$barcode"
}

/** The picture of a hit: its food's, else its categories', else the guess from its name, else the plate. */
fun iconOfHit(hit: SearchHit): FoodIcon = hit.food?.let(::iconOf) ?: hit.icon ?: guessIcon(hit.name) ?: FoodIcon.PLATE

/** A food as a search hit with its numbers. */
fun hitOf(food: Food): SearchHit = SearchHit(food.barcode ?: food.id.removePrefix("off:"), food.name, food.brand, food, food.icon)

/** What a search came to. */
sealed interface SearchAnswer {
    data class Found(val hits: List<SearchHit>) : SearchAnswer
    data object Offline : SearchAnswer
    data object Busy : SearchAnswer
}

/**
 * The fields a search asks for: those of a product, with the name in the phone's [lang] ahead of the others.
 * [lang] is a two-letter code; anything else adds nothing.
 */
fun searchFields(lang: String): String {
    val local = lang.lowercase().takeIf { it.matches(Regex("[a-z]{2}")) && it != "en" }?.let { "product_name_$it," }.orEmpty()
    return "code,product_name,${local}product_name_en,generic_name,brands,quantity,product_quantity,product_quantity_unit," +
        "serving_quantity,serving_quantity_unit,nutrition_data_per,nutriments,categories"
}

/** The languages a search looks in: the phone's, then English. */
fun searchLangs(lang: String): String = listOf(lang.lowercase(), "en").filter { it.matches(Regex("[a-z]{2}")) }.distinct().joinToString(",")

/**
 * The query Search-a-licious gets: the words typed, with the characters of its query syntax (Lucene) taken out so a
 * typed "Ben & Jerry's: Cookie" is words and never a broken query, and the products sold in the phone's country
 * ([country], an Open Food Facts tag such as `en:germany`, see [countryTag]) when it is known.
 */
fun searchQuery(query: String, country: String?): String {
    val words = query.replace(Regex("""[+\-&|!(){}\[\]^"~*?:\\/]"""), " ").trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        // AND, OR and NOT are operators in capitals: a typed one is a word.
        .map { if (it == "AND" || it == "OR" || it == "NOT") it.lowercase() else it }
    val terms = words.joinToString(" ")
    return if (country == null || terms.isEmpty()) terms else "$terms countries:\"$country\""
}

/** The Open Food Facts tag of a country by its English name: "Germany" is `en:germany`, "United States" `en:united-states`. Null for none. */
fun countryTag(englishName: String): String? =
    englishName.trim().lowercase().replace(Regex("[^a-z]+"), "-").trim('-').takeIf { it.isNotEmpty() }?.let { "en:$it" }

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

/**
 * The answer of Search-a-licious to a search: its `hits`, each with a barcode and a name, in the order it ranked
 * them; with their numbers when the hit has its calories. A refused query (a 4xx other than 429) found nothing.
 * Names in the phone's [lang] come first.
 */
fun searchAnswer(status: Int, body: String?, lang: String = "en"): SearchAnswer {
    if (busy(status)) return SearchAnswer.Busy
    val json = parse(body)
    if (json == null || json["hits"] !is JsonArray) return if (status in 400..499 && json != null) SearchAnswer.Found(emptyList()) else SearchAnswer.Offline
    val hits = (json["hits"] as JsonArray).mapNotNull { element ->
        val product = element as? JsonObject ?: return@mapNotNull null
        val code = product["code"].text()?.let(::normalBarcode)?.takeIf { barcodeProblem(it) == null } ?: return@mapNotNull null
        val name = nameOf(product, lang).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val food = foodOf(product, code, lang)
        SearchHit(code, food?.name ?: name, food?.brand ?: brandOf(product), food, iconOfCategories(categoriesOf(product)))
    }
    return SearchAnswer.Found(hits.distinctBy { it.barcode }.take(SEARCH_PAGE_SIZE))
}

/** A product as a food, or null without its calories. Per 100 ml when the product is sold by volume. */
private fun foodOf(product: JsonObject, barcode: String, lang: String = "en"): Food? {
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
    val name = nameOf(product, lang).ifEmpty { brandOf(product).ifEmpty { "Product $code" } }
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
            icon = iconOfCategories(categoriesOf(product)),
        ),
    )
}

/**
 * The categories of a product, broad to specific: `categories_tags` in a product read, `categories` in a search (which
 * Search-a-licious fills from `categories_tags`). Empty when the product has none.
 */
private fun categoriesOf(product: JsonObject): List<String> =
    ((product["categories_tags"] ?: product["categories"]) as? JsonArray).orEmpty().mapNotNull { it.text() }

private fun nameOf(product: JsonObject, lang: String = "en"): String =
    (listOfNotNull("product_name_${lang.lowercase()}".takeIf { lang.lowercase() != "en" }) + listOf("product_name", "product_name_en", "generic_name"))
        .firstNotNullOfOrNull { key -> product[key].text()?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()
        .take(MAX_FOOD_NAME)

/**
 * The first brand. The product API lists them with commas ("Ferrero, Nutella"); the search as a list of tags
 * (`xx:k-classic`, `xx:Ehrmann`), whose prefix goes and whose slug becomes words ("K Classic").
 */
private fun brandOf(product: JsonObject): String {
    val brands = product["brands"]
    if (brands !is JsonArray) return brands.text()?.split(',')?.firstOrNull()?.trim().orEmpty().take(MAX_BRAND)
    val slug = brands.firstOrNull().text()?.trim().orEmpty().replace(Regex("^[a-z]{2}:"), "")
    val brand = if (slug.any(Char::isUpperCase)) slug else slug.split('-').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    return brand.take(MAX_BRAND)
}

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
 * Central. Each entry: `id`, `name`, `kcal`, `protein`, `carbs`, `fat` per 100 g, an optional `serving`, and its `icon`.
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
                icon = foodIconNamed(item["icon"].text()),
            ),
        )
    }.distinctBy { it.id }
}
