package xyz.mastriel.cutapi.item.attachments

import org.bukkit.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

/**
 * Makes a custom item display as this material. This is purely client sided (except when
 * the holder is in creative mode, as creative mode enables client-sided changes like this
 * to occur without the server arguing)
 */
public data class DisplayAs(public val material: Material) : ItemAttachment {
    public companion object : Schema<DisplayAs> by schema(id(Plugin, "display_as"), {
        property(DisplayAs::material, VariantSerializer.Enum<Material>())
    })
}

internal object DisplayAsSystem : ItemSystem by attachmentItemSystem(DisplayAs) {

    override fun onRender(context: ItemRenderContext) {
        context.item.handle.type = context.attachment(DisplayAs).material
    }
}
