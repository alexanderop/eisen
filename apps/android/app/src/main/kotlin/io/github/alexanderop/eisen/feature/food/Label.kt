package io.github.alexanderop.eisen.feature.food

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.tan

/*
 * A photographed nutrition table, read. The phone's text recognizer (ML Kit, in `platform`) gives lines of text, each with
 * its box in the photo; this file finds the calories, protein, carbs and fat per 100 g or 100 ml in them. Pure: nothing
 * of the photo is kept, nothing leaves the phone.
 *
 * A table is rows: a label on the left ("Fett", "Eiweiß", "Brennwert kJ/kcal", "of which sugars") and its numbers to the
 * right, one column per 100 g, maybe one per portion, maybe one of the reference intake in %. The recognizer often gives
 * the labels and the numbers as separate lines, so each number goes to the row of the label it lines up with; a label
 * with its number in the same line (running text, an American label) keeps it. The column under "100 g" is the one read.
 */

/** A rectangle in the photo's pixels. */
data class TextBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}

/** A word the recognizer read, with its box. */
data class LabelWord(val text: String, val box: TextBox)

/** A line the recognizer read: its text, its box, how far it is turned (degrees, clockwise) and its words. */
data class LabelLine(val text: String, val box: TextBox, val angle: Double = 0.0, val words: List<LabelWord> = emptyList())

/** Everything the recognizer read on one photo. */
data class LabelText(val lines: List<LabelLine>)

/** How sure a value from a photo is: it was read and adds up, or it should be looked at before saving. */
enum class LabelMark { READ, CHECK }

data class LabelValue(val value: Double, val mark: LabelMark)

/**
 * What a nutrition table says per 100 g or 100 ml ([unit]): each value with its mark, or null where nothing was read.
 * [serving]: the portion the label names ("pro Portion (50 g)"), in the same unit, when it names one.
 */
data class LabelReading(
    val unit: FoodUnit,
    val kcal: LabelValue?,
    val protein: LabelValue?,
    val carbs: LabelValue?,
    val fat: LabelValue?,
    val serving: Double? = null,
) {
    val values: List<LabelValue?> get() = listOf(kcal, protein, carbs, fat)
}

/** The fewest of the four values a photo must give to count as a nutrition table: one number alone is more likely noise. */
const val LABEL_MIN_VALUES = 2

/**
 * How far the calories a label states may be from what its numbers make (4 kcal a gram of protein and of carbs, 9 of fat,
 * 2 of fibre) before all four values are marked to check: 4 %, or 10 kcal for small numbers. Picked from the real labels in
 * LabelTest: read right, they are all within 10 kcal or 1.6 % (with the fibre; without it a bread with 14 g of fibre is
 * 11 % under, which is why fibre counts), while the one misread that adds up loosely, McVitie's protein 1.0 g for 7.0, is
 * 5.3 % off. Chewing gum (polyols, 2.4 kcal a gram) is 40 to 60 % off and is marked, which is calm and right.
 */
const val LABEL_TOLERANCE = 0.04
const val LABEL_TOLERANCE_KCAL = 10.0

/** Fibre, by the EU's rule for labels. */
const val KCAL_PER_GRAM_FIBRE = 2

/** Protein, carbs and fat of 100 g can not add up to more than 100 g; a little more is rounding. */
private const val MAX_MACRO_SUM = 102.0

/**
 * The calories, protein, carbs and fat per 100 g (or ml) of the nutrition table in [text], each marked [LabelMark.READ]
 * or [LabelMark.CHECK]; null when fewer than [LABEL_MIN_VALUES] of them were found. A value that was not read stays null:
 * nothing is guessed.
 */
fun readLabel(text: LabelText): LabelReading? {
    val lines = text.lines.filter { it.text.isNotBlank() }.mapIndexed { i, line -> parsed(line, i) }
    val wide = lines.filter { it.line.box.width > 2 * it.line.box.height }.map { it.line.angle }
    val rows = rowsOf(lines, tan(Math.toRadians(median(wide))))
    val firstRow = rows.filter { it.kind in TARGETS }.minOfOrNull { it.anchor.line.box.top } ?: return null
    val headers = lines.flatMap { it.numbers }.filter { it.isHeader }
    // "Nährwerte pro 100 g: Energie 657 kJ, ..." in running text: per 100 g, but no column.
    val running = lines.filter { line -> line.segments.any { it.kind in TARGETS } }.map { it.index }.toSet()
    val column = columnOf(rows, headers.filter { it.line !in running })
    val stated = headers.isNotEmpty()
    val unit = if (headers.any { it.unit == NumUnit.ML }) FoodUnit.ML else FoodUnit.G
    val serving = servingOf(lines, firstRow)
    val gContext = rows.flatMap { it.numbers }.any { it.unit == NumUnit.G && !it.isHeader }
    fun pick(kind: RowKind): Num? = rows.filter { it.kind == kind }.firstNotNullOfOrNull { row -> chooseIn(row.numbers.filter { it.unit.isValue && !it.isHeader && it.unit != NumUnit.KJ && it.unit != NumUnit.KCAL }, column) }
    val energy = energyOf(rows.filter { it.kind == RowKind.ENERGY || it.kind == RowKind.KJ || it.kind == RowKind.KCAL }, rows, column)
    val macros = listOf(RowKind.PROTEIN, RowKind.CARBS, RowKind.FAT).map { kind -> pick(kind)?.let { candidates(it, gContext) } }
    // Fibre has energy of its own (2 kcal a gram) and is not in the carbs of a European label.
    val fibre = if (stated) pick(RowKind.FIBRE)?.let { candidates(it, gContext).firstOrNull() }?.takeIf { it <= MAX_MACRO_PER_100 } ?: 0.0 else 0.0
    // The reading of each unclear number ("1,69": 1,6 g or 1,69) that makes the calories add up, else the usual one.
    var values: List<Double?> = listOf(energy?.kcal) + choose(energy?.kcal, macros, fibre)
    var marks = MutableList(4) { LabelMark.READ }
    var portion: Double? = serving
    // A label of portions only ("Amount per serving"): per 100 only when it says how big a portion is.
    val portionOnly = !stated && lines.any { line -> PORTION_WORDS.any { line.normal.contains(it) } }
    if (portionOnly) {
        val size = serving ?: return null
        values = values.map { it?.let { value -> value * 100.0 / size } }
        portion = size
    }
    // No "100 g" on the photo (EU labels start with it, but the photo may have cut it off), or per 100 worked out from a
    // portion, which multiplies the label's rounding.
    if (!stated) marks = MutableList(4) { LabelMark.CHECK }
    if (energy != null && !energy.sure) marks[0] = LabelMark.CHECK
    // Out of what a food can have: not a value of this food.
    values = values.mapIndexed { i, value -> value?.takeIf { it >= 0 && it <= if (i == 0) MAX_KCAL_PER_100 else MAX_MACRO_PER_100 } }
    if (values.count { it != null } < LABEL_MIN_VALUES) return null
    val (kcal, protein, carbs, fat) = values
    if (unit == FoodUnit.G && listOfNotNull(protein, carbs, fat).sum() > MAX_MACRO_SUM) marks = MutableList(4) { LabelMark.CHECK }
    // Nothing to check against when one of the four is missing: what was read is offered to check.
    if (kcal == null || protein == null || carbs == null || fat == null || !addsUp(kcal, protein, carbs, fat, if (portionOnly) 0.0 else fibre)) marks = MutableList(4) { LabelMark.CHECK }
    fun value(i: Int) = values[i]?.let { LabelValue(round1(it), marks[i]) }
    return LabelReading(unit, value(0), value(1), value(2), value(3), portion?.let(::round1))
}

/** Whether [kcal] is what 4 kcal a gram of protein and carbs, 9 of fat and 2 of [fibre] make, within [LABEL_TOLERANCE]. */
fun addsUp(kcal: Double, protein: Double, carbs: Double, fat: Double, fibre: Double = 0.0): Boolean {
    val made = protein * KCAL_PER_GRAM_PROTEIN + carbs * KCAL_PER_GRAM_CARBS + fat * KCAL_PER_GRAM_FAT + fibre * KCAL_PER_GRAM_FIBRE
    return abs(made - kcal) <= maxOf(LABEL_TOLERANCE * kcal, LABEL_TOLERANCE_KCAL)
}

// The words

private enum class RowKind { ENERGY, KJ, KCAL, PROTEIN, CARBS, FAT, FIBRE, OTHER }

private val TARGETS = setOf(RowKind.ENERGY, RowKind.KJ, RowKind.KCAL, RowKind.PROTEIN, RowKind.CARBS, RowKind.FAT)

/** The labels of a row, German and English, without accents and with ß as ss. */
private val KEYWORDS: List<Pair<String, RowKind>> = listOf(
    "brennwert" to RowKind.ENERGY, "energie" to RowKind.ENERGY, "energy" to RowKind.ENERGY, "kalorien" to RowKind.ENERGY, "calories" to RowKind.ENERGY,
    "eiweiss" to RowKind.PROTEIN, "protein" to RowKind.PROTEIN, "proteine" to RowKind.PROTEIN, "proteins" to RowKind.PROTEIN,
    "kohlenhydrate" to RowKind.CARBS, "kohlenhydrat" to RowKind.CARBS, "carbohydrate" to RowKind.CARBS, "carbohydrates" to RowKind.CARBS, "carbs" to RowKind.CARBS,
    "fett" to RowKind.FAT, "fat" to RowKind.FAT,
    // Rows of their own, never the four: "davon Zucker" is not carbs, "gesättigte Fettsäuren" and "saturates" are not fat.
    "davon" to RowKind.OTHER, "zucker" to RowKind.OTHER, "sugar" to RowKind.OTHER, "sugars" to RowKind.OTHER, "gesattigte" to RowKind.OTHER,
    "fettsauren" to RowKind.OTHER, "saturates" to RowKind.OTHER, "saturated" to RowKind.OTHER, "trans" to RowKind.OTHER, "salz" to RowKind.OTHER,
    "salt" to RowKind.OTHER, "ballaststoffe" to RowKind.FIBRE, "fibre" to RowKind.FIBRE, "fiber" to RowKind.FIBRE, "alkohole" to RowKind.OTHER,
    "mehrwertige" to RowKind.OTHER, "polyols" to RowKind.OTHER, "starke" to RowKind.OTHER, "starch" to RowKind.OTHER, "sodium" to RowKind.OTHER,
    "natrium" to RowKind.OTHER, "cholesterol" to RowKind.OTHER, "vitamin" to RowKind.OTHER, "calcium" to RowKind.OTHER, "potassium" to RowKind.OTHER,
    "iron" to RowKind.OTHER, "which" to RowKind.OTHER, "referenzmenge" to RowKind.OTHER, "reference" to RowKind.OTHER, "zutaten" to RowKind.OTHER,
    "ingredients" to RowKind.OTHER,
)

/** A word before "fat" that makes it a part of the fat, not the fat: "Saturated Fat", "Trans Fat". */
private val FAT_PARTS = setOf("saturated", "trans", "davon", "which", "gesattigte")

/** A word the recognizer misread by a letter or two still counts: "Elweiß", "EWEISS", "Kohlenhydrale". */
private fun kindOf(word: String): RowKind? {
    if (word.length < 3) return null
    if (word == "kj" || word == "ki" || word == "kl") return RowKind.KJ
    if (word == "kcal" || word == "kal" || word == "kcai" || word == "kca") return RowKind.KCAL
    val exact = KEYWORDS.firstOrNull { it.first == word }
    if (exact != null) return exact.second
    val allowed = when {
        word.length >= 6 -> 2
        word.length >= 4 -> 1
        else -> 0
    }
    if (allowed == 0) return null
    return KEYWORDS.filter { it.first.length >= 4 && abs(it.first.length - word.length) <= allowed }
        .map { it to distance(it.first, word) }
        .filter { (keyword, d) -> d <= allowed && d <= (keyword.first.length + 1) / 4 }
        .minByOrNull { it.second }?.first?.second
}

/** The edit distance of two words. */
private fun distance(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        previous = current
    }
    return previous[b.length]
}

/** Lower case, no accents, ß as ss; an upper case B after small letters is a ß the recognizer misread ("EiweiB"). */
private fun normal(text: String): String {
    val fixed = Regex("(?<=[a-zäöü])B").replace(text, "ß")
    return Normalizer.normalize(fixed.lowercase().replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

// The numbers

private enum class NumUnit(val isValue: Boolean) { G(true), MAYBE_G(true), NONE(true), ML(true), KJ(true), KCAL(true), MG(false), PERCENT(false) }

/**
 * A number on the photo: [raw] as read ("12,39"), its [unit], whether it said "<" ([less]), where it is, the line it is in
 * and its word ([word], counted in the line). [slash]: a "/" stands between it and the number before ("870 kJ/209 kcal").
 */
private data class Num(val raw: String, val unit: NumUnit, val less: Boolean, val box: TextBox, val line: Int, val word: Int, val slash: Boolean, val ofIntake: Boolean = false) {
    val value: Double? get() = numberOf(raw)
    val isHeader: Boolean get() = (unit == NumUnit.G || unit == NumUnit.ML) && value == 100.0
}

/** "12,3" is 12.3; "02" and "<01" lost their comma: no number starts with 0 but 0 itself. */
private fun numberOf(raw: String): Double? =
    (if (raw.length >= 2 && raw[0] == '0' && raw[1].isDigit()) "0.${raw.drop(1)}" else raw.replace(',', '.')).toDoubleOrNull()

private data class Segment(val kind: RowKind, val x: Int, val word: Int)

private data class Parsed(val index: Int, val line: LabelLine, val normal: String, val numbers: List<Num>, val segments: List<Segment>)

private val NUMBER = Regex("""(<\s*)?(\d{1,5}(?:[.,]\d{1,3})?)\s*(kj|ki|kcal|kcai|kca1|kca|kal|cal|mg|µg|ml|g|%|9(?![\d.,]))?""")
private val UNIT_WORD = Regex("""^[(|/]?(kj|ki|kcal|kca|kal|g|ml|mg|%)(?![a-z0-9])""")
private val INTAKE = Regex("""%|\brm\b|\bnrv\b|\bri\b|\bgda\b""")
private val PER_100 = Regex("""(^|[^\d.,])100\s*(g|ml)\b""")
private val PORTION_WORDS = listOf("portion", "serving", "stuck", "riegel", "scheibe", "schale", "keks", "biscuit", "reihe", "per pack", "per bar")

private fun unitOf(text: String): NumUnit = when (text) {
    "kj", "ki" -> NumUnit.KJ
    "kcal", "kcai", "kca1", "kca", "kal", "cal" -> NumUnit.KCAL
    "mg", "µg" -> NumUnit.MG
    "ml" -> NumUnit.ML
    "g" -> NumUnit.G
    "%" -> NumUnit.PERCENT
    "9" -> NumUnit.MAYBE_G
    else -> NumUnit.NONE
}

/** A word with the usual misreadings put right: "Og" and "Ug" are 0 g, "FETTOg" is "fett 0g", "O,5" is 0,5. */
private fun fixed(word: String): String = word
    .replace(Regex("^[(|]?[ou]g[)|:;,.]*$"), "0g")
    .replace(Regex("(?<=[a-z]{3})[o0]g(?=$|[),;:.])"), " 0g")
    .replace(Regex("(?<=\\d)o(?=[\\d,.g])|(?<![a-z])o(?=[,.]\\d)"), "0")
    .replace(Regex("(?<=\\d\\s?)q(?![a-z])"), "g")

/** One line in labels and numbers. */
private fun parsed(line: LabelLine, index: Int): Parsed {
    val words = line.words.ifEmpty { splitWords(line) }
    val numbers = mutableListOf<Num>()
    val segments = mutableListOf<Segment>()
    var previous = ""
    var slash = false
    words.forEachIndexed { position, word ->
        val whole = fixed(normal(word.text))
        val last = numbers.lastOrNull()
        // "16,1" "g": the unit is a word of its own, or starts one ("kcal-Fett").
        val unitWord = UNIT_WORD.find(whole)
        val unitTaken = unitWord != null && last != null && last.word == position - 1 && last.unit == NumUnit.NONE
        if (unitWord != null && last != null && unitTaken) {
            numbers[numbers.size - 1] = last.copy(unit = unitOf(unitWord.groupValues[1]), box = last.box.copy(right = word.box.right))
        }
        // What is left of the word is read as any other: blanked, so the places of letters and numbers stay where they are.
        val text = if (unitWord != null && unitTaken) " ".repeat(unitWord.range.last + 1) + whole.substring(unitWord.range.last + 1) else whole
        val firstNumber = NUMBER.find(text)?.range?.first ?: Int.MAX_VALUE
        for (match in Regex("[a-z]+").findAll(text)) {
            val kind = kindOf(match.value)
            // A unit after a number ("870 kJ") is no label.
            val unit = kind == RowKind.KJ || kind == RowKind.KCAL
            if (kind != null && !(unit && firstNumber < match.range.first)) {
                val x = word.box.left + word.box.width * match.range.first / text.length.coerceAtLeast(1)
                segments += Segment(if (kind == RowKind.FAT && previous in FAT_PARTS) RowKind.OTHER else kind, x, position)
            }
            previous = match.value
        }
        var end = 0
        for (match in NUMBER.findAll(text)) {
            if (text.substring(end, match.range.first).contains('/')) slash = true
            val from = word.box.left + word.box.width * match.range.first / text.length.coerceAtLeast(1)
            val to = word.box.left + word.box.width * (match.range.last + 1) / text.length.coerceAtLeast(1)
            numbers += Num(match.groupValues[2], unitOf(match.groupValues[3]), match.groupValues[1].isNotEmpty(), TextBox(from, word.box.top, to, word.box.bottom), index, position, slash)
            slash = false
            end = match.range.last + 1
        }
        if (text.substring(end).contains('/')) slash = true
    }
    val text = normal(line.text)
    // "kJ" and "kcal" are the label of a row only alone ("(kcal)" under "ENERGY (kJ)"): beside "Brennwert" they are its units.
    val labels = if (segments.any { it.kind != RowKind.KJ && it.kind != RowKind.KCAL }) segments.filter { it.kind != RowKind.KJ && it.kind != RowKind.KCAL } else segments
    // A line of letters without a label of its own (a title, an unknown row) still holds its row, so its numbers go nowhere else.
    val letters = Regex("(?<![0-9a-z])[a-z]{3,}").findAll(text).map { it.value }.filter { it !in UNITS }.toList()
    val all = if (labels.isEmpty() && letters.isNotEmpty()) listOf(Segment(RowKind.OTHER, line.box.left, 0)) else labels
    // "% RM pro 100 g": the head of the reference intake's column, not of the values.
    val marked = numbers.map { n ->
        val before = words.subList((n.word - 2).coerceAtLeast(0), n.word + 1).joinToString(" ") { normal(it.text) }.substringBeforeLast("100")
        if (n.isHeader && INTAKE.containsMatchIn(before)) n.copy(ofIntake = true) else n
    }
    return Parsed(index, line, text, joinSplit(marked), all)
}

private val UNITS = setOf("kj", "kcal", "kal", "kca", "kcai", "mg", "ml", "pro", "per", "je")

/** Words of a line without its words: spread over its box by their share of the letters. */
private fun splitWords(line: LabelLine): List<LabelWord> {
    val total = line.text.length.coerceAtLeast(1)
    var at = 0
    return line.text.split(' ').filter { it.isNotEmpty() }.map { part ->
        val start = line.text.indexOf(part, at).coerceAtLeast(0)
        at = start + part.length
        LabelWord(part, TextBox(line.box.left + line.box.width * start / total, line.box.top, line.box.left + line.box.width * at / total, line.box.bottom))
    }
}

/** Numbers the recognizer split: "1,6" and "9" (the g read as a 9) are 1,6 g; "1" and "046 kJ" are 1046 kJ. */
private fun joinSplit(numbers: List<Num>): List<Num> {
    val out = mutableListOf<Num>()
    for (n in numbers) {
        val last = out.lastOrNull()
        val near = last != null && n.word == last.word + 1 && n.box.left - last.box.right < 2 * n.box.height
        when {
            near && last != null && last.unit == NumUnit.NONE && last.raw.contains(Regex("[.,]")) && n.raw == "9" && n.unit == NumUnit.NONE ->
                out[out.size - 1] = last.copy(unit = NumUnit.G, box = last.box.copy(right = n.box.right))
            near && last != null && last.unit == NumUnit.NONE && !last.raw.contains(Regex("[.,]")) && last.raw.length <= 2 && n.raw.length == 3 && (n.unit == NumUnit.KJ || n.unit == NumUnit.KCAL) ->
                out[out.size - 1] = n.copy(raw = last.raw + n.raw, box = last.box.copy(right = n.box.right), slash = last.slash)
            else -> out += n
        }
    }
    return out
}

// The rows

private data class Row(val kind: RowKind, val anchor: Parsed, val numbers: List<Num>)

/**
 * Each number to the row it belongs to: a label before it in its own line ("Fett 16,1 g", running text), else the label
 * it lines up with to its left, the photo's tilt ([slope]) taken into account.
 */
private fun rowsOf(lines: List<Parsed>, slope: Double): List<Row> {
    val anchors = lines.filter { it.segments.isNotEmpty() }
    val owned = mutableMapOf<Pair<Int, Int>, MutableList<Num>>()
    fun own(anchor: Parsed, segment: Int, n: Num) = owned.getOrPut(anchor.index to segment) { mutableListOf() }.add(n)
    data class Pair3(val anchor: Parsed, val n: Num, val d: Double)
    val pairs = mutableListOf<Pair3>()
    val loose = mutableListOf<Num>()
    for (line in lines) {
        for (n in line.numbers) {
            val inLine = line.segments.indices.lastOrNull { i -> line.segments[i].word < n.word || (line.segments[i].word == n.word && line.segments[i].x <= n.box.left) }
            if (inLine != null) {
                own(line, inLine, n)
                continue
            }
            val near = anchors.filter { it.index != line.index && it.line.box.left < n.box.left && it.line.box.centerX < n.box.centerX }
                .map { anchor -> Pair3(anchor, n, rowDistance(anchor.line, n.box, slope)) }
                .filter { it.d <= 0.75 * maxOf(it.anchor.line.box.height, n.box.height) }
            // A line further left that has another label of the row between it and the number (ingredients beside a table)
            // is not its row.
            pairs += near.filter { a -> near.none { b -> b.anchor.line.box.left >= a.anchor.line.box.right } }
            loose += n
        }
    }
    // The closest pairs first, one number a row in each column: of "0g" between two rows, the row the next number does not
    // fit better.
    val taken = mutableMapOf<Int, MutableList<Num>>()
    val placed = mutableSetOf<Num>()
    for (pair in pairs.sortedBy { it.d / it.n.box.height }) {
        if (pair.n in placed) continue
        val cell = taken.getOrPut(pair.anchor.index) { mutableListOf() }
        // A cell holds one number, or kJ over kcal.
        if (cell.any { overlaps(it.box, pair.n.box) && setOf(it.unit, pair.n.unit) != setOf(NumUnit.KJ, NumUnit.KCAL) }) continue
        cell += pair.n
        placed += pair.n
        own(pair.anchor, pair.anchor.segments.lastIndex, pair.n)
    }
    for (n in loose) {
        if (n in placed) continue
        pairs.filter { it.n === n }.minByOrNull { it.d }?.let { own(it.anchor, it.anchor.segments.lastIndex, n) }
    }
    return anchors.flatMap { anchor ->
        anchor.segments.mapIndexed { i, segment -> Row(segment.kind, anchor, owned[anchor.index to i].orEmpty().sortedWith(compareBy({ it.line }, { it.box.left }))) }
    }
}

/** How far [box] is above or below the row of [anchor], following the tilt of the photo. */
private fun rowDistance(anchor: LabelLine, box: TextBox, slope: Double): Double =
    abs(box.centerY - (anchor.box.centerY + slope * (box.centerX - anchor.box.centerX)))

private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    return sorted[sorted.size / 2]
}

// The column

/** A column of numbers: from the left of its leftmost middle to the right of its rightmost (each number's middle half). */
private data class Column(val left: Double, val right: Double) {
    val x: Double get() = (left + right) / 2
    fun has(n: Num): Boolean = core(n).let { (l, r) -> l <= right && r >= left }
}

/** The middle half of a number: numbers of one column overlap there, whether they are aligned left, right or centered. */
private fun core(n: Num): Pair<Double, Double> = (n.box.centerX - n.box.width / 4.0) to (n.box.centerX + n.box.width / 4.0)

/**
 * The column of values under a head "100 g" (or ml): the numbers of the rows grouped into columns, and of them the one
 * nearest to such a head. Null without a head of the values (a head "% RM pro 100 g" is the reference intake's).
 */
private fun columnOf(rows: List<Row>, headers: List<Num>): Column? {
    val heads = headers.filter { !it.ofIntake }
    if (heads.isEmpty()) return null
    val values = rows.filter { it.kind != RowKind.OTHER }.flatMap { it.numbers }.filter { it.unit.isValue && !it.isHeader }
    val columns = mutableListOf<Column>()
    for (n in values.sortedBy { it.box.centerX }) {
        val (l, r) = core(n)
        val last = columns.lastOrNull()
        if (last != null && l <= last.right) columns[columns.size - 1] = Column(last.left, maxOf(last.right, r)) else columns += Column(l, r)
    }
    return heads.flatMap { head -> columns.map { it to abs(it.x - head.box.centerX) } }
        .filter { (column, d) -> d <= (column.right - column.left) + 2 * headers.maxOf { it.box.width } }
        .minByOrNull { it.second }?.first
}

/** The number of a row in [column], or the first of the row when there is no column. */
private fun chooseIn(numbers: List<Num>, column: Column?): Num? =
    if (column == null) numbers.firstOrNull() else numbers.filter { column.has(it) }.minByOrNull { abs(it.box.centerX - column.x) }

// Energy

private data class Energy(val kcal: Double, val sure: Boolean)

/**
 * The kcal of the energy rows [energyRows]: "870 kJ/209 kcal", "275/65" under "kJ/kcal", kJ and kcal in two rows, kcal
 * alone, or kJ alone (divided by 4.184). [Energy.sure] is false when kJ and kcal disagree.
 */
private fun energyOf(energyRows: List<Row>, rows: List<Row>, column: Column?): Energy? {
    for (row in energyRows) {
        val label = row.anchor.normal
        val numbers = row.numbers.filter { it.unit == NumUnit.KJ || it.unit == NumUnit.KCAL || it.unit == NumUnit.NONE || it.unit == NumUnit.MAYBE_G }
        val ordered = if (column == null) numbers else numbers.filter { column.has(it) }.sortedBy { abs(it.box.centerX - column.x) }
        val first = ordered.firstOrNull() ?: continue
        // The cell of the column: what lies over or under the first, and the other half of a pair "836/200".
        val cell = numbers.filter { it === first || (it in ordered && overlaps(it.box, first.box)) || paired(it, first) }
        val values = cell.mapNotNull { n -> thousands(n)?.let { n to it } }
        val kcal = values.firstOrNull { it.first.unit == NumUnit.KCAL }?.second
        val kj = values.firstOrNull { it.first.unit == NumUnit.KJ }?.second
        val bare = values.filter { it.first.unit != NumUnit.KCAL && it.first.unit != NumUnit.KJ }.sortedBy { it.first.box.left }.map { it.second }
        val saysKj = Regex("k[ji]\\b").containsMatchIn(label) || row.kind == RowKind.KJ
        val saysKcal = Regex("k?cal|kal").containsMatchIn(label) || row.kind == RowKind.KCAL
        val energy = when {
            kcal != null && kj != null -> Energy(kcal, consistent(kj, kcal))
            kcal != null -> Energy(kcal, true)
            bare.size >= 2 && consistent(bare[0], bare[1]) -> Energy(bare[1], true)
            kj != null && bare.isNotEmpty() && consistent(kj, bare[0]) -> Energy(bare[0], true)
            kj != null -> kcalBelow(rows, row, column)?.let { Energy(it, consistent(kj, it)) } ?: Energy(kcalFromKj(kj), true)
            bare.size >= 2 && saysKj && saysKcal -> Energy(bare[1], false)
            // "kJ/kcal" over one number: which of the two it is, the photo does not say.
            bare.size == 1 && saysKj && saysKcal -> Energy(if (bare[0] > MAX_KCAL_PER_100) kcalFromKj(bare[0]) else bare[0], false)
            bare.isNotEmpty() && saysKcal && !saysKj -> Energy(bare[0], true)
            bare.isNotEmpty() && saysKj -> kcalBelow(rows, row, column)?.let { Energy(it, consistent(bare[0], it)) } ?: Energy(kcalFromKj(bare[0]), true)
            else -> null
        }
        if (energy != null) return energy
    }
    return null
}

private fun overlaps(a: TextBox, b: TextBox): Boolean = a.left < b.right && b.left < a.right

/** Two numbers of one cell: next to each other in one line with a "/" between them. */
private fun paired(a: Num, b: Num): Boolean = a.line == b.line && ((a.word - b.word) in 0..2 && a.slash || (b.word - a.word) in 0..2 && b.slash)

/** kcal in a row of its own under the kJ ("ENERGY (kJ) 2025" over "(kcal) 483"). */
private fun kcalBelow(rows: List<Row>, row: Row, column: Column?): Double? =
    rows.filter { it !== row && it.anchor.line.box.top > row.anchor.line.box.top && it.anchor.line.box.top < row.anchor.line.box.bottom + 2 * row.anchor.line.box.height }
        .sortedBy { it.anchor.line.box.top }
        .firstNotNullOfOrNull { below -> chooseIn(below.numbers.filter { it.unit == NumUnit.KCAL || (it.unit == NumUnit.NONE && below.kind == RowKind.KCAL) }, column) }
        ?.let(::thousands)

/** kJ and kcal that belong together: kJ is 4.184 times kcal, give or take the rounding. */
private fun consistent(kj: Double, kcal: Double): Boolean = kcal > 0 && abs(kj / kcal - KJ_PER_KCAL) < 0.25

/** A number of energy, where "1.046" is a thousand and forty-six. */
private fun thousands(n: Num): Double? = if (Regex("""^\d{1,2}[.]\d{3}$""").matches(n.raw)) n.raw.replace(".", "").toDoubleOrNull() else n.value

// Protein, carbs and fat

/**
 * What a number of grams may be. "12,39" without a unit where the label writes "g" is mostly 12,3 g with the g read as a
 * 9, and "139" may be 1,3 g that also lost its comma: the usual reading first, then the others.
 */
private fun candidates(n: Num, gContext: Boolean): List<Double> {
    val value = n.value ?: return emptyList()
    val raw = n.raw
    val decimals = raw.substringAfter(',', raw.substringAfter('.', "")).length
    val separated = raw.contains(Regex("[.,]"))
    val suspicious = gContext && n.unit == NumUnit.NONE && raw.endsWith('9') && if (separated) decimals >= 2 else raw.length >= 2
    if (!suspicious) return listOf(value)
    val stripped = raw.dropLast(1)
    val first = numberOf(stripped) ?: return listOf(value)
    val comma = if (!separated && stripped.length >= 2 && stripped[0] != '0') "${stripped.dropLast(1)}.${stripped.last()}".toDoubleOrNull() else null
    return listOfNotNull(first, comma, value).distinct()
}

/**
 * For each of protein, carbs and fat, one of its [candidates]: the readings that make [kcal] add up, the usual ones among
 * them; the usual ones when none does or there is no kcal.
 */
private fun choose(kcal: Double?, macros: List<List<Double>?>, fibre: Double): List<Double?> {
    val usual = macros.map { it?.firstOrNull() }
    if (kcal == null || macros.any { it.isNullOrEmpty() }) return usual
    val options = macros.map { it.orEmpty() }
    var best: List<Double>? = null
    var cost = Int.MAX_VALUE
    for (p in options[0].indices) for (c in options[1].indices) for (f in options[2].indices) {
        if (p + c + f < cost && addsUp(kcal, options[0][p], options[1][c], options[2][f], fibre)) {
            best = listOf(options[0][p], options[1][c], options[2][f])
            cost = p + c + f
        }
    }
    return best ?: usual
}

// The serving

private val AMOUNT = Regex("""(\d{1,4}(?:[.,]\d)?)\s*(g|ml)\b""")

/** The portion the label names in the head of its table ("pro Portion (50 g)", "Serving size 1 oz (28g)"), other than 100. */
private fun servingOf(lines: List<Parsed>, firstRow: Int): Double? =
    lines.filter { line -> line.line.box.bottom <= firstRow + line.line.box.height / 2 || PORTION_WORDS.any { line.normal.contains(it) } }
        .filter { line -> !Regex("k[ji]\\b|kcal|%").containsMatchIn(line.normal) || line.normal.contains("portion") }
        .firstNotNullOfOrNull { line ->
            AMOUNT.findAll(line.normal).mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.firstOrNull { it != 100.0 && it >= MIN_AMOUNT }
        }?.takeIf { it <= MAX_AMOUNT }

