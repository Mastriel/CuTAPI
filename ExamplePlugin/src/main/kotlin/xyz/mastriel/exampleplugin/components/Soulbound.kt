package xyz.mastriel.exampleplugin.components

import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.identifiable
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin

class Soulbound : ItemComponent(id) {
    companion object : Identifiable by identifiable(Plugin, "soulbound")


    val player by nullablePlayerTag("Owner", null)


    override val lore : Component get() {
        if (player == null) {
            return "Soulbound (&b???&r)".colored
        }
        return "Soulbound (&b${player?.name}&r)".colored
    }
}