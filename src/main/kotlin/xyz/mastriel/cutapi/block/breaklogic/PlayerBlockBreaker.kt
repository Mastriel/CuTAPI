package xyz.mastriel.cutapi.block.breaklogic

import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.craftbukkit.block.*
import org.bukkit.craftbukkit.util.*
import org.bukkit.enchantments.*
import org.bukkit.entity.*
import org.bukkit.potion.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.behaviors.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.utils.*

/**
 * Exists only when a player is breaking a block. Controlled by the [BlockBreakManager].
 *
 * @param block The block being broken.
 * @param player The player breaking the block.
 * @param item The item being used to break the block.
 */
@UsesNMS
public open class PlayerBlockBreaker(
    public val block: Block,
    public val player: Player,
    public val item: AgnosticItemStack
) {

    protected val soundGroup: SoundGroup = block.blockSoundGroup
    protected val material: Material = block.type
    protected val hardness: Float = material.hardness
    protected val tools: List<Tool> = Tool.from(item)
    protected val toolCategories: List<ToolCategory> = tools.map { it.category }
    protected val correctToolCategories: List<ToolCategory> = ToolCategory.vanillaProperCategoriesOf(block)

    protected val correctToolIsUsed: Boolean = correctToolCategories.any { it in toolCategories }

    protected val utilizedTool: Tool = tools
        .filter { it.category in correctToolCategories }
        .maxByOrNull { it.toolSpeed.speed } ?: Tool.Fists


    public var hasStopped: Boolean = false

    public val isDone: Boolean
        get() = progress >= 1.0f

    public var progress: Float = 0.0f
        private set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    public open fun tick() {
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

        val nmsItem = item.vanilla().nms()

        return CraftMagicNumbers.getItem(material).isCorrectToolForDrops(nmsItem, nmsBlock)
    }

    /**
     * Calculates the damage dealt to the block.
     * @return The damage dealt to the block, as a percentage of the block's health from 0.0f to 1.0f.
     */
    @Suppress("DEPRECATION")
    public open fun calculateDamageDealt(): Float {

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

            val efficiencyLevel = item.vanilla().getEnchantmentLevel(Enchantment.EFFICIENCY)
            if (efficiencyLevel > 0) {
                totalSpeedMultiplier += efficiencyLevel * efficiencyLevel + 1
            }
        }
        val hasteLevel = player.getPotionEffect(PotionEffectType.HASTE)?.amplifier?.plus(1) ?: 0

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