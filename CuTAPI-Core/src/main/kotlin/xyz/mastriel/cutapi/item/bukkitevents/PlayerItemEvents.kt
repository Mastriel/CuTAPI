package xyz.mastriel.cutapi.item.bukkitevents

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
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


    }
}