package xyz.mastriel.cutapi.item.nativeitem

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CompassItem
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.HoeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.MinecartItem
import net.minecraft.world.item.ProjectileItem
import net.minecraft.world.item.ShovelItem
import net.minecraft.world.item.context.UseOnContext

/**
 * Defines the backing-item categories whose behavior can be transferred safely. Component-driven
 * vanilla items use [Item]'s virtual contract and are fully represented by copied components.
 * Block items are also safe when their identity-bound placement behavior is suppressed: CuTAPI's
 * placement attachments own the authoritative use-on-block path. Projectile items are safe when
 * their [ProjectileItem] contract is projected by [NativeProjectileCustomItem]. Supported generic
 * item behavior is forwarded to the backing instance. Types registered with
 * [NativeItemImplementationAdapters] instead retain their concrete vanilla subtype. Other
 * specialized subclasses frequently depend on external identity-keyed tables and remain rejected
 * until a dedicated adapter is implemented.
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
            require(
                backing.javaClass == Item::class.java ||
                    suppressBackingPlacement ||
                    backing is ProjectileItem ||
                    NativeItemImplementationAdapters.hasSubtypeAdapter(backing) ||
                    backing is AxeItem ||
                    backing is DyeItem ||
                    backing is HoeItem ||
                    backing is ShovelItem ||
                    backing is CompassItem ||
                    backing is MinecartItem
            ) {
                val key = BuiltInRegistries.ITEM.getKey(backing)
                "Backing item $key uses unsupported specialized behavior " +
                    "(${backing.javaClass.name}). Add a native implementation adapter before using it."
            }
            return NativeItemBehaviorAdapter(backing, suppressBackingPlacement)
        }
    }
}
