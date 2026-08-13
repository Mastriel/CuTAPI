@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item.attachments

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemType
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.data.VariantSerializer
import xyz.mastriel.cutapi.data.schema
import xyz.mastriel.cutapi.item.ItemRenderContext
import xyz.mastriel.cutapi.item.ItemSystem
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.attachmentItemSystem
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.toIdentifier

/** Explicitly changes only the vanilla item type used for a rendered client copy. */
public data class DisplayAs(public val itemType: ItemType) : ItemAttachment {
    init {
        require(
            itemType.key.namespace == NamespacedKey.MINECRAFT &&
                CustomItem.getOrNull(itemType.key.toIdentifier()) == null &&
                itemType.asMaterial() != null
        ) { "DisplayAs requires a vanilla ItemType: ${itemType.key}" }
    }

    public companion object : Schema<DisplayAs> by schema(id("cutapi:display_as"), {
        property(DisplayAs::itemType, ItemTypeSerializer, name = "item_type")
    })
}

private val ItemTypeSerializer = VariantSerializer.mapped(
    serializer = VariantSerializer.String,
    serialize = { type: ItemType -> type.key.toString() },
    deserialize = { value ->
        val key = NamespacedKey.fromString(value) ?: error("Invalid ItemType key: $value")
        Registry.ITEM.get(key) ?: error("Unknown ItemType: $value")
    },
)

internal object DisplayAsSystem : ItemSystem by attachmentItemSystem(DisplayAs) {
    override fun onRender(context: ItemRenderContext) {
        context.item.handle.type = context.attachment(DisplayAs).itemType.asMaterial()
            ?: error("DisplayAs requires a vanilla ItemType.")
    }
}
