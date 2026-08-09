package xyz.mastriel.cutapi.item.attachments

import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*

public data class Equipable(
    public val slot: EquipmentSlot,
    public val isSwappable: Boolean = true,
    public val model: ResourceRef<Model3D>? = null,
    public var damageItemWhenHurt: Boolean = false
) : ItemAttachment {

    public class Builder internal constructor(public val slot: EquipmentSlot) {
        public var isSwappable: Boolean = true
        public var model: ResourceRef<Model3D>? = null
        public var damageItemWhenHurt: Boolean = false
    }

    public companion object : Schema<Equipable> by schema(id(Plugin, "equipable"), {
        property(Equipable::slot, VariantSerializer.Enum<EquipmentSlot>())
        property(Equipable::isSwappable, VariantSerializer.Boolean, name = "is_swappable")
        property(Equipable::model, VariantSerializer.ResourceRef<Model3D>().nullable())
        property(
            Equipable::damageItemWhenHurt,
            VariantSerializer.Boolean,
            name = "damage_item_when_hurt"
        )
    }) {
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

@Suppress("UnstableApiUsage")
internal val EquipableMaterializer: ItemAttachmentMaterializer<Equipable>
    get() = itemAttachmentMaterializer(
    id = Equipable.id / "materializer",
    schema = Equipable,
    revision = 1,
    claims = setOf(ItemTraitClaim.Component(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE)),
) { context, output ->
    val attachment = context.attachments.single()
    output.setUsing(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE) { stack ->
        stack.editMeta { meta ->
            meta.setEquippable(meta.equippable.also {
                it.slot = attachment.slot
                it.isSwappable = attachment.isSwappable
                it.model = attachment.model?.getResource()?.getItemModel()?.toIdentifier()?.toNamespacedKey()
                it.isDamageOnHurt = attachment.damageItemWhenHurt
            })
        }
    }
}
