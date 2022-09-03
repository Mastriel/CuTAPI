package xyz.mastriel.exampleplugin.items

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import xyz.mastriel.cutapi.ShinyKnifeDamager
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.components.MaterialComponent
import xyz.mastriel.cutapi.items.components.getComponent
import xyz.mastriel.cutapi.items.components.getComponentOrNull
import xyz.mastriel.cutapi.registry.descriptors.materialDescriptor
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resourcepack.Texture
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.playSound
import xyz.mastriel.exampleplugin.Plugin
import xyz.mastriel.exampleplugin.components.DisableOffhand
import xyz.mastriel.exampleplugin.components.Soulbound
import kotlin.random.Random

object ShinyKnife : CustomMaterial(id(Plugin, "shiny_knife"), Material.IRON_SWORD) {

    override val descriptor = materialDescriptor {
        name = "&fShiny Knife".colored
        texture = Texture(Plugin, "textures/shiny_knife.png")

        component(Soulbound())
        component(DisableOffhand())
        component(ShinyKnifeDamager())

        description {
            val damager = getComponent<ShinyKnifeDamager>()
            if (damager.getDeathChance(itemStack) < 1.0) {
                textComponent("&7A very, very shiny knife. You can".colored)
                textComponent("&7even see your own reflection!".colored)
            } else {
                textComponent("&7A dulled knife. You can no longer".colored)
                textComponent("&7view your own reflection.".colored)
            }
            emptyLine()
            components(Color.Elethium)
        }
    }
}

