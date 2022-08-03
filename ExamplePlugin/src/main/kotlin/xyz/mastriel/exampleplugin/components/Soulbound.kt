package xyz.mastriel.exampleplugin.components

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTContainer
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin

class Soulbound(var player: OfflinePlayer? = null) : ItemComponent(id(Plugin, "soulbound")) {


    override val lore : Component get() {
        if (player == null) {
            return "Soulbound (&b???&r)".colored
        }
        return "Soulbound (&b${player?.name}&r)".colored
    }


    companion object : Listener {

        private const val UUID_KEY = "OwnerUUID"

        @EventHandler
        fun onObtain(e: CustomItemObtainEvent) {
            val soulbound = e.getComponentOrNull<Soulbound>() ?: return

            if (soulbound.player == null) {
                soulbound.player = e.player
            }
        }
    }
}