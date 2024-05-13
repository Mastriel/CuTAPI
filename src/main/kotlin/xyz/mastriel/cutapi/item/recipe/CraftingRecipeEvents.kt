package xyz.mastriel.cutapi.item.recipe

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.inventory.*
import org.bukkit.inventory.meta.Damageable
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.toAgnostic
import xyz.mastriel.cutapi.registry.toIdentifier
import xyz.mastriel.cutapi.utils.playSound
import xyz.mastriel.cutapi.utils.trim

class CraftingRecipeEvents : Listener {

    @EventHandler
    fun onCraftAttempt(e: CraftItemEvent) {
        val recipe = e.recipe
        if (recipe is ShapelessRecipe) {

            val canCraft = testShapelessRecipe(recipe, e.inventory)
            if (!canCraft) {
                e.result = Event.Result.DENY
                return
            }

            // this goes through every ingredient and makes sure it's in the recipe and satisfies all requirements.
            val customRecipe = getCustomShapelessRecipe(recipe) ?: return
            val ingredientsRemaining = customRecipe.ingredients.toMutableList()

            for ((i, item) in e.inventory.matrix.withIndex()) {
                if (item == null) continue
                val ingredient = ingredientsRemaining.find {
                    it.itemRequirement.withEntity(item.toAgnostic()) && item.amount >= it.quantity && item.type == it.material
                }


                if (ingredient == null) {
                    Plugin.warn("Missing vital ingredient in post-craft shapeless ${customRecipe.id}")
                    continue
                }
                ingredientsRemaining.remove(ingredient)
                modifyIngredientPostCraft(ingredient.quantity, ingredient.onCraft, e.inventory, i)
            }
        } else if (recipe is ShapedRecipe) {
            val canCraft = testShapedRecipe(recipe, e.inventory)

            if (!canCraft) {
                e.result = Event.Result.DENY
                return
            }

            // account for multiple item requirement in 1 slot
            for ((i, item) in e.inventory.matrix.withIndex()) {

                val customRecipe = getCustomShapedRecipe(recipe) ?: return
                customRecipe.getIngredientAtIndex(i)?.let {
                    if (item != null) {
                        modifyIngredientPostCraft(it.quantity, it.onCraft, e.inventory, i)
                    }
                }
            }
        }
    }

    private fun modifyIngredientPostCraft(
        quantity: Int,
        onCraft: IngredientCraftContext.() -> Unit,
        inventory: CraftingInventory,
        index: Int
    ) {
        val item = inventory.matrix[index] ?: return
        item.amount -= quantity - 1

        val context = IngredientCraftContext(item.toAgnostic()).apply(onCraft)

        println(context.dontConsume)

        if (context.dontConsume) {
            item.amount += quantity
        }
        if (context.replaceWith != null) {
            inventory.setItem(index, context.replaceWith?.vanilla())
        }
        if (context.lowerDurabilityBy != 0) {
            item.itemMeta = item.itemMeta?.also a@{ meta ->
                if (meta !is Damageable) return@a
                meta.damage += context.lowerDurabilityBy
                if (meta.damage > item.type.maxDurability) {
                    item.amount -= 1
                    inventory.viewers.forEach { entity ->
                        entity.playSound("minecraft:block.anvil.place", 1.0f)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onPrepareCraftShapeless(e: PrepareItemCraftEvent) {
        val recipe = e.recipe as? ShapelessRecipe ?: return

        val canCraft = testShapelessRecipe(recipe, e.inventory)

        val customRecipe: CustomShapelessRecipe? = getCustomShapelessRecipe(recipe)
        if (customRecipe?.result?.isCustom == true) {
            e.inventory.result = customRecipe.result
        }

        if (!canCraft) {
            e.inventory.result = null
        }
    }

    @EventHandler
    fun onPrepareCraftShaped(e: PrepareItemCraftEvent) {
        val recipe = e.recipe as? ShapedRecipe ?: return

        val canCraft = testShapedRecipe(recipe, e.inventory)

        val customRecipe: CustomShapedRecipe? = getCustomShapedRecipe(recipe)
        if (customRecipe?.result?.isCustom == true) {
            e.inventory.result = customRecipe.result
        }

        if (!canCraft) {
            e.inventory.result = null
        }
    }

    private fun getCustomShapelessRecipe(recipe: ShapelessRecipe): CustomShapelessRecipe? {
        return CustomShapelessRecipe.getOrNull(recipe.key.toIdentifier())
    }

    private fun getCustomShapedRecipe(recipe: ShapedRecipe): CustomShapedRecipe? {
        return CustomShapedRecipe.getOrNull(recipe.key.toIdentifier())
    }


    private fun testShapelessRecipe(recipe: ShapelessRecipe, inventory: CraftingInventory): Boolean {
        val customRecipe = getCustomShapelessRecipe(recipe) ?: return true

        val ingredientsRemaining = customRecipe.ingredients.toMutableList()
        var canCraft = true
        for (item in inventory.matrix) {
            if (item == null) continue
            val ingredient = ingredientsRemaining.find {
                it.itemRequirement.withEntity(item.toAgnostic()) && item.amount >= it.quantity && item.type == it.material
            }
            if (ingredient == null) {
                canCraft = false
                break
            }
            ingredientsRemaining.remove(ingredient)
        }
        return canCraft
    }

    private fun testShapedRecipe(recipe: ShapedRecipe, inventory: CraftingInventory): Boolean {
        val customRecipe = getCustomShapedRecipe(recipe) ?: return true


        var canCraft = true
        val size = if (customRecipe.size == CustomShapedRecipe.Size.Four) 2 else 3
        val matrixData: List<ItemStack?> = inventory.matrix.toList().chunked(size).trim { it != null }.flatten()
        val recipeData = customRecipe.getMatrix()

        for ((i, item) in matrixData.withIndex()) {
            if (item == null) continue
            val ingredient = recipeData.getOrNull(i) ?: continue
            val succeeds =
                ingredient.itemRequirement.withEntity(item.toAgnostic()) && item.amount >= ingredient.quantity && item.type == ingredient.material
            if (!succeeds) {
                canCraft = false
                break
            }
        }
        return canCraft
    }

    @EventHandler
    fun recipeBookClick(e: PlayerRecipeBookClickEvent) {
        val recipeKey = e.recipe
        val recipe = Bukkit.getRecipe(recipeKey)
        if (recipe is ShapelessRecipe) {
            val customRecipe = getCustomShapelessRecipe(recipe) ?: return

            if (customRecipe.ingredients.any { it.quantity > 1 }) {
                e.isMakeAll = true
            }
        } else if (recipe is ShapedRecipe) {
            val customRecipe = getCustomShapedRecipe(recipe) ?: return

            if (customRecipe.ingredients.any { it.value.quantity > 1 }) {
                e.isMakeAll = true
            }
        }
    }


    @EventHandler
    fun `smithing table craft event`(ev: PrepareSmithingEvent) {
        val recipe = ev.inventory.recipe as? SmithingRecipe ?: return
        val template = ev.inventory.inputTemplate ?: ItemStack.empty()
        val base = ev.inventory.inputEquipment ?: ItemStack.empty()
        val addition = ev.inventory.inputMineral ?: ItemStack.empty()
        val customRecipe = CustomSmithingTableRecipe.getOrNull(recipe.key.toIdentifier()) ?: return

        if (
            !customRecipe.template.matches(template) ||
            !customRecipe.base.matches(base) ||
            !customRecipe.addition.matches(addition)
        ) {
            ev.inventory.result = null
            return
        }
    }
}