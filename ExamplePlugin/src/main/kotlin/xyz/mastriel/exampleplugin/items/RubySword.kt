package xyz.mastriel.exampleplugin.items

import org.bukkit.Material
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.registry.descriptors.materialDescriptor
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resourcepack.Texture
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin
import xyz.mastriel.exampleplugin.components.Soulbound

object RubySword : CustomMaterial(id(Plugin, "ruby_sword"), Material.DIAMOND_SWORD) {

    override val materialDescriptor = materialDescriptor {
        name = "&fRuby Sword".colored
        texture = Texture(Plugin, "textures/ruby_sword.png")

        description {
            textComponent("&7A sword, charged from &eDragon's Breath&7.".colored)
            emptyLine()
            itemComponents(Color.Blue)
        }
    }

    override fun onCreate(item: CustomItemStack) {
        item.addComponent(Soulbound(null))

    }

    fun onObtain(event: CustomItemObtainEvent) {
        event.player
    }
}