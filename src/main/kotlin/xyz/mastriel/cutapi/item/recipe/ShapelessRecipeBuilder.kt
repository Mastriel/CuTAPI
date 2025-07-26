package xyz.mastriel.cutapi.item.recipe

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.computable.*

public open class ShapelessRecipeIngredient(
    public val material: Material,
    public val quantity: Int = 1,
    public val itemRequirement: Computable<AgnosticItemStack, Boolean>,
    public val onCraft: IngredientCraftContext.() -> Unit
)

public class CustomShapelessRecipeIngredient(
    material: Material,
    quantity: Int = 1,
    itemRequirement: Computable<AgnosticItemStack, Boolean>,
    onCraft: IngredientCraftContext.() -> Unit,
    public val placeholderItem: CustomItem<*>
) : ShapelessRecipeIngredient(material, quantity, itemRequirement, onCraft)

public data class CustomShapelessRecipe(
    override val id: Identifier,
    val ingredients: List<ShapelessRecipeIngredient>,
    val result: ItemStack
) : Identifiable {
    public companion object : IdentifierRegistry<CustomShapelessRecipe>("Shapeless Recipes") {

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

public class ShapelessRecipeBuilder(
    public val result: ItemStack,
    override val id: Identifier
) : CraftingRecipeBuilder<CustomShapelessRecipe>, Identifiable {

    public constructor(result: CuTItemStack, id: Identifier) : this(result.handle, id)

    private val ingredients = mutableListOf<ShapelessRecipeIngredient>()

    public fun ingredient(
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

    public fun ingredient(
        item: CustomItem<*>,
        quantity: Int = 1,
        slotsRequired: Int = 1,
        onCraft: IngredientCraftContext.() -> Unit = {}
    ) {
        repeat(slotsRequired) {
            ingredients += CustomShapelessRecipeIngredient(
                item.type,
                quantity,
                IngredientPredicates.isItem(item),
                onCraft,
                item
            )
        }
    }

    override fun build(): CustomShapelessRecipe {

        return CustomShapelessRecipe(id, ingredients, result)
    }
}


public fun ItemDescriptorBuilder.shapelessRecipe(
    id: Identifier,
    amount: Int = 1,
    block: ShapelessRecipeBuilder.() -> Unit
) {
    onRegister += {
        val builder = ShapelessRecipeBuilder(item.createItemStack(amount), id).apply(block)
        CustomShapelessRecipe.register(builder.build())
    }
}

public fun shapelessRecipe(
    id: Identifier,
    result: ItemStack,
    block: ShapelessRecipeBuilder.() -> Unit
): CustomShapelessRecipe {
    val builder = ShapelessRecipeBuilder(result, id).apply(block)
    return builder.build()
}

public fun registerShapelessRecipe(
    id: Identifier,
    result: ItemStack,
    block: ShapelessRecipeBuilder.() -> Unit
): CustomShapelessRecipe = CustomShapelessRecipe.register(shapelessRecipe(id, result, block))