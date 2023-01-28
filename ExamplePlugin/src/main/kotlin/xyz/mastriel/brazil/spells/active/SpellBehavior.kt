package xyz.mastriel.brazil.spells.active

import com.github.shynixn.mccoroutine.bukkit.launch
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.brazil.Plugin
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior

/**
 * Creates a behavior based on the inputted spell.
 */
class SpellBehavior(val activeSpell: ActiveSpell, val castMethod: CastMethod) : ItemBehavior(activeSpell.id) {

    override fun getLore(item: CuTItemStack, viewer: Player?): Component? {
        return null
    }

    override fun onLeftClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {
        if (castMethod == CastMethod.LEFT_CLICK) startCast(player, item)
    }

    fun startCast(player: Player, item: CuTItemStack) {
        Plugin.launch {
            activeSpell.onStartCast(player, item, CastContext.StartCast())

        }
    }


    companion object {
        private val casting = hashMapOf<Player, ActiveSpell>()

        fun Player.getCasting() : ActiveSpell? {
            return casting[this]
        }
        fun Player.isCasting() = getCasting() != null

        fun Player.cancelCast() : Boolean {
            TODO()
        }


    }
}