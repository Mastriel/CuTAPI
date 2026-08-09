package xyz.mastriel.cutapi.item.bukkitevents

import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.events.*

internal object PlayerItemEvents : Listener {

    @EventHandler
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        val item = event.item.itemStack.wrap()
        val itemObtainEvent = CustomItemObtainEvent(item, event.player)
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
        val item = event.currentItem?.wrap() ?: return
        val itemObtainEvent = CustomItemObtainEvent(item, event.whoClicked as Player)
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
