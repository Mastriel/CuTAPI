package xyz.mastriel.cutapi.block

import org.bukkit.*
import xyz.mastriel.cutapi.*
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

    public val tileEntityTypeId: Identifier = id(Plugin, "builtin_tile_entity")
    public val blockTypeId: Identifier = id(Plugin, "builtin_block")
    private val types = mutableMapOf<Identifier, CustomTileType<*>>()

    public fun getPlacedTile(block: BukkitBlock): CuTPlacedTile {
        val type = types[block.customId] ?: error("Block ${block.customId} is not a custom block!")
        return type.constructor(block)
    }

    /**
     * Returns a list of the custom placed tiles in a chunk, represented as vanilla blocks.
     */
    private fun chunkVanillaBlocks(chunk: Chunk) =
        chunk.persistentDataContainer.keys
            .filter { it.key.startsWith("CuTBlockData") }
            .mapNotNull { namespacedKey ->
                val (_, x, y, z) = namespacedKey.key.split("/").map { it.toIntOrNull() }

                if (x == null || y == null || z == null) return@mapNotNull null

                val block = chunk.getBlock(x, y, z)
                if (block.tags.getIdentifier(CUT_ID_KEY) == null) return@mapNotNull null

                block
            }

    public fun getType(id: Identifier): KClass<out CuTPlacedTile>? {
        return types[id]?.kClass
    }

    public fun getType(kClass: KClass<out CuTPlacedTile>): Identifier? {
        return types.toList().firstOrNull { it.second.kClass == kClass }?.first
    }

    public fun <T : CuTPlacedTile> placeTile(location: Location, tile: CustomTile<T>): CuTPlacedTile {
        val block = location.block

        block.tags.setIdentifier(CUT_ID_KEY, tile.id)

        val typeId = getType(tile.placedBlockTypeClass)
        block.tags.setIdentifier(CUT_TYPE_KEY, typeId)

        val wrapped = block.wrap<CuTPlacedTile>()
        return wrapped!!
    }


    /**
     * Returns a list of the custom placed tiles in a chunk.
     */
    private inline fun <reified T : CuTPlacedTile> chunkCustomTiles(chunk: Chunk) =
        chunkVanillaBlocks(chunk)
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
        public const val CUT_ID_KEY: String = "cutapi.CuTID"
        public const val CUT_TYPE_KEY: String = "cutapi.CuTType"


        public val BukkitBlock.isCustom: Boolean
            get() = this.tags.getIdentifier(CUT_ID_KEY) != null

        public val BukkitBlock.customId: Identifier
            get() = this.tags.getIdentifier(CUT_ID_KEY) ?: unknownID()

        public val BukkitBlock.customTypeId: Identifier
            get() {
                val id = this.tags.getIdentifier(CUT_TYPE_KEY)
                if (id != null) return id
                val manager = CuTAPI.blockManager
                return when (customTileOrNull) {
                    is CustomBlock -> manager.blockTypeId
                    is CustomTileEntity -> manager.tileEntityTypeId
                    else -> unknownID()
                }
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