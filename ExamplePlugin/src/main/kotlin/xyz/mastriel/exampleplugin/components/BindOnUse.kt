package xyz.mastriel.exampleplugin.components

import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.items.components.removeComponent
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.identifiable
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin

class BindOnUse : ItemComponent(id) {
    companion object : Identifiable by identifiable(Plugin, "bind_on_use")

    override fun getLore(cuTItemStack: CuTItemStack, viewer: Player): Component {
        return "Bind On Use".colored
    }

    override fun onInteract(item: CuTItemStack, event: PlayerInteractEvent) {
        event.player.sendMessage("&aYou interacted with a BindOnUse item!".colored)
        item.addComponent(Soulbound(event.player))
        item.removeComponent<BindOnUse>()
    }


}