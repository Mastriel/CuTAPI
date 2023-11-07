package xyz.mastriel.brazil.items

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.brazil.Plugin
import xyz.mastriel.brazil.spells.active.CastContext
import xyz.mastriel.brazil.spells.active.CastMethod
import xyz.mastriel.brazil.spells.active.ActiveSpellItem
import xyz.mastriel.brazil.utils.format
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.customItem
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.ref
import xyz.mastriel.cutapi.utils.chatTooltip
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.personalized

object RedHandsSpellItem : ActiveSpellItem(
    name = personalized("Red Hands".colored),
    material = Material.FIRE_CORAL_FAN,
    castTime = 20L,
    cooldown = 400L,
    flags = emptyList(),
    castMethod = CastMethod.RIGHT_CLICK,
    id = id(Plugin, "red_hands")
) {

    fun getHealingAmount(player: Player): Double {
        // todo make this calculate the contrast of the player's skin to determine the healing amount. hc = more
        // nvm this algorithm is probably racist
        return 5.0
    }

    override fun getLore(item: CuTItemStack, viewer: Player?): List<Component> {
        val healingAmount = viewer?.let { getHealingAmount(it) }?.format("#.#") ?: "5.0"
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


val ItemExample = customItem(id(Plugin, "example_item"), Material.STICK) {
    display {
        name = "&6Example Stick".colored
        texture = ref<Texture2D>("example://items/example_item.png")
    }
}