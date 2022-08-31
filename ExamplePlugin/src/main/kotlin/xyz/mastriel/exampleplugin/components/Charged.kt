package xyz.mastriel.exampleplugin.components

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.identifiable
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin
import java.util.function.Consumer


// Prevents interactions when the value is 0 or lower.
class Charged private constructor() : ItemComponent(id) {
    companion object : Identifiable by identifiable(Plugin, "charge")

    constructor(value: Int) : this() {
        this.value = value
    }

    private var value by intTag("", 10)

    fun get() : Int = value
    fun set(value: Int) { this.value = value }
    fun add(value: Int) { this.value += value }
    fun subtract(value: Int) { this.value -= value }


    override fun onInteract(item: CuTItemStack, event: PlayerInteractEvent) {
        val isUsed = event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR
                || event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK

        if (isUsed) {
            if (get() <= 0) {
                event.player.sendMessage("&cThis item is out of charge!")
                event.isCancelled = true
            }
        }
    }

    override fun getLore(cuTItemStack: CuTItemStack, viewer: Player): Component {
        return "Charged ($value)".colored
    }
}