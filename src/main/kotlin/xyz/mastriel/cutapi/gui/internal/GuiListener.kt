package xyz.mastriel.cutapi.gui.internal

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.inventory.PrepareGrindstoneEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.event.player.PlayerQuitEvent
import xyz.mastriel.cutapi.gui.GuiCloseReason
import xyz.mastriel.cutapi.gui.GuiManager

internal class GuiListener(
    private val manager: GuiManager,
) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onClick(event: InventoryClickEvent) {
        manager.handleClick(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDrag(event: InventoryDragEvent) {
        manager.handleDrag(event)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onClose(event: InventoryCloseEvent) {
        manager.handleNaturalClose(event.player as? org.bukkit.entity.Player ?: return, event.view)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        manager.activeSession(event.player)?.close(GuiCloseReason.Quit)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPrepareAnvil(event: PrepareAnvilEvent) {
        manager.handleInventoryEvent(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPrepareSmithing(event: PrepareSmithingEvent) {
        manager.handleInventoryEvent(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPrepareGrindstone(event: PrepareGrindstoneEvent) {
        manager.handleInventoryEvent(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPrepareCrafting(event: PrepareItemCraftEvent) {
        manager.handleInventoryEvent(event)
    }
}
