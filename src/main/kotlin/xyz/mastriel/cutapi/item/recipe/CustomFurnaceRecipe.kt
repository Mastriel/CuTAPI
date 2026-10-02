package xyz.mastriel.cutapi.item.recipe

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import xyz.mastriel.cutapi.utils.computable.*
import kotlin.time.*

public data class CustomFurnaceRecipe(
    override val id: Identifier,
    val input: Material,
    val output: CuTItemStack,
    val cookTime: Duration,
    val experienceGranted: Float = 1.0f,
    /** Evaluated against the source stack before a furnace, smoker, or blast furnace completes cooking. */
    val inputRequirement: Computable<CuTItemStack, Boolean> = computable(true)
) : Identifiable {

    public companion object :
        IdentifierRegistry<CustomFurnaceRecipe>(id("cutapi:registry/custom_furnace_recipe")) {

        override fun register(item: CustomFurnaceRecipe): CustomFurnaceRecipe {
            with (item) {
                val recipe = FurnaceRecipe(
                    id.toNamespacedKey(),
                    output.vanilla(),
                    input,
                    experienceGranted,
                    cookTime.inWholeTicks.toInt()
                )
                Bukkit.addRecipe(recipe)
            }
            return super.register(item)
        }

        public fun registerBlasting(item: CustomFurnaceRecipe): CustomFurnaceRecipe {
            with (item) {
                val recipe = BlastingRecipe(
                    id.toNamespacedKey(),
                    output.vanilla(),
                    input,
                    experienceGranted,
                    cookTime.inWholeTicks.toInt()
                )
                Bukkit.addRecipe(recipe)
            }
            return super.register(item)
        }

        public fun registerSmoking(item: CustomFurnaceRecipe): CustomFurnaceRecipe {
            with (item) {
                val recipe = SmokingRecipe(
                    id.toNamespacedKey(),
                    output.vanilla(),
                    input,
                    experienceGranted,
                    cookTime.inWholeTicks.toInt()
                )
                Bukkit.addRecipe(recipe)
            }
            return super.register(item)
        }
    }
}
