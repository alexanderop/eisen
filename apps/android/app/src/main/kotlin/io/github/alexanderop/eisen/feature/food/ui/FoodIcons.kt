package io.github.alexanderop.eisen.feature.food.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.R
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.feature.food.FoodIcon

/*
 * The pictures of the foods: the drawable of each [FoodIcon], and how a picture sits next to a name. Every value has its
 * picture in the `when` of [drawableOf]: a new value without one does not compile.
 */

@DrawableRes
fun drawableOf(icon: FoodIcon): Int = when (icon) {
    FoodIcon.MILK -> R.drawable.food_milk
    FoodIcon.EGG -> R.drawable.food_egg
    FoodIcon.POULTRY -> R.drawable.food_poultry
    FoodIcon.RICE -> R.drawable.food_rice
    FoodIcon.BANANA -> R.drawable.food_banana
    FoodIcon.NUTS -> R.drawable.food_nuts
    FoodIcon.YOGURT -> R.drawable.food_yogurt
    FoodIcon.CHEESE -> R.drawable.food_cheese
    FoodIcon.BUTTER -> R.drawable.food_butter
    FoodIcon.OIL -> R.drawable.food_oil
    FoodIcon.TOFU -> R.drawable.food_tofu
    FoodIcon.SHAKE -> R.drawable.food_shake
    FoodIcon.MEAT -> R.drawable.food_meat
    FoodIcon.SAUSAGE -> R.drawable.food_sausage
    FoodIcon.FISH -> R.drawable.food_fish
    FoodIcon.BEANS -> R.drawable.food_beans
    FoodIcon.BAR -> R.drawable.food_bar
    FoodIcon.PLATE -> R.drawable.food_plate
    FoodIcon.BREAD -> R.drawable.food_bread
    FoodIcon.PASTA -> R.drawable.food_pasta
    FoodIcon.CEREAL -> R.drawable.food_cereal
    FoodIcon.POTATO -> R.drawable.food_potato
    FoodIcon.PASTRY -> R.drawable.food_pastry
    FoodIcon.PIZZA -> R.drawable.food_pizza
    FoodIcon.FRUIT -> R.drawable.food_fruit
    FoodIcon.BERRIES -> R.drawable.food_berries
    FoodIcon.VEGETABLE -> R.drawable.food_vegetable
    FoodIcon.SALAD -> R.drawable.food_salad
    FoodIcon.SOUP -> R.drawable.food_soup
    FoodIcon.BURGER -> R.drawable.food_burger
    FoodIcon.CHOCOLATE -> R.drawable.food_chocolate
    FoodIcon.ICE_CREAM -> R.drawable.food_ice_cream
    FoodIcon.CHIPS -> R.drawable.food_chips
    FoodIcon.SPREAD -> R.drawable.food_spread
    FoodIcon.SAUCE -> R.drawable.food_sauce
    FoodIcon.COOKIE -> R.drawable.food_cookie
    FoodIcon.WATER -> R.drawable.food_water
    FoodIcon.JUICE -> R.drawable.food_juice
    FoodIcon.SODA -> R.drawable.food_soda
    FoodIcon.COFFEE -> R.drawable.food_coffee
    FoodIcon.BEER -> R.drawable.food_beer
    FoodIcon.MEAL -> R.drawable.food_meal
}

/** The size of a picture next to a name in a row. */
val RowIconSize = 40.dp

/** The size of the picture on top of the add sheet. */
val HeaderIconSize = 64.dp

/** [icon] as decoration next to a name: no description, the row already reads the name. */
@Composable
fun FoodIconImage(icon: FoodIcon, modifier: Modifier = Modifier, size: Dp = RowIconSize) {
    Image(painterResource(drawableOf(icon)), contentDescription = null, modifier = modifier.size(size))
}

/**
 * The picture of a row at the phone's font scale and width: 40 dp up to a font of 1.3, 32 dp above it, and none where
 * the screen is narrow for the font (fewer than [MIN_ROW_EMS] "font dp" across: 320 dp at 2.0), so the name keeps its room
 * first. Null: no picture.
 */
@Composable
fun rowIconSize(): Dp? {
    val scale = LocalDensity.current.fontScale
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width / scale < MIN_ROW_EMS -> null
        scale > 1.3f -> 32.dp
        else -> RowIconSize
    }
}

/** The width in dp, divided by the font scale, under which a row leaves out its picture. */
private const val MIN_ROW_EMS = 200

/** A row's picture, or nothing at a font where the name needs the room. */
@Composable
fun RowIcon(icon: FoodIcon) {
    rowIconSize()?.let { FoodIconImage(icon, size = it) }
}

/**
 * The grid of all the pictures: [selected] is the food's, a tap picks one. A sheet in the house style, over the add sheet
 * or the own food form; Back, its close button or a swipe down put it away without a choice. Each picture is a target of
 * at least 48 dp, said by TalkBack with its name and whether it is the one selected.
 */
@Composable
internal fun IconPickerSheet(selected: FoodIcon, onPick: (FoodIcon) -> Unit, onClose: () -> Unit) {
    FoodSheet("Picture", onClose) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // As many columns of about 80 dp as fit, three at least.
            val columns = (maxWidth / CellWidth).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FoodIcon.entries.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { icon -> IconCell(icon, icon == selected, Modifier.weight(1f)) { onPick(icon) } }
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val CellWidth = 80.dp
private const val MIN_COLUMNS = 3
private const val MAX_COLUMNS = 6
private val CellShape = RoundedCornerShape(14.dp)

@Composable
private fun IconCell(icon: FoodIcon, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clip(CellShape)
            .then(if (selected) Modifier.border(BorderStroke(2.dp, EisenColors.Primary), CellShape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "Choose") { onPick() }
            .semantics(mergeDescendants = true) {
                contentDescription = icon.label
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FoodIconImage(icon, size = 44.dp)
        Text(icon.label, fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = EisenColors.Foreground, modifier = Modifier.fillMaxWidth())
    }
}
