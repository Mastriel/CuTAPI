package xyz.mastriel.cutapi.item.attachments

import org.bukkit.attribute.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import org.bukkit.Registry as BukkitRegistry

@Suppress("UnstableApiUsage")
@RepeatableAttachment
public data class ModifyAttribute(
    public val key: Identifier,
    public val slotGroup: EquipmentSlotGroup,
    public val attribute: Attribute,
    public val amount: Double,
    public val operation: AttributeModifier.Operation = AttributeModifier.Operation.ADD_NUMBER
) : ItemAttachment {
    public companion object : Schema<ModifyAttribute> by schema(id(Plugin, "attribute"), {
        property(ModifyAttribute::key, VariantSerializer.Id)
        property(ModifyAttribute::slotGroup, EquipmentSlotGroupSerializer, name = "slot_group")
        property(ModifyAttribute::attribute, AttributeSerializer)
        property(ModifyAttribute::amount, VariantSerializer.Double)
        property(ModifyAttribute::operation, VariantSerializer.Enum<AttributeModifier.Operation>())
    })

}

internal val ModifyAttributeMaterializer: ItemAttachmentMaterializer<ModifyAttribute>
    get() = itemAttachmentMaterializer(
    id = ModifyAttribute.id / "materializer",
    schema = ModifyAttribute,
    revision = 1,
    claims = setOf(ItemTraitClaim.KeyedAttributeModifiers),
) { context, output ->
    context.attachments.forEach { attachment ->
        output.setAttributeModifier(
            attachment.attribute,
            AttributeModifier(
                attachment.key.toNamespacedKey(),
                attachment.amount,
                attachment.operation,
                attachment.slotGroup,
            ),
        )
    }
}

private val EquipmentSlotGroupSerializer: Serializer<EquipmentSlotGroup> = VariantSerializer.mapped(
    serializer = VariantSerializer.String,
    serialize = { it.toString() },
    deserialize = { name ->
        EquipmentSlotGroup.getByName(name)
            ?: throw DataSerializationException("Unknown equipment slot group '$name'")
    }
)

private val AttributeSerializer: Serializer<Attribute> = VariantSerializer.mapped(
    serializer = VariantSerializer.Id,
    serialize = { it.key.toIdentifier() },
    deserialize = { identifier ->
        BukkitRegistry.ATTRIBUTE.get(identifier.toNamespacedKey())
            ?: throw DataSerializationException("Unknown attribute '$identifier'")
    }
)
