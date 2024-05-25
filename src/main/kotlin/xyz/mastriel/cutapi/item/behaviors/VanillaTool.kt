package xyz.mastriel.cutapi.item.behaviors

import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import org.bukkit.craftbukkit.util.CraftMagicNumbers
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.nms.UsesNMS
import xyz.mastriel.cutapi.nms.bukkit
import xyz.mastriel.cutapi.nms.nms
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.TemporaryAPI
import java.util.*


typealias ToolComponent = net.minecraft.world.item.component.Tool
typealias ToolComponentRule = net.minecraft.world.item.component.Tool.Rule

@TemporaryAPI
@UsesNMS
class VanillaTool(
    vararg val tools: Tool,
    val defaultMiningSpeed: ToolSpeed = ToolSpeed.Fists,
    val specialBreakingMultipliers: SpecialBreakingMultipliers = SpecialBreakingMultipliers(),
    val itemDamage: Int = 1
) : ItemBehavior(id(Plugin, "vanilla_tool_component")) {

    private val rules: List<PlainToolComponentRule>

    init {
        val materialData = mutableListOf<ToolMaterialData>()
        for (tool in tools) {
            for (blockHolder in BuiltInRegistries.BLOCK.holders()) {
                val blockType = blockHolder.value()
                val material = CraftMagicNumbers.getMaterial(blockType)
                val isProper = ToolCategory.vanillaProperCategoriesOf(material).any { it == tool.category }
                if (!isProper) continue

                val special = specialBreakingMultipliers.getBreakingSpeedMultiplier(material)
                    ?: tool.category.attributes.getBreakingSpeedMultiplier(material)

                val minToolTier = ToolTier.fromVanillaMaterial(material)
                val correctToolForDrops = minToolTier.breakingLevel <= tool.tier.breakingLevel

                materialData += ToolMaterialData(blockHolder, correctToolForDrops, special ?: tool.toolSpeed.speed)
            }
        }
        this.rules = optimize(materialData)
    }

    override fun onCreate(item: CuTItemStack) {
        val nmsItem = item.vanilla().nms()

        val tool = ToolComponent(rules.map { it.toRule() }, defaultMiningSpeed.speed, itemDamage)

        val patch = DataComponentPatch.builder().set(
            DataComponents.TOOL,
            tool
        ).build()

        nmsItem.applyComponents(patch)


        item.vanilla().itemMeta = nmsItem.bukkit().itemMeta
    }

}

// if we don't optimize this then it becomes so fucking large that it just
// breaks a lot of stuff
// tool rule properties such as speed and correct blocks can be grouped together.
@TemporaryAPI
@UsesNMS
private fun optimize(materialData: List<ToolMaterialData>): List<PlainToolComponentRule> {
    val map = mutableMapOf<ToolRuleProperties, MutableList<Holder.Reference<Block>>>()

    for (data in materialData) {
        val properties = ToolRuleProperties(data.correctForBlocks, data.speed)
        map.getOrPut(properties) { mutableListOf() }.add(data.holder)
    }
    val list = mutableListOf<PlainToolComponentRule>()
    for ((properties, holders) in map) {
        list += PlainToolComponentRule(
            holders,
            Optional.of(properties.speed),
            Optional.of(properties.correctForBlocks)
        )
    }
    return list
}

@TemporaryAPI
@UsesNMS
private data class ToolMaterialData(
    val holder: Holder.Reference<Block>,
    val correctForBlocks: Boolean,
    val speed: Float
)

@TemporaryAPI
@UsesNMS
private data class ToolRuleProperties(
    val correctForBlocks: Boolean,
    val speed: Float
)

@TemporaryAPI
@UsesNMS
private data class PlainToolComponentRule(
    val blocks: List<Holder.Reference<Block>>,
    val speed: Optional<Float>,
    val correctForDrops: Optional<Boolean>
) {
    fun toRule() = ToolComponentRule(
        HolderSet.direct(blocks),
        speed,
        correctForDrops
    )
}

@TemporaryAPI
@UsesNMS
private fun ToolComponentRule.copy() =
    ToolComponentRule(blocks, speed, correctForDrops)

@TemporaryAPI
@UsesNMS
internal fun stripToolData(itemStack: ItemStack): ItemStack {
    val nmsItem = itemStack.nms()
    val patch = DataComponentPatch.builder()
        .remove(DataComponents.TOOL)
        .build()
    nmsItem.applyComponents(patch)
    return nmsItem.bukkit()
}

