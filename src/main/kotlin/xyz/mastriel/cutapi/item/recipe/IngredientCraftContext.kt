package xyz.mastriel.cutapi.item.recipe

import xyz.mastriel.cutapi.item.AgnosticItemStack

data class IngredientCraftContext(val itemStack: AgnosticItemStack) {

    var dontConsume: Boolean = false

    /**
     * Only use with items that have durability.
     */
    var lowerDurabilityBy: Int = 0

    /**
     * You should probably only use this with items that are not stackable.
     */
    var replaceWith: AgnosticItemStack? = null
}