package xyz.mastriel.exampleplugin.items

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.components.getComponentOrNull
import xyz.mastriel.cutapi.registry.descriptors.materialDescriptor
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resourcepack.Texture
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin
import xyz.mastriel.exampleplugin.components.BindOnUse
import xyz.mastriel.exampleplugin.components.Charged

object RubySword : CustomMaterial(id(Plugin, "ruby_sword"), Material.DIAMOND_SWORD) {

    override val materialDescriptor = materialDescriptor {
        name = "&fRuby Sword".colored
        texture = Texture(Plugin, "textures/ruby_sword.png")

        component { BindOnUse() }
        component { Charged(30) }

        description {
            textComponent("&7A sword, charged from &eDragon's Breath&7.".colored)
            emptyLine()
            itemComponents(Color.Elethium)
        }
    }


    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val customItem = CuTItemStack(item)

        val charged = customItem.getComponentOrNull<Charged>()
        charged?.subtract(1)
    }
}