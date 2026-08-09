package xyz.mastriel.cutapi.block

import org.bukkit.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.block.nativeblock.*
import org.bukkit.craftbukkit.*
import org.bukkit.craftbukkit.block.*
import net.minecraft.world.level.chunk.status.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import kotlin.reflect.*

private typealias BukkitBlock = org.bukkit.block.Block

private sealed class CustomTileType<T : CuTPlacedTile>(val kClass: KClass<out T>, val constructor: (BukkitBlock) -> T) {

    class Block(kClass: KClass<out CuTPlacedBlock>, constructor: (BukkitBlock) -> CuTPlacedBlock) :
        CustomTileType<CuTPlacedBlock>(kClass, constructor)

    class TileEntity(kClass: KClass<out CuTPlacedTileEntity>, constructor: (BukkitBlock) -> CuTPlacedTileEntity) :
        CustomTileType<CuTPlacedTileEntity>(kClass, constructor)
}

public class CustomBlockManager {

    public val tileEntityTypeId: Identifier = id("cutapi:builtin_tile_entity")
    public val blockTypeId: Identifier = id("cutapi:builtin_block")
    private val types = mutableMapOf<Identifier, CustomTileType<*>>()

    public fun getPlacedTile(block: BukkitBlock): CuTPlacedTile {
        val type = types[block.customTypeId] ?: error("Block ${block.customId} has no registered placed wrapper type!")
        return type.constructor(block)
    }

    private fun chunkNativeTileBlocks(chunk: Chunk): List<BukkitBlock> {
        val handle = (chunk as CraftChunk).getHandle(ChunkStatus.FULL)
        return handle.blockEntities.entries
            .filter { it.value is NativeCustomBlockEntity }
            .map { (pos, _) -> chunk.world.getBlockAt(pos.x, pos.y, pos.z) }
    }

    public fun getType(id: Identifier): KClass<out CuTPlacedTile>? {
        return types[id]?.kClass
    }

    public fun getType(kClass: KClass<out CuTPlacedTile>): Identifier? {
        return types.toList().firstOrNull { it.second.kClass == kClass }?.first
    }

    public fun <T : CuTPlacedTile> placeTile(location: Location, tile: CustomTile<T>): CuTPlacedTile {
        val block = location.block
        val typeId = requireNotNull(getType(tile.placedBlockTypeClass)) {
            "Custom tile ${tile.id} uses unregistered placed wrapper type " +
                "${tile.placedBlockTypeClass.qualifiedName}. Register it with registerPlacedTileType first."
        }

        return NativeBlockTypes.setAt(block, tile, tile.descriptor.states.defaultState)
    }


    /**
     * Returns a list of the custom placed tiles in a chunk.
     */
    private inline fun <reified T : CuTPlacedTile> chunkCustomTiles(chunk: Chunk) =
        chunkNativeTileBlocks(chunk)
            .mapNotNull { it.wrap<T>() }


    public fun getTileEntities(chunk: Chunk): Collection<CuTPlacedTileEntity> {
        return chunkCustomTiles<CuTPlacedTileEntity>(chunk)
    }

    @JvmName("getTileEntitiesByType")
    public inline fun <reified T : CuTPlacedTileEntity> getTileEntities(chunk: Chunk): Collection<T> {
        return getTileEntities(chunk).filterIsInstance<T>()
    }

    public fun getLoadedTileEntities(world: World): List<CuTPlacedTileEntity> {
        return world.loadedChunks.flatMap { chunk ->
            getTileEntities(chunk)
        }
    }


    @Suppress("UNCHECKED_CAST")
    public fun <T : CuTPlacedTile> registerPlacedTileType(
        id: Identifier,
        kClass: KClass<T>,
        constructor: (BukkitBlock) -> T
    ) {
        val isTileEntity = kClass isAtleast CuTPlacedTileEntity::class
        val isBlock = kClass isAtleast CuTPlacedBlock::class

        if (isTileEntity && isBlock) error("Class $kClass inherits from both CuTPlacedTileEntity and CuTPlacedBlock! What the fuck?")

        val tileType = when {
            isTileEntity -> CustomTileType.TileEntity(
                kClass as KClass<out CuTPlacedTileEntity>, constructor as (BukkitBlock) -> CuTPlacedTileEntity
            )

            isBlock -> CustomTileType.Block(
                kClass as KClass<out CuTPlacedBlock>, constructor as (BukkitBlock) -> CuTPlacedBlock
            )

            else -> error("Class $kClass inherits from neither CuTPlacedTileEntity nor CuTPlacedBlock.")
        }

        types[id] = tileType
    }

    public inline fun <reified T : CuTPlacedTile> registerPlacedTileType(
        id: Identifier,
        noinline constructor: (BukkitBlock) -> T
    ): Unit = registerPlacedTileType(id, T::class, constructor)

    public companion object {
        public val CUT_ID_KEY: Identifier = id("cutapi:id")
        public val CUT_TYPE_KEY: Identifier = id("cutapi:type")


        public val BukkitBlock.isCustom: Boolean
            get() = NativeBlockTypes.definition(this) != null

        public val BukkitBlock.customId: Identifier
            get() = NativeBlockTypes.definition(this)?.id ?: unknownID()

        public val BukkitBlock.customTypeId: Identifier
            get() {
                val manager = CuTAPI.blockManager
                val definition = customTileOrNull ?: return unknownID()
                return manager.getType(definition.placedBlockTypeClass) ?: unknownID()
            }


        public val BukkitBlock.customTile: CustomTile<*>
            get() = CustomTile.get(customId)

        public val BukkitBlock.customTileOrNull: CustomTile<*>?
            get() = CustomTile.getOrNull(customId)

        public val BukkitBlock.tags: TagContainer
            get() = BlockDataTagContainer(this)

        @Suppress("UNCHECKED_CAST")
        public fun <T : CuTPlacedTile> BukkitBlock.wrap(): T? {
            return CuTAPI.blockManager.getPlacedTile(this) as? T
        }
    }
}
