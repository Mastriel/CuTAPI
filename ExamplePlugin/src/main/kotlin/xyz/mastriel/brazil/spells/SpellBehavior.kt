package xyz.mastriel.brazil.spells

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.behaviors.ItemBehavior

/**
 * Creates a behavior based on the inputted spell.
 */
class SpellBehavior(val spell: Spell) : ItemBehavior(spell.id) {

    override fun getLore(item: CuTItemStack, viewer: Player): Component? {
        return null
    }
}