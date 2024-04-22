package xyz.mastriel.cutapi.item.recipe

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import xyz.mastriel.cutapi.item.AgnosticItemStack
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.ItemDescriptorBuilder
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.utils.computable.Computable
import xyz.mastriel.cutapi.utils.computable.computable
import xyz.mastriel.cutapi.utils.trim


data class ShapedRecipeIngredient(
    val char: Char,
    val material: Material,
    val quantity: Int = 1,
    val itemRequirement: Computable<AgnosticItemStack, Boolean>,
    val onCraft: IngredientCraftContext.() -> Unit
)

private typealias RecipePattern = Triple<String, String, String?>

data class CustomShapedRecipe(
    override val id: Identifier,
    val pattern: RecipePattern,
    val size: Size,
    val ingredients: Map<Char, ShapedRecipeIngredient>,
    val result: ItemStack
) : Identifiable {

    enum class Size(val size: Int) { Four(4), Nine(9) }


    fun getIngredientAtIndex(i: Int): ShapedRecipeIngredient? {
        if (size == Size.Four) {
            val char = when (i) {
                in 0..1 -> pattern.first.getOrNull(i)
                in 2..3 -> pattern.second.getOrNull(i)
                else -> throw IndexOutOfBoundsException(i)
            }
            return ingredients[char]
        } else if (size == Size.Nine) {
            val char = when (i) {
                in 0..2 -> pattern.first.getOrNull(i)
                in 3..5 -> pattern.second.getOrNull(i)
                in 6..8 -> pattern.third?.getOrNull(i)
                else -> throw IndexOutOfBoundsException(i)
            }
            return ingredients[char]
        }
        throw IllegalStateException()
    }

    fun getMatrix(): List<ShapedRecipeIngredient?> {
        val matrix = mutableListOf<ShapedRecipeIngredient?>()
        for (i in 0 until size.size) {
            val ingredient = getIngredientAtIndex(i)
            if (ingredient != null) matrix.add(ingredient)
        }
        val items = if (size == Size.Four) {
            matrix.chunked(2)
        } else {
            matrix.chunked(3)
        }

        return items.trim { it != null }.flatten()
    }

    companion object : IdentifierRegistry<CustomShapedRecipe>("Shaped Recipes") {
        override fun register(item: CustomShapedRecipe): CustomShapedRecipe {
            val itemResult = item.result

            val recipe = ShapedRecipe(item.id.toNamespacedKey(), itemResult)

            if (item.size == Size.Four) {
                recipe.shape(item.pattern.first, item.pattern.second)
            } else if (item.size == Size.Nine) {
                recipe.shape(
                    item.pattern.first,
                    item.pattern.second,
                    item.pattern.third ?: "   "
                )
            }

            for ((char, ingredient) in item.ingredients) {

                recipe.setIngredient(char, ingredient.material)
            }

            Bukkit.addRecipe(recipe)

            return super.register(item)
        }
    }
}

class ShapedRecipeBuilder(
    val result: ItemStack,
    override val id: Identifier,
    val size: CustomShapedRecipe.Size
) : CraftingRecipeBuilder<CustomShapedRecipe>, Identifiable {


    constructor(result: CuTItemStack, id: Identifier, size: CustomShapedRecipe.Size) : this(result.handle, id, size)

    private var pattern: RecipePattern? = null
    private var ingredients: MutableMap<Char, ShapedRecipeIngredient> = mutableMapOf()

    fun pattern(row1: String, row2: String, row3: String? = null) {
        if ((size == CustomShapedRecipe.Size.Four && row1.length > 2) ||
            (size == CustomShapedRecipe.Size.Four && row2.length > 2) ||
            (size == CustomShapedRecipe.Size.Four && row3 != null)
        ) {
            throw InvalidRecipeException(id, "Pattern too big for Size.Four")
        }

        if ((size == CustomShapedRecipe.Size.Nine && row1.length > 3) ||
            (size == CustomShapedRecipe.Size.Nine && row2.length > 3) ||
            (size == CustomShapedRecipe.Size.Nine && (row3?.length ?: 3) > 3)
        ) {
            throw InvalidRecipeException(id, "Pattern too big for Size.Nine")
        }


        pattern = Triple(row1, row2, row3)
    }

    fun ingredient(
        char: Char,
        material: Material,
        quantity: Int = 1,
        itemRequirement: Computable<AgnosticItemStack, Boolean> = computable(true),
        onCraft: IngredientCraftContext.() -> Unit = {}
    ) {
        ingredients[char] = ShapedRecipeIngredient(char, material, quantity, itemRequirement, onCraft)
    }

    fun ingredient(
        char: Char,
        item: CustomItem<*>,
        quantity: Int = 1,
        slotsRequired: Int = 1,
        onCraft: IngredientCraftContext.() -> Unit = {}
    ) {
        repeat(slotsRequired) {
            ingredients[char] =
                ShapedRecipeIngredient(char, item.type, quantity, IngredientPredicates.isItem(item), onCraft)
        }
    }


    override fun build(): CustomShapedRecipe {

        val pattern = pattern ?: throw InvalidRecipeException(id, "Pattern is not set.")


        return CustomShapedRecipe(id, pattern, size, ingredients, result)
    }
}

fun ItemDescriptorBuilder.shapedRecipe(
    id: Identifier,
    size: CustomShapedRecipe.Size,
    amount: Int = 1,
    block: ShapedRecipeBuilder.() -> Unit
) {
    onRegister += {
        val builder = ShapedRecipeBuilder(item.createItemStack(amount), id, size).apply(block)
        CustomShapedRecipe.register(builder.build())
    }
}

fun shapedRecipe(
    id: Identifier,
    size: CustomShapedRecipe.Size,
    result: ItemStack,
    block: ShapedRecipeBuilder.() -> Unit
): CustomShapedRecipe {
    val builder = ShapedRecipeBuilder(result, id, size).apply(block)
    return builder.build()
}

fun registerShapedRecipe(
    id: Identifier,
    size: CustomShapedRecipe.Size,
    result: ItemStack,
    block: ShapedRecipeBuilder.() -> Unit
) = CustomShapedRecipe.register(shapedRecipe(id, size, result, block))