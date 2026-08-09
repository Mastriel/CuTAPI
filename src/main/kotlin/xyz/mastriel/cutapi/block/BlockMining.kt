package xyz.mastriel.cutapi.block

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.attachments.ToolCategory
import xyz.mastriel.cutapi.item.attachments.ToolTier

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
) {
    init {
        require(!hardness.isNaN()) { "Block hardness cannot be NaN." }
        require(explosionResistance >= 0.0f && !explosionResistance.isNaN()) {
            "Block explosion resistance must be a non-negative number."
        }
    }
}

public class BlockSettingsBuilder {
    public var hardness: Float = 1.0f
    public var explosionResistance: Float = 1.0f
    public var effectiveTools: Set<ToolCategory> = emptySet()
    public var minimumToolTier: ToolTier? = null
    public var requiresCorrectToolForDrops: Boolean = false

    public fun build(): BlockSettings = BlockSettings(
        hardness = hardness,
        explosionResistance = explosionResistance,
        effectiveTools = effectiveTools.toSet(),
        minimumToolTier = minimumToolTier,
        requiresCorrectToolForDrops = requiresCorrectToolForDrops,
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
    public fun placementItemOrNull(): ItemStack? = tile.type.placementItemOrNull()?.createItemStack()?.vanilla()

    public fun requirePlacementItem(): ItemStack = placementItemOrNull()
        ?: error("Custom block ${tile.type.id} has no associated placement item.")
}

internal fun defaultBlockDrops(context: BlockDropContext): List<ItemStack> {
    if (context.cause == BlockBreakCause.Creative) return emptyList()
    return listOfNotNull(context.placementItemOrNull())
}
