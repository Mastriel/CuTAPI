package xyz.mastriel.cutapi.item.attachments

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public object HideTooltip : Attachment, Schema<HideTooltip> by singletonSchema(id(Plugin, "blank_name"))

internal object HideTooltipSystem : ItemSystem by attachmentItemSystem(HideTooltip) {

    override fun onRender(context: ItemRenderContext) {
        context.item.handle.editMeta { it.isHideTooltip = true }
    }
}
