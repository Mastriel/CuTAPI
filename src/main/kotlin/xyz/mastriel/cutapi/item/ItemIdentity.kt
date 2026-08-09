@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item

import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import xyz.mastriel.cutapi.attachment.AttachmentHolder
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.attachment.matching
import xyz.mastriel.cutapi.attachment.schema
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.item.nativeitem.NativeItemTypes
import xyz.mastriel.cutapi.registry.toIdentifier

/** Identifies either a CuTAPI custom item or a vanilla Minecraft item type. */
public sealed interface ItemIdentity : AttachmentHolder<ItemAttachment> {
    public val itemType: ItemType
    public val backingItem: ItemType
    public val isCustom: Boolean

    override fun hasAttachment(schema: Schema<out ItemAttachment>): Boolean =
        ItemIdentityExtension.hasIntrinsicAttachment(this, schema.id)

    override fun <T : ItemAttachment> getAttachment(schema: Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on item $logicalId.")

    override fun <T : ItemAttachment> getAttachmentOrNull(schema: Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : ItemAttachment> getAttachments(schema: Schema<T>): List<T> =
        getAllAttachments().matching(schema)

    override fun getAllAttachments(): List<ItemAttachment> = ItemIdentityExtension.resolve(this)

    @ConsistentCopyVisibility
    public data class Custom internal constructor(public val item: CustomItem<*>) : ItemIdentity {
        override val itemType: ItemType get() = item.itemType
        override val backingItem: ItemType get() = item.backingItem
        override val isCustom: Boolean get() = true
    }

    @ConsistentCopyVisibility
    public data class Vanilla internal constructor(public val type: ItemType) : ItemIdentity {
        override val itemType: ItemType get() = type
        override val backingItem: ItemType get() = type
        override val isCustom: Boolean get() = false
    }

    public companion object {
        public fun of(item: CustomItem<*>): Custom = Custom(item)

        public fun of(type: ItemType): ItemIdentity =
            CustomItem.getOrNull(type.key.toIdentifier())?.let(::Custom) ?: Vanilla(type)
    }
}

public fun CustomItem<*>.asIdentity(): ItemIdentity.Custom = ItemIdentity.of(this)

public fun ItemType.asIdentity(): ItemIdentity = ItemIdentity.of(this)

public val ItemStack.itemIdentity: ItemIdentity
    get() = NativeItemTypes.resolve(this).asIdentity()

public fun ItemIdentity.matches(stack: CuTItemStack): Boolean = this == stack.identity
