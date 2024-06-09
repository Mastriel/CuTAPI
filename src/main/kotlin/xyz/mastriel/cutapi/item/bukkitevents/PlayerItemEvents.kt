package xyz.mastriel.cutapi.item.bukkitevents

import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.events.*

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