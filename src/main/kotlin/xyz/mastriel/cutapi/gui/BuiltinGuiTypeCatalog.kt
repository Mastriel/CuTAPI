@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.gui

import org.bukkit.event.inventory.*
import org.bukkit.inventory.*
import org.bukkit.inventory.view.*
import xyz.mastriel.cutapi.registry.*

internal object BuiltinGuiTypeCatalog {
    private val chestTypes: Map<Int, GuiType<InventoryView>> by lazy {
        (1..6).associateWith(::createChest)
    }

    private fun layout(
        size: Int,
        columns: Int,
        rows: Int,
        vararg roles: Pair<GuiSlotRole, Collection<Int>>,
    ): GuiLayout = GuiLayout(size, columns, rows, mapOf(*roles))

    private fun <V : InventoryView> menu(
        key: String,
        menu: MenuType.Typed<V, *>,
        inventoryType: InventoryType,
        layout: GuiLayout,
        profile: GuiOverlayProfile,
    ): GuiType<V> = MenuGuiType(
        id = id("cutapi:gui_type/$key"),
        layout = layout,
        inventoryType = inventoryType,
        overlayProfile = profile,
    ) { context -> menu.create(context.player, context.title) }

    public fun Chest(rows: Int): GuiType<InventoryView> {
        require(rows in 1..6) { "Chest rows must be between 1 and 6, found $rows." }
        return chestTypes.getValue(rows)
    }

    private fun createChest(rows: Int): GuiType<InventoryView> {
        val menu = when (rows) {
            1 -> MenuType.GENERIC_9X1
            2 -> MenuType.GENERIC_9X2
            3 -> MenuType.GENERIC_9X3
            4 -> MenuType.GENERIC_9X4
            5 -> MenuType.GENERIC_9X5
            else -> MenuType.GENERIC_9X6
        }
        return menu("chest_$rows", menu, InventoryType.CHEST, GuiLayout.grid(rows), GuiOverlayProfile.chest(rows))
    }

    private val ThreeByThree: GuiLayout = GuiLayout.grid(rows = 3, columns = 3)

    public val Anvil: GuiType<AnvilView> = menu(
        "anvil", MenuType.ANVIL, InventoryType.ANVIL,
        layout(3, 3, 1, GuiSlotRole.Input to listOf(0, 1), GuiSlotRole.Result to listOf(2)),
        GuiOverlayProfile.Anvil,
    )
    public val Barrel: GuiType<InventoryView> = menu(
        "barrel", MenuType.GENERIC_9X3, InventoryType.BARREL, GuiLayout.grid(3), GuiOverlayProfile.Barrel,
    )
    public val Beacon: GuiType<BeaconView> = menu(
        "beacon", MenuType.BEACON, InventoryType.BEACON,
        layout(1, 1, 1, GuiSlotRole.Payment to listOf(0)), GuiOverlayProfile.Beacon,
    )
    public val BlastFurnace: GuiType<FurnaceView> = menu(
        "blast_furnace", MenuType.BLAST_FURNACE, InventoryType.BLAST_FURNACE,
        furnaceLayout(), GuiOverlayProfile.BlastFurnace,
    )
    public val BrewingStand: GuiType<BrewingStandView> = menu(
        "brewing_stand", MenuType.BREWING_STAND, InventoryType.BREWING,
        layout(
            5, 5, 1,
            GuiSlotRole.Bottle to listOf(0, 1, 2),
            GuiSlotRole.Ingredient to listOf(3),
            GuiSlotRole.Fuel to listOf(4),
        ),
        GuiOverlayProfile.BrewingStand,
    )
    public val CartographyTable: GuiType<InventoryView> = menu(
        "cartography_table", MenuType.CARTOGRAPHY_TABLE, InventoryType.CARTOGRAPHY,
        layout(3, 3, 1, GuiSlotRole.Input to listOf(0, 1), GuiSlotRole.Result to listOf(2)),
        GuiOverlayProfile.CartographyTable,
    )
    public val Crafter: GuiType<CrafterView> = menu(
        "crafter", MenuType.CRAFTER_3X3, InventoryType.CRAFTER,
        layout(10, 3, 4, GuiSlotRole.Input to (0..8).toList(), GuiSlotRole.Result to listOf(9)),
        GuiOverlayProfile.Crafter,
    )
    public val CraftingTable: GuiType<InventoryView> = menu(
        "crafting_table", MenuType.CRAFTING, InventoryType.WORKBENCH,
        layout(10, 3, 4, GuiSlotRole.Result to listOf(0), GuiSlotRole.Input to (1..9).toList()),
        GuiOverlayProfile.CraftingTable,
    )
    public val Dispenser: GuiType<InventoryView> = menu(
        "dispenser", MenuType.GENERIC_3X3, InventoryType.DISPENSER, ThreeByThree, GuiOverlayProfile.Dispenser,
    )
    public val Dropper: GuiType<InventoryView> = menu(
        "dropper", MenuType.GENERIC_3X3, InventoryType.DROPPER, ThreeByThree, GuiOverlayProfile.Dropper,
    )
    public val EnchantingTable: GuiType<EnchantmentView> = menu(
        "enchanting_table", MenuType.ENCHANTMENT, InventoryType.ENCHANTING,
        layout(2, 2, 1, GuiSlotRole.Input to listOf(0), GuiSlotRole.Ingredient to listOf(1)),
        GuiOverlayProfile.EnchantingTable,
    )
    public val EnderChest: GuiType<InventoryView> = menu(
        "ender_chest", MenuType.GENERIC_9X3, InventoryType.ENDER_CHEST, GuiLayout.grid(3), GuiOverlayProfile.EnderChest,
    )
    public val Furnace: GuiType<FurnaceView> = menu(
        "furnace", MenuType.FURNACE, InventoryType.FURNACE, furnaceLayout(), GuiOverlayProfile.Furnace,
    )
    public val Grindstone: GuiType<InventoryView> = menu(
        "grindstone", MenuType.GRINDSTONE, InventoryType.GRINDSTONE,
        layout(3, 3, 1, GuiSlotRole.Input to listOf(0, 1), GuiSlotRole.Result to listOf(2)),
        GuiOverlayProfile.Grindstone,
    )
    public val Hopper: GuiType<InventoryView> = menu(
        "hopper",
        MenuType.HOPPER,
        InventoryType.HOPPER,
        GuiLayout.grid(rows = 1, columns = 5),
        GuiOverlayProfile.Hopper,
    )
    public val Lectern: GuiType<LecternView> = menu(
        "lectern", MenuType.LECTERN, InventoryType.LECTERN,
        layout(1, 1, 1, GuiSlotRole.Book to listOf(0)), GuiOverlayProfile.Lectern,
    )
    public val Loom: GuiType<LoomView> = menu(
        "loom", MenuType.LOOM, InventoryType.LOOM,
        layout(4, 4, 1, GuiSlotRole.Input to listOf(0, 1, 2), GuiSlotRole.Result to listOf(3)),
        GuiOverlayProfile.Loom,
    )
    public val Merchant: GuiType<MerchantView> = menu(
        "merchant", MenuType.MERCHANT, InventoryType.MERCHANT,
        layout(3, 3, 1, GuiSlotRole.Payment to listOf(0, 1), GuiSlotRole.Result to listOf(2)),
        GuiOverlayProfile.Merchant,
    )
    public val PlayerInventory: GuiType<InventoryView> = menu(
        "player_inventory", MenuType.GENERIC_9X4, InventoryType.PLAYER,
        GuiLayout.grid(4), GuiOverlayProfile.PlayerInventory,
    )
    public val ShulkerBox: GuiType<InventoryView> = menu(
        "shulker_box", MenuType.SHULKER_BOX, InventoryType.SHULKER_BOX,
        GuiLayout.grid(3), GuiOverlayProfile.ShulkerBox,
    )
    public val SmithingTable: GuiType<InventoryView> = menu(
        "smithing_table", MenuType.SMITHING, InventoryType.SMITHING,
        layout(4, 4, 1, GuiSlotRole.Input to listOf(0, 1, 2), GuiSlotRole.Result to listOf(3)),
        GuiOverlayProfile.SmithingTable,
    )
    public val Smoker: GuiType<FurnaceView> = menu(
        "smoker", MenuType.SMOKER, InventoryType.SMOKER, furnaceLayout(), GuiOverlayProfile.Smoker,
    )
    public val Stonecutter: GuiType<StonecutterView> = menu(
        "stonecutter", MenuType.STONECUTTER, InventoryType.STONECUTTER,
        layout(2, 2, 1, GuiSlotRole.Input to listOf(0), GuiSlotRole.Result to listOf(1)),
        GuiOverlayProfile.Stonecutter,
    )

    private fun furnaceLayout(): GuiLayout = layout(
        3, 3, 1,
        GuiSlotRole.Input to listOf(0),
        GuiSlotRole.Fuel to listOf(1),
        GuiSlotRole.Result to listOf(2),
    )

    internal fun registerBuiltins() {
        val types = listOf(
            Anvil, Barrel, Beacon, BlastFurnace, BrewingStand, CartographyTable, Crafter,
            CraftingTable, Dispenser, Dropper, EnchantingTable, EnderChest, Furnace, Grindstone,
            Hopper, Lectern, Loom, Merchant, PlayerInventory, ShulkerBox, SmithingTable, Smoker,
            Stonecutter,
        ) + (1..6).map(::Chest)
        GuiType.modifyRegistry {
            types.forEach { type -> if (!GuiType.has(type.id)) register(type) }
        }
    }
}
