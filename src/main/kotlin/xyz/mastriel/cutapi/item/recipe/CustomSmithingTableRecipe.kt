package xyz.mastriel.cutapi.item.recipe

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public class CustomSmithingTableRecipe(
    override val id: Identifier,
    public val template: AgnosticMaterial,
    public val base: AgnosticMaterial,
    public val addition: AgnosticMaterial,
    public val result: AgnosticItemStack
) : Identifiable {

    public companion object : IdentifierRegistry<CustomSmithingTableRecipe>("Custom Smithing Table Recipes") {

        override fun register(item: CustomSmithingTableRecipe): CustomSmithingTableRecipe {
            with(item) {
                val recipe = SmithingTransformRecipe(
                    id.toNamespacedKey(),
                    result.vanilla(),
                    RecipeChoice.MaterialChoice(template.expectedVanillaMaterial),
                    RecipeChoice.MaterialChoice(base.expectedVanillaMaterial),
                    RecipeChoice.MaterialChoice(addition.expectedVanillaMaterial),
                )
                Bukkit.addRecipe(recipe)
            }
            return super.register(item)
        }
    }

}