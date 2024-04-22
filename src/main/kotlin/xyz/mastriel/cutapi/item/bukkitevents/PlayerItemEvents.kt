package xyz.mastriel.cutapi.item.bukkitevents

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.inventory.PlayerInventory
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.events.CustomItemObtainEvent

internal object PlayerItemEvents : Listener {

    @EventHandler
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        if (!event.item.itemStack.isCustom) return
        val customItem = event.item.itemStack.wrap()!!
        val itemObtainEvent = CustomItemObtainEvent(customItem, event.player)
        Bukkit.getServer().pluginManager.callEvent(itemObtainEvent)

        if (itemObtainEvent.isCancelled) {
            event.isCancelled = true
        }

        if (itemObtainEvent.destroyItem) {
            event.isCancelled = true
            event.item.remove()
        }
    }

    @EventHandler
    fun onPickup(event: InventoryClickEvent) {
        if (event.clickedInventory is PlayerInventory) return
        val customItem = event.currentItem?.wrap() ?: return
        val itemObtainEvent = CustomItemObtainEvent(customItem, event.whoClicked as Player)
        Bukkit.getServer().pluginManager.callEvent(itemObtainEvent)

        if (itemObtainEvent.isCancelled) {
            event.isCancelled = true
        }

        if (itemObtainEvent.destroyItem) {
            event.isCancelled = true
            event.currentItem = null
        }
    }
}