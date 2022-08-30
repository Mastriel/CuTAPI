package xyz.mastriel.cutapi.items.events

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CuTItemStack.Companion.isCustom

class CustomItemEvents : Listener {

    @EventHandler
    fun onPickupItem(e: EntityPickupItemEvent) {
        val player = e.entity as? Player ?: return
        val itemStack = e.item.itemStack

        if (!itemStack.isCustom) return
        val customItem = CuTItemStack(itemStack)
        val event = CustomItemObtainEvent(customItem, player)

        Bukkit.getPluginManager().callEvent(event)
    }
}