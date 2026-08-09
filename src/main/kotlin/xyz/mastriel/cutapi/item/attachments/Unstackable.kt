package xyz.mastriel.cutapi.item.attachments

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import io.papermc.paper.datacomponent.DataComponentTypes

public object Unstackable : ItemAttachment, Schema<Unstackable> by singletonSchema(id(Plugin, "unstackable"))

internal val UnstackableMaterializer: ItemAttachmentMaterializer<Unstackable>
    get() = itemAttachmentMaterializer(
    id = Unstackable.id / "materializer",
    schema = Unstackable,
    revision = 1,
    claims = setOf(ItemTraitClaim.Component(DataComponentTypes.MAX_STACK_SIZE)),
) { context, output ->
    require(context.item.handle.amount <= 1) {
        "Unstackable cannot be materialized onto a stack with amount ${context.item.handle.amount}."
    }
    output.set(DataComponentTypes.MAX_STACK_SIZE, 1)
}
