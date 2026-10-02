package xyz.mastriel.cutapi.item.attachments

import net.kyori.adventure.text.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

@RepeatableAttachment
public data class StaticLore(public val lore: Component) : ItemLoreAttachment {
    public companion object : Schema<StaticLore> by schema(id(Plugin, "static_lore"), {
        property(StaticLore::lore, BuiltinSerializers.Component)
    })

    override fun getLore(item: CuTItemStack, viewer: Player?): Component {
        return lore
    }
}
