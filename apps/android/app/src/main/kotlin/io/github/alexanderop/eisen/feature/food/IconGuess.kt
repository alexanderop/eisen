package io.github.alexanderop.eisen.feature.food

import java.text.Normalizer
import java.util.Locale

/*
 * The picture a food's name suggests, in English and German: what the lifter types for an own food, and the name of a
 * product Open Food Facts files under no category the app knows. Pure. One table, [NAME_WORDS]; [guessIcon] reads it.
 *
 * How a word matches. Names and words are compared without case and accents ("Käse", "KASE" and "Kaese" are one word),
 * and only as words: a word of the table matches a word of the name that is it, or starts with it ("Nudel" in "Nudeln"),
 * never one that merely holds it, so "Reis" does not match "Preis" and "Eis" does not match "Eiweiß". German glues words
 * together, and the last part names the thing ("Hafermilch" is a milk, "Erdnussflips" are chips): a word marked `-` also
 * matches the end of a longer word, when at least three letters are left in front ("Milch" in "Hafermilch", "Reis" in
 * "Basmatireis", never "Reis" in "Preis"). A word marked `=` matches only as itself ("bar" is not "Barilla").
 *
 * When several words match, the strongest [Kind] wins, then the one that ends last (the head of a compound or of an
 * English name: "milk chocolate" is chocolate, "chocolate milk" is milk), then the longest. So:
 * - "Pizza Salami" is a pizza: a dish beats what it holds (a [Kind.PRODUCT]).
 * - "Joghurt Erdbeere" is a yogurt: a product beats a flavour or an ingredient.
 * - "Hähnchen Schnitzel" is chicken: the animal beats the cut.
 * - "Proteinriegel" is a bar, "Protein Pudding" a yogurt: protein alone is a shake, as an ingredient.
 * - "Hafermilch" and "oat drink" are milk; "Erdnussbutter" and "peanut butter" a spread; "Milchreis" is rice, not ice
 *   cream; "Wassermelone" a fruit, not water; "Eistee" a soda: compounds that would read wrong are [Kind.EXCEPTION]s.
 * Only the head of a name is read first: what comes before a comma, a "mit", "im", "nach", "with", ... ("Thunfisch im
 * eigenen Saft" is fish, "Greek yogurt, plain, whole milk" a yogurt). When the head gives nothing, the whole name is read.
 */

/** How strongly a word says what a food is, weakest first. */
private enum class Kind {
    /** A cut of meat: the animal says more ("Hähnchen Schnitzel"). */
    CUT,

    /** What a food is made of, or tastes of: fruit, nuts, an animal, "Schoko", "Protein". */
    INGREDIENT,

    /** A product on a shelf: milk, bread, chocolate, sauce. */
    PRODUCT,

    /** A dish that holds products: pizza, soup, salad, a burger, a protein bar or shake. */
    DISH,

    /** A compound or phrase whose parts would say something else. */
    EXCEPTION,
}

private class Word(val parts: List<String>, val icon: FoodIcon, val kind: Kind, val start: Boolean, val tail: Boolean)

private fun words(icon: FoodIcon, kind: Kind, vararg specs: String): List<Word> = specs.map { spec ->
    val whole = spec.startsWith("=")
    val bare = spec.removePrefix("=")
    val tail = bare.startsWith("-")
    Word(normalName(bare.removePrefix("-")).split(' ').filter { it.isNotEmpty() }, icon, kind, start = !whole, tail = tail)
}

private val NAME_WORDS: List<Word> = listOf(
    // Exceptions: whole compounds and phrases.
    words(FoodIcon.SPREAD, Kind.EXCEPTION, "-erdnussbutter", "peanut butter", "-nussbutter", "almond butter", "-mandelmus", "-nussmus", "nuss nougat", "nougat creme", "-nougatcreme", "-schokocreme", "chocolate spread"),
    words(FoodIcon.MILK, Kind.EXCEPTION, "oat drink", "oat milk", "-haferdrink", "-sojadrink", "soy drink", "almond drink", "-mandeldrink", "-reisdrink", "rice drink", "oatly", "coconut milk", "-kaffeesahne"),
    words(FoodIcon.FRUIT, Kind.EXCEPTION, "-wassermelone", "watermelon", "-weintraube", "pineapple"),
    words(FoodIcon.VEGETABLE, Kind.EXCEPTION, "eggplant", "-salatgurke", "sweet corn", "-zuckermais"),
    words(FoodIcon.SAUSAGE, Kind.EXCEPTION, "-leberkase", "-fleischkase", "hot dog"),
    words(FoodIcon.BREAD, Kind.EXCEPTION, "-pizzamehl", "-reiswaffel", "rice cake"),
    words(FoodIcon.SODA, Kind.EXCEPTION, "-eistee", "iced tea", "energy drink", "red bull"),
    words(FoodIcon.COFFEE, Kind.EXCEPTION, "-eiskaffee", "iced coffee"),
    words(FoodIcon.EGG, Kind.EXCEPTION, "-huhnerei", "egg white", "-eiklar", "-ruhrei", "-spiegelei", "scrambled egg", "omelett"),
    words(FoodIcon.PIZZA, Kind.EXCEPTION, "-flammkuchen"),
    words(FoodIcon.ICE_CREAM, Kind.EXCEPTION, "ice cream", "-wassereis", "frozen yogurt"),
    words(FoodIcon.SHAKE, Kind.EXCEPTION, "-eiweisspulver", "-proteinpulver", "protein powder", "protein shake", "-proteinshake"),
    words(FoodIcon.BAR, Kind.EXCEPTION, "protein bar", "-proteinbar"),
    words(FoodIcon.COOKIE, Kind.EXCEPTION, "-lebkuchen"),
    words(FoodIcon.MEAL, Kind.EXCEPTION, "chili con carne", "lunch box"),
    // Dishes.
    words(FoodIcon.PIZZA, Kind.DISH, "-pizza"),
    words(FoodIcon.BURGER, Kind.DISH, "-burger", "doner", "kebab", "gyros"),
    words(FoodIcon.MEAL, Kind.DISH, "fertiggericht", "-gericht", "=bowl", "lunchbox", "=meal", "=menu", "-auflauf", "burrito", "sushi", "bento", "gyoza", "dumpling", "-fruhlingsrolle", "spring roll"),
    words(FoodIcon.SOUP, Kind.DISH, "-suppe", "-suppen", "soup", "-eintopf", "-bruhe", "broth", "stew"),
    words(FoodIcon.SALAD, Kind.DISH, "-salat", "salad", "coleslaw"),
    words(FoodIcon.SHAKE, Kind.DISH, "whey", "shake", "casein"),
    words(FoodIcon.BAR, Kind.DISH, "-riegel", "=bar", "=bars"),
    // Products.
    words(FoodIcon.MILK, Kind.PRODUCT, "-milch", "-milk"),
    words(FoodIcon.YOGURT, Kind.PRODUCT, "-joghurt", "-jogurt", "-yogurt", "-yoghurt", "-skyr", "-quark", "-pudding", "kefir", "ayran"),
    words(FoodIcon.CHEESE, Kind.PRODUCT, "-kase", "-cheese", "gouda", "emmentaler", "-mozzarella", "parmesan", "parmigiano", "grana", "feta", "cheddar", "camembert", "=brie", "edamer", "=edam", "ricotta", "mascarpone", "halloumi", "harzer", "raclette", "provolone"),
    words(FoodIcon.BUTTER, Kind.PRODUCT, "-butter", "margarine", "ghee"),
    words(FoodIcon.OIL, Kind.PRODUCT, "=ol", "-olivenol", "-rapsol", "-sonnenblumenol", "-kokosol", "-leinol", "-speiseol", "oil"),
    words(FoodIcon.TOFU, Kind.PRODUCT, "-tofu", "tempeh", "seitan"),
    words(FoodIcon.SAUSAGE, Kind.PRODUCT, "-wurst", "-wurstchen", "-salami", "-schinken", "=ham", "sausage", "wiener", "frankfurter", "chorizo", "mortadella", "aufschnitt", "lyoner", "cervelat", "pepperoni"),
    words(FoodIcon.BREAD, Kind.PRODUCT, "-brot", "-brotchen", "toast", "-semmel", "-semmeln", "schrippe", "-bread", "bagel", "-baguette", "ciabatta", "=wrap", "=wraps", "tortilla", "pita", "-brezel", "-brezeln", "breze", "pretzel", "zwieback", "=bun", "=buns", "-mehl", "flour", "sandwich", "matzo"),
    words(FoodIcon.PASTA, Kind.PRODUCT, "-nudel", "-nudeln", "pasta", "spaghetti", "penne", "fusilli", "farfalle", "-tortellini", "tortelloni", "ravioli", "lasagne", "lasagna", "gnocchi", "-spatzle", "maultasche", "-noodle", "-noodles", "macaroni", "makkaroni", "ramen", "tagliatelle", "rigatoni", "linguine"),
    words(FoodIcon.RICE, Kind.PRODUCT, "-reis", "-rice", "risotto", "paella", "couscous", "bulgur", "quinoa", "hirse", "millet", "buchweizen", "buckwheat", "barley", "graupen", "polenta"),
    words(FoodIcon.CEREAL, Kind.PRODUCT, "-haferflocken", "-flocken", "-musli", "-muesli", "granola", "porridge", "oatmeal", "-flakes", "cereal", "-cerealien", "cheerios", "-griess", "semolina", "farina", "=oats"),
    words(FoodIcon.POTATO, Kind.PRODUCT, "-kartoffel", "-kartoffeln", "-potato", "-potatoes", "pommes", "fries", "rosti", "kroketten"),
    words(FoodIcon.PASTRY, Kind.PRODUCT, "croissant", "-kuchen", "-torte", "muffin", "donut", "doughnut", "berliner", "-plunder", "-geback", "-cake", "brownie", "-strudel", "pancake", "-pfannkuchen", "crepe", "stollen", "-blatterteig", "-pastry"),
    words(FoodIcon.CHOCOLATE, Kind.PRODUCT, "-schokolade", "-chocolate", "praline"),
    words(FoodIcon.ICE_CREAM, Kind.PRODUCT, "=-eis", "eiscreme", "gelato", "sorbet"),
    words(FoodIcon.CHIPS, Kind.PRODUCT, "-chips", "crisps", "nachos", "-flips", "popcorn", "salzstangen"),
    words(FoodIcon.SPREAD, Kind.PRODUCT, "-marmelade", "-konfiture", "=jam", "-gelee", "nutella", "-honig", "honey", "-aufstrich", "sirup", "syrup", "zucker", "sugar"),
    words(FoodIcon.SAUCE, Kind.PRODUCT, "-ketchup", "mayo", "-mayonnaise", "-senf", "mustard", "-sauce", "-sosse", "pesto", "-dressing", "salsa", "=dip", "hollandaise", "remoulade", "aioli"),
    words(FoodIcon.COOKIE, Kind.PRODUCT, "-keks", "-kekse", "-cookie", "-cookies", "-biscuit", "-biscuits", "-cracker", "-crackers", "-waffel", "-waffeln", "waffle", "spekulatius"),
    words(FoodIcon.WATER, Kind.PRODUCT, "-wasser", "-water", "sprudel"),
    words(FoodIcon.JUICE, Kind.PRODUCT, "=-saft", "-juice", "smoothie", "-nektar", "nectar", "-schorle"),
    words(FoodIcon.SODA, Kind.PRODUCT, "-cola", "limo", "-limonade", "fanta", "sprite", "soda", "=pepsi", "-brause", "tonic"),
    words(FoodIcon.COFFEE, Kind.PRODUCT, "-kaffee", "coffee", "espresso", "cappuccino", "=latte", "=-tee", "=tea", "chai", "matcha"),
    words(FoodIcon.BEER, Kind.PRODUCT, "-bier", "beer", "-pils", "pilsner", "radler", "=lager", "=ale", "=-wein", "-wine", "sekt", "prosecco", "vodka", "=gin", "whiskey", "whisky", "=rum", "schnaps", "-likor", "spirits"),
    // Ingredients and flavours.
    words(FoodIcon.MEAT, Kind.INGREDIENT, "fleisch", "rind", "beef", "schwein", "pork", "hack", "lamm", "lamb", "kalb", "veal", "speck", "bacon"),
    words(FoodIcon.POULTRY, Kind.INGREDIENT, "-hahnchen", "huhn", "huhner", "hendl", "-pute", "truthahn", "chicken", "turkey", "-geflugel", "poultry", "nuggets"),
    words(FoodIcon.FISH, Kind.INGREDIENT, "-fisch", "-fische", "thunfisch", "seelachs", "-fish", "-lachs", "salmon", "tuna", "forelle", "trout", "kabeljau", "=cod", "hering", "herring", "makrele", "mackerel", "sardine", "garnele", "shrimp", "scampi", "krabbe", "matjes", "dorsch", "tilapia", "pollock", "haddock", "squid", "-muschel", "mussel", "surimi", "zander"),
    words(FoodIcon.BEANS, Kind.INGREDIENT, "-bohne", "-bohnen", "-bean", "-beans", "-linse", "-linsen", "lentil", "kichererbse", "chickpea", "erbse", "=pea", "=peas", "split pea", "hummus", "edamame", "soja", "=soy", "falafel", "kidney"),
    words(FoodIcon.NUTS, Kind.INGREDIENT, "-nuss", "-nusse", "=nut", "=nuts", "-mandel", "-mandeln", "almond", "peanut", "walnut", "hazelnut", "cashew", "pistazie", "pistachio", "macadamia", "pekan", "pecan", "-kerne", "-seeds", "-samen", "chia"),
    words(FoodIcon.FRUIT, Kind.INGREDIENT, "-apfel", "-apple", "-obst", "-frucht", "-fruit", "birne", "=pear", "=pears", "-orange", "mandarine", "clementine", "kiwi", "mango", "ananas", "-traube", "grape", "pfirsich", "peach", "nektarine", "pflaume", "plum", "-kirsche", "-kirschen", "cherry", "cherries", "-melone", "melon", "zitrone", "lemon", "limette", "aprikose", "apricot", "dattel", "=date", "=dates", "rosine", "raisin", "feige", "avocado", "papaya"),
    words(FoodIcon.BANANA, Kind.INGREDIENT, "-banane", "banana"),
    words(FoodIcon.BERRIES, Kind.INGREDIENT, "-beere", "-beeren", "erdbeer", "himbeer", "heidelbeer", "blaubeer", "-berry", "-berries"),
    words(FoodIcon.VEGETABLE, Kind.INGREDIENT, "-gemuse", "vegetable", "veggie", "brokkoli", "broccoli", "-karotte", "-karotten", "-mohre", "carrot", "-tomate", "-tomaten", "tomato", "-gurke", "-gurken", "cucumber", "paprika", "-zwiebel", "-zwiebeln", "onion", "knoblauch", "garlic", "-spinat", "spinach", "zucchini", "-pilz", "-pilze", "champignon", "mushroom", "blumenkohl", "cauliflower", "-kohl", "cabbage", "=kale", "=mais", "=corn", "spargel", "asparagus", "sellerie", "celery", "rote bete", "beet", "aubergine", "kurbis", "pumpkin", "radieschen", "radish", "lauch", "leek", "sauerkraut", "pickle", "rucola"),
    words(FoodIcon.MILK, Kind.INGREDIENT, "-sahne", "cream"),
    words(FoodIcon.SHAKE, Kind.INGREDIENT, "-protein", "eiweiss"),
    words(FoodIcon.CHOCOLATE, Kind.INGREDIENT, "schoko", "choco", "kakao", "cocoa", "nougat"),
    words(FoodIcon.CEREAL, Kind.INGREDIENT, "hafer", "=oat"),
    words(FoodIcon.EGG, Kind.INGREDIENT, "=ei", "-eier", "egg"),
    // Cuts of meat.
    words(FoodIcon.MEAT, Kind.CUT, "-schnitzel", "-steak", "-steaks", "-filet", "-filets", "fillet", "-braten", "-gulasch", "goulash", "frikadelle", "bulette", "=mince", "cordon bleu"),
).flatten()

/** The words that start what is said about a food rather than what it is: "Thunfisch im eigenen Saft". */
private val AFTER_THE_HEAD = setOf("mit", "im", "in", "nach", "ohne", "auf", "with", "without")

/** [text] for matching: lower case, "ß" as "ss", umlauts and accents gone, "ae", "oe" and "ue" as the vowel alone. */
internal fun normalName(text: String): String {
    val lower = text.lowercase(Locale.ROOT).replace("ß", "ss")
    val plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    return plain.replace("ae", "a").replace("oe", "o").replace("ue", "u")
}

private class Token(val text: String, val start: Int, val end: Int)

private class Match(val word: Word, val start: Int, val end: Int)

private fun tokens(text: String): List<Token> = Regex("[\\p{L}\\p{N}]+").findAll(text).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

private fun matches(word: Word, tokens: List<Token>): List<Match> {
    val parts = word.parts
    if (parts.isEmpty()) return emptyList()
    val last = parts.last()
    return tokens.indices.mapNotNull { index ->
        val end = index + parts.size - 1
        if (end >= tokens.size) return@mapNotNull null
        if ((0 until parts.size - 1).any { tokens[index + it].text != parts[it] }) return@mapNotNull null
        val token = tokens[end]
        val first = tokens[index].start
        when {
            token.text == last -> Match(word, first, token.end)
            word.start && token.text.startsWith(last) -> Match(word, first, token.start + last.length)
            word.tail && parts.size == 1 && token.text.endsWith(last) && token.text.length - last.length >= MIN_TAIL_PREFIX -> Match(word, token.end - last.length, token.end)
            else -> null
        }
    }
}

/** The fewest letters before a word at the end of a longer one: "Hafer" before "milch", never "P" before "reis". */
private const val MIN_TAIL_PREFIX = 3

private fun guessIn(text: String): FoodIcon? {
    val found = tokens(text)
    if (found.isEmpty()) return null
    return NAME_WORDS.flatMap { matches(it, found) }
        .maxWithOrNull(compareBy<Match> { it.word.kind }.thenBy { it.end }.thenBy { it.end - it.start })
        ?.word?.icon
}

/**
 * The picture [name] suggests, or null when no word of it says what it is. The head of the name first (before a comma
 * or a word like "mit"), then the whole name. See the top of this file for how words match and which one wins.
 */
fun guessIcon(name: String): FoodIcon? {
    val text = normalName(name)
    val all = tokens(text)
    val cut = all.drop(1).firstOrNull { it.text in AFTER_THE_HEAD }?.start
    val comma = text.indexOf(',').takeIf { it > 0 }
    val head = listOfNotNull(cut, comma).minOrNull()?.let { text.substring(0, it) }
    return head?.let(::guessIn) ?: guessIn(text)
}
