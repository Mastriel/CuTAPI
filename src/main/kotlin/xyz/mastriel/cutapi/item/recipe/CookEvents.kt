package xyz.mastriel.cutapi.item.recipe

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceSmeltEvent
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.registry.toIdentifier

internal class CookingRecipeEvents : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSmelt(event: FurnaceSmeltEvent) {
        val recipe = event.recipe ?: return
        val customRecipe = CustomFurnaceRecipe.getOrNull(recipe.key.toIdentifier()) ?: return
        if (!customRecipe.inputRequirement.withEntity(CuTItemStack.wrap(event.source))) {
            event.isCancelled = true
        }
    }
}
