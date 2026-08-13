package xyz.mastriel.cutapi.block.inventory

import org.bukkit.entity.Player
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.block.CuTPlacedTileEntity
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.gui.GuiOpenResult

public fun CuTPlacedTileEntity.openInventory(player: Player): GuiOpenResult {
    val definition = (type as? CustomTileEntity<*>)?.descriptor?.inventory
        ?: return GuiOpenResult.Unsupported("Custom tile ${type.id} does not define an inventory.")
    val presentation = definition.presentation
        ?: return GuiOpenResult.Unsupported("Block inventory ${definition.id} does not define a GUI presentation.")
    val inventory = inventoryOrNull
        ?: return GuiOpenResult.Unsupported("Block inventory ${definition.id} is unavailable.")
    val failure = inventory.failureOrNull
    if (failure != null) {
        return GuiOpenResult.Unsupported("Block inventory ${definition.id} contains malformed data: ${failure.message}")
    }
    return CuTAPI.guiManager.open(player, this, presentation)
}
