package xyz.mastriel.cutapi.item.recipe

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.SmokingRecipe
import xyz.mastriel.cutapi.item.AgnosticItemStack
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.utils.computable.Computable
import xyz.mastriel.cutapi.utils.computable.computable
import xyz.mastriel.cutapi.utils.inWholeTicks
import kotlin.time.Duration

data class CustomFurnaceRecipe(
    override val id: Identifier,
    val input: Material,
    val output: AgnosticItemStack,
    val cookTime: Duration,
    val experienceGranted: Float = 1.0f,
    // doesn't work; spigot hates me.
    val inputRequirement : Computable<AgnosticItemStack, Boolean> = computable(true)
) : Identifiable {

    companion object : IdentifierRegistry<CustomFurnaceRecipe>("Custom Furnace Recipes") {

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

        fun registerBlasting(item: CustomFurnaceRecipe): CustomFurnaceRecipe {
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

        fun registerSmoking(item: CustomFurnaceRecipe): CustomFurnaceRecipe {
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
