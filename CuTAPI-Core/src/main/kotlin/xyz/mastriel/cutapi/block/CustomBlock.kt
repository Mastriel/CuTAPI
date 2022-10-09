package xyz.mastriel.cutapi.block

import org.bukkit.Material
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.block.behaviors.BlockBehavior
import xyz.mastriel.cutapi.items.CustomItem
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import kotlin.reflect.KClass

class CustomBlock(
    override val id: Identifier,
    val type: Material,
    val item: CustomItem
) : Identifiable {

}