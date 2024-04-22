package xyz.mastriel.cutapi.block.breaklogic

import org.bukkit.SoundGroup
import org.bukkit.block.Block
import org.bukkit.craftbukkit.v1_20_R3.block.CraftBlock
import org.bukkit.craftbukkit.v1_20_R3.util.CraftMagicNumbers
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import xyz.mastriel.cutapi.item.AgnosticItemStack
import xyz.mastriel.cutapi.item.behaviors.Tool
import xyz.mastriel.cutapi.item.behaviors.ToolCategory
import xyz.mastriel.cutapi.item.behaviors.ToolTier
import xyz.mastriel.cutapi.nms.UsesNMS
import xyz.mastriel.cutapi.utils.roundToDecimalPlaces

/**
 * Exists only when a player is breaking a block. Controlled by the [BlockBreakManager].
 *
 * @param block The block being broken.
 * @param player The player breaking the block.
 * @param item The item being used to break the block.
 */
@UsesNMS
open class PlayerBlockBreaker(
    val block: Block,
    val player: Player,
    val item: AgnosticItemStack
) {

    protected val soundGroup: SoundGroup? = block.blockSoundGroup
    protected val material = block.type
    protected val hardness: Float = material.hardness
    protected val tools: List<Tool> = Tool.from(item)
    protected val toolCategories = tools.map { it.category }
    protected val correctToolCategories = ToolCategory.vanillaProperCategoriesOf(block)

    protected val correctToolIsUsed = correctToolCategories.any { it in toolCategories }

    protected val utilizedTool = tools
        .filter { it.category in correctToolCategories }
        .maxByOrNull { it.toolSpeed.speed } ?: Tool.Fists



    var hasStopped : Boolean = false

    val isDone: Boolean
        get() = progress >= 1.0f

    var progress: Float = 0.0f
        private set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    open fun tick() {
        if (isDone) return

    }

    private fun isCorrectToolForDropsCustomTool(block: Block): Boolean {
        val nmsBlock = (block as CraftBlock).nms

        if (!nmsBlock.requiresCorrectToolForDrops()) return true


        val blockValidCategories = ToolCategory.vanillaProperCategoriesOf(block)
        val blockToolTier = ToolTier.fromVanillaBlock(block).breakingLevel
        val itemToolCategory = utilizedTool.category
        val itemToolTier = utilizedTool.tier.breakingLevel

        return itemToolCategory in blockValidCategories && itemToolTier >= blockToolTier
    }

    private fun isCorrectToolForDropsVanillaTool(block: Block): Boolean {
        val nmsBlock = (block as CraftBlock).nms

        if (!nmsBlock.requiresCorrectToolForDrops()) return true

        return CraftMagicNumbers.getItem(material).isCorrectToolForDrops(nmsBlock)
    }

    /**
     * Calculates the damage dealt to the block.
     * @return The damage dealt to the block, as a percentage of the block's health from 0.0f to 1.0f.
     */
    @Suppress("DEPRECATION")
    open fun calculateDamageDealt() : Float {

        // blocks under 0 are unbreakable
        if (hardness < 0f) return 0f

        // blocks at 0 are instant break
        if (hardness == 0f) return 1f

        var totalSpeedMultiplier = 1f

        // calculates out the tool's base speed, and efficiency enchant.
        if (correctToolIsUsed) {

            totalSpeedMultiplier = utilizedTool
                .category
                .attributes
                .getBreakingSpeedMultiplier(material) ?: utilizedTool.toolSpeed.speed

            val efficiencyLevel = item.vanilla().getEnchantmentLevel(Enchantment.DIG_SPEED)
            if (efficiencyLevel > 0) {
                totalSpeedMultiplier += efficiencyLevel * efficiencyLevel + 1
            }
        }
        val hasteLevel = player.getPotionEffect(PotionEffectType.FAST_DIGGING)?.amplifier?.plus(1) ?: 0

        totalSpeedMultiplier *= hasteLevel * 0.2f + 1

        if (player.isUnderWater) totalSpeedMultiplier /= 5f

        // deprecation is suppressed here because isOnGround can be
        // easily spoofed. it doesn't really matter that much since
        // it's used for a minor benefit when mining while flying.
        // (which fly hacks should be pretty obvious to spot.
        if (!player.isOnGround) totalSpeedMultiplier /= 5f

        val correctToolIsUsed = when (item) {
            is AgnosticItemStack.Custom -> isCorrectToolForDropsCustomTool(block)
            is AgnosticItemStack.Vanilla -> isCorrectToolForDropsVanillaTool(block)
        }

        val correctToolBonus = if (correctToolIsUsed) 30f else 100f

        return (totalSpeedMultiplier / hardness / correctToolBonus).roundToDecimalPlaces(3)
    }



}