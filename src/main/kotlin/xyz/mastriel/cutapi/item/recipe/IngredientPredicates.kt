package xyz.mastriel.cutapi.item.recipe

import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.computable.*

public object IngredientPredicates {

    public fun hasId(id: Identifier): Computable<CuTItemStack, Boolean> {
        return computable { it.customItem?.id == id }
    }

    public fun isItem(item: CustomItem<*>): Computable<CuTItemStack, Boolean> {
        return computable { it.customItem == item }
    }

    public fun isSimilar(item: ItemStack): Computable<CuTItemStack, Boolean> {
        return computable { it.vanilla().isSimilar(item) }
    }

}
