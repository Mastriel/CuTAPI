package xyz.mastriel.cutapi.items.components

import de.tr7zw.changeme.nbtapi.NBTContainer
import net.kyori.adventure.text.Component
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierMap

/**
 * A class for all Item Components.
 *
 * An Item Component is essentially a small bit of data that can be attached to any [CustomItemStack] in
 * their CustomMaterial's onCreate functions that can alter behavior without having a ton of boilerplate
 * code to do so. An example is shown below:
 *
 */
abstract class ItemComponent(
    override val id: Identifier
) : Identifiable {

    /**
     * The lore which this component shows when applied to a [CustomItemStack].
     */
    open val lore : Component? = null

    /**
     * Called whenever this [ItemComponent] is applied to a [CustomItemStack].
     *
     * Events should be registered here.
     *
     * @param item The [CustomItemStack] involved.
     */
    open fun onApply(item: CustomItemStack) {}

    open val container = NBTContainer()


    companion object : IdentifierMap<ItemComponent>()
}