package xyz.mastriel.cutapi.block

import org.bukkit.entity.*
import org.bukkit.event.block.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.system.*

public open class BlockSystemContext(public val tile: CuTPlacedTile) {
    public fun <T : BlockAttachment> attachment(schema: Schema<T>): T = tile.getAttachment(schema)
}

public class BlockPlaceContext(
    tile: CuTPlacedTile,
    public val player: Player?,
    public val event: BlockPlaceEvent?,
) : BlockSystemContext(tile)

public class BlockInteractContext(
    tile: CuTPlacedTile,
    public val player: Player,
    public val event: PlayerInteractEvent,
) : BlockSystemContext(tile)

public class BlockNeighborChangeContext(tile: CuTPlacedTile) : BlockSystemContext(tile)

public class BlockStateChangeContext(
    tile: CuTPlacedTile,
    public val previous: CustomBlockState,
    public val current: CustomBlockState,
) : BlockSystemContext(tile)

public class BlockPreBreakContext(
    tile: CuTPlacedTile,
    public val player: Player?,
    public val cause: BlockBreakCause,
    public val event: BlockBreakEvent?,
    public var isCancelled: Boolean = false,
) : BlockSystemContext(tile)

public class BlockPostBreakContext(
    tile: CuTPlacedTile,
    public val player: Player?,
    public val cause: BlockBreakCause,
    public val drops: List<org.bukkit.inventory.ItemStack>,
    public val experience: Int,
) : BlockSystemContext(tile)

public class BlockExplosionContext(
    tile: CuTPlacedTile,
    public val event: BlockExplodeEvent?,
) : BlockSystemContext(tile)

public open class TileSystemContext(public val tileEntity: CuTPlacedTileEntity) :
    BlockSystemContext(tileEntity)

public class TileDataLoadContext(tileEntity: CuTPlacedTileEntity) : TileSystemContext(tileEntity)
public class TileLoadContext(tileEntity: CuTPlacedTileEntity) : TileSystemContext(tileEntity)
public class TileTickContext(tileEntity: CuTPlacedTileEntity) : TileSystemContext(tileEntity)
public class TileSaveContext(tileEntity: CuTPlacedTileEntity) : TileSystemContext(tileEntity)
public class TileAttachmentChangeContext(
    tileEntity: CuTPlacedTileEntity,
    public val schema: Schema<out BlockAttachment>,
) : TileSystemContext(tileEntity)

public class TileUnloadContext(tileEntity: CuTPlacedTileEntity) : TileSystemContext(tileEntity)
public class TileRemoveContext(tileEntity: CuTPlacedTileEntity) : TileSystemContext(tileEntity)

public interface BlockSystem : CuTSystem<CuTPlacedTile> {
    public fun onPlaced(context: BlockPlaceContext) {}
    public fun onLeftClick(context: BlockInteractContext) {}
    public fun onRightClick(context: BlockInteractContext) {}
    public fun onNeighborChanged(context: BlockNeighborChangeContext) {}
    public fun onStateChanged(context: BlockStateChangeContext) {}
    public fun onPreBreak(context: BlockPreBreakContext) {}
    public fun onDrops(context: BlockDropContext) {}
    public fun onPostBreak(context: BlockPostBreakContext) {}
    public fun onExploded(context: BlockExplosionContext) {}

    public companion object : IdentifierRegistry<BlockSystem>(id("cutapi:registry/block_system")) {
        internal val DeferredRegistry = defer()

        internal inline fun dispatch(tile: CuTPlacedTile, action: (BlockSystem) -> Unit) {
            systemsFor(tile).forEach(action)
        }

        internal fun systemsFor(tile: CuTPlacedTile): List<BlockSystem> =
            getAllValues()
                .asSequence()
                .filter { system -> system !is TileSystem || tile is CuTPlacedTileEntity }
                .filter { system -> system.prerequisite(tile) }
                .sortedByDescending(BlockSystem::priority)
                .toList()
    }
}

public interface TileSystem : BlockSystem {
    public fun tilePrerequisite(tile: CuTPlacedTileEntity): Boolean = true

    /** Runtime dispatch also enforces this type gate, even if an implementation overrides this method. */
    override fun prerequisite(target: CuTPlacedTile): Boolean =
        target is CuTPlacedTileEntity && tilePrerequisite(target)

    public fun onDataLoaded(context: TileDataLoadContext) {}
    public fun onLoaded(context: TileLoadContext) {}
    public fun onTick(context: TileTickContext) {}
    public fun onBeforeSave(context: TileSaveContext) {}
    public fun onAttachmentChanged(context: TileAttachmentChangeContext) {}
    public fun onUnloaded(context: TileUnloadContext) {}
    public fun onRemoved(context: TileRemoveContext) {}
}

public fun generalBlockSystem(
    id: Identifier,
    priority: RegistryPriority = RegistryPriority.Medium,
): BlockSystem = object : BlockSystem {
    override val id: Identifier = id
    override val priority: RegistryPriority = priority
    override fun prerequisite(target: CuTPlacedTile): Boolean = true
}

public fun attachmentBlockSystem(
    attachment: Schema<out BlockAttachment>,
    id: Identifier = attachment.id / "system",
    priority: RegistryPriority = RegistryPriority.Medium,
): BlockSystem = object : BlockSystem {
    override val id: Identifier = id
    override val priority: RegistryPriority = priority
    override fun prerequisite(target: CuTPlacedTile): Boolean = target.hasAttachment(attachment)
}

public fun generalTileSystem(
    id: Identifier,
    priority: RegistryPriority = RegistryPriority.Medium,
): TileSystem = object : TileSystem {
    override val id: Identifier = id
    override val priority: RegistryPriority = priority
    override fun prerequisite(target: CuTPlacedTile): Boolean = true
}

public fun attachmentTileSystem(
    attachment: Schema<out BlockAttachment>,
    id: Identifier = attachment.id / "system",
    priority: RegistryPriority = RegistryPriority.Medium,
): TileSystem = object : TileSystem {
    override val id: Identifier = id
    override val priority: RegistryPriority = priority
    override fun prerequisite(target: CuTPlacedTile): Boolean = target.hasAttachment(attachment)
}
