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
        property(ModifyAttribute::slotGroup, EquipmentSlotGroupSerializer)
        property(ModifyAttribute::attribute, AttributeSerializer)
        property(ModifyAttribute::amount, VariantSerializer.Double)
        property(ModifyAttribute::operation, VariantSerializer.Enum<AttributeModifier.Operation>())
    })

    public fun updateItem(item: CuTItemStack) {
        item.handle.editMeta { meta ->

            val previous = meta.getAttributeModifiers(attribute)?.firstOrNull { it.key == key.toNamespacedKey() }
            if (previous != null) {
                // If the attribute modifier is already present, remove it
                meta.removeAttributeModifier(attribute, previous)
            }

            meta.addAttributeModifier(
                attribute,
                AttributeModifier(
                    key.toNamespacedKey(),
                    amount,
                    operation,
                    slotGroup
                )
            )
        }
    }
}

internal object ModifyAttributeSystem : ItemSystem by attachmentItemSystem(ModifyAttribute) {

    override fun onCreate(context: ItemCreateContext) {
        context.item.getAttachments(ModifyAttribute).forEach { it.updateItem(context.item) }
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
