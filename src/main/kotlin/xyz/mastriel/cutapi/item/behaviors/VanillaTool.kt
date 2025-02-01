package xyz.mastriel.cutapi.item.behaviors

import net.minecraft.core.*
import net.minecraft.core.component.*
import net.minecraft.core.registries.*
import net.minecraft.world.level.block.*
import org.bukkit.craftbukkit.util.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import java.util.*


public typealias ToolComponent = net.minecraft.world.item.component.Tool
public typealias ToolComponentRule = net.minecraft.world.item.component.Tool.Rule

@TemporaryAPI
@UsesNMS
public class VanillaTool(
    public vararg val tools: Tool,
    public val defaultMiningSpeed: ToolSpeed = ToolSpeed.Fists,
    public val specialBreakingMultipliers: SpecialBreakingMultipliers = SpecialBreakingMultipliers(),
    public val itemDamage: Int = 1
) : ItemBehavior(id(Plugin, "vanilla_tool_component")) {

    private val rules: List<PlainToolComponentRule>

    init {
        val materialData = mutableListOf<ToolMaterialData>()
        for (tool in tools) {
            for (blockType in BuiltInRegistries.BLOCK.stream()) {
                val material = CraftMagicNumbers.getMaterial(blockType)
                val isProper = ToolCategory.vanillaProperCategoriesOf(material).any { it == tool.category }
                if (!isProper) continue

                val special = specialBreakingMultipliers.getBreakingSpeedMultiplier(material)
                    ?: tool.category.attributes.getBreakingSpeedMultiplier(material)

                val minToolTier = ToolTier.fromVanillaMaterial(material)
                val correctToolForDrops = minToolTier.breakingLevel <= tool.tier.breakingLevel

                val holder = BuiltInRegistries.BLOCK.wrapAsHolder(blockType)
                materialData += ToolMaterialData(holder, correctToolForDrops, special ?: tool.toolSpeed.speed)
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
    val map = mutableMapOf<ToolRuleProperties, MutableList<Holder<Block>>>()

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
    val holder: Holder<Block>,
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
    val blocks: List<Holder<Block>>,
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

