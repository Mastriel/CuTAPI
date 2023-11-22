package xyz.mastriel.cutapi.item.recipe

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.computable.*

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
            val recipe = ShapelessRecipe(item.id.toNamespacedKey(), item.result)

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

    override fun build(): CustomShapelessRecipe {

        return CustomShapelessRecipe(id, ingredients, result)
    }
}


fun ItemDescriptorBuilder.shapelessRecipe(id: Identifier, block: ShapelessRecipeBuilder.() -> Unit) {
    onRegister += {
        val builder = ShapelessRecipeBuilder(item.createItemStack(), id).apply(block)
        CustomShapelessRecipe.register(builder.build())
    }
}

fun shapelessRecipe(
    id: Identifier,
    result: ItemStack,
    block: ShapelessRecipeBuilder.() -> Unit
) : CustomShapelessRecipe {
    val builder = ShapelessRecipeBuilder(result, id).apply(block)
    return builder.build()
}

fun registerShapelessRecipe(
    id: Identifier,
    result: ItemStack,
    block: ShapelessRecipeBuilder.() -> Unit
) = CustomShapelessRecipe.register(shapelessRecipe(id, result, block))