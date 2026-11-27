package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.StateJson
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodIcon
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.SearchAnswer
import io.github.alexanderop.eisen.feature.food.fileName
import io.github.alexanderop.eisen.feature.food.guessIcon
import io.github.alexanderop.eisen.feature.food.iconOf
import io.github.alexanderop.eisen.feature.food.iconOfCategories
import io.github.alexanderop.eisen.feature.food.iconOfEntry
import io.github.alexanderop.eisen.feature.food.iconOfHit
import io.github.alexanderop.eisen.feature.food.key
import io.github.alexanderop.eisen.feature.food.parsePlainFoods
import io.github.alexanderop.eisen.feature.food.productAnswer
import io.github.alexanderop.eisen.feature.food.quickEntry
import io.github.alexanderop.eisen.feature.food.searchAnswer
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The picture of a food: the guess from its name (English and German, and the traps of German words), Open Food Facts'
 * categories against real answers saved in `src/test/resources/off` (fetched on 2026-11-27 with the app's `fields=`,
 * Edeka and Gut & Günstig products), the order of the three sources, the file, the plain foods' asset, and that the enum,
 * the pictures and the script that cut them agree.
 */
class FoodIconTest {
    private fun saved(path: String): String = File("src/test/resources/off/$path").readText()

    private fun guesses(vararg cases: Pair<String, FoodIcon?>) {
        for ((name, icon) in cases) assertEquals("\"$name\"", icon, guessIcon(name))
    }

    // The guess from the name

    @Test fun `English names say what they are`() = guesses(
        "Milk" to FoodIcon.MILK,
        "Banana" to FoodIcon.BANANA,
        "Chicken breast, grilled" to FoodIcon.POULTRY,
        "Rice, white, cooked" to FoodIcon.RICE,
        "Oats, rolled" to FoodIcon.CEREAL,
        "Whey protein powder" to FoodIcon.SHAKE,
        "Protein bar" to FoodIcon.BAR,
        "Blueberries" to FoodIcon.BERRIES,
        "Broccoli, cooked" to FoodIcon.VEGETABLE,
        "Salmon fillet" to FoodIcon.FISH,
        "Kidney beans" to FoodIcon.BEANS,
        "Potato chips" to FoodIcon.CHIPS,
        "Orange juice" to FoodIcon.JUICE,
        "Coffee, black" to FoodIcon.COFFEE,
        "Pizza, cheese" to FoodIcon.PIZZA,
        "Ice cream, vanilla" to FoodIcon.ICE_CREAM,
        "Sparkling water" to FoodIcon.WATER,
        "Eggs" to FoodIcon.EGG,
    )

    @Test fun `German names say what they are`() = guesses(
        "Milch" to FoodIcon.MILK,
        "H-Vollmilch 3,5%" to FoodIcon.MILK,
        "Joghurt mild" to FoodIcon.YOGURT,
        "Skyr Natur" to FoodIcon.YOGURT,
        "Magerquark" to FoodIcon.YOGURT,
        "Bergkäse" to FoodIcon.CHEESE,
        "Ei" to FoodIcon.EGG,
        "Eier Freiland" to FoodIcon.EGG,
        "Hähnchenbrustfilet" to FoodIcon.POULTRY,
        "Putenbrust" to FoodIcon.POULTRY,
        "Rinderhack" to FoodIcon.MEAT,
        "Schweineschnitzel" to FoodIcon.MEAT,
        "Leberwurst" to FoodIcon.SAUSAGE,
        "Kochschinken" to FoodIcon.SAUSAGE,
        "Salami" to FoodIcon.SAUSAGE,
        "Lachsfilet" to FoodIcon.FISH,
        "Thunfisch" to FoodIcon.FISH,
        "Vollkornbrot" to FoodIcon.BREAD,
        "Weizenbrötchen" to FoodIcon.BREAD,
        "Toast" to FoodIcon.BREAD,
        "Reis" to FoodIcon.RICE,
        "Basmatireis" to FoodIcon.RICE,
        "Bandnudeln" to FoodIcon.PASTA,
        "Pasta" to FoodIcon.PASTA,
        "Haferflocken" to FoodIcon.CEREAL,
        "Früchtemüsli" to FoodIcon.CEREAL,
        "Kartoffeln" to FoodIcon.POTATO,
        "Apfel" to FoodIcon.FRUIT,
        "Obst" to FoodIcon.FRUIT,
        "Himbeeren" to FoodIcon.BERRIES,
        "Gemüse" to FoodIcon.VEGETABLE,
        "Brokkoli" to FoodIcon.VEGETABLE,
        "Salat" to FoodIcon.SALAD,
        "Kidney Bohnen" to FoodIcon.BEANS,
        "Rote Linsen" to FoodIcon.BEANS,
        "Haselnüsse" to FoodIcon.NUTS,
        "Mandeln" to FoodIcon.NUTS,
        "Erdnüsse geröstet" to FoodIcon.NUTS,
        "Zartbitterschokolade" to FoodIcon.CHOCOLATE,
        "Eis" to FoodIcon.ICE_CREAM,
        "Kartoffelchips" to FoodIcon.CHIPS,
        "Orangensaft" to FoodIcon.JUICE,
        "Mineralwasser" to FoodIcon.WATER,
        "Kaffee" to FoodIcon.COFFEE,
        "Pils" to FoodIcon.BEER,
        "Weißbier" to FoodIcon.BEER,
        "Whey Protein" to FoodIcon.SHAKE,
        "Protein" to FoodIcon.SHAKE,
        "Eiweißpulver Vanille" to FoodIcon.SHAKE,
        "Müsliriegel" to FoodIcon.BAR,
        "Erdbeer Konfitüre" to FoodIcon.SPREAD,
        "Tomatensuppe" to FoodIcon.SOUP,
    )

    @Test fun `a word matches only as a word or the start of one, never inside another`() = guesses(
        // "Reis" is not in "Preis", "Eis" not in "Reis" or "Eiweiß", "Ei" not in "Eis".
        "Preis" to null,
        "Reis" to FoodIcon.RICE,
        "Eiweiß" to FoodIcon.SHAKE,
        "Eiweißbrot" to FoodIcon.BREAD,
        "Milchreis" to FoodIcon.RICE,
        "Vanilleeis" to FoodIcon.ICE_CREAM,
        "Wassereis" to FoodIcon.ICE_CREAM,
        "Barilla Spaghetti" to FoodIcon.PASTA,
        "Saftige Hähnchenbrust" to FoodIcon.POULTRY,
        "Schweine Filet" to FoodIcon.MEAT,
        "Eggplant" to FoodIcon.VEGETABLE,
        "Hamburger" to FoodIcon.BURGER,
    )

    @Test fun `case and accents do not matter`() = guesses(
        "KÄSE" to FoodIcon.CHEESE,
        "Kase" to FoodIcon.CHEESE,
        "Kaese" to FoodIcon.CHEESE,
        "Muesli" to FoodIcon.CEREAL,
        "Hähnchen" to FoodIcon.POULTRY,
        "Haehnchen" to FoodIcon.POULTRY,
        "Brötchen" to FoodIcon.BREAD,
    )

    @Test fun `of several words the strongest wins, then the head of the name`() = guesses(
        // A whole compound or phrase that would read wrong by its parts.
        "Erdnussbutter" to FoodIcon.SPREAD,
        "Peanut butter" to FoodIcon.SPREAD,
        "Hafermilch" to FoodIcon.MILK,
        "Oat drink" to FoodIcon.MILK,
        "Wassermelone" to FoodIcon.FRUIT,
        "Eistee Pfirsich" to FoodIcon.SODA,
        // A dish beats what it holds, a product its flavour, an animal its cut.
        "Pizza Salami" to FoodIcon.PIZZA,
        "Mini Pizza Salami" to FoodIcon.PIZZA,
        "Proteinriegel" to FoodIcon.BAR,
        "Joghurt Erdbeere" to FoodIcon.YOGURT,
        "Schoko Müsli" to FoodIcon.CEREAL,
        "Hähnchen Schnitzel" to FoodIcon.POULTRY,
        "Protein Pudding" to FoodIcon.YOGURT,
        // Of the same kind the last one, the head: in a compound and in English.
        "Vollmilchschokolade" to FoodIcon.CHOCOLATE,
        "Milk chocolate" to FoodIcon.CHOCOLATE,
        "Chocolate milk" to FoodIcon.MILK,
        "Apfelsaft" to FoodIcon.JUICE,
        "Butterkeks" to FoodIcon.COOKIE,
        "Erdnussflips" to FoodIcon.CHIPS,
        // What comes after a comma or a "mit", "im", "with" says how, not what.
        "Thunfisch Filets im eigenen Saft" to FoodIcon.FISH,
        "Greek yogurt, plain, whole milk" to FoodIcon.YOGURT,
        "Spaghetti mit Tomatensauce" to FoodIcon.PASTA,
    )

    @Test fun `a name that says nothing gives no guess`() = guesses(
        "" to null,
        "   " to null,
        "Quick kcal" to null,
        "Lunch at Anna's" to null,
        "4056489000006" to null,
    )

    // Open Food Facts' categories

    private fun found(barcode: String): Food = (productAnswer(barcode, 200, saved("product/$barcode.json")) as ProductAnswer.Found).food

    @Test fun `a product read gets the picture of its most specific category with one, from real answers`() {
        val expected = mapOf(
            "4311501065525" to FoodIcon.MILK, // Edeka Milch: …, milks, whole-milks, de:Molkereiprodukte
            "4311501384268" to FoodIcon.YOGURT, // Gut & Günstig Joghurt
            "4311501457023" to FoodIcon.SPREAD, // Erdbeer Konfitüre: fruit-based-foods, …, jams, strawberry-jams
            "4311596436040" to FoodIcon.SODA, // Gut & Günstig Cola: …, colas, sweetened-beverages
            "4311501705483" to FoodIcon.FISH, // Lachs
            "4311501634295" to FoodIcon.CEREAL, // Schoko Müsli: …, chocolate-cereals, mueslis, mueslis-with-chocolate
            "4311501078471" to FoodIcon.PIZZA, // Pizza Speziale: frozen-foods, meals, …, frozen-pizzas
            "4311596421640" to FoodIcon.CHIPS, // Chips Paprika
            "4313249419653" to FoodIcon.MEAT, // Hackfleisch Gemischt
            "4311501133262" to FoodIcon.BAR, // Müsliriegel Schokolade: …, bars, cereal-bars, chocolate-cereal-bars
            "4311501099278" to FoodIcon.SPREAD, // Nuss Nougat Creme
            // The rule is the most specific tag: a salmon lasagne is filed last under salmons.
            "4311501668252" to FoodIcon.FISH,
        )
        for ((barcode, icon) in expected) assertEquals(barcode, icon, found(barcode).icon)
    }

    @Test fun `a product without a category the table knows keeps none, and its name says it`() {
        // Gut & Günstig Hähnchen Schnitzel has no categories at all.
        val schnitzel = found("4311501714768")
        assertNull(schnitzel.icon)
        assertEquals(FoodIcon.POULTRY, iconOf(schnitzel))
        // The answers saved before part E were asked without categories: no icon, the name's guess.
        val nutella = found("3017620422003")
        assertNull(nutella.icon)
        assertEquals(FoodIcon.SPREAD, iconOf(nutella))
    }

    @Test fun `the most specific tag with a picture wins, unknown tags and none say nothing`() {
        val nutella = listOf("en:breakfasts", "en:desserts", "en:spreads", "en:sweet-spreads", "fr:pates-a-tartiner", "en:chocolate-spreads", "en:hazelnut-spreads")
        assertEquals(FoodIcon.SPREAD, iconOfCategories(nutella))
        assertEquals(FoodIcon.BUTTER, iconOfCategories(listOf("en:dairies", "en:spreads", "en:fats", "en:spreadable-fats", "en:milkfat", "en:butters", "de:Butterzubereitung")))
        assertEquals(FoodIcon.MILK, iconOfCategories(listOf("en:beverages", "en:plant-based-milk-alternatives")))
        assertNull(iconOfCategories(listOf("en:beverages", "en:plant-based-foods", "en:snacks", "en:dairies", "de:getränke")))
        assertNull(iconOfCategories(emptyList()))
        // Gut & Günstig Kartoffelknödel: a broad "meals" says less than its name.
        assertNull(iconOfCategories(listOf("en:meals", "en:knodel")))
        assertEquals(FoodIcon.POTATO, guessIcon("Kartoffelknödel"))
    }

    @Test fun `a search hit carries the picture of its categories, also without its calories`() {
        val hits = (searchAnswer(200, saved("search/cola.json"), "de") as SearchAnswer.Found).hits
        val first = hits.first { it.barcode == "41061817" }
        assertNull(first.food)
        assertEquals(FoodIcon.SODA, first.icon)
        assertEquals(FoodIcon.SODA, iconOfHit(first))
        // "Die Limo Limette-Zitrone" has no categories: its name says it.
        val limo = hits.first { it.barcode == "4048517734090" }
        assertNull(limo.icon)
        assertEquals(FoodIcon.SODA, iconOfHit(limo))
    }

    // The order of the three sources

    private val skyr = Food("off:4056489000006", "Skyr Natur", kcal = 60.0, source = FoodSource.SCANNED)

    @Test fun `a food shows its own icon, else the guess from its name, else the plate`() {
        assertEquals(FoodIcon.YOGURT, iconOf(skyr))
        assertEquals(FoodIcon.BERRIES, iconOf(skyr.copy(icon = FoodIcon.BERRIES)))
        assertEquals(FoodIcon.PLATE, iconOf(skyr.copy(name = "Lunch at Anna's")))
    }

    @Test fun `an entry shows its food's icon while the food exists, else its own name's, quick kcal the plate`() {
        val entry = FoodEntry("e", skyr.id, "Skyr Natur", 450.0, kcal = 270, at = 0, day = "2026-11-27")
        assertEquals(FoodIcon.BERRIES, iconOfEntry(entry, skyr.copy(icon = FoodIcon.BERRIES)))
        assertEquals(FoodIcon.YOGURT, iconOfEntry(entry, null))
        assertEquals(FoodIcon.PLATE, iconOfEntry(quickEntry(800, null, "", "q", 0, "2026-11-27"), null))
        assertEquals(FoodIcon.PIZZA, iconOfEntry(quickEntry(800, null, "Pizza at Luigi's", "q", 0, "2026-11-27"), null))
    }

    // The file

    @Test fun `a food's icon is saved by its name and read back, and without one it is left out of the file`() {
        val state = freshState(WeightUnit.KG).copy(foods = listOf(skyr.copy(icon = FoodIcon.ICE_CREAM), skyr.copy(id = "own:1", source = FoodSource.OWN)))
        val text = encodeState(state)
        val foods = StateJson.parseToJsonElement(text).jsonObject.getValue("foods").toString()
        assertTrue(foods, foods.contains("\"icon\":\"ice_cream\""))
        assertEquals(1, Regex("\"icon\"").findAll(foods).count())
        assertEquals(Loaded.Ok(state), loadState(text))
    }

    @Test fun `an icon a newer build wrote is read as none, and the file still loads`() {
        val text = encodeState(freshState(WeightUnit.KG).copy(foods = listOf(skyr))).replace("\"source\":\"scanned\"", "\"source\":\"scanned\",\"icon\":\"dragonfruit\"")
        assertTrue(text.contains("dragonfruit"))
        val loaded = loadState(text) as Loaded.Ok
        assertEquals(listOf(skyr), loaded.state.foods)
        val chosen = encodeState(freshState(WeightUnit.KG).copy(plainIcons = mapOf("fdc:1" to "banana", "fdc:2" to "dragonfruit")))
        assertEquals(mapOf("fdc:1" to "banana"), (loadState(chosen) as Loaded.Ok).state.plainIcons)
    }

    @Test fun `a file of build 25 loads unchanged, its foods have no icon and show their name's`() {
        val text = File("src/test/resources/state/build-25-food.json").readText()
        assertFalse(text.contains("icon"))
        val state = (loadState(text) as Loaded.Ok).state
        assertEquals(listOf(null), state.foods.map { it.icon })
        assertEquals(emptyMap<String, String>(), state.plainIcons)
        assertEquals(FoodIcon.YOGURT, iconOf(state.foods.single()))
        // Saved again, it gains only the empty choices of plain foods.
        val again = StateJson.parseToJsonElement(encodeState(state)).jsonObject
        assertEquals(StateJson.parseToJsonElement(text).jsonObject + ("plainIcons" to StateJson.parseToJsonElement("{}")), again)
    }

    // The plain foods

    private val plain = parsePlainFoods(File("src/main/assets/foods/plain.json").readText())

    @Test fun `every plain food of the asset carries its icon, and few end on the plate`() {
        assertEquals(279, plain.size)
        assertEquals(emptyList<String>(), plain.filter { it.icon == null }.map { it.name })
        val plates = plain.filter { it.icon == FoodIcon.PLATE }.map { it.name }
        assertTrue("on the plate: $plates", plates.size <= 5)
        val names = plain.associate { it.name to it.icon }
        assertEquals(FoodIcon.BANANA, names["Banana"])
        assertEquals(FoodIcon.RICE, names["Rice, white, cooked"])
        assertEquals(FoodIcon.POULTRY, names["Chicken breast, grilled"])
        assertEquals(FoodIcon.SHAKE, names["Whey protein powder"])
    }

    @Test fun `a plain food in an asset without icons, or with an unknown one, has none`() {
        val foods = parsePlainFoods("""[{"id":"1","name":"Rice","kcal":130},{"id":"2","name":"Mystery","kcal":100,"icon":"dragonfruit"}]""")
        assertEquals(listOf(null, null), foods.map { it.icon })
        assertEquals(listOf(FoodIcon.RICE, FoodIcon.PLATE), foods.map(::iconOf))
    }

    // The pictures

    @Test fun `FoodIcon, the pictures in res and the sheets of cut_py name the same 42 pictures`() {
        val enum = FoodIcon.entries.map { it.key }.toSet()
        val files = File("src/main/res/drawable-nodpi").listFiles().orEmpty().map { it.name }.filter { it.startsWith("food_") }.toSet()
        val script = File("../scripts/food-icons/cut.py").readText()
        val sheets = Regex("""SHEETS = \[(.*?)\n\]""", RegexOption.DOT_MATCHES_ALL).find(script)?.groupValues?.get(1).orEmpty()
        val cut = Regex(""""([a-z_]+)"""").findAll(sheets).map { it.groupValues[1] }.toList()
        assertEquals(42, enum.size)
        assertEquals("cut.py names a picture twice", cut.size, cut.toSet().size)
        assertEquals(enum, cut.toSet())
        assertEquals(FoodIcon.entries.map { it.fileName }.toSet(), files)
    }
}
