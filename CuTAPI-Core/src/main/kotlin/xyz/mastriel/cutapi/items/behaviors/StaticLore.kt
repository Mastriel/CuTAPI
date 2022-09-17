package xyz.mastriel.cutapi.items.behaviors

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.registry.id

@RepeatableBehavior
class StaticLore(val lore: Component) : MaterialBehavior(id(Plugin, "lore_behavior")) {
    override fun getLore(item: CuTItemStack, viewer: Player): Component {
        return lore
    }
}