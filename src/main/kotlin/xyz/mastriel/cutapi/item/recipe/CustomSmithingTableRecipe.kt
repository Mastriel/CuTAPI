package xyz.mastriel.cutapi.item.recipe

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public class CustomSmithingTableRecipe(
    override val id: Identifier,
    public val template: ItemIdentity,
    public val base: ItemIdentity,
    public val addition: ItemIdentity,
    public val result: CuTItemStack
) : Identifiable {

    public companion object :
        IdentifierRegistry<CustomSmithingTableRecipe>(id("cutapi:registry/custom_smithing_table_recipe")) {

        override fun register(item: CustomSmithingTableRecipe): CustomSmithingTableRecipe {
            with(item) {
                val recipe = SmithingTransformRecipe(
                    id.toNamespacedKey(),
                    result.vanilla(),
                    RecipeChoice.itemType(template.backingItem),
                    RecipeChoice.itemType(base.backingItem),
                    RecipeChoice.itemType(addition.backingItem),
                )
                Bukkit.addRecipe(recipe)
            }
            return super.register(item)
        }
    }

}
