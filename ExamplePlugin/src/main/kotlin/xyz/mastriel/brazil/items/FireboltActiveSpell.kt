package xyz.mastriel.brazil.items

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.brazil.Plugin
import xyz.mastriel.brazil.spells.active.CastContext
import xyz.mastriel.brazil.spells.active.CastMethod
import xyz.mastriel.brazil.spells.active.ActiveSpell
import xyz.mastriel.brazil.spells.active.ActiveSpellFlag
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import xyz.mastriel.cutapi.utils.personalized.personalized

class FireboltActiveSpell(val damage: Double) : ActiveSpell {
    override val name: PersonalizedWithDefault<Component> = personalized("Firebolt".colored)
    override val castTime: Long = 5L
    override val cooldown: Long = 100L
    override val flags: Collection<ActiveSpellFlag> = listOf(ActiveSpellFlag.CAST_WHILE_MOVING)
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