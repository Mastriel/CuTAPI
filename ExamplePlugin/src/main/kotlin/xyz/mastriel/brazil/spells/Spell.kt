package xyz.mastriel.brazil.spells

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault

interface Spell : Identifiable {
    val name: PersonalizedWithDefault<Component>
    val castTime: Long
    val cooldown: Long
    val flags: Collection<SpellFlag>
    val castMethod: CastMethod

    fun hasFlag(flag: SpellFlag) = flags.contains(flag)

    /**
     * Called when this spell is cast.
     */
    fun onCast(player: Player, item: CuTItemStack, event: PlayerInteractEvent, context: CastContext.FinishCast) = Unit

    /**
     * Called every tick, while this spell is casting.
     */
    fun onCastingTick(player: Player, item: CuTItemStack, context: CastContext.CastTick) = Unit

    /**
     * Called every tick, while this spell is casting.
     */
    fun onStartCast(player: Player, item: CuTItemStack, context: CastContext.StartCast) = Unit

    /**
     * Called when this spell is cancelled. This can be due to many reasons.
     */
    fun onCancel(player: Player, item: CuTItemStack, context: CastContext.Cancel) = Unit
}