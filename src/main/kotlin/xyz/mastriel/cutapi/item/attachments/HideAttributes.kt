package xyz.mastriel.cutapi.item.attachments

import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public object HideAttributes : Attachment, Schema<HideAttributes> by singletonSchema(id(Plugin, "hide_attributes"))

internal object HideAttributesSystem : ItemSystem by attachmentItemSystem(HideAttributes) {

    override fun onRender(context: ItemRenderContext) {
        context.item.handle.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
    }
}

