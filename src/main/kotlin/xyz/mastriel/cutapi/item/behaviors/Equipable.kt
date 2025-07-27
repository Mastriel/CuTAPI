package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*

@RepeatableBehavior
public class Equipable private constructor(
    private val slot: EquipmentSlot,
    private val isSwappable: Boolean,
    private val model: ResourceRef<Model3D>?,
    private var damageItemWhenHurt: Boolean
) : ItemBehavior(id(Plugin, "equipable")) {

    public class Builder internal constructor(public val slot: EquipmentSlot) {
        public var isSwappable: Boolean = true
        public var model: ResourceRef<Model3D>? = null
        public var damageItemWhenHurt: Boolean = false
    }

    @Suppress("UnstableApiUsage")
    override fun onCreate(item: CuTItemStack) {
        item.handle.editMeta { meta ->
            meta.setEquippable(meta.equippable.also {
                it.slot = slot
                it.model = model?.getResource()?.getItemModel()?.toIdentifier()?.toNamespacedKey()
                it.isDamageOnHurt = damageItemWhenHurt
            })
        }
    }

    public companion object {
        public fun of(slot: EquipmentSlot, builder: Builder.() -> Unit): Equipable {
            val b = Builder(slot).apply(builder)
            return Equipable(
                slot,
                b.isSwappable,
                b.model,
                b.damageItemWhenHurt
            )
        }

        public fun head(builder: Builder.() -> Unit): Equipable = of(
            EquipmentSlot.HEAD,
            builder
        )

        public fun chest(builder: Builder.() -> Unit): Equipable = of(
            EquipmentSlot.CHEST,
            builder
        )

        public fun legs(builder: Builder.() -> Unit): Equipable = of(
            EquipmentSlot.LEGS,
            builder
        )

        public fun feet(builder: Builder.() -> Unit): Equipable = of(
            EquipmentSlot.FEET,
            builder
        )
    }
}
