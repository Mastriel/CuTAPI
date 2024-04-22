package xyz.mastriel.cutapi.item.recipe

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapelessRecipe
import xyz.mastriel.cutapi.item.AgnosticItemStack
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.ItemDescriptorBuilder
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.utils.computable.Computable
import xyz.mastriel.cutapi.utils.computable.computable

data class ShapelessRecipeIngredient(
    val material: Material,
    val quantity: Int = 1,
    val itemRequirement: Computable<AgnosticItemStack, Boolean>,
    val onCraft: IngredientCraftContext.() -> Unit
)

data class CustomShapelessRecipe(
    override val id: Identifier,
    val ingredients: List<ShapelessRecipeIngredient>,
    val result: ItemStack
) : Identifiable {
    companion object : IdentifierRegistry<CustomShapelessRecipe>("Shapeless Recipes") {

        override fun register(item: CustomShapelessRecipe): CustomShapelessRecipe {
            val itemResult = item.result
            val recipe = ShapelessRecipe(item.id.toNamespacedKey(), itemResult)

            for (ingredient in item.ingredients) {
                recipe.addIngredient(ingredient.material)
            }
            Bukkit.addRecipe(recipe)

            return super.register(item)
        }
    }
}

class ShapelessRecipeBuilder(
    val result: ItemStack,
    override val id: Identifier
) : CraftingRecipeBuilder<CustomShapelessRecipe>, Identifiable {

    constructor(result: CuTItemStack, id: Identifier) : this(result.handle, id)

    private val ingredients = mutableListOf<ShapelessRecipeIngredient>()

    fun ingredient(
        material: Material,
        quantity: Int = 1,
        slotsRequired: Int = 1,
        itemRequirement: Computable<AgnosticItemStack, Boolean> = computable(true),
        onCraft: IngredientCraftContext.() -> Unit = {}
    ) {
        repeat(slotsRequired) {
            ingredients += ShapelessRecipeIngredient(material, quantity, itemRequirement, onCraft)
        }
    }

    fun ingredient(
        item: CustomItem<*>,
        quantity: Int = 1,
        slotsRequired: Int = 1,
        onCraft: IngredientCraftContext.() -> Unit = {}
    ) {
        repeat(slotsRequired) {
            ingredients += ShapelessRecipeIngredient(item.type, quantity, IngredientPredicates.isItem(item), onCraft)
        }
    }

    override fun build(): CustomShapelessRecipe {

        return CustomShapelessRecipe(id, ingredients, result)
    }
}


fun ItemDescriptorBuilder.shapelessRecipe(id: Identifier, amount: Int = 1, block: ShapelessRecipeBuilder.() -> Unit) {
    onRegister += {
        val builder = ShapelessRecipeBuilder(item.createItemStack(amount), id).apply(block)
        CustomShapelessRecipe.register(builder.build())
    }
}

fun shapelessRecipe(
    id: Identifier,
    result: ItemStack,
    block: ShapelessRecipeBuilder.() -> Unit
): CustomShapelessRecipe {
    val builder = ShapelessRecipeBuilder(result, id).apply(block)
    return builder.build()
}

fun registerShapelessRecipe(
    id: Identifier,
    result: ItemStack,
    block: ShapelessRecipeBuilder.() -> Unit
) = CustomShapelessRecipe.register(shapelessRecipe(id, result, block))