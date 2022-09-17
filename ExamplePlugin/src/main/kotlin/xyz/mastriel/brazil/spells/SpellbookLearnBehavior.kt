package xyz.mastriel.brazil.spells

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.behaviors.MaterialBehavior
import xyz.mastriel.cutapi.utils.chatTooltip
import xyz.mastriel.cutapi.utils.colored

class SpellbookLearnBehavior(val spell: SpellItem) : MaterialBehavior(spell.id.append("_learn")) {
    override fun onRightClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {
        player.sendMessage("&aYou learned ".colored
            .append(spell.createItemStack(1).handle.chatTooltip)
            .append("&a!".colored)
        )
    }
}