package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.FoodUnit
import io.github.alexanderop.eisen.feature.food.MAX_KCAL_PER_100
import io.github.alexanderop.eisen.feature.food.PRODUCT_FIELDS
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.SEARCH_PAGE_SIZE
import io.github.alexanderop.eisen.feature.food.SearchAnswer
import io.github.alexanderop.eisen.feature.food.SearchHit
import io.github.alexanderop.eisen.feature.food.countryTag
import io.github.alexanderop.eisen.feature.food.searchFields
import io.github.alexanderop.eisen.feature.food.searchLangs
import io.github.alexanderop.eisen.feature.food.searchQuery
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.foodWithin
import io.github.alexanderop.eisen.feature.food.parsePlainFoods
import io.github.alexanderop.eisen.feature.food.productAnswer
import io.github.alexanderop.eisen.feature.food.searchAnswer
import io.github.alexanderop.eisen.feature.food.underLimit
import io.github.alexanderop.eisen.feature.food.withRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reading what Open Food Facts answers, against real answers saved in `src/test/resources/off` (API v2 with the
 * app's `fields=`, fetched on 2026-11-26): a product, a drink per 100 ml, a product without calories, a barcode
 * it does not know; and two searches of Search-a-licious (fetched on 2026-11-26 with the app's query): "skyr" in
 * Germany (`search/skyr.json`, whose hits carry no calories) and worldwide (`search/skyr-world.json`, most with them).
 */
class OpenFoodFactsTest {
    private fun saved(path: String): String = File("src/test/resources/off/$path").readText()

    @Test fun `a product is read with its numbers per 100 g, its brand and its pack`() {
        val answer = productAnswer("3017620422003", 200, saved("product/3017620422003.json"))
        assertEquals(
            ProductAnswer.Found(
                Food(
                    id = "off:3017620422003",
                    name = "Nutella",
                    brand = "Ferrero",
                    barcode = "3017620422003",
                    kcal = 539.0,
                    protein = 6.3,
                    carbs = 57.5,
                    fat = 30.9,
                    unit = FoodUnit.G,
                    serving = null,
                    pack = 400.0,
                    source = FoodSource.SCANNED,
                ),
            ),
            answer,
        )
        // 15 g on bread: 80.85 kcal, 81 logged.
        assertEquals(81, entryOf((answer as ProductAnswer.Found).food, 15.0, "e", 0, "d").kcal)
    }

    @Test fun `a drink sold by the millilitre counts per 100 ml with its can as the serving`() {
        val food = (productAnswer("5449000000996", 200, saved("product/5449000000996.json")) as ProductAnswer.Found).food
        assertEquals(FoodUnit.ML, food.unit)
        assertEquals(44.0, food.kcal, 0.0)
        assertEquals(330.0, food.serving!!, 0.0)
        assertEquals(330.0, food.pack!!, 0.0)
        assertEquals("Coca-Cola Original", food.name)
        assertEquals(145, entryOf(food, 330.0, "e", 0, "d").kcal)
    }

    @Test fun `a product without calories is found without them, with its name`() {
        // Its pack is written as text, "0": no pack.
        assertEquals(ProductAnswer.NoKcal("Cornedbeef"), productAnswer("2000000000039", 200, saved("product/2000000000039.json")))
    }

    @Test fun `a barcode Open Food Facts does not know is not found`() {
        assertEquals(ProductAnswer.NotFound, productAnswer("4260999999990", 404, saved("product/4260999999990.json")))
        // A 404 without a body of theirs is the same.
        assertEquals(ProductAnswer.NotFound, productAnswer("4260999999990", 404, "<html>Not found</html>"))
    }

    @Test fun `no answer, a page that is not theirs, or a request to wait`() {
        assertEquals(ProductAnswer.Offline, productAnswer("3017620422003", 0, null))
        assertEquals(ProductAnswer.Offline, productAnswer("3017620422003", 200, "<html>Hotel wifi login</html>"))
        assertEquals(ProductAnswer.Busy, productAnswer("3017620422003", 429, null))
        // Their page "temporarily unavailable" comes as 503 with HTML.
        assertEquals(ProductAnswer.Busy, productAnswer("3017620422003", 503, "<!DOCTYPE html><title>Page temporarily unavailable</title>"))
        assertEquals(SearchAnswer.Busy, searchAnswer(503, "<!DOCTYPE html>"))
        assertEquals(SearchAnswer.Offline, searchAnswer(0, null))
    }

    @Test fun `without kcal the kilojoules are used, and numbers written as text are read`() {
        val kj = """{"code":"3017620422003","status":1,"product":{"product_name":"Label in kJ","nutriments":{"energy-kj_100g":2252,"proteins_100g":"6,3"}}}"""
        val food = (productAnswer("3017620422003", 200, kj) as ProductAnswer.Found).food
        assertEquals(538.2, food.kcal, 0.0)
        assertEquals(6.3, food.protein, 0.0)
        val energy = """{"status":1,"product":{"product_name":"Only energy","nutriments":{"energy_100g":418.4}}}"""
        assertEquals(100.0, (productAnswer("3017620422003", 200, energy) as ProductAnswer.Found).food.kcal, 0.0)
        // A label beyond what food can have is kept at the limit.
        val wild = """{"status":1,"product":{"product_name":"Typo","nutriments":{"energy-kcal_100g":53900}}}"""
        assertEquals(MAX_KCAL_PER_100, (productAnswer("3017620422003", 200, wild) as ProductAnswer.Found).food.kcal, 0.0)
        // A product without a name is called by its brand, or by its barcode.
        val nameless = """{"status":1,"product":{"brands":"Lidl","nutriments":{"energy-kcal_100g":60}}}"""
        assertEquals("Lidl", (productAnswer("3017620422003", 200, nameless) as ProductAnswer.Found).food.name)
    }

    @Test fun `a search gives its hits with a name, with their numbers where it has them, one page`() {
        val world = (searchAnswer(200, saved("search/skyr-world.json")) as SearchAnswer.Found).hits
        assertTrue(world.size in 1..SEARCH_PAGE_SIZE)
        assertTrue(world.all { it.name.isNotBlank() && it.id == "off:${it.barcode}" })
        // One hit has no name at all: it is left out.
        assertTrue(world.none { it.barcode == "13206994" })
        val citron = world.first { it.barcode == "0049606042434" }
        assertEquals("Skyr citron", citron.name)
        val food = citron.food!!
        assertEquals(Triple(89.0, 8.9, 140.0), Triple(food.kcal, food.protein, food.serving))
        assertEquals(FoodSource.SCANNED to "off:0049606042434", food.source to food.id)
        // Brands come as tags: "skyr".
        assertEquals("Skyr", citron.brand)
        assertTrue(world.count { it.food != null } >= 10)
    }

    @Test fun `a search in Germany finds German products, which are looked up for their numbers`() {
        val germany = (searchAnswer(200, saved("search/skyr.json"), lang = "de") as SearchAnswer.Found).hits
        assertTrue(germany.size in 10..SEARCH_PAGE_SIZE)
        // Search-a-licious leaves `nutriments` out of these hits: each is looked up by its barcode when picked.
        assertTrue(germany.all { it.food == null })
        assertEquals(SearchHit("4337185761638", "Skyr natur", "K Classic", null), germany.first { it.barcode == "4337185761638" })
        assertEquals("Ehrmann", germany.first { it.barcode == "4002971296808" }.brand)
        // The product that hit is, by its barcode, has them.
        val product = (productAnswer("4337185761638", 200, saved("product/4337185761638.json")) as ProductAnswer.Found).food
        assertEquals(Triple("Skyr natur", 64.0, 500.0), Triple(product.name, product.kcal, product.pack))
    }

    @Test fun `a refused search finds nothing, a page that is not theirs is no network`() {
        val refused = """{"debug":{},"errors":[{"title":"QueryCheckError","status":400}]}"""
        assertEquals(SearchAnswer.Found(emptyList()), searchAnswer(400, refused))
        assertEquals(SearchAnswer.Offline, searchAnswer(200, "<html>Hotel wifi login</html>"))
        assertEquals(SearchAnswer.Busy, searchAnswer(429, null))
    }

    @Test fun `the query keeps the words typed, without the syntax of the search, and the phone's country`() {
        assertEquals("skyr natur countries:\"en:germany\"", searchQuery("  skyr  natur ", "en:germany"))
        assertEquals("Ben Jerry's Cookie and Dough", searchQuery("Ben & Jerry's: Cookie AND Dough", null))
        assertEquals("k classic skyr", searchQuery("k-classic (skyr)*", null))
        assertEquals("", searchQuery("**", "en:germany"))
        assertEquals("en:germany", countryTag("Germany"))
        assertEquals("en:united-states", countryTag("United States"))
        assertEquals("en:united-kingdom", countryTag(" United Kingdom "))
        assertEquals(null, countryTag(""))
        assertEquals("de,en", searchLangs("de"))
        assertEquals("en", searchLangs("en"))
        assertEquals("en", searchLangs(""))
        assertTrue(searchFields("de").split(',').containsAll(listOf("product_name_de", "nutriments", "brands", "product_quantity")))
        assertEquals(searchFields("en"), searchFields("zz-bad"))
        assertTrue(searchFields("x;y").split(',').none { it.startsWith("product_name_x") })
    }

    @Test fun `the fields asked for include the quantity, without which the pack is left out`() {
        val fields = PRODUCT_FIELDS.split(',')
        assertTrue(fields.containsAll(listOf("quantity", "product_quantity", "nutriments", "serving_quantity", "brands", "product_name")))
    }

    @Test fun `the rate limits count the requests of the last minute`() {
        val sent = (0 until 15).fold(emptyList<Long>()) { at, i -> withRequest(at, i * 1_000L) }
        assertTrue(!underLimit(sent, 20_000, 15))
        assertTrue(underLimit(sent, 60_000, 15))
        assertEquals(15, sent.size)
        assertEquals(1, withRequest(sent, 200_000).size)
    }

    @Test fun `the plain foods of the asset are read, a broken one gives none`() {
        val foods = parsePlainFoods("""[{"id":"171287","name":"Egg, whole, raw","kcal":143,"protein":12.6,"carbs":0.7,"fat":9.5,"serving":50},{"name":"no id"}]""")
        assertEquals(listOf(Food("fdc:171287", "Egg, whole, raw", kcal = 143.0, protein = 12.6, carbs = 0.7, fat = 9.5, serving = 50.0, source = FoodSource.PLAIN)), foods)
        assertEquals(emptyList<Food>(), parsePlainFoods("not json"))
    }

    /** The asset the app ships, built by `scripts/plain-foods/build.py`: everyday foods, each once, in their limits. */
    @Test fun `the plain foods shipped are 250 to 300 everyday foods, each in its limits`() {
        val foods = parsePlainFoods(File("src/main/assets/foods/plain.json").readText())
        assertTrue("${foods.size} plain foods", foods.size in 250..300)
        assertEquals(foods.size, foods.map { it.name.lowercase() }.toSet().size)
        assertTrue(foods.all { it.source == FoodSource.PLAIN && it.id.startsWith("fdc:") && it == foodWithin(it) && it.kcal > 0 })
        val names = foods.map { it.name }
        for (everyday in listOf("Rice, white, cooked", "Rice, white, raw", "Egg, whole, raw", "Banana", "Chicken breast, raw", "Chicken breast, grilled", "Oats, rolled")) assertTrue(everyday, everyday in names)
        assertEquals(FoodUnit.ML, foods.first { it.name == "Milk, whole" }.unit)
        assertEquals(50.0, foods.first { it.name == "Egg, whole, raw" }.serving!!, 0.0)
    }
}
