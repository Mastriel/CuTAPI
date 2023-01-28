package xyz.mastriel.cutapi.item.events

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap

class CustomItemEvents : Listener {

    @EventHandler
    fun onPickupItem(e: EntityPickupItemEvent) {
        val player = e.entity as? Player ?: return
        val itemStack = e.item.itemStack

        if (!itemStack.isCustom) return
        val customItem = itemStack.wrap()!!
        val event = CustomItemObtainEvent(customItem, player)

        Bukkit.getPluginManager().callEvent(event)
    }

}