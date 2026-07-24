package xyz.mastriel.cutapi.item.attachments

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public data class BlockPlaceAttachment(
    public val tileId: Identifier,
    public val consumesItem: Boolean = true
) : Attachment {
    public constructor(
        tile: CustomTile<*>,
        consumesItem: Boolean = true
    ) : this(tile.id, consumesItem)

    public val tile: CustomTile<*> get() = CustomTile.get(tileId)

    public companion object : Schema<BlockPlaceAttachment> by schema(id(Plugin, "block_place"), {
        property(BlockPlaceAttachment::tileId, VariantSerializer.Id)
        property(BlockPlaceAttachment::consumesItem, VariantSerializer.Boolean)
    })
}

internal object BlockPlaceSystem : ItemSystem by attachmentItemSystem(BlockPlaceAttachment) {

    override fun onPlace(context: ItemBlockPlaceContext) {
        if (context.event.isCancelled || !context.event.canBuild()) return
        for (attachment in context.item.getAttachments(BlockPlaceAttachment)) {
            attachment.tile.setAt(context.event.blockPlaced)
            if (!attachment.consumesItem) context.item.handle.amount += 1
        }
    }
}
