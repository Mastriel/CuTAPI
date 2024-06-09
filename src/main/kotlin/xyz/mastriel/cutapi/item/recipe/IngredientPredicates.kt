package xyz.mastriel.cutapi.item.recipe

import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.ItemStackUtility.customIdOrNull
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.computable.*

public object IngredientPredicates {

    public fun hasId(id: Identifier): Computable<AgnosticItemStack, Boolean> {
        return computable { it.vanilla().customIdOrNull == id }
    }

    public fun isItem(item: CustomItem<*>): Computable<AgnosticItemStack, Boolean> {
        return computable { it.vanilla().customIdOrNull == item.id }
    }

    public fun isSimilar(item: ItemStack): Computable<AgnosticItemStack, Boolean> {
        return computable { it.vanilla().isSimilar(item) }
    }

}