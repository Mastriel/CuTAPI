package xyz.mastriel.cutapi.item.behaviors

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.behavior.RepeatableBehavior
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.registry.id

@RepeatableBehavior
class StaticLore(val lore: Component) : ItemBehavior(id(Plugin, "lore_behavior")) {
    override fun getLore(item: CuTItemStack, viewer: Player?): Component {
        return lore
    }
}