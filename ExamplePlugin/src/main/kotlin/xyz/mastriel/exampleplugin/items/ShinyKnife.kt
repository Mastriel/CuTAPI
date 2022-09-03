package xyz.mastriel.exampleplugin.items

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.components.MaterialComponent
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
            val damager = getComponentOrNull() ?: ShinyKnifeDamager()
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

private class ShinyKnifeDamager : MaterialComponent(id(Plugin, "shiny_knife_damager")) {
    private val defaultDeathChance = 0.25

    override fun onDamageEntity(
        attacker: LivingEntity,
        victim: LivingEntity,
        mainHandItem: CuTItemStack,
        event: EntityDamageByEntityEvent
    ) {
        val deathChance = getDeathChance(mainHandItem)

        val attackerIsInCreativeMode = (attacker as? Player)?.gameMode == GameMode.CREATIVE
        if (Random.nextDouble() < deathChance && !attackerIsInCreativeMode) {
            attacker.health = 0.0
            event.isCancelled = true
            attacker.sendMessage("&c&oYou were holding the knife backwards...".colored)
        } else {
            event.damage = 143.0
            attacker.sendMessage("&e&oIT HIT RIGHT IN THE HEART!".colored)
            attacker.playSound("minecraft:item.totem.use", 1.0f, 1.0f)

            val newDeathChance = deathChance + 0.1
            setDeathChance(mainHandItem, newDeathChance)
            println(getDeathChance(mainHandItem))
            if (newDeathChance >= 1.0) {
                attacker.sendMessage("&7&oYour reflection is no longer visible in the blade. This may be danagerous to use...".colored)
                mainHandItem.name = "&cDull Knife".colored
            }
        }
    }

    private val suicideChanceKey = "SuicideChance"
    fun getDeathChance(item: CuTItemStack) = getData(item).getDouble(suicideChanceKey) ?: defaultDeathChance
    fun setDeathChance(item: CuTItemStack, chance: Double) = getData(item).setDouble(suicideChanceKey, chance)

}