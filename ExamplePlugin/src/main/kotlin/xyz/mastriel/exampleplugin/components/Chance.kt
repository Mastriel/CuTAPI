package xyz.mastriel.exampleplugin.components

import net.kyori.adventure.text.Component
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.components.MaterialComponent
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.Plugin
import kotlin.random.Random

/**
 * @param chanceId The ID of this Chance.
 * @param chance The probability of [one] occurring, with a max of 1.0. If option1 doesn't occur, option 2
 * will be triggered.
 */
abstract class Chance(chanceId: String, val chance: Double) : MaterialComponent(id(Plugin, "chance/$chanceId")) {

    override fun getLore(item: CuTItemStack, viewer: Player): Component? {
        return "Luck-based".colored
    }

    fun run(item: CuTItemStack, holder: LivingEntity) {
        if (Random.nextDouble() < chance) one(item, holder) else two(item, holder)
    }

    abstract fun one(item: CuTItemStack, holder: LivingEntity)
    abstract fun two(item: CuTItemStack, holder: LivingEntity)
}