package io.github.alexanderop.eisen.feature.food

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 * The picture a food shows next to its name: milk a milk carton, a scanned Skyr a cup of yogurt. Pure. Which picture a
 * thing shows is decided here, in one order: the food's own icon (from Open Food Facts' category, the plain foods' asset,
 * or the lifter's choice), else a guess from its name ([guessIcon]), else [FoodIcon.PLATE]. The pictures themselves are
 * resources of the app (`res/drawable-nodpi/food_<name>.webp`); `feature/food/ui/FoodIcons.kt` maps each value to its
 * drawable, so a value without a picture does not compile.
 */

/**
 * One picture. The name in the file is the [SerialName], which is also the picture's file `food_<name>.webp` (see
 * [fileName]) and its row in `scripts/food-icons/cut.py`. [label] is what the picker and TalkBack say.
 */
@Serializable
enum class FoodIcon(val label: String) {
    @SerialName("milk") MILK("Milk"),
    @SerialName("egg") EGG("Egg"),
    @SerialName("poultry") POULTRY("Chicken"),
    @SerialName("rice") RICE("Rice"),
    @SerialName("banana") BANANA("Banana"),
    @SerialName("nuts") NUTS("Nuts"),
    @SerialName("yogurt") YOGURT("Yogurt"),
    @SerialName("cheese") CHEESE("Cheese"),
    @SerialName("butter") BUTTER("Butter"),
    @SerialName("oil") OIL("Oil"),
    @SerialName("tofu") TOFU("Tofu"),
    @SerialName("shake") SHAKE("Protein shake"),
    @SerialName("meat") MEAT("Meat"),
    @SerialName("sausage") SAUSAGE("Sausage"),
    @SerialName("fish") FISH("Fish"),
    @SerialName("beans") BEANS("Beans"),
    @SerialName("bar") BAR("Protein bar"),
    @SerialName("plate") PLATE("Plate"),
    @SerialName("bread") BREAD("Bread"),
    @SerialName("pasta") PASTA("Pasta"),
    @SerialName("cereal") CEREAL("Oats and muesli"),
    @SerialName("potato") POTATO("Potato"),
    @SerialName("pastry") PASTRY("Pastry"),
    @SerialName("pizza") PIZZA("Pizza"),
    @SerialName("fruit") FRUIT("Fruit"),
    @SerialName("berries") BERRIES("Berries"),
    @SerialName("vegetable") VEGETABLE("Vegetables"),
    @SerialName("salad") SALAD("Salad"),
    @SerialName("soup") SOUP("Soup"),
    @SerialName("burger") BURGER("Burger"),
    @SerialName("chocolate") CHOCOLATE("Chocolate"),
    @SerialName("ice_cream") ICE_CREAM("Ice cream"),
    @SerialName("chips") CHIPS("Chips"),
    @SerialName("spread") SPREAD("Jam and spreads"),
    @SerialName("sauce") SAUCE("Sauce"),
    @SerialName("cookie") COOKIE("Cookie"),
    @SerialName("water") WATER("Water"),
    @SerialName("juice") JUICE("Juice"),
    @SerialName("soda") SODA("Soda"),
    @SerialName("coffee") COFFEE("Coffee and tea"),
    @SerialName("beer") BEER("Beer and wine"),
    @SerialName("meal") MEAL("Meal"),
}

/** The name an icon has in the file, the asset and the picture's file: "milk", "ice_cream". */
val FoodIcon.key: String get() = FoodIcon.serializer().descriptor.getElementName(ordinal)

/** The picture's file in `res/drawable-nodpi`: `food_milk.webp`. */
val FoodIcon.fileName: String get() = "food_$key.webp"

private val ICON_NAMES: Map<String, FoodIcon> = FoodIcon.entries.associateBy { it.key }

/** The icon of a name in a file or the asset, or none for a name this build does not know. */
fun foodIconNamed(name: String?): FoodIcon? = name?.let { ICON_NAMES[it.trim()] }

/**
 * An icon in the file, or none. A name this build does not know (written by a newer one) is read as none, so one food
 * never makes the whole file unreadable.
 */
object FoodIconOrNone : KSerializer<FoodIcon?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("io.github.alexanderop.eisen.FoodIconOrNone", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: FoodIcon?) {
        val name = value?.key
        if (encoder is JsonEncoder) encoder.encodeJsonElement(if (name == null) JsonNull else JsonPrimitive(name)) else encoder.encodeString(name.orEmpty())
    }

    override fun deserialize(decoder: Decoder): FoodIcon? {
        val element = if (decoder is JsonDecoder) decoder.decodeJsonElement() else JsonPrimitive(decoder.decodeString())
        val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return foodIconNamed(name)
    }
}

// Which picture a thing shows

/** The picture of [food]: its own icon, else a guess from its name, else the plate. */
fun iconOf(food: Food): FoodIcon = food.icon ?: guessIcon(food.name) ?: FoodIcon.PLATE

/**
 * The picture of an [entry] (of the log or the plate): the icon of its food while the phone knows that food ([food],
 * found by the entry's food id), else a guess from the entry's own name, else the plate. Quick kcal has no food: the
 * plate, unless its name says what it was.
 */
fun iconOfEntry(entry: FoodEntry, food: Food?): FoodIcon = food?.let(::iconOf) ?: guessIcon(entry.name) ?: FoodIcon.PLATE

/** A meal shows the lunch box. */
val MEAL_ICON = FoodIcon.MEAL
