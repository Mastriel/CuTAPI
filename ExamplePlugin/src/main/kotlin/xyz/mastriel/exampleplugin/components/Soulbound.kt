package xyz.mastriel.exampleplugin.components

import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.identifiable
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin

class Soulbound private constructor() : ItemComponent(id) {
    companion object : Identifiable by identifiable(Plugin, "soulbound")

    constructor(player: OfflinePlayer?) : this() {
        this.player = player
    }

    var player by nullablePlayerTag("Owner")

    var enumTest by enumTag("State", State.OFF)
    var radius by intTag("Radius", 10)

    override val lore: Component
        get() {
            if (player == null) {
                return "Soulbound (&b???&r)".colored
            }
            return "Soulbound (&b${player?.name}&r)".colored
        }

    override fun onInteract(item: CuTItemStack, event: PlayerInteractEvent) {
        event.player.sendMessage("&aYou interacted using a Soulbound item!".colored)
        player = event.player

        radius += 1

        val value = enumTest

        if (value == State.ON) enumTest = State.OFF else enumTest = State.ON
    }


}

enum class State {
    ON,
    OFF
}