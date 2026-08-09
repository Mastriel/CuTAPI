package xyz.mastriel.cutapi.item.attachments

import io.papermc.paper.datacomponent.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public data class Durability(public val maxDamage: Int) : ItemAttachment {
    public companion object : Schema<Durability> by schema(id(Plugin, "custom_durability"), {
        property(Durability::maxDamage, VariantSerializer.Int, name = "max_damage")
    })
}

internal val DurabilityMaterializer: ItemAttachmentMaterializer<Durability> = itemAttachmentMaterializer(
    id = Durability.id / "materializer",
    schema = Durability,
    revision = 1,
    claims = setOf(ItemTraitClaim.Component(DataComponentTypes.MAX_DAMAGE)),
) { context, output ->
    val durability = context.attachments.single()
    require(durability.maxDamage > 0) { "Durability.maxDamage must be positive." }
    output.set(DataComponentTypes.MAX_DAMAGE, durability.maxDamage)
}
