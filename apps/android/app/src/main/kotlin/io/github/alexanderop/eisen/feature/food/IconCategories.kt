package io.github.alexanderop.eisen.feature.food

/*
 * The picture of a product by its Open Food Facts categories. Pure. A product read answers `categories_tags`, the search
 * (Search-a-licious) the same tags as `categories`; both run from broad to specific
 * (`en:beverages, …, en:plant-based-milk-alternatives`), so the most specific tag with a picture wins. The table holds
 * only tags seen in real answers for German supermarket products (Edeka, Gut & Günstig and others, November 2026; some
 * are saved in the tests' resources). A tag of the taxonomy the table does not hold says nothing; the very broad ones
 * (`en:beverages`, `en:plant-based-foods`, `en:snacks`, `en:dairies`, `en:meals`) are left out on purpose, so a product filed only
 * under them gets the guess from its name.
 */

private fun tags(icon: FoodIcon, vararg names: String): List<Pair<String, FoodIcon>> = names.map { "en:$it" to icon }

private val CATEGORY_ICONS: Map<String, FoodIcon> = listOf(
    tags(
        FoodIcon.MILK, "milks", "milks-liquid-and-powder", "whole-milks", "semi-skimmed-milks", "uht-milks", "homogenized-milks", "lactose-free-milk",
        "evaporated-milks", "creams", "compound-dairy-creams", "plant-based-milk-alternatives", "milk-substitutes", "oat-based-drinks", "cereal-based-drinks",
        "dairy-substitutes", "dairy-drinks", "coconut-milks-and-creams", "plant-based-creams",
    ),
    tags(FoodIcon.EGG, "eggs", "eggs-and-their-products", "chicken-eggs", "fresh-eggs", "free-range-chicken-eggs"),
    tags(FoodIcon.POULTRY, "poultries", "chickens", "chicken-and-its-products", "chicken-thighs", "turkeys", "turkey-and-its-products"),
    tags(
        FoodIcon.RICE, "rices", "cereal-grains", "white-rices", "long-grain-rices", "parboiled-rices", "parboiled-white-rices", "basmati-rices", "aromatic-rices",
        "indica-rices", "rice-dishes",
    ),
    tags(FoodIcon.NUTS, "nuts", "nuts-and-their-products", "peanuts", "macadamia-nuts", "nüsse", "nusse", "nüsse-und-nussprodukte", "nusse-und-nussprodukte"),
    tags(
        FoodIcon.YOGURT, "yogurts", "fruit-yogurts", "plain-yogurts", "low-fat-yogurts", "greek-style-yogurts", "dairy-desserts", "fermented-dairy-desserts",
        "fermented-dairy-desserts-with-fruits", "plain-fermented-dairy-desserts", "fermented-milk-products", "skyrs", "plain-skyrs", "fruit-skyrs",
        "fromages-blancs-petit-suisses-and-skyr",
    ),
    tags(FoodIcon.CHEESE, "cheeses", "cream-cheeses", "fresh-cheeses", "raclette"),
    tags(FoodIcon.BUTTER, "butters", "milkfat", "dairy-spreads", "spreadable-fats", "mildly-soured-butters", "molded-butters"),
    tags(FoodIcon.OIL, "fats", "vegetable-fats", "vegetable-oils", "rapeseed-oils"),
    tags(FoodIcon.TOFU, "tofu", "meat-alternatives", "meat-analogues", "meat-analogues-from-soy-or-wheat-proteins"),
    tags(FoodIcon.SHAKE, "protein-powders", "protein-shakes", "bodybuilding-supplements", "pea-protein"),
    tags(
        FoodIcon.MEAT, "meats", "beef", "beef-and-its-products", "ground-meats", "ground-beef-meats", "ground-meat-preparations", "fresh-meats",
        "meat-preparations", "fresh-meat-preparations",
    ),
    tags(FoodIcon.SAUSAGE, "sausages", "prepared-meats"),
    tags(
        FoodIcon.FISH, "fishes", "seafood", "fishes-and-their-products", "fatty-fishes", "salmons", "salmon-fillets", "smoked-salmons", "smoked-fishes", "tunas",
        "canned-tunas", "tuna-fillets", "tunas-in-brine", "canned-fishes", "canned-seafood", "fish-fillets", "shrimps", "crustaceans", "meals-with-fish",
    ),
    tags(
        FoodIcon.BEANS, "legumes", "pulses", "legumes-and-their-products", "legume-seeds", "common-beans", "canned-common-beans", "red-beans",
        "canned-red-kidney-beans", "canned-legumes", "lentils", "canned-lentils",
    ),
    tags(FoodIcon.BAR, "bars", "cereal-bars", "chocolate-cereal-bars", "protein-bars"),
    tags(
        FoodIcon.BREAD, "breads", "white-breads", "wheat-breads", "rye-breads", "wholemeal-breads", "special-breads", "sliced-breads", "wholemeal-sliced-breads",
        "pre-baked-breads", "flatbreads", "wheat-flatbreads", "toasts", "hamburger-buns", "sandwiches", "sandwiches-filled-with-cold-cuts", "flours",
        "cereal-flours", "wheat-flours",
    ),
    tags(
        FoodIcon.PASTA, "pastas-and-dumplings", "pastas", "noodles", "spaghetti", "penne", "penne-rigate", "dry-pastas", "cereal-pastas", "durum-wheat-pasta",
        "durum-wheat-spaghetti", "durum-wheat-pasta-with-egg", "dry-durum-wheat-pasta", "pasta-dishes", "pasta-in-sauce", "prepared-lasagne", "stuffed-pastas",
        "tortellini", "tortelloni", "raviolis", "spatzle",
    ),
    tags(
        FoodIcon.CEREAL, "breakfast-cereals", "mueslis", "mueslis-with-chocolate", "mueslis-with-fruits", "mueslis-with-nuts", "chocolate-cereals",
        "cereals-with-fruits", "cereals-with-nuts", "extruded-cereals", "chocolate-wheat-shells",
    ),
    tags(
        FoodIcon.POTATO, "potatoes-and-their-products", "potato-preparations", "potato-dishes", "mashed-potatoes", "fries", "potato-fries",
        "frozen-potato-fries", "frozen-fried-potatoes", "frozen-french-fries-to-roast",
    ),
    tags(FoodIcon.PASTRY, "pastries", "cakes", "marble-cakes", "chocolate-based-cakes", "stollen", "puff-pastry-sheets", "pie-dough", "raw-doughs"),
    tags(
        FoodIcon.PIZZA, "pizzas", "pizzas-pies-and-quiches", "frozen-pizzas", "frozen-pizzas-and-pies", "cheese-pizzas", "four-cheese-pizza",
        "vegetarian-pizzas", "mini-appetizer-pizzas",
    ),
    tags(
        FoodIcon.FRUIT, "fruits", "fresh-fruits", "tropical-fruits", "canned-fruits", "canned-fruits-in-juice", "pineapples", "canned-pineapples",
        "canned-pineapples-in-juice", "compotes", "apple-compotes", "applesauces", "oranges", "citrus",
    ),
    tags(FoodIcon.BERRIES, "berries", "blueberries"),
    tags(
        FoodIcon.VEGETABLE, "vegetables", "vegetables-based-foods", "frozen-vegetables", "mixed-vegetables", "frozen-mixed-vegetables", "prepared-vegetables",
        "prepared-spinachs", "spinach-with-cream-sauce", "canned-vegetables", "leaf-vegetables", "processed-vegetables",
    ),
    tags(FoodIcon.SALAD, "salads", "prepared-salads", "leaf-salads", "potato-salads", "tuna-salads", "salads-with-fish", "salads-without-dressing"),
    tags(FoodIcon.SOUP, "soups", "canned-soups"),
    tags(FoodIcon.BURGER, "hamburgers", "cheeseburgers", "vegetarian-patties", "patties-from-soy-and-or-wheat-proteins"),
    tags(
        FoodIcon.CHOCOLATE, "cocoa-and-its-products", "chocolates", "dark-chocolates", "milk-chocolates", "white-chocolates", "chocolates-with-hazelnuts",
        "milk-chocolates-with-hazelnuts", "cocoa-and-chocolate-powders", "instant-chocolate-powders", "chocolate-sprinkles",
    ),
    tags(
        FoodIcon.ICE_CREAM, "frozen-desserts", "ice-creams-and-sorbets", "ice-creams", "ice-cream-tubs", "vanilla-ice-cream-tubs", "ice-cream-cones",
        "frozen-cones", "ice-pops",
    ),
    tags(
        FoodIcon.CHIPS, "salty-snacks", "crisps", "potato-crisps", "flavoured-potato-crisps", "paprika-crisps", "salty-snacks-made-from-potato",
        "potato-crisps-in-sunflower-oil", "popcorn", "salted-popcorn",
    ),
    tags(
        FoodIcon.SPREAD, "spreads", "sweet-spreads", "plant-based-spreads", "fruit-preserves", "fruit-and-vegetable-preserves", "jams", "extra-jams",
        "fruit-jams", "berry-jams", "strawberry-jams", "raspberry-jams", "apricot-jams", "blueberry-jams", "bilberry-jams", "citrus-jams", "marmalades",
        "chocolate-spreads", "hazelnut-spreads", "cocoa-and-hazelnuts-spreads", "honeys", "flower-honeys", "bee-products",
    ),
    tags(
        FoodIcon.SAUCE, "condiments", "sauces", "ketchup", "tomato-ketchup", "curry-ketchups", "curry-sauces", "barbecue-sauces", "hollandaise-sauce",
        "emulsified-sauces", "tomato-sauces", "hot-sauces", "salad-dressings", "dips",
    ),
    tags(
        FoodIcon.COOKIE, "biscuits-and-cakes", "biscuits", "biscuits-and-crackers", "chocolate-biscuits", "milk-chocolate-biscuits",
        "chocolate-covered-biscuits", "gingerbreads",
    ),
    tags(FoodIcon.WATER, "waters", "mineral-waters", "spring-waters", "natural-mineral-waters", "carbonated-waters"),
    tags(FoodIcon.JUICE, "juices-and-nectars", "fruit-juices", "orange-juices", "squeezed-fruit-juices", "vegetable-juices", "carrot-juices"),
    tags(
        FoodIcon.SODA, "soft-drinks", "sodas", "colas", "diet-sodas", "diet-cola-soft-drink", "cola-mixes", "fruit-sodas", "light-fruit-sodas",
        "flavoured-soft-drinks", "flavoured-syrups",
    ),
    tags(
        FoodIcon.COFFEE, "coffees", "instant-coffees", "freeze-dried-instant-coffees", "coffee-capsules", "teas", "black-teas", "herbal-teas",
        "hot-beverages",
    ),
    tags(FoodIcon.BEER, "alcoholic-beverages", "beers", "lagers", "pilsners", "non-alcoholic-beers", "wines", "red-wines"),
    tags(FoodIcon.MEAL, "combination-meals", "frozen-ready-made-meals", "canned-meals", "spring-rolls", "gyoza"),
).flatten().toMap()

/**
 * The picture of a product filed under [categories] (Open Food Facts tags, broad to specific): the one of the most
 * specific tag the table holds. Null when none of them has a picture: the name says it then.
 */
fun iconOfCategories(categories: List<String>): FoodIcon? =
    categories.asReversed().firstNotNullOfOrNull { CATEGORY_ICONS[it.trim().lowercase()] }
