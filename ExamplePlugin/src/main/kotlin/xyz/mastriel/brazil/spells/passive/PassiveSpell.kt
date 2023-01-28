package xyz.mastriel.brazil.spells.passive

import net.kyori.adventure.text.Component
import org.bukkit.Material
import xyz.mastriel.brazil.spells.active.ActiveSpellFlag
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resourcepack.resourcetypes.TextureRef

abstract class PassiveSpell(override val id: Identifier) : Identifiable {

    abstract val texture: TextureRef
    abstract val name: Component
    abstract val displayMaterial: Material
    abstract val description: List<Component>
    abstract val flags: ActiveSpellFlag
}