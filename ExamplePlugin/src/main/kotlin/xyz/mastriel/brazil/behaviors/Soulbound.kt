package xyz.mastriel.brazil.behaviors

import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.behaviors.MaterialBehavior
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.brazil.Plugin

class Soulbound : MaterialBehavior(id(Plugin, "soulbound")) {

    private val ownerKey = "Owner"

    override fun getLore(item: CuTItemStack, viewer: Player): Component {
        val owner = getOwner(item)?.name ?: "???"
        return "Soulbound (${owner})".colored
    }

    fun setOwner(item: CuTItemStack, player: OfflinePlayer) {
        val data = getData(item)
        data.setPlayer(ownerKey, player)
    }

    fun getOwner(item: CuTItemStack) : OfflinePlayer? {
        val data = getData(item)
        return data.getPlayer(ownerKey)
    }

    override fun onObtain(player: Player, item: CuTItemStack, event: CustomItemObtainEvent) {
        val owner = getOwner(item)
        if (owner != null && player.uniqueId != owner.uniqueId) {
            event.isCancelled = true
            return
        }
        setOwner(item, player)
    }

}