@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item.recipe

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

internal fun registerBuiltInItemProductionSources() {
    ItemProductionSource.modifyRegistry {
        register(BukkitItemProductionSource)
        register(CuTItemProductionSource)
    }
}

private object BukkitItemProductionSource : ItemProductionSource {
    override val id: Identifier = id("cutapi:production_source/bukkit")
    override val priority: RegistryPriority = RegistryPriority.Low

    override fun productions(): Sequence<ItemProductionRecipe> =
        Bukkit.recipeIterator().asSequence().mapNotNull(Recipe::toItemProductionRecipe)
}

internal fun Recipe.toItemProductionRecipe(
    craftingRemainder: (org.bukkit.inventory.ItemType) -> org.bukkit.inventory.ItemType? = {
        it.craftingRemainingItem
    },
): ItemProductionRecipe? {
    val keyed = this as? org.bukkit.Keyed ?: return null
    val result = result.validProductionOutput() ?: return null
    val ingredients: List<ItemProductionIngredient>
    val kind: Identifier

    when (this) {
        is ShapedRecipe -> {
            kind = ItemProductionType.CraftingShaped
            ingredients = shape.flatMap { row ->
                row.mapNotNull { character -> choiceMap[character]?.asProductionIngredient(craftingRemainder) }
            }
        }

        is ShapelessRecipe -> {
            kind = ItemProductionType.CraftingShapeless
            ingredients = choiceList.mapNotNull { it.asProductionIngredient(craftingRemainder) }
        }

        is BlastingRecipe -> {
            kind = ItemProductionType.Blasting
            ingredients = listOfNotNull(inputChoice.asProductionIngredient(craftingRemainder))
        }

        is SmokingRecipe -> {
            kind = ItemProductionType.Smoking
            ingredients = listOfNotNull(inputChoice.asProductionIngredient(craftingRemainder))
        }

        is CampfireRecipe -> {
            kind = ItemProductionType.CampfireCooking
            ingredients = listOfNotNull(inputChoice.asProductionIngredient(craftingRemainder))
        }

        is FurnaceRecipe -> {
            kind = ItemProductionType.Smelting
            ingredients = listOfNotNull(inputChoice.asProductionIngredient(craftingRemainder))
        }

        is CookingRecipe<*> -> return null

        is StonecuttingRecipe -> {
            kind = ItemProductionType.Stonecutting
            ingredients = listOfNotNull(inputChoice.asProductionIngredient(craftingRemainder))
        }

        is SmithingTransformRecipe -> {
            kind = ItemProductionType.SmithingTransform
            ingredients = listOfNotNull(
                template.asProductionIngredient(craftingRemainder),
                base.asProductionIngredient(craftingRemainder),
                addition.asProductionIngredient(craftingRemainder),
            )
        }

        is TransmuteRecipe -> {
            kind = ItemProductionType.CraftingTransmute
            ingredients = listOfNotNull(
                input.asProductionIngredient(craftingRemainder),
                material.asProductionIngredient(craftingRemainder),
            )
        }

        else -> return null
    }

    if (ingredients.isEmpty()) return null
    return ItemProductionRecipe(keyed.key.toIdentifier(), kind, ingredients, result)
}

private object CuTItemProductionSource : ItemProductionSource {
    override val id: Identifier = id("cutapi:production_source/cutapi")
    override val priority: RegistryPriority = RegistryPriority.High

    override fun productions(): Sequence<ItemProductionRecipe> = sequence {
        for (recipe in CustomShapedRecipe.getAllValues()) {
            val ingredients = (0 until recipe.size.size)
                .mapNotNull(recipe::getIngredientAtIndex)
                .map(ShapedRecipeIngredient::asProductionIngredient)
            recipe.result.validProductionOutput()?.let { output ->
                yield(
                    ItemProductionRecipe(
                        recipe.id,
                        ItemProductionType.CraftingShaped,
                        ingredients,
                        output,
                    ),
                )
            }
        }

        for (recipe in CustomShapelessRecipe.getAllValues()) {
            val ingredients = recipe.ingredients.map(ShapelessRecipeIngredient::asProductionIngredient)
            recipe.result.validProductionOutput()?.let { output ->
                yield(
                    ItemProductionRecipe(
                        recipe.id,
                        ItemProductionType.CraftingShapeless,
                        ingredients,
                        output,
                    ),
                )
            }
        }

        for (recipe in CustomFurnaceRecipe.getAllValues()) {
            val input = recipe.input.asProductionChoice() ?: continue
            yield(
                ItemProductionRecipe(
                    recipe.id,
                    ItemProductionType.Smelting,
                    listOf(ItemProductionIngredient(setOf(input))),
                    ItemProductionOutput(recipe.output.handle.itemIdentity, recipe.output.handle.amount),
                ),
            )
        }

        for (recipe in CustomSmithingTableRecipe.getAllValues()) {
            yield(
                ItemProductionRecipe(
                    recipe.id,
                    ItemProductionType.SmithingTransform,
                    listOf(
                        ItemProductionIngredient(setOf(recipe.template.asProductionChoice())),
                        ItemProductionIngredient(setOf(recipe.base.asProductionChoice())),
                        ItemProductionIngredient(setOf(recipe.addition.asProductionChoice())),
                    ),
                    ItemProductionOutput(recipe.result.handle.itemIdentity, recipe.result.handle.amount),
                ),
            )
        }
    }
}

private fun ShapedRecipeIngredient.asProductionIngredient(): ItemProductionIngredient {
    val identity = when (this) {
        is CustomShapedRecipeIngredient -> placeholderItem.identity
        else -> material.asItemType()?.asIdentity()
            ?: error("Recipe ingredient $material has no item type.")
    }
    return ItemProductionIngredient(setOf(identity.asProductionChoice(quantity)))
}

private fun ShapelessRecipeIngredient.asProductionIngredient(): ItemProductionIngredient {
    val identity = when (this) {
        is CustomShapelessRecipeIngredient -> placeholderItem.identity
        else -> material.asItemType()?.asIdentity()
            ?: error("Recipe ingredient $material has no item type.")
    }
    return ItemProductionIngredient(setOf(identity.asProductionChoice(quantity)))
}

private fun RecipeChoice.asProductionIngredient(
    craftingRemainder: (org.bukkit.inventory.ItemType) -> org.bukkit.inventory.ItemType?,
): ItemProductionIngredient? {
    val choices = when (this) {
        is RecipeChoice.MaterialChoice -> choices.mapNotNull { it.asProductionChoice(craftingRemainder) }
        is RecipeChoice.ExactChoice -> choices.mapNotNull { it.asProductionChoice(craftingRemainder) }
        else -> emptyList()
    }
    return choices.toSet().takeIf(Set<ItemProductionChoice>::isNotEmpty)?.let(::ItemProductionIngredient)
}

private fun Material.asProductionChoice(
    craftingRemainder: (org.bukkit.inventory.ItemType) -> org.bukkit.inventory.ItemType? = {
        it.craftingRemainingItem
    },
): ItemProductionChoice? =
    asItemType()?.asIdentity()?.asProductionChoice(craftingRemainder = craftingRemainder)

private fun ItemStack.asProductionChoice(
    craftingRemainder: (org.bukkit.inventory.ItemType) -> org.bukkit.inventory.ItemType?,
): ItemProductionChoice? {
    if (isEmpty || amount <= 0) return null
    return productionIdentity().asProductionChoice(amount, craftingRemainder)
}

private fun ItemIdentity.asProductionChoice(
    amount: Int = 1,
    craftingRemainder: (org.bukkit.inventory.ItemType) -> org.bukkit.inventory.ItemType? = {
        it.craftingRemainingItem
    },
): ItemProductionChoice {
    val remainder = craftingRemainder(itemType)
    val returned = if (remainder == null) {
        emptyList()
    } else {
        listOf(ItemProductionOutput(remainder.asIdentity(), amount))
    }
    return ItemProductionChoice(this, amount, returned)
}

private fun ItemStack.validProductionOutput(): ItemProductionOutput? {
    if (isEmpty || amount <= 0) return null
    return ItemProductionOutput(productionIdentity(), amount)
}

private fun ItemStack.productionIdentity(): ItemIdentity = try {
    itemIdentity
} catch (_: NoClassDefFoundError) {
    type.asItemType()?.asIdentity()
        ?: error("Recipe stack $type has no item identity.")
}
