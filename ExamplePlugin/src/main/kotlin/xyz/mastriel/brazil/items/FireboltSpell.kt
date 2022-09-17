package xyz.mastriel.brazil.items

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.brazil.Plugin
import xyz.mastriel.brazil.spells.CastContext
import xyz.mastriel.brazil.spells.CastMethod
import xyz.mastriel.brazil.spells.Spell
import xyz.mastriel.brazil.spells.SpellFlag
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored

class FireboltSpell(val damage: Double) : Spell {
    override val name: Component = "Firebolt".colored
    override val castTime: Long = 5L
    override val cooldown: Long = 100L
    override val flags: Collection<SpellFlag> = listOf(SpellFlag.CAST_WHILE_MOVING)
    override val castMethod: CastMethod = CastMethod.RIGHT_CLICK
    override val id: Identifier = id(Plugin, "firebolt")

    override fun onCast(
        player: Player,
        item: CuTItemStack,
        event: PlayerInteractEvent,
        context: CastContext.FinishCast
    ) {

    }
}