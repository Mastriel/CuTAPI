package xyz.mastriel.cutapi.items.bukkitevents

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CuTItemStack.Companion.isCustom
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent

internal object PlayerItemEvents : Listener {

    @EventHandler
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        if (!event.item.itemStack.isCustom) return
        val customItem = CuTItemStack(event.item.itemStack)
        val itemObtainEvent = CustomItemObtainEvent(customItem, event.player)
        Bukkit.getServer().pluginManager.callEvent(itemObtainEvent)


    }
}