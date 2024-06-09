package xyz.mastriel.cutapi.block

import org.bukkit.*
import org.bukkit.block.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.block.CustomBlockManager.Companion.tags
import xyz.mastriel.cutapi.block.behaviors.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*


public sealed interface CustomTile<T : CuTPlacedTile> : Identifiable {
    public val descriptor: TileDescriptor
    public val placedBlockTypeClass: KClass<out T>

    public fun setAt(location: Location) {
        val block = location.toBlockLocation().block
        setAt(block)
    }

    public fun setAt(block: Block) {
        block.tags.setIdentifier("cutapi.CuTID", id)
        when (val strategy = descriptor.blockStrategy) {
            is BlockStrategy.FakeEntity -> block.type = Material.BARRIER
            is BlockStrategy.Mushroom -> block.type = Material.RED_MUSHROOM
            is BlockStrategy.NoteBlock -> block.type = Material.NOTE_BLOCK
            is BlockStrategy.Vanilla -> block.type = strategy.material
        }
    }

    public companion object : IdentifierRegistry<CustomTile<*>>("Custom Tiles") {

    }
}

public class CustomBlock<T : CuTPlacedBlock>(
    override val id: Identifier,
    override val descriptor: BlockDescriptor,
    override val placedBlockTypeClass: KClass<out T>
) : CustomTile<T>, BehaviorHolder<BlockBehavior> {


    private val behaviorHolder by lazy { blockBehaviorHolder(this) }
    override fun hasBehavior(behavior: KClass<out BlockBehavior>): Boolean = behaviorHolder.hasBehavior(behavior)
    override fun hasBehavior(behaviorId: Identifier): Boolean = behaviorHolder.hasBehavior(behaviorId)
    override fun getAllBehaviors(): Set<BlockBehavior> = behaviorHolder.getAllBehaviors()
    override fun <T : BlockBehavior> getBehaviorOrNull(behaviorId: Identifier): T? =
        behaviorHolder.getBehaviorOrNull(behaviorId)

    override fun <T : BlockBehavior> getBehaviorOrNull(behavior: KClass<T>): T? =
        behaviorHolder.getBehaviorOrNull(behavior)

    override fun <T : BlockBehavior> getBehavior(behaviorId: Identifier): T = behaviorHolder.getBehavior(behaviorId)
    override fun <T : BlockBehavior> getBehavior(behavior: KClass<T>): T = behaviorHolder.getBehavior(behavior)


    public companion object : IdentifierRegistry<CustomBlock<*>>("Custom Blocks") {

        public val Unknown: CustomBlock<CuTPlacedBlock> = customBlock(
            id(Plugin, "unknown_block")
        ) {
            blockStrategy = BlockStrategy.Vanilla(Material.BARRIER)
            itemPolicy = BlockItemPolicy.Generate()

        }

        init {
            register(Unknown)

            addHook(HookPriority.FIRST) {
                CustomTile.register(item)
            }
        }
    }
}

public class CustomTileEntity<T : CuTPlacedTileEntity>(
    override val id: Identifier,
    override val descriptor: TileEntityDescriptor,
    override val placedBlockTypeClass: KClass<out T>
) : CustomTile<T>, BehaviorHolder<TileEntityBehavior> {

    private val behaviorHolder by lazy { tileEntityBehaviorHolder(this) }
    override fun hasBehavior(behavior: KClass<out TileEntityBehavior>): Boolean = behaviorHolder.hasBehavior(behavior)
    override fun hasBehavior(behaviorId: Identifier): Boolean = behaviorHolder.hasBehavior(behaviorId)
    override fun getAllBehaviors(): Set<TileEntityBehavior> = behaviorHolder.getAllBehaviors()
    override fun <T : TileEntityBehavior> getBehaviorOrNull(behaviorId: Identifier): T? =
        behaviorHolder.getBehaviorOrNull(behaviorId)

    override fun <T : TileEntityBehavior> getBehaviorOrNull(behavior: KClass<T>): T? =
        behaviorHolder.getBehaviorOrNull(behavior)

    override fun <T : TileEntityBehavior> getBehavior(behaviorId: Identifier): T =
        behaviorHolder.getBehavior(behaviorId)

    override fun <T : TileEntityBehavior> getBehavior(behavior: KClass<T>): T = behaviorHolder.getBehavior(behavior)

    public companion object : IdentifierRegistry<CustomTileEntity<*>>("Custom Tile Entities") {

        public val Unknown: CustomTileEntity<CuTPlacedTileEntity> = customTileEntity(
            id(Plugin, "unknown_tile_entity")
        ) {
            blockStrategy = BlockStrategy.Vanilla(Material.BARRIER)
            itemPolicy = BlockItemPolicy.Generate()
        }

        init {
            register(Unknown)

            addHook(HookPriority.FIRST) {
                CustomTile.register(item)
            }
        }
    }
}