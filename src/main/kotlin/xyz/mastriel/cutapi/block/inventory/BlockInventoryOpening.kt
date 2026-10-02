package xyz.mastriel.cutapi.block.inventory

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.block.CuTPlacedTileEntity
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.gui.GuiOpenResult

/** Called only for tiles with an enabled inventory presentation. */
internal fun PlayerInteractEvent.handleBlockInventoryInteraction(open: () -> Unit) {
    if (action != Action.RIGHT_CLICK_BLOCK || player.isSneaking) return
    if (useInteractedBlock() == Event.Result.DENY) return

    // Claim the interaction even if a GUI open listener rejects the opening. The held
    // item must not place a block or activate as a fallback, including in the off hand.
    setUseInteractedBlock(Event.Result.DENY)
    setUseItemInHand(Event.Result.DENY)
    if (hand == EquipmentSlot.HAND) open()
}

public fun CuTPlacedTileEntity.openInventory(player: Player): GuiOpenResult {
    val tileDefinition = identity.customTile as? CustomTileEntity<*>
        ?: return GuiOpenResult.Unsupported("Block at $location is not a native custom tile entity.")
    val definition = tileDefinition.descriptor.inventory
        ?: return GuiOpenResult.Unsupported("Custom tile ${tileDefinition.id} does not define an inventory.")
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
