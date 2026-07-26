package xyz.mastriel.cutapi.item.attachments

import org.bukkit.*
import org.bukkit.enchantments.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public object Shiny : ItemAttachment, Schema<Shiny> by singletonSchema(id(Plugin, "shiny"))

internal object ShinySystem : ItemSystem by attachmentItemSystem(Shiny) {

    override fun onRender(context: ItemRenderContext) {
        val item = context.item.handle
        if (item.enchantments.isNotEmpty()) return

        if (item.type == Material.FISHING_ROD) {
            item.addUnsafeEnchantment(Enchantment.INFINITY, 1)
        }
        item.addUnsafeEnchantment(Enchantment.LURE, 1)
        item.addItemFlags(ItemFlag.HIDE_ENCHANTS)
    }
}
