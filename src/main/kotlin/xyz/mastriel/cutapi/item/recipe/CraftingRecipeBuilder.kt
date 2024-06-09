package xyz.mastriel.cutapi.item.recipe

import xyz.mastriel.cutapi.registry.*

public fun interface CraftingRecipeBuilder<T: Identifiable> {

    public fun build() : T
}