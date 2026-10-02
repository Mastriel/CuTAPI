package xyz.mastriel.cutapi.item.recipe

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class ItemProductionCatalogTest : MockBukkitTest() {
    @Test
    public fun `higher priority source replaces lower fidelity recipe`() {
        val recipeId = id("test:recipe")
        val lower = recipe(recipeId, ItemType.STONE, ItemType.STICK)
        val higher = recipe(recipeId, ItemType.DIAMOND, ItemType.STICK)

        val snapshot = ItemProductionCatalog.snapshot(
            listOf(
                source("test:low", RegistryPriority.Low, lower),
                source("test:high", RegistryPriority.High, higher),
            ),
        )

        assertEquals(listOf(higher), snapshot.recipes)
    }

    @Test
    public fun `same priority duplicate is rejected`() {
        val recipeId = id("test:duplicate")

        assertFailsWith<IllegalStateException> {
            ItemProductionCatalog.snapshot(
                listOf(
                    source("test:first", RegistryPriority.Medium, recipe(recipeId, ItemType.STONE, ItemType.STICK)),
                    source("test:second", RegistryPriority.Medium, recipe(recipeId, ItemType.DIRT, ItemType.STICK)),
                ),
            )
        }
    }

    @Test
    public fun `snapshot indexes every recipe by output identity`() {
        val stick = ItemType.STICK.asIdentity()
        val first = recipe(id("test:first"), ItemType.STONE, ItemType.STICK)
        val second = recipe(id("test:second"), ItemType.DIRT, ItemType.STICK)

        val snapshot = ItemProductionCatalog.snapshot(
            listOf(source("test:source", RegistryPriority.Medium, first, second)),
        )

        assertEquals(listOf(first, second), snapshot.recipesByOutput.getValue(stick))
        assertSame(
            first.primaryOutput.identity,
            snapshot.recipesByOutput.getValue(stick).first().primaryOutput.identity
        )
    }

    @Test
    public fun `production quantities must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            ItemProductionChoice(ItemType.STONE.asIdentity(), amount = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ItemProductionOutput(ItemType.STICK.asIdentity(), amount = 0)
        }
    }

    @Test
    public fun `Bukkit adapter preserves alternatives output amounts and returned containers`() {
        val recipe = ShapelessRecipe(
            NamespacedKey("test", "container_recipe"),
            ItemStack(Material.CAKE, 2),
        ).apply {
            addIngredient(RecipeChoice.MaterialChoice(Material.MILK_BUCKET, Material.HONEY_BOTTLE))
        }

        val production = requireNotNull(
            recipe.toItemProductionRecipe { type ->
                when (type) {
                    ItemType.MILK_BUCKET -> ItemType.BUCKET
                    ItemType.HONEY_BOTTLE -> ItemType.GLASS_BOTTLE
                    else -> null
                }
            },
        )

        assertEquals(ItemProductionType.CraftingShapeless, production.kind)
        assertEquals(2, production.primaryOutput.amount)
        assertEquals(
            setOf(ItemType.MILK_BUCKET.asIdentity(), ItemType.HONEY_BOTTLE.asIdentity()),
            production.ingredients.single().choices.map { it.identity }.toSet(),
        )
        assertEquals(
            setOf(ItemType.BUCKET.asIdentity(), ItemType.GLASS_BOTTLE.asIdentity()),
            production.ingredients.single().choices.flatMap { choice ->
                choice.returnedItems.map { it.identity }
            }.toSet(),
        )
    }

    private fun recipe(id: Identifier, input: ItemType, output: ItemType): ItemProductionRecipe =
        ItemProductionRecipe(
            id,
            ItemProductionType.CraftingShapeless,
            listOf(ItemProductionIngredient(setOf(ItemProductionChoice(input.asIdentity())))),
            ItemProductionOutput(output.asIdentity(), 1),
        )

    private fun source(
        sourceId: String,
        sourcePriority: RegistryPriority,
        vararg recipes: ItemProductionRecipe,
    ): ItemProductionSource = object : ItemProductionSource {
        override val id: Identifier = id(sourceId)
        override val priority: RegistryPriority = sourcePriority
        override fun productions(): Sequence<ItemProductionRecipe> = recipes.asSequence()
    }
}
