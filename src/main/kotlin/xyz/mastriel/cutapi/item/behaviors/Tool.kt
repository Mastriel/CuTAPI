package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.behavior.RepeatableBehavior
import xyz.mastriel.cutapi.behavior.getBehaviorsOfType
import xyz.mastriel.cutapi.item.AgnosticItemStack
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.ItemDescriptorBuilder
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.id
import java.util.function.Predicate

/**
 * If the vanilla material of the custom item is already a tool, it will be considered a tool here as well!
 * Don't use a vanilla tool as your vanilla material if you don't want this behavior.
 *
 * This class is also used to contain info for vanilla tools. Use [Tool.from] to get the tool data
 * for any item stack, including vanilla.
 */
@RepeatableBehavior
data class Tool(val category: ToolCategory, val tier: ToolTier, val toolSpeed: ToolSpeed) :
    ItemBehavior(id(Plugin, "tool")) {

    override fun ItemDescriptorBuilder.modifyDescriptor() {
        if (!ToolCategory.has(category.id)) {
            error("Tool category ${category.id} is not registered.")
        }
        if (!ToolTier.has(tier.id)) {
            error("Tool tier ${tier.id} is not registered.")
        }
    }

    companion object {

        val Fists = Tool(ToolCategory.Fists, ToolTier.Nothing, ToolSpeed.Fists)

        fun from(itemStack: AgnosticItemStack): List<Tool> {
            return when (itemStack) {
                is AgnosticItemStack.Vanilla -> listOf(fromVanilla(itemStack.vanilla()))
                is AgnosticItemStack.Custom -> fromCustom(itemStack.custom())
            }
        }

        private fun fromCustom(itemStack: CuTItemStack): List<Tool> {
            val behaviors = itemStack.getBehaviorsOfType<Tool>()

            if (behaviors.isEmpty()) {
                return listOf(fromVanilla(itemStack.vanilla()))
            }

            return behaviors
        }

        private fun fromVanilla(itemStack: ItemStack): Tool {
            val category = categoryFromVanilla(itemStack)
            val tier = tierFromVanilla(itemStack)
            val speed = speedFromVanillaTierAndCategory(tier, category) ?: error("$tier is not a vanilla tier.")


            return Tool(category, tier, speed)
        }

        private fun categoryFromVanilla(itemStack: ItemStack): ToolCategory {
            return when (itemStack.type) {
                Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL, Material.GOLDEN_SHOVEL -> ToolCategory.Shovel
                Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE, Material.GOLDEN_PICKAXE -> ToolCategory.Pickaxe
                Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE, Material.GOLDEN_AXE -> ToolCategory.Axe
                Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE, Material.GOLDEN_HOE -> ToolCategory.Hoe
                Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.GOLDEN_SWORD -> ToolCategory.Sword
                Material.SHEARS -> ToolCategory.Shears
                else -> ToolCategory.Fists
            }
        }

        private fun tierFromVanilla(itemStack: ItemStack): ToolTier {
            return when (itemStack.type) {
                Material.WOODEN_SHOVEL, Material.WOODEN_PICKAXE, Material.WOODEN_AXE, Material.WOODEN_HOE, Material.WOODEN_SWORD -> ToolTier.Wood
                Material.STONE_SHOVEL, Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_HOE, Material.STONE_SWORD -> ToolTier.Stone
                Material.IRON_SHOVEL, Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_HOE, Material.IRON_SWORD -> ToolTier.Iron
                Material.GOLDEN_SHOVEL, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_HOE, Material.GOLDEN_SWORD -> ToolTier.Gold
                Material.DIAMOND_SHOVEL, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_HOE, Material.DIAMOND_SWORD -> ToolTier.Diamond
                Material.NETHERITE_SHOVEL, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_HOE, Material.NETHERITE_SWORD -> ToolTier.Netherite
                else -> ToolTier.Nothing
            }
        }

        private fun speedFromVanillaTierAndCategory(tier: ToolTier, category: ToolCategory): ToolSpeed? {
            return when (category) {
                ToolCategory.Sword -> ToolSpeed(1.5f)
                ToolCategory.Shears -> ToolSpeed(1.5f)
                else -> when (tier) {
                    ToolTier.Nothing -> ToolSpeed(1.0f)
                    ToolTier.Wood -> ToolSpeed(2.0f)
                    ToolTier.Stone -> ToolSpeed(4.0f)
                    ToolTier.Iron -> ToolSpeed(6.0f)
                    ToolTier.Diamond -> ToolSpeed(8.0f)
                    ToolTier.Netherite -> ToolSpeed(9.0f)
                    ToolTier.Gold -> ToolSpeed(12.0f)
                    else -> null
                }
            }
        }
    }
}


@JvmInline
value class ToolSpeed(val speed: Float) {

    /**
     * Note: This only contains VANILLA tool speeds!
     */
    companion object Vanilla {
        private val map = mutableMapOf<Identifier, ToolSpeed>()

        val Fists = ToolSpeed(1.0f)
        val Wood = ToolSpeed(2.0f)
        val Stone = ToolSpeed(4.0f)
        val Iron = ToolSpeed(6.0f)
        val Diamond = ToolSpeed(8.0f)
        val Netherite = ToolSpeed(9.0f)
        val Gold = ToolSpeed(12.0f)

        private fun register(id: Identifier, speed: ToolSpeed) {
            if (map.containsKey(id)) error("Tool speed $id is already registered.")
            map[id] = speed
        }

        fun overwrite(id: Identifier, speed: ToolSpeed) {
            if (!map.containsKey(id)) error("Tool speed $id is not registered. This registry only contains Vanilla Tool Speeds.")
            map[id] = speed
        }

        init {
            register(id(Plugin, "fists"), ToolSpeed(1.0f))
            register(id(Plugin, "wood"), ToolSpeed(2.0f))
            register(id(Plugin, "stone"), ToolSpeed(4.0f))
            register(id(Plugin, "iron"), ToolSpeed(6.0f))
            register(id(Plugin, "diamond"), ToolSpeed(8.0f))
            register(id(Plugin, "netherite"), ToolSpeed(9.0f))
            register(id(Plugin, "gold"), ToolSpeed(12.0f))
        }
    }
}

class ToolTier private constructor(
    override val id: Identifier,
    val breakingLevel: Float,
    val isVanilla: Boolean = false
) : Identifiable {

    constructor(id: Identifier, breakingLevel: Float) : this(id, breakingLevel, false)


    companion object : IdentifierRegistry<ToolTier>("Tool Tiers") {

        val Nothing get() = get(id(Plugin, "nothing"))
        val Wood get() = get(id(Plugin, "wood"))
        val Stone get() = get(id(Plugin, "stone"))
        val Iron get() = get(id(Plugin, "iron"))
        val Gold get() = get(id(Plugin, "gold"))
        val Diamond get() = get(id(Plugin, "diamond"))
        val Netherite get() = get(id(Plugin, "netherite"))


        fun fromVanillaBlock(block: Block): ToolTier {
            return fromVanillaMaterial(block.type)
        }

        fun fromVanillaMaterial(material: Material): ToolTier {
            return when {
                Tag.NEEDS_STONE_TOOL.isTagged(material) -> Stone
                Tag.NEEDS_IRON_TOOL.isTagged(material) -> Gold
                Tag.NEEDS_DIAMOND_TOOL.isTagged(material) -> Diamond
                else -> Nothing
            }
        }

        init {
            register(ToolTier(id(Plugin, "nothing"), 0f, true))
            register(ToolTier(id(Plugin, "gold"), 1f, true))
            register(ToolTier(id(Plugin, "wood"), 1f, true))
            register(ToolTier(id(Plugin, "stone"), 2f, true))
            register(ToolTier(id(Plugin, "iron"), 3f, true))
            register(ToolTier(id(Plugin, "diamond"), 4f, true))
            register(ToolTier(id(Plugin, "netherite"), 5f, true))
        }

    }
}

class ToolCategory private constructor(
    override val id: Identifier,
    val attributes: ToolCategoryAttributes,
    val isVanilla: Boolean = false
) : Identifiable {

    constructor(id: Identifier, attributes: ToolCategoryAttributes) : this(id, attributes, false)


    companion object : IdentifierRegistry<ToolCategory>("Tool Categories") {

        val Pickaxe = ToolCategory(
            id(Plugin, "pickaxe"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1
            ),
            true
        )
        val Axe = ToolCategory(
            id(Plugin, "axe"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1
            ),
            true
        )
        val Sword = ToolCategory(
            id(Plugin, "sword"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 1,
                breakBlockItemDamage = 2,
                specialBreakingMultipliers = SpecialBreakingMultipliers(
                    Predicate<Material> { material -> material == Material.COBWEB } to 15.0f,
                    Predicate<Material> { material -> material == Material.BAMBOO || material == Material.BAMBOO_SAPLING } to 1.0E9f,
                )
            ),
            true
        )
        val Hoe = ToolCategory(
            id(Plugin, "hoe"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1
            ),
            true
        )
        val Shovel = ToolCategory(
            id(Plugin, "shovel"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1
            ),
            true
        )
        val Shears = ToolCategory(
            id(Plugin, "shears"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1,
                specialBreakingMultipliers = SpecialBreakingMultipliers(
                    Predicate<Material> { it == Material.VINE || it == Material.GLOW_LICHEN } to 1.0f,
                    Predicate<Material> { material -> Tag.WOOL.isTagged(material) } to 5.0f,
                    Predicate<Material> { material -> Tag.LEAVES.isTagged(material) } to 15.0f
                )
            ),
            true
        )

        val Fists = ToolCategory(
            id(Plugin, "fists"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 0,
                breakBlockItemDamage = 0
            ), false
        )

        init {
            register(Pickaxe)
            register(Axe)
            register(Sword)
            register(Hoe)
            register(Shovel)
            register(Shears)
            register(Fists)
        }

        fun vanillaProperCategoriesOf(block: Block): List<ToolCategory> {
            return vanillaProperCategoriesOf(block.type)
        }

        fun vanillaProperCategoriesOf(material: Material): List<ToolCategory> {
            return buildList {
                if (Tag.MINEABLE_SHOVEL.isTagged(material)) add(Shovel)
                if (Tag.MINEABLE_PICKAXE.isTagged(material)) add(Pickaxe)
                if (Tag.MINEABLE_AXE.isTagged(material)) add(Axe)
                if (Tag.MINEABLE_HOE.isTagged(material)) add(Hoe)
                if (material == Material.COBWEB || material == Material.BAMBOO_SAPLING || material == Material.BAMBOO) add(
                    Sword
                )
                if (Tag.LEAVES.isTagged(material) || Tag.WOOL.isTagged(material) || material == Material.COBWEB) add(
                    Shears
                )
            }
        }
    }
}


open class ToolCategoryAttributes(
    val breakBlockItemDamage: Int,
    val attackEntityItemDamage: Int,
    val specialBreakingMultipliers: SpecialBreakingMultipliers = SpecialBreakingMultipliers()
) {

    /**
     * Gets the breaking speed multiplier from the special breaking multipliers.
     */
    fun getBreakingSpeedMultiplier(material: Material): Float? {
        return specialBreakingMultipliers.getBreakingSpeedMultiplier(material)
    }
}

class SpecialBreakingMultipliers(
    predicateMap: Map<Predicate<Material>, Float> = emptyMap()
) {
    val map: Map<Material, Float>

    init {
        val materialMap = mutableMapOf<Material, Float>()

        for (material in Material.entries) {
            for ((predicate, speed) in predicateMap) {
                if (predicate.test(material)) {
                    materialMap[material] = speed
                    continue
                }
            }
        }

        map = materialMap
    }

    /**
     * Returns the special breaking speed of a block, or 1 for any non-special ones.
     */
    fun getBreakingSpeedMultiplier(material: Material): Float? {
        return map[material]
    }
}

fun SpecialBreakingMultipliers(vararg pairs: Pair<Predicate<Material>, Float>) =
    SpecialBreakingMultipliers(mapOf(*pairs))