package xyz.mastriel.cutapi.item.nativeitem

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

/**
 * Defines the backing-item categories whose behavior can be transferred safely. Component-driven
 * vanilla items use [Item]'s virtual contract and are fully represented by copied components.
 * Block items are also safe when their identity-bound placement behavior is suppressed: CuTAPI's
 * placement attachments own the authoritative use-on-block path. Other specialized subclasses
 * frequently depend on external identity-keyed tables (for example, dispenser behavior) and remain
 * rejected until a dedicated adapter is implemented for that class.
 */
internal class NativeItemBehaviorAdapter private constructor(
    internal val backing: Item,
    private val suppressBackingPlacement: Boolean,
) {
    fun useOn(context: UseOnContext): InteractionResult =
        if (suppressBackingPlacement) InteractionResult.PASS else backing.useOn(context)

    companion object {
        fun create(backing: Item): NativeItemBehaviorAdapter {
            val suppressBackingPlacement = backing is BlockItem
            require(backing.javaClass == Item::class.java || suppressBackingPlacement) {
                val key = BuiltInRegistries.ITEM.getKey(backing)
                "Backing item $key uses unsupported specialized behavior " +
                    "(${backing.javaClass.name}). Add a NativeItemBehaviorAdapter before using it."
            }
            return NativeItemBehaviorAdapter(backing, suppressBackingPlacement)
        }
    }
}
