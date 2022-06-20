package xyz.mastriel.cutapi.items.components

import net.kyori.adventure.text.Component
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier

/**
 * A class for all Item Components.
 *
 * An Item Component is essentially a small bit of data that can be attached to any [CustomItemStack] in
 * their CustomMaterial's onCreate functions that can alter behavior without having a ton of boilerplate
 * code to do so. An example is shown below:
 *
 * See the Example Plugin for examples.
 */
abstract class ItemComponent : Identifiable {

    /**
     * The identifier, which is automatically fetched from the [ComponentSerializer] associated with this component.
     */
    override val id: Identifier
        get() = ComponentSerializer.kclassToId[this::class] ?: error("Item component serializer for ${this::class} not found!")

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

}