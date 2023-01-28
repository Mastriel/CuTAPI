package xyz.mastriel.brazil.items

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import xyz.mastriel.brazil.Plugin
import xyz.mastriel.brazil.behaviors.DisableOffhand
import xyz.mastriel.brazil.behaviors.Soulbound
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.ItemDescriptor
import xyz.mastriel.cutapi.item.behaviors.DisplayAs
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.item.behaviors.StaticLore
import xyz.mastriel.cutapi.item.customItem
import xyz.mastriel.cutapi.pdc.tags.getDouble
import xyz.mastriel.cutapi.pdc.tags.setDouble
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resourcepack.management.ref
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.personalized
import xyz.mastriel.cutapi.utils.playSound
import kotlin.random.Random

val ShinyKnife = customItem(id(Plugin, "shiny_knife"), Material.IRON_SWORD) {
    name = personalized("&fShiny Knife".colored)
    texture = personalized( ref(Plugin, "textures/rivers_of_blood.png") )

    behavior(
        StaticLore("I'm lore!".colored),
        Soulbound(),
        DisableOffhand(),
        ShinyKnifeDamager(),
        StaticLore("I'm &oalso&r lore!".colored),
        DisplayAs(Material.GLISTERING_MELON_SLICE)
    )

    description {
        val damager = getBehavior<ShinyKnifeDamager>()
        if (damager.getDeathChance(itemStack) < 1.0) {
            textComponent("&7A very, very shiny knife. You can".colored)
            textComponent("&7even see your own reflection!".colored)
        } else {
            textComponent("&7A dulled knife. You can no longer".colored)
            textComponent("&7view your own reflection.".colored)
        }
        emptyLine()
        behaviorLore(Color.Elethium)
    }
}

class ShinyKnifeDamager : ItemBehavior(id(Plugin, "shiny_knife_damager")) {
    private val defaultDeathChance = 0.25

    override fun onDamageEntity(
        attacker: LivingEntity,
        victim: LivingEntity,
        mainHandItem: CuTItemStack,
        event: EntityDamageByEntityEvent
    ) {
        val deathChance = getDeathChance(mainHandItem)

        val attackerIsInCreativeMode = (attacker as? Player)?.gameMode == GameMode.CREATIVE
        val killsPlayer = Random.nextDouble() < deathChance
        if (killsPlayer && !attackerIsInCreativeMode) {
            attacker.health = 0.0
            event.isCancelled = true
            attacker.sendMessage("&c&oYou were holding the knife backwards...".colored)
            return
        }

        event.damage = 143.0
        attacker.sendMessage("&e&oIT HIT RIGHT IN THE HEART!".colored)
        attacker.playSound("minecraft:item.totem.use", 0.4f, 1.0f)

        val newDeathChance = deathChance + 0.1
        setDeathChance(mainHandItem, newDeathChance)
        if (newDeathChance >= 1.0) {
            attacker.sendMessage("&7&oYour reflection is no longer visible in the blade. This may be danagerous to use...".colored)
            mainHandItem.name = "&cDull Knife".colored
        }

    }

    private val suicideChanceKey = "SuicideChance"
    fun getDeathChance(item: CuTItemStack) = getData(item).getDouble(suicideChanceKey) ?: defaultDeathChance
    fun setDeathChance(item: CuTItemStack, chance: Double) = getData(item).setDouble(suicideChanceKey, chance)

}