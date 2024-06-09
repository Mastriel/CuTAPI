package xyz.mastriel.cutapi.item.behaviors

import net.kyori.adventure.text.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

@RepeatableBehavior
public class StaticLore(public val lore: Component) : ItemBehavior(id(Plugin, "lore_behavior")) {
    override fun getLore(item: CuTItemStack, viewer: Player?): Component {
        return lore
    }
}