package xyz.mastriel.cutapi.item.recipe

import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.item.AgnosticItemStack
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.ItemStackUtility.customIdOrNull
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.utils.computable.Computable
import xyz.mastriel.cutapi.utils.computable.computable

object IngredientPredicates {

    fun hasId(id: Identifier): Computable<AgnosticItemStack, Boolean> {
        return computable { it.vanilla().customIdOrNull == id }
    }

    fun isItem(item: CustomItem<*>): Computable<AgnosticItemStack, Boolean> {
        return computable { it.vanilla().customIdOrNull == item.id }
    }

    fun isSimilar(item: ItemStack): Computable<AgnosticItemStack, Boolean> {
        return computable { it.vanilla().isSimilar(item) }
    }

}