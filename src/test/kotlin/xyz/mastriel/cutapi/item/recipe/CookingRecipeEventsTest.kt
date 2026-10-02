@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.item.recipe

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.inventory.CookingRecipe
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.mockbukkit.mockbukkit.MockBukkit
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.nativeitem.NativeItemTypes
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.computable.computable
import java.util.UUID
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

public class CookingRecipeEventsTest : xyz.mastriel.cutapi.testing.MockBukkitTest() {
    private var previousResolver: ((ItemStack) -> ItemType)? = null

    @BeforeTest
    public fun registerListener() {
        previousResolver = NativeItemTypes.stackResolverForTests
        NativeItemTypes.stackResolverForTests = { it.type.asItemType()!! }
        CookingRecipeFixtures.initialize()
        CookingRecipeFixtures.checkedAmount = 0
        server.pluginManager.registerEvents(CookingRecipeEvents(), MockBukkit.createMockPlugin())
    }

    @AfterTest
    public fun restoreResolver() {
        NativeItemTypes.stackResolverForTests = previousResolver
    }

    @Test
    public fun `all furnace types enforce requirements against the actual source stack`() {
        for (blockType in listOf(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER)) {
            val definition = CookingRecipeFixtures.definitions.getValue(blockType)
            val cooking = CookingRecipeFixtures.cookingRecipe(definition)
            val rejected = event(blockType, ItemStack(Material.IRON_ORE), cooking)
            server.pluginManager.callEvent(rejected)
            assertTrue(rejected.isCancelled, "$blockType must reject a failed requirement")
            assertEquals(1, CookingRecipeFixtures.checkedAmount)
            assertEquals(Material.IRON_ORE, rejected.source.type)
            assertEquals(1, rejected.source.amount)

            val accepted = event(blockType, ItemStack(Material.IRON_ORE, 2), cooking)
            server.pluginManager.callEvent(accepted)
            assertFalse(accepted.isCancelled, "$blockType must allow a passing requirement")
            assertEquals(2, CookingRecipeFixtures.checkedAmount)
        }
    }

    @Test
    public fun `a rejected custom recipe does not block another recipe for the same material`() {
        val unrelated = FurnaceRecipe(
            NamespacedKey("minecraft", "smelt_iron"),
            ItemStack(Material.IRON_INGOT),
            Material.IRON_ORE,
            0.7f,
            200,
        )
        val event = event(Material.FURNACE, ItemStack(Material.IRON_ORE), unrelated)

        server.pluginManager.callEvent(event)

        assertFalse(event.isCancelled)
    }

    @Test
    public fun `cancelled smelts do not evaluate predicates or become uncancelled`() {
        val definition = CookingRecipeFixtures.definitions.getValue(Material.FURNACE)
        val cooking = CookingRecipeFixtures.cookingRecipe(definition)
        val event = event(Material.FURNACE, ItemStack(Material.IRON_ORE), cooking)
        event.isCancelled = true

        server.pluginManager.callEvent(event)

        assertTrue(event.isCancelled)
        assertEquals(0, CookingRecipeFixtures.checkedAmount)
    }

    @Test
    public fun `smelts without a recipe remain unchanged`() {
        val event = event(Material.FURNACE, ItemStack(Material.IRON_ORE), null)

        server.pluginManager.callEvent(event)

        assertFalse(event.isCancelled)
    }

    @Test
    public fun `default requirement accepts the source`() {
        val definition = CookingRecipeFixtures.default
        val cooking = CookingRecipeFixtures.cookingRecipe(definition)
        val event = event(Material.FURNACE, ItemStack(Material.IRON_ORE), cooking)

        server.pluginManager.callEvent(event)

        assertFalse(event.isCancelled)
    }

    private fun event(
        blockType: Material,
        source: ItemStack,
        recipe: CookingRecipe<*>?,
    ): FurnaceSmeltEvent {
        val block = server.addSimpleWorld("world_${UUID.randomUUID()}").getBlockAt(0, 64, 0)
        block.type = blockType
        return FurnaceSmeltEvent(block, source, ItemStack(Material.IRON_INGOT), recipe)
    }
}

private object CookingRecipeFixtures {
    var checkedAmount: Int = 0
    private var initialized: Boolean = false
    private lateinit var cookingRecipes: Map<Identifier, CookingRecipe<*>>
    val definitions: Map<Material, CustomFurnaceRecipe> =
        listOf(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER).associateWith { type ->
            CustomFurnaceRecipe(
                id("test:cooking/${type.name.lowercase()}"),
                Material.IRON_ORE,
                CuTItemStack.wrap(ItemStack(Material.IRON_INGOT)),
                10.seconds,
                inputRequirement = computable {
                    checkedAmount = it.handle.amount
                    it.handle.amount >= 2
                },
            )
        }
    val default: CustomFurnaceRecipe = CustomFurnaceRecipe(
        id("test:cooking/default"),
        Material.IRON_ORE,
        CuTItemStack.wrap(ItemStack(Material.IRON_INGOT)),
        10.seconds,
    )

    fun cookingRecipe(definition: CustomFurnaceRecipe): CookingRecipe<*> = cookingRecipes.getValue(definition.id)

    fun initialize() {
        if (initialized) return
        CustomFurnaceRecipe.modifyRegistry {
            register(definitions.getValue(Material.FURNACE))
            register(default)
        }
        CustomFurnaceRecipe.registerBlasting(definitions.getValue(Material.BLAST_FURNACE))
        CustomFurnaceRecipe.registerSmoking(definitions.getValue(Material.SMOKER))
        CustomFurnaceRecipe.initialize()
        // Retain the registered recipes because each test gets a fresh MockBukkit server.
        cookingRecipes = (definitions.values + default).associate { definition ->
            definition.id to assertIs<CookingRecipe<*>>(Bukkit.getRecipe(definition.id.toNamespacedKey()))
        }
        initialized = true
    }
}
