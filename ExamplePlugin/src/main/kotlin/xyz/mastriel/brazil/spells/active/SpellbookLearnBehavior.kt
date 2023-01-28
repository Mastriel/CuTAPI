package xyz.mastriel.brazil.spells.active

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.utils.chatTooltip
import xyz.mastriel.cutapi.utils.colored

class SpellbookLearnBehavior(val spell: ActiveSpellItem) : ItemBehavior(spell.id.appendSubId("learn")) {
    override fun onRightClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {
        player.sendMessage("&aYou learned ".colored
            .append(spell.createItemStack(1).handle.chatTooltip)
            .append("&a!".colored)
        )
    }
}