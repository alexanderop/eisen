package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.food.FoodUnit
import io.github.alexanderop.eisen.feature.food.LabelLine
import io.github.alexanderop.eisen.feature.food.LabelMark
import io.github.alexanderop.eisen.feature.food.LabelReading
import io.github.alexanderop.eisen.feature.food.LabelText
import io.github.alexanderop.eisen.feature.food.LabelValue
import io.github.alexanderop.eisen.feature.food.LabelWord
import io.github.alexanderop.eisen.feature.food.TextBox
import io.github.alexanderop.eisen.feature.food.addsUp
import io.github.alexanderop.eisen.feature.food.readLabel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Reading nutrition tables, against what ML Kit's text recognizer (com.google.mlkit:text-recognition 16.0.1, bundled) read
 * on the emulator from real photos of Open Food Facts (`image_nutrition_url`, fetched on 2026-11-27), saved in
 * `src/test/resources/label/<barcode>.json`: the lines, their boxes and their words, as the app gets them. The real values
 * are what each label prints (Open Food Facts' `nutriments` where they agree; where its record is of an older label, the
 * photo's).
 */
class LabelTest {
    /** What a label prints per 100 g or ml; a label of portions only: per 100 worked out from its portion. */
    private data class Truth(
        val code: String,
        val name: String,
        val kcal: Double?,
        val protein: Double?,
        val carbs: Double?,
        val fat: Double?,
        val unit: FoodUnit = FoodUnit.G,
        val serving: Double? = null,
        /** German or English: what the reader is for. The others only must not make anything up. */
        val ours: Boolean = true,
        /** One of the 29 photos fetched after the reader was written, to see how it does on photos it was not made on. */
        val heldOut: Boolean = false,
    ) {
        val values: List<Double?> get() = listOf(kcal, protein, carbs, fat)
    }

    private val truths = listOf(
        Truth("4006374962278", "Hähnchen Schenkel (Gut & Günstig), Ø/100g, units in words", 209.0, 16.1, 0.2, 16.1),
        Truth("4009900584357", "Extra Kaugummi, capitals, <0.5 g", 156.0, 0.0, 62.0, 0.0),
        Truth("4311501042533", "Jogurt Drink (Edeka), curved bottle, glossy", 65.0, 2.9, 12.3, 0.1),
        Truth("4311501065525", "Milch (Gut & Günstig), per 100 ml, curved", 64.0, 3.3, 4.8, 3.5, FoodUnit.ML),
        Truth("4311501070628", "Vanille Eis, grid: 100 g and portion (50 g)", 200.0, 1.6, 25.5, 10.1, serving = 50.0),
        Truth("4311501099278", "Nuss Nougat Creme, curved jar", 557.0, 6.8, 50.1, 35.6),
        Truth("4311501133262", "Müsliriegel, 100 g and Riegel (25 g), small print", 442.0, 7.3, 66.5, 13.9, serving = 25.0),
        Truth("4311501450611", "Bandnudeln, 631 px, wrinkled bag", 365.0, 14.0, 70.0, 2.6),
        Truth("4311501492123", "Kokosnussmilch, per 100 ml", 211.0, 1.7, 3.2, 21.0, FoodUnit.ML),
        Truth("4311501604977", "Knusper Müsli, sharp", 419.0, 10.7, 56.6, 13.9),
        Truth("4311501612583", "Tortellini, glossy wrinkled bag", 128.0, 4.7, 15.0, 5.0),
        Truth("4311501634295", "Schoko Müsli, 290 px wide", 413.0, 11.0, 61.0, 12.0),
        Truth("4311501668252", "Lachs Lasagne, 100 g and Schale (380 g)", 153.0, 8.1, 11.3, 8.2, serving = 380.0),
        Truth("4311501679357", "Butter Croissants, 100 g and Stück (ca. 60 g)", 363.0, 6.8, 38.0, 20.0, serving = 60.0),
        Truth("4311501687673", "Monster Doppel Kekse, 100 g and Keks (26,3 g)", 473.0, 5.4, 69.4, 18.6, serving = 26.3),
        Truth("4311501705483", "Lachs, 0 g carbs", 170.0, 21.0, 0.0, 9.6),
        Truth("4311501762516", "Alpenvollmilch Schokolade, 100 g and Portion (17 g)", 550.0, 6.5, 56.4, 32.8, serving = 17.0),
        Truth("4311501769058", "Eiweiß Brot, 100 g and Scheibe (56 g), 14 g fibre", 256.0, 21.2, 6.5, 13.0, serving = 56.0),
        Truth("4311596410644", "Spaghetti, the head cut off", 368.0, 12.0, 75.0, 1.5),
        Truth("4311596421640", "Chips Paprika, crumpled bag", 521.0, 6.0, 50.7, 31.5),
        Truth("4311596436040", "Gut & Günstig Cola, per 100 ml", 39.0, 0.0, 9.7, 0.0, FoodUnit.ML),
        Truth("4311596455065", "Thunfisch, curved can", 112.0, 25.3, 0.1, 1.1),
        Truth("4311596455188", "Karotten Saft, per 100 ml", 35.0, 0.5, 7.8, 0.08, FoodUnit.ML),
        Truth("4311596479696", "Reis Parboiled, small table in a large photo", 353.0, 7.3, 77.0, 1.3),
        Truth("4311596616336", "Markenbutter, one line per row", 744.0, 0.6, 1.0, 82.0),
        Truth("4313249419653", "Hackfleisch gemischt", 260.0, 16.5, 0.8, 22.0),
        Truth("5000168001142", "McVitie's Digestives, English, kJ and kcal in two rows, per biscuit (14.7 g)", 483.0, 7.0, 63.6, 21.3, serving = 14.7),
        Truth("5449000000996", "Coca-Cola can, English, curved, per 100 ml and 330 ml", 42.0, 0.0, 10.6, 0.0, FoodUnit.ML, 330.0),
        Truth("0038000138416", "Doritos, US Nutrition Facts: per serving of 28 g only", 150.0 / 0.28, 1 / 0.28, 16 / 0.28, 9 / 0.28, serving = 28.0),
        Truth("50173273", "Orbit, running text in capitals", 157.0, 0.0, 63.4, 0.0),
        Truth("57028095", "Kaugummi, running text on a curved tin", 158.0, 0.1, 63.0, 0.1),
        Truth("57030104", "Kaugummi, running text on a curved tin, glare", 160.0, 0.1, 65.0, 0.1),
        Truth("3017620422003", "Nutella, the front of the jar: no table", null, null, null, null),
        Truth("8000500310427", "Nutella Biscuits, the front of the bag: no table", null, null, null, null, ours = false),
        Truth("5000157024671", "Heinz Beanz, Nordic languages", 81.0, 4.8, 15.5, 0.4, ours = false),
        Truth("5000159461122", "Snickers, French and Dutch, very curved", 481.0, 8.6, 60.5, 22.5, ours = false),
        Truth("5010029000061", "Weetabix, Spanish and Portuguese", 362.0, 12.0, 69.0, 2.0, ours = false),
        Truth("7622210449283", "Prince, French", 467.0, 6.3, 69.0, 17.0, ours = false),
        // Fetched after the reader was written (see heldOut).
        Truth("4011364586150", "Putenhack, Ø-less pro/100g, capitals", 159.0, 18.0, 0.7, 9.8, heldOut = true),
        Truth("4014388001036", "Radeberger, energy only in running text", 41.0, null, null, null, FoodUnit.ML, heldOut = true),
        Truth("4048952500090", "Wiener, 100 g and Portion (63 g), kJ over kcal", 269.0, 12.5, 1.0, 23.0, serving = 63.0, heldOut = true),
        Truth("4311501041277", "Edelbitter Schokolade", 600.0, 10.9, 20.0, 50.0, heldOut = true),
        Truth("4311501123621", "Sauce Hollandaise, small table", 208.0, 0.6, 8.5, 19.1, heldOut = true),
        Truth("4311501342602", "Macadamia, header in the title", 761.0, 9.0, 5.0, 77.0, heldOut = true),
        Truth("4311501355688", "Knoblauch-Butter, purple glossy", 581.0, 1.3, 3.6, 62.3, heldOut = true),
        Truth("4311501447918", "Kaiser Gemüse, wrinkled, tilted", 25.0, 1.5, 2.9, 0.2, heldOut = true),
        Truth("4311501449073", "Gemüse Nudeltopf, web page", 37.3, 1.4, 5.0, 0.9, heldOut = true),
        Truth("4311501463536", "Thai Suppe, per 100 ml", 35.0, 1.5, 3.7, 1.5, FoodUnit.ML, heldOut = true),
        Truth("4311501473320", "Gemüse Bratlinge", 320.0, 10.6, 56.3, 3.0, heldOut = true),
        Truth("4311501480366", "Erdnuss Flips, crumpled", 495.0, 14.6, 54.7, 22.6, heldOut = true),
        Truth("4311501608258", "Früchte Müsli", 338.0, 8.4, 62.5, 4.1, heldOut = true),
        Truth("4311501614884", "Rahmspinat", 55.0, 3.4, 3.1, 2.8, heldOut = true),
        Truth("4311501639887", "Fettarme Milch, per 100 ml", 47.0, 3.4, 4.9, 1.5, FoodUnit.ML, heldOut = true),
        Truth("4311501670149", "Harzer Käse, 100 g and Portion (50 g)", 125.0, 30.0, 0.1, 0.5, serving = 50.0, heldOut = true),
        Truth("4311501684207", "Lachsfilet", 202.0, 19.0, 0.0, 14.0, heldOut = true),
        Truth("4311501685549", "Express Reis, washed out", 177.0, 3.8, 32.0, 3.4, heldOut = true),
        Truth("4311501690567", "Cervelat, davon on one line", 306.0, 19.0, 1.0, 25.0, heldOut = true),
        Truth("4311501694503", "Müsli Riegel, 100 g and Riegel (25 g)", 380.0, 8.2, 63.0, 12.0, serving = 25.0, heldOut = true),
        Truth("4311501712207", "Apfelmus", 57.0, 0.3, 12.5, 0.1, heldOut = true),
        Truth("4311501716625", "Curry Sauce, labels cut off left, per 100 ml", 327.0, 0.6, 13.4, 29.7, FoodUnit.ML, heldOut = true),
        Truth("4311501722558", "Laktosefreie Milch, per 100 ml", 69.0, 3.5, 4.9, 3.9, FoodUnit.ML, heldOut = true),
        Truth("4311596413232", "Öl, per 100 ml, curved", 828.0, 0.0, 0.0, 92.0, FoodUnit.ML, heldOut = true),
        Truth("4311596413874", "Kartoffelknödel, 100 g and Knödel (90 g)", 106.0, 1.1, 24.0, 0.6, serving = 90.0, heldOut = true),
        Truth("4311596413898", "Kartoffel Püree, 100 g and Portion (205 g)", 65.0, 2.0, 11.4, 1.0, serving = 205.0, heldOut = true),
        Truth("4311596447893", "Cola Zero, 1/0 kJ/kcal", 0.0, 0.01, 0.01, 0.0, FoodUnit.ML, heldOut = true),
        Truth("4311501312957", "Brotkorb: three tables of three breads", null, null, null, null, ours = false, heldOut = true),
        Truth("4311501666593", "Käse-Snack: two tables of two cheeses, the first is Butterkäse", 332.0, 24.0, 0.5, 26.0, ours = false, heldOut = true),
    )

    private fun saved(code: String): LabelText = savedLabel(code)

    private fun right(truth: Double?, read: LabelValue?): Boolean = when {
        truth == null -> read == null
        read == null -> false
        else -> abs(read.value - truth) <= maxOf(0.051, truth * 0.006)
    }

    private fun reading(truth: Truth): LabelReading? = readLabel(saved(truth.code))

    @Test fun `every saved photo is in the table, and every table has its photo`() {
        val files = File("src/test/resources/label").list().orEmpty().map { it.removeSuffix(".json") }.toSet()
        assertEquals(files, truths.map { it.code }.toSet())
    }

    @Test fun `a value is right, empty, or marked to check, never a wrong value marked read`() {
        val wrongAndRead = truths.flatMap { truth ->
            val read = reading(truth)
            truth.values.zip(read?.values ?: List(4) { null }).mapIndexedNotNull { i, (real, value) ->
                if (value != null && value.mark == LabelMark.READ && !right(real, value)) "${truth.name}: ${FIELDS[i]} read ${value.value}, is $real" else null
            }
        }
        assertEquals(emptyList<String>(), wrongAndRead)
    }

    /** For each of kcal, protein, carbs and fat, on how many of [photos] it is right; and on how many all four are. */
    private fun tally(photos: List<Truth>): Pair<List<Int>, Int> {
        val table = photos.map { truth ->
            val read = reading(truth)?.values ?: List(4) { null }
            println(truth.name + " | " + truth.values.indices.joinToString(" | ") { i -> "${truth.values[i]?.let { "%.1f".format(it) }} → ${read[i]?.let { "${it.value} ${it.mark.name.lowercase()}" } ?: "-"} ${if (right(truth.values[i], read[i])) "ok" else "WRONG"}" })
            truth.values.zip(read).map { (real, value) -> right(real, value) }
        }
        return FIELDS.indices.map { i -> table.count { it[i] } } to table.count { rights -> rights.all { it } }
    }

    @Test fun `the photos of German and English labels, how many give each value right`() {
        // Pinned: a change to the reader that loses a photo shows here. Kcal, protein, carbs, fat; then all four.
        val made = truths.filter { it.ours && it.kcal != null && !it.heldOut }
        assertEquals(32, made.size)
        assertEquals(listOf(32, 31, 32, 31) to 30, tally(made))
        // On the 27 it was not written for: 20 with all four before the fixes they showed (a "9.8 9" split, "EIWEIO", a
        // head "% RM pro 100 g" taken for the values', "kJ" glued to "Brennwert"), 22 after.
        val held = truths.filter { it.ours && it.kcal != null && it.heldOut }
        assertEquals(27, held.size)
        assertEquals(listOf(24, 25, 25, 23) to 22, tally(held))
    }

    @Test fun `a photo without a table gives nothing`() {
        assertNull(readLabel(saved("3017620422003")))
        assertNull(readLabel(saved("8000500310427")))
        assertNull(readLabel(LabelText(emptyList())))
    }

    @Test fun `the 4, 4 and 9 kcal a gram check`() {
        // The bread with 14 g of fibre: 228 kcal by 4/4/9 against the 256 it states, 11 % under; with the fibre 256.
        assertTrue(!addsUp(256.0, 21.2, 6.5, 13.0))
        assertTrue(addsUp(256.0, 21.2, 6.5, 13.0, fibre = 14.2))
        assertTrue(addsUp(39.0, 0.0, 9.7, 0.0))
        // A comma lost: 13 g of fat instead of 1.3.
        assertTrue(!addsUp(353.0, 7.3, 77.0, 13.0))
    }

    private fun read(code: String): LabelReading = readLabel(saved(code)) ?: error("nothing read on $code")

    /** A table typed by hand: each row's label on the left, its number in the column under [head] on the right. */
    private fun table(head: String?, vararg rows: Pair<String, String>): LabelText {
        val lines = mutableListOf<LabelLine>()
        if (head != null) lines += line(head, 600, 0)
        rows.forEachIndexed { i, (label, value) ->
            lines += line(label, 20, 60 * (i + 1))
            lines += line(value, 600, 60 * (i + 1))
        }
        return LabelText(lines)
    }

    private fun line(text: String, left: Int, top: Int): LabelLine {
        var x = left
        val words = text.split(' ').map { word -> LabelWord(word, TextBox(x, top, x + 18 * word.length, top + 40)).also { x += 18 * word.length + 12 } }
        return LabelLine(text, TextBox(left, top, x - 12, top + 40), 0.0, words)
    }

    @Test fun `German words, a decimal comma, units as words of their own`() {
        // "Eiweiß", "16,1" "g", "870 kJ/209 kcal", "Ø/100g" read as "O100g".
        assertEquals(LabelReading(FoodUnit.G, LabelValue(209.0, LabelMark.READ), LabelValue(16.1, LabelMark.READ), LabelValue(0.2, LabelMark.READ), LabelValue(16.1, LabelMark.READ)), read("4006374962278"))
    }

    @Test fun `English words, per 100 g, not per biscuit`() {
        val coke = read("5449000000996")
        assertEquals(listOf(42.0, 0.0, 10.6, 0.0), coke.values.map { it?.value })
        assertEquals(FoodUnit.ML, coke.unit)
    }

    @Test fun `davon Zucker is not carbs, gesättigte Fettsäuren and Saturated Fat are not fat`() {
        // Hähnchen: Zucker 0,0 g and gesättigte Fettsäuren 4,9 g sit right under carbs 0,2 g and fat 16,1 g.
        val chicken = read("4006374962278")
        assertEquals(0.2, chicken.carbs?.value)
        assertEquals(16.1, chicken.fat?.value)
        // Doritos: "Saturated Fat/Grasa Saturada 2.5g" under "Total Fat/Grasa Total 9g".
        assertEquals(32.1, read("0038000138416").fat?.value)
        // A row "of which sugars" in English.
        assertEquals(63.6, read("5000168001142").carbs?.value)
    }

    @Test fun `a value under 0,5 g, a comma lost, a g read as 9`() {
        // Extra: "<0.5g" is the sugar; protein "0g" and fat "09" (0 g with the g read as a 9).
        assertEquals(listOf(156.0, 0.0, 62.0, 0.0), read("4009900584357").values.map { it?.value })
        // Kaugummi: "Eiweiß <01g": the comma lost, 0,1.
        assertEquals(0.1, read("57028095").protein?.value)
        // Reis: fat "139" is 1,3 g that lost its comma and had its g read as a 9: only that makes the 353 kcal add up.
        assertEquals(LabelValue(1.3, LabelMark.READ), read("4311596479696").fat)
        // Milch: "3,59" is 3,5 g.
        assertEquals(3.5, read("4311501065525").fat?.value)
    }

    @Test fun `energy as kJ slash kcal, as a pair without units, on two rows, and as kJ alone`() {
        assertEquals(209.0, read("4006374962278").kcal?.value)
        // "275/65" under "Brennwert kJ/kcal".
        assertEquals(65.0, read("4311501042533").kcal?.value)
        // McVitie's: "ENERGY (kJ) 2025" over "(kcal) 483".
        assertEquals(483.0, read("5000168001142").kcal?.value)
        // kJ alone: divided by 4.184, as for a product without kcal.
        val kj = readLabel(table("pro 100 g", "Energie" to "1046 kJ", "Fett" to "4,0 g", "Kohlenhydrate" to "45,0 g", "Eiweiß" to "8,0 g"))
        assertEquals(LabelValue(250.0, LabelMark.READ), kj?.kcal)
        // "1.046 kJ / 250 kcal": the dot of the thousands.
        assertEquals(250.0, readLabel(table("pro 100 g", "Brennwert" to "1.046 kJ / 250 kcal", "Fett" to "4,0 g", "Kohlenhydrate" to "45,0 g", "Eiweiß" to "8,0 g"))?.kcal?.value)
        // kJ and kcal that do not belong together: the kcal is marked.
        assertEquals(LabelMark.CHECK, readLabel(table("pro 100 g", "Brennwert" to "1500 kJ / 250 kcal", "Fett" to "4,0 g", "Kohlenhydrate" to "45,0 g", "Eiweiß" to "8,0 g"))?.kcal?.mark)
    }

    @Test fun `two columns, per 100 g not the portion, and the portion as the serving`() {
        // Vanille Eis: 100 g and Portion (50 g).
        val ice = read("4311501070628")
        assertEquals(listOf(200.0, 1.6, 25.5, 10.1), ice.values.map { it?.value })
        assertEquals(50.0, ice.serving)
        assertEquals(380.0, read("4311501668252").serving)
        assertEquals(60.0, read("4311501679357").serving)
    }

    @Test fun `a label of portions only gives per 100 g when it says how big a portion is, marked to check`() {
        // Doritos: 150 kcal, 1 g protein, 16 g carbs, 9 g fat in a serving of 28 g.
        val chips = read("0038000138416")
        assertEquals(listOf(535.7, 3.6, 57.1, 32.1), chips.values.map { it?.value })
        assertTrue(chips.values.all { it?.mark == LabelMark.CHECK })
        assertEquals(28.0, chips.serving)
        // The same without the size of the portion: nothing.
        assertNull(readLabel(table("pro Portion", "Brennwert" to "620 kJ / 150 kcal", "Fett" to "9 g", "Kohlenhydrate" to "16 g", "Eiweiß" to "1 g")))
    }

    @Test fun `a row and its number that do not line up, and numbers the recognizer split`() {
        // Jogurt Drink: a curved bottle, the labels 5 to 10 degrees turned, the numbers not.
        assertEquals(listOf(65.0, 2.9, 12.3, 0.1), read("4311501042533").values.map { it?.value })
        // Putenhack: "9.8" and "9" (the g) as two words.
        assertEquals(9.8, read("4011364586150").fat?.value)
        // Extra: "0g" between the rows of fat and of saturates; the other "0g" fits the saturates better.
        assertEquals(0.0, read("4009900584357").fat?.value)
    }

    @Test fun `running text`() {
        assertEquals(listOf(157.0, 0.0, 63.4, 0.0), read("50173273").values.map { it?.value })
    }

    @Test fun `no head that says 100 g, read but all marked to check`() {
        val spaghetti = read("4311596410644")
        assertEquals(listOf(368.0, 12.0, 75.0, 1.5), spaghetti.values.map { it?.value })
        assertTrue(spaghetti.values.all { it?.mark == LabelMark.CHECK })
    }

    @Test fun `calories that do not add up mark all four to check`() {
        // McVitie's: protein read 1.0 for 7.0 g; 450 kcal by its numbers against 483.
        assertTrue(read("5000168001142").values.all { it?.mark == LabelMark.CHECK })
        // A value over what 100 g can hold is not taken.
        val over = readLabel(table("pro 100 g", "Brennwert" to "1046 kJ / 250 kcal", "Fett" to "4,0 g", "Kohlenhydrate" to "145 g", "Eiweiß" to "8,0 g"))
        assertNull(over?.carbs)
        assertTrue(over?.values?.filterNotNull()?.all { it.mark == LabelMark.CHECK } == true)
    }

    private companion object {
        val FIELDS = listOf("kcal", "protein", "carbs", "fat")
    }
}

/** What the recognizer read on the photo of product [code], as saved in `src/test/resources/label`. */
internal fun savedLabel(code: String): LabelText {
    val json = Json.parseToJsonElement(File("src/test/resources/label/$code.json").readText()).jsonObject
    fun box(value: JsonArray) = value.map { it.jsonPrimitive.int }.let { TextBox(it[0], it[1], it[2], it[3]) }
    return LabelText(
        json.getValue("lines").jsonArray.map { element ->
            val line = element.jsonObject
            LabelLine(
                line.getValue("text").jsonPrimitive.content,
                box(line.getValue("box").jsonArray),
                line.getValue("angle").jsonPrimitive.double,
                line.getValue("words").jsonArray.map { (it as JsonObject).let { w -> LabelWord(w.getValue("text").jsonPrimitive.content, box(w.getValue("box").jsonArray)) } },
            )
        },
    )
}
