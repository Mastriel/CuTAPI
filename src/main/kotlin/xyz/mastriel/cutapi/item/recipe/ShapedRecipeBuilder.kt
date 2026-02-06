package xyz.mastriel.cutapi.item.recipe

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import xyz.mastriel.cutapi.utils.computable.*


public open class ShapedRecipeIngredient(
    public val char: Char,
    public val material: Material,
    public val quantity: Int = 1,
    public val itemRequirement: Computable<AgnosticItemStack, Boolean>,
    public val onCraft: IngredientCraftContext.() -> Unit
)

public class CustomShapedRecipeIngredient(
    char: Char,
    material: Material,
    quantity: Int = 1,
    itemRequirement: Computable<AgnosticItemStack, Boolean>,
    onCraft: IngredientCraftContext.() -> Unit,
    public val placeholderItem: CustomItem<*>
) : ShapedRecipeIngredient(char, material, quantity, itemRequirement, onCraft)


private typealias RecipePattern = Triple<String, String, String?>

public data class CustomShapedRecipe(
    override val id: Identifier,
    val pattern: RecipePattern,
    val size: Size,
    val ingredients: Map<Char, ShapedRecipeIngredient>,
    val result: ItemStack
) : Identifiable {

    public enum class Size(public val size: Int) { Four(4), Nine(9) }


    public fun getIngredientAtIndex(i: Int): ShapedRecipeIngredient? {
        if (size == Size.Four) {
            val char = when (i) {
                in 0..1 -> pattern.first.getOrNull(i)
                in 2..3 -> pattern.second.getOrNull(i % 2)
                else -> throw IndexOutOfBoundsException(i)
            }
            return ingredients[char]
        } else if (size == Size.Nine) {
            val char = when (i) {
                in 0..2 -> pattern.first.getOrNull(i)
                in 3..5 -> pattern.second.getOrNull(i % 3)
                in 6..8 -> pattern.third?.getOrNull(i % 3)
                else -> throw IndexOutOfBoundsException(i)
            }
            return ingredients[char]
        }
        throw IllegalStateException()
    }

    public fun getMatrix(): List<ShapedRecipeIngredient?> {
        println(pattern)
        val matrix = mutableListOf<ShapedRecipeIngredient?>()
        for (i in 0 until size.size) {
            val ingredient = getIngredientAtIndex(i)
            matrix.add(ingredient)
        }
        val items = if (size == Size.Four) {
            matrix.chunked(2)
        } else {
            matrix.chunked(3)
        }

        return items.trim({ null }) { it != null }.flatten()
    }

    public companion object : IdentifierRegistry<CustomShapedRecipe>("Shaped Recipes") {
        override fun register(item: CustomShapedRecipe): CustomShapedRecipe {
            val itemResult = item.result

            val recipe = ShapedRecipe(item.id.toNamespacedKey(), itemResult)

            if (item.size == Size.Four) {
                recipe.shape(item.pattern.first, item.pattern.second)
            } else if (item.size == Size.Nine) {
                recipe.shape(
                    *listOfNotNull(
                        item.pattern.first,
                        item.pattern.second,
                        item.pattern.third
                    ).toTypedArray()
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

public class ShapedRecipeBuilder(
    public val result: ItemStack,
    override val id: Identifier,
    public val size: CustomShapedRecipe.Size
) : CraftingRecipeBuilder<CustomShapedRecipe>, Identifiable {


    public constructor(result: CuTItemStack, id: Identifier, size: CustomShapedRecipe.Size) : this(
        result.handle,
        id,
        size
    )

    private var pattern: RecipePattern? = null
    private var ingredients: MutableMap<Char, ShapedRecipeIngredient> = mutableMapOf()

    public fun pattern(row1: String, row2: String, row3: String? = null) {
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

    public fun ingredient(
        char: Char,
        material: Material,
        quantity: Int = 1,
        itemRequirement: Computable<AgnosticItemStack, Boolean> = computable(true),
        onCraft: IngredientCraftContext.() -> Unit = {}
    ) {
        ingredients[char] = ShapedRecipeIngredient(char, material, quantity, itemRequirement, onCraft)
    }

    public fun ingredient(
        char: Char,
        item: CustomItem<*>,
        quantity: Int = 1,
        slotsRequired: Int = 1,
        onCraft: IngredientCraftContext.() -> Unit = {}
    ) {
        repeat(slotsRequired) {
            val predicate = IngredientPredicates.isItem(item)
            ingredients[char] = CustomShapedRecipeIngredient(char, item.type, quantity, predicate, onCraft, item)
        }
    }


    override fun build(): CustomShapedRecipe {

        val pattern = pattern ?: throw InvalidRecipeException(id, "Pattern is not set.")


        return CustomShapedRecipe(id, pattern, size, ingredients, result)
    }
}

public fun ItemDescriptorBuilder.shapedRecipe(
    id: Identifier,
    size: CustomShapedRecipe.Size,
    amount: Int = 1,
    block: ShapedRecipeBuilder.() -> Unit
) {
    onRegister += {
        val builder = ShapedRecipeBuilder(item.createItemStack(amount), id, size).apply(block)
        CustomShapedRecipe.modifyRegistry {
            register(builder.build())
        }
    }
}

public fun shapedRecipe(
    id: Identifier,
    size: CustomShapedRecipe.Size,
    result: ItemStack,
    block: ShapedRecipeBuilder.() -> Unit
): CustomShapedRecipe {
    val builder = ShapedRecipeBuilder(result, id, size).apply(block)
    return builder.build()
}

public fun DeferredRegistry<CustomShapedRecipe>.registerShapedRecipe(
    id: Identifier,
    size: CustomShapedRecipe.Size,
    result: ItemStack,
    block: ShapedRecipeBuilder.() -> Unit
): Deferred<CustomShapedRecipe> = register { shapedRecipe(id, size, result, block) }