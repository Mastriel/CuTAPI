package xyz.mastriel.cutapi.item.recipe

import xyz.mastriel.cutapi.registry.*

fun interface CraftingRecipeBuilder<T: Identifiable> {



    fun build() : T
}