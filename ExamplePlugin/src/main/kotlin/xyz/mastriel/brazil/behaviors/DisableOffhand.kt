package xyz.mastriel.brazil.behaviors

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import xyz.mastriel.brazil.Plugin
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.behaviors.ItemBehavior
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored

class DisableOffhand(val message: Component? = "&7&oThis item cannot go in your offhand.".colored) :
    ItemBehavior(id(Plugin, "disable_offhand")) {

    override fun getLore(item: CuTItemStack, viewer: Player?): Component {
        return "Main-hand Only".colored
    }

    override fun onOffhandEquip(player: Player, item: CuTItemStack, event: Cancellable) {
        event.isCancelled = true
        if (message != null) player.sendMessage(message)
    }

}