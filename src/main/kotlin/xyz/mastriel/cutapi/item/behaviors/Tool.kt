package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import java.util.function.*

/**
 * If the vanilla material of the custom item is already a tool, it will be considered a tool here as well!
 * Don't use a vanilla tool as your vanilla material if you don't want this behavior.
 *
 * This class is also used to contain info for vanilla tools. Use [Tool.from] to get the tool data
 * for any item stack, including vanilla.
 */
@RepeatableBehavior
public data class Tool(val category: ToolCategory, val tier: ToolTier, val toolSpeed: ToolSpeed) :
    ItemBehavior(id(Plugin, "tool")) {

    override fun ItemDescriptorBuilder.modifyDescriptor() {
        if (!ToolCategory.has(category.id)) {
            error("Tool category ${category.id} is not registered.")
        }
        if (!ToolTier.has(tier.id)) {
            error("Tool tier ${tier.id} is not registered.")
        }
    }

    public companion object {

        public val Fists: Tool = Tool(ToolCategory.Fists, ToolTier.Nothing, ToolSpeed.Fists)

        public fun from(itemStack: AgnosticItemStack): List<Tool> {
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
public value class ToolSpeed(public val speed: Float) {

    /**
     * Note: This only contains VANILLA tool speeds!
     */
    public companion object Vanilla {
        private val map = mutableMapOf<Identifier, ToolSpeed>()

        public val Fists: ToolSpeed = ToolSpeed(1.0f)
        public val Wood: ToolSpeed = ToolSpeed(2.0f)
        public val Stone: ToolSpeed = ToolSpeed(4.0f)
        public val Iron: ToolSpeed = ToolSpeed(6.0f)
        public val Diamond: ToolSpeed = ToolSpeed(8.0f)
        public val Netherite: ToolSpeed = ToolSpeed(9.0f)
        public val Gold: ToolSpeed = ToolSpeed(12.0f)

        private fun register(id: Identifier, speed: ToolSpeed) {
            if (map.containsKey(id)) error("Tool speed $id is already registered.")
            map[id] = speed
        }

        public fun overwrite(id: Identifier, speed: ToolSpeed) {
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

public class ToolTier private constructor(
    override val id: Identifier,
    public val breakingLevel: Float,
    public val isVanilla: Boolean = false
) : Identifiable {

    public constructor(id: Identifier, breakingLevel: Float) : this(id, breakingLevel, false)


    public companion object : IdentifierRegistry<ToolTier>("Tool Tiers") {

        public val Nothing: ToolTier get() = get(id(Plugin, "nothing"))
        public val Wood: ToolTier get() = get(id(Plugin, "wood"))
        public val Stone: ToolTier get() = get(id(Plugin, "stone"))
        public val Iron: ToolTier get() = get(id(Plugin, "iron"))
        public val Gold: ToolTier get() = get(id(Plugin, "gold"))
        public val Diamond: ToolTier get() = get(id(Plugin, "diamond"))
        public val Netherite: ToolTier get() = get(id(Plugin, "netherite"))


        public fun fromVanillaBlock(block: Block): ToolTier {
            return fromVanillaMaterial(block.type)
        }

        public fun fromVanillaMaterial(material: Material): ToolTier {
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

public class ToolCategory private constructor(
    override val id: Identifier,
    public val attributes: ToolCategoryAttributes,
    public val isVanilla: Boolean = false
) : Identifiable {

    public constructor(id: Identifier, attributes: ToolCategoryAttributes) : this(id, attributes, false)


    public companion object : IdentifierRegistry<ToolCategory>("Tool Categories") {

        public val Pickaxe: ToolCategory = ToolCategory(
            id(Plugin, "pickaxe"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1
            ),
            true
        )
        public val Axe: ToolCategory = ToolCategory(
            id(Plugin, "axe"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1
            ),
            true
        )
        public val Sword: ToolCategory = ToolCategory(
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
        public val Hoe: ToolCategory = ToolCategory(
            id(Plugin, "hoe"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1
            ),
            true
        )
        public val Shovel: ToolCategory = ToolCategory(
            id(Plugin, "shovel"),
            ToolCategoryAttributes(
                attackEntityItemDamage = 2,
                breakBlockItemDamage = 1
            ),
            true
        )
        public val Shears: ToolCategory = ToolCategory(
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

        public val Fists: ToolCategory = ToolCategory(
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

        public fun vanillaProperCategoriesOf(block: Block): List<ToolCategory> {
            return vanillaProperCategoriesOf(block.type)
        }

        public fun vanillaProperCategoriesOf(material: Material): List<ToolCategory> {
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


public open class ToolCategoryAttributes(
    public val breakBlockItemDamage: Int,
    public val attackEntityItemDamage: Int,
    public val specialBreakingMultipliers: SpecialBreakingMultipliers = SpecialBreakingMultipliers()
) {

    /**
     * Gets the breaking speed multiplier from the special breaking multipliers.
     */
    public fun getBreakingSpeedMultiplier(material: Material): Float? {
        return specialBreakingMultipliers.getBreakingSpeedMultiplier(material)
    }
}

public class SpecialBreakingMultipliers(
    predicateMap: Map<Predicate<Material>, Float> = emptyMap()
) {
    public val map: Map<Material, Float>

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
    public fun getBreakingSpeedMultiplier(material: Material): Float? {
        return map[material]
    }
}

public fun SpecialBreakingMultipliers(vararg pairs: Pair<Predicate<Material>, Float>): SpecialBreakingMultipliers =
    SpecialBreakingMultipliers(mapOf(*pairs))