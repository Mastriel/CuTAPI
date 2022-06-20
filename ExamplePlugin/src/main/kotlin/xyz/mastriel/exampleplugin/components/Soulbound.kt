package xyz.mastriel.exampleplugin.components

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTContainer
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import xyz.mastriel.cutapi.items.components.ComponentSerializer
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin

class Soulbound(var player: OfflinePlayer? = null) : ItemComponent() {

    override val lore : Component get() {
        if (player == null) {
            return "Soulbound (&b???&r)".colored
        }
        return "Soulbound (&b${player?.name}&r)".colored
    }


    companion object : ComponentSerializer<Soulbound>(Soulbound::class, id(Plugin, "soulbound")), Listener {

        private const val UUID_KEY = "OwnerUUID"

        @EventHandler
        fun onObtain(e: CustomItemObtainEvent) {
            val soulbound = e.getComponentOrNull<Soulbound>() ?: return

            if (soulbound.player == null) {
                soulbound.player = e.player
            }
        }


        override fun toCompound(component: Soulbound): NBTCompound {
            val compound = NBTContainer()
            val uuid = component.player?.uniqueId ?: return compound
            compound.setUUID(UUID_KEY, uuid)
            return compound
        }

        override fun fromCompound(compound: NBTCompound): Soulbound {
            if (!compound.hasKey(UUID_KEY)) return Soulbound()
            val uuid = compound.getUUID(UUID_KEY)
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)

            return Soulbound(offlinePlayer)
        }


    }
}