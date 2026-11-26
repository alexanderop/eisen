package io.github.alexanderop.eisen.feature.food.platform

import android.content.Context
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.parsePlainFoods

/** The plain foods shipped with the app, `assets/foods/plain.json`. None when it cannot be read. */
object PlainFoodAsset {
    fun read(context: Context): List<Food> =
        runCatching { context.assets.open("foods/plain.json").use { parsePlainFoods(it.readBytes().decodeToString()) } }.getOrDefault(emptyList())
}
