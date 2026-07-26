package xyz.mastriel.cutapi.item.attachments

import org.bukkit.inventory.meta.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public data class Durability(public val maxDamage: Int, public val currentDamage: Int = 0) : ItemAttachment {
    public companion object : Schema<Durability> by schema(id(Plugin, "custom_durability"), {
        property(Durability::maxDamage, VariantSerializer.Int)
        property(Durability::currentDamage, VariantSerializer.Int)
    })
}

internal object DurabilitySystem : ItemSystem by attachmentItemSystem(Durability) {

    override fun onCreate(context: ItemCreateContext) {
        val durability = context.attachment(Durability)
        context.item.handle.editMeta {
            if (it !is Damageable) return@editMeta
            it.setMaxDamage(durability.maxDamage)
            it.damage = durability.currentDamage
        }
    }
}
