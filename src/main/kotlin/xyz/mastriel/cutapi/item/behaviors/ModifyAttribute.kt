package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.attribute.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

@Suppress("UnstableApiUsage")
@RepeatableBehavior
public class ModifyAttribute(
    public val key: Identifier,
    public val slotGroup: EquipmentSlotGroup,
    public val attribute: Attribute,
    public val amount: Double,
    public val operation: AttributeModifier.Operation = AttributeModifier.Operation.ADD_NUMBER
) : ItemBehavior(id(Plugin, "attribute")) {

    override fun onCreate(item: CuTItemStack) {
        updateItem(item)
    }

    public fun updateItem(item: CuTItemStack) {
        item.handle.editMeta { meta ->

            val previous = meta.getAttributeModifiers(attribute)?.first { it.key == key.toNamespacedKey() }
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