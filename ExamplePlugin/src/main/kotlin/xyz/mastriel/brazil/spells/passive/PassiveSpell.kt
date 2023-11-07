package xyz.mastriel.brazil.spells.passive

import net.kyori.adventure.text.Component
import org.bukkit.Material
import xyz.mastriel.brazil.spells.active.ActiveSpellFlag
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.resourcetypes.TextureRef

abstract class PassiveSpell(override val id: Identifier) : Identifiable {

    abstract val texture: ResourceRef<Texture2D>
    abstract val name: Component
    abstract val displayMaterial: Material
    abstract val description: List<Component>
    abstract val flags: ActiveSpellFlag
}