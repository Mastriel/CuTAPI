package xyz.mastriel.exampleplugin.components

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.components.MaterialComponent
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin

class DisableOffhand(val message: Component? = "&7&oThis item cannot go in your offhand.".colored) :
    MaterialComponent(id(Plugin, "disable_offhand")) {

    override fun getLore(item: CuTItemStack, viewer: Player): Component {
        return "Main-hand Only".colored
    }

    override fun onOffhandEquip(player: Player, item: CuTItemStack, event: Cancellable) {
        event.isCancelled = true
        if (message != null) player.sendMessage(message)
    }

}