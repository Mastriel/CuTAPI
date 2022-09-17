package xyz.mastriel.brazil.items

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.chatTooltip
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.brazil.Plugin
import xyz.mastriel.brazil.spells.CastContext
import xyz.mastriel.brazil.spells.CastMethod
import xyz.mastriel.brazil.spells.SpellItem
import xyz.mastriel.brazil.utils.format

object RedHandsSpellItem : SpellItem(
    name = "Red Hands".colored,
    material = Material.FIRE_CORAL_FAN,
    castTime = 20L,
    cooldown = 400L,
    flags = emptyList(),
    castMethod = CastMethod.RIGHT_CLICK,
    id = id(Plugin, "red_hands")
) {

    fun getHealingAmount(player: Player): Double {
        // todo make this calculate the contrast of the player's skin to determine the healing amount. hc = more
        return 5.0
    }

    override fun getLore(item: CuTItemStack, viewer: Player): List<Component> {
        val healingAmount = getHealingAmount(viewer).format("#.#")
        return listOf(
            "&7Carries you to your spawnpoint.".colored,
            "&7Heals you for &e${healingAmount}hp&7, based on".colored,
            "a &c&omystery statistic&7.".colored
        )
    }

    override fun onCast(
        player: Player,
        item: CuTItemStack,
        event: PlayerInteractEvent,
        context: CastContext.FinishCast
    ) {
        player.health = 0.0
        player.sendMessage(
            "&c&oWhat did you expect using the ".colored
                .append(item.handle.chatTooltip)
                .append("&c&o?".colored)
        )
    }
}