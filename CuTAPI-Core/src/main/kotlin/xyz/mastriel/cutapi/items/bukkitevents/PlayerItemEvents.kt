package xyz.mastriel.cutapi.items.bukkitevents

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.utils.updateCustomItems

internal object PlayerItemEvents : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        event.player.updateCustomItems()
    }

    @EventHandler
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        val customItem = CustomItemStack.fromVanillaOrNull(event.item.itemStack) ?: return
        val itemObtainEvent = CustomItemObtainEvent(customItem, event.player)
        Bukkit.getServer().pluginManager.callEvent(itemObtainEvent)

        event.item.itemStack = customItem.toBukkitItemStack()
    }
}