package xyz.mastriel.cutapi.block

import org.bukkit.entity.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.registry.*

public enum class BlockBreakCause {
    Player,
    Creative,
    Explosion,
    Piston,
    Command,
    Api,
}

public data class BlockSettings(
    val hardness: Float,
    val explosionResistance: Float,
    val effectiveTools: Set<ToolCategory>,
    val minimumToolTier: ToolTier?,
    val requiresCorrectToolForDrops: Boolean,
    /** Whether this block blocks skylight and behaves as a solid redstone conductor. */
    val occludes: Boolean = true,
) {
    init {
        require(!hardness.isNaN()) { "Block hardness cannot be NaN." }
        require(explosionResistance >= 0.0f && !explosionResistance.isNaN()) {
            "Block explosion resistance must be a non-negative number."
        }
    }

    public companion object : DebugView<BlockSettings> by debugView(id("cutapi:block_settings"), {
        property(BlockSettings::hardness, VariantSerializer.Float)
        property(BlockSettings::explosionResistance, VariantSerializer.Float)
        property("effectiveTools", VariantSerializer.ListOf(ToolCategory)) {
            it.effectiveTools.toList()
        }
        property(BlockSettings::minimumToolTier, ToolTier.nullable())
        property(BlockSettings::requiresCorrectToolForDrops, VariantSerializer.Boolean)
        property(BlockSettings::occludes, VariantSerializer.Boolean)
    }) {}
}

public class BlockSettingsBuilder {
    public var hardness: Float = 1.0f
    public var explosionResistance: Float = 1.0f
    public var effectiveTools: Set<ToolCategory> = emptySet()
    public var minimumToolTier: ToolTier? = null
    public var requiresCorrectToolForDrops: Boolean = false
    /** Set to false for glass-like skylight, spawning, suffocation, and redstone behavior. */
    public var occludes: Boolean = true

    public fun build(): BlockSettings = BlockSettings(
        hardness = hardness,
        explosionResistance = explosionResistance,
        effectiveTools = effectiveTools.toSet(),
        minimumToolTier = minimumToolTier,
        requiresCorrectToolForDrops = requiresCorrectToolForDrops,
        occludes = occludes,
    )
}

public class BlockDropContext(
    public val tile: CuTPlacedTile,
    public val player: Player?,
    public val tool: CuTItemStack?,
    public val correctToolUsed: Boolean,
    public val cause: BlockBreakCause,
    public var drops: MutableList<ItemStack> = mutableListOf(),
    public var experience: Int = 0,
) {
    private val definition: CustomTile<*>
        get() = tile.identity.customTile
            ?: error("Block at ${tile.location} is not a native custom block.")

    public fun placementItemOrNull(): ItemStack? = definition.placementItemOrNull()?.createItemStack()?.vanilla()

    public fun requirePlacementItem(): ItemStack = placementItemOrNull()
        ?: error("Custom block ${definition.id} has no associated placement item.")
}

internal fun defaultBlockDrops(context: BlockDropContext): List<ItemStack> {
    if (context.cause == BlockBreakCause.Creative) return emptyList()
    return listOfNotNull(context.placementItemOrNull())
}
