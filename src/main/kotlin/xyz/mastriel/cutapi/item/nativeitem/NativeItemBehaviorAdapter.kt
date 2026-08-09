package xyz.mastriel.cutapi.item.nativeitem

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item

/**
 * Defines the backing-item categories whose behavior can be transferred safely. Component-driven
 * vanilla items use [Item]'s virtual contract and are fully represented by copied components.
 * Specialized subclasses frequently depend on external identity-keyed tables (for example,
 * dispenser behavior) and are rejected until a dedicated adapter is implemented for that class.
 */
internal class NativeItemBehaviorAdapter private constructor(internal val backing: Item) {
    companion object {
        fun create(backing: Item): NativeItemBehaviorAdapter {
            require(backing.javaClass == Item::class.java) {
                val key = BuiltInRegistries.ITEM.getKey(backing)
                "Backing item $key uses unsupported specialized behavior " +
                    "(${backing.javaClass.name}). Add a NativeItemBehaviorAdapter before using it."
            }
            return NativeItemBehaviorAdapter(backing)
        }
    }
}
