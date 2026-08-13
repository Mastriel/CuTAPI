@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.gui

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.view.*
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.id

public data class GuiCreateContext(
    public val player: Player,
    public val title: Component,
    public val location: Location? = null,
)

public interface GuiType<V : InventoryView> : Identifiable {
    public val layout: GuiLayout
    public val inventoryType: InventoryType?
    public val overlayProfile: GuiOverlayProfile?

    public fun create(context: GuiCreateContext): V

    public companion object : IdentifierRegistry<GuiType<*>>(id("cutapi:registry/gui_type")) {
        public fun Chest(rows: Int): GuiType<InventoryView> = BuiltinGuiTypeCatalog.Chest(rows)

        public val Anvil: GuiType<AnvilView> get() = BuiltinGuiTypeCatalog.Anvil
        public val Barrel: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.Barrel
        public val Beacon: GuiType<BeaconView> get() = BuiltinGuiTypeCatalog.Beacon
        public val BlastFurnace: GuiType<FurnaceView> get() = BuiltinGuiTypeCatalog.BlastFurnace
        public val BrewingStand: GuiType<BrewingStandView> get() = BuiltinGuiTypeCatalog.BrewingStand
        public val CartographyTable: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.CartographyTable
        public val Crafter: GuiType<CrafterView> get() = BuiltinGuiTypeCatalog.Crafter
        public val CraftingTable: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.CraftingTable
        public val Dispenser: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.Dispenser
        public val Dropper: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.Dropper
        public val EnchantingTable: GuiType<EnchantmentView> get() = BuiltinGuiTypeCatalog.EnchantingTable
        public val EnderChest: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.EnderChest
        public val Furnace: GuiType<FurnaceView> get() = BuiltinGuiTypeCatalog.Furnace
        public val Grindstone: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.Grindstone
        public val Hopper: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.Hopper
        public val Lectern: GuiType<LecternView> get() = BuiltinGuiTypeCatalog.Lectern
        public val Loom: GuiType<LoomView> get() = BuiltinGuiTypeCatalog.Loom
        public val Merchant: GuiType<MerchantView> get() = BuiltinGuiTypeCatalog.Merchant
        public val PlayerInventory: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.PlayerInventory
        public val ShulkerBox: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.ShulkerBox
        public val SmithingTable: GuiType<InventoryView> get() = BuiltinGuiTypeCatalog.SmithingTable
        public val Smoker: GuiType<FurnaceView> get() = BuiltinGuiTypeCatalog.Smoker
        public val Stonecutter: GuiType<StonecutterView> get() = BuiltinGuiTypeCatalog.Stonecutter

        internal fun registerBuiltins() {
            BuiltinGuiTypeCatalog.registerBuiltins()
        }
    }
}

internal class MenuGuiType<V : InventoryView>(
    override val id: Identifier,
    override val layout: GuiLayout,
    override val inventoryType: InventoryType?,
    override val overlayProfile: GuiOverlayProfile?,
    private val creator: (GuiCreateContext) -> V,
) : GuiType<V> {
    override fun create(context: GuiCreateContext): V = creator(context)
}
