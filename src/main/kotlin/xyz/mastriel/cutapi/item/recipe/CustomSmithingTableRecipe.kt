package xyz.mastriel.cutapi.item.recipe

import org.bukkit.Bukkit
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.SmithingTransformRecipe
import xyz.mastriel.cutapi.item.AgnosticItemStack
import xyz.mastriel.cutapi.item.AgnosticMaterial
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry

class CustomSmithingTableRecipe(
    override val id: Identifier,
    val template: AgnosticMaterial,
    val base: AgnosticMaterial,
    val addition: AgnosticMaterial,
    val result: AgnosticItemStack
) : Identifiable {

    companion object : IdentifierRegistry<CustomSmithingTableRecipe>("Custom Smithing Table Recipes") {

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