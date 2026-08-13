package xyz.mastriel.cutapi.block.inventory

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import xyz.mastriel.cutapi.block.CuTPlacedTileEntity

public class BlockInventoryHolder internal constructor(
    public val tile: CuTPlacedTileEntity,
    private val logicalInventory: Inventory,
) : InventoryHolder {
    override fun getInventory(): Inventory = logicalInventory
}
