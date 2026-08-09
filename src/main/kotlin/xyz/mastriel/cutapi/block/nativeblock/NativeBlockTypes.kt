@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier as MinecraftIdentifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import org.bukkit.block.Block as BukkitBlock
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.block.CraftBlock
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.block.CuTPlacedTile
import xyz.mastriel.cutapi.block.CustomBlockState
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.registry.Identifier
import java.util.IdentityHashMap

public object NativeBlockTypes {
    private val definitionsByBlock: MutableMap<Block, CustomTile<*>> = IdentityHashMap()
    private val blocksById: MutableMap<Identifier, NativeCustomBlock> = linkedMapOf()
    private val blockEntityTypes: MutableMap<Identifier, BlockEntityType<NativeCustomBlockEntity>> = linkedMapOf()

    internal fun bind(definition: CustomTile<*>, block: NativeCustomBlock) {
        check(blocksById.putIfAbsent(definition.id, block) == null) {
            "Native block ${definition.id} is already bound."
        }
        definitionsByBlock[block] = definition
    }

    internal fun bindBlockEntityType(
        id: Identifier,
        type: BlockEntityType<NativeCustomBlockEntity>,
    ) {
        check(blockEntityTypes.putIfAbsent(id, type) == null) { "Native block entity type $id is already bound." }
    }

    public fun getMinecraft(definition: CustomTile<*>): Block =
        blocksById[definition.id] ?: error("Native block ${definition.id} has not been installed.")

    internal fun getNative(definition: CustomTile<*>): NativeCustomBlock =
        blocksById[definition.id] ?: error("Native block ${definition.id} has not been installed.")

    internal fun getBlockEntityType(id: Identifier): BlockEntityType<NativeCustomBlockEntity> =
        blockEntityTypes[id] ?: error("Native block entity type $id has not been installed.")

    internal fun isCustomBlockEntityType(type: BlockEntityType<*>): Boolean =
        type in blockEntityTypes.values

    public fun isInstalled(id: Identifier): Boolean = id in blocksById

    public fun definition(state: BlockState): CustomTile<*>? = definitionsByBlock[state.block]

    public fun definition(block: BukkitBlock): CustomTile<*>? =
        definitionsByBlock[(block as? CraftBlock)?.nms?.block]

    public fun idOf(state: BlockState): Identifier? = definition(state)?.id

    public fun idOf(block: Block): Identifier? {
        val location: MinecraftIdentifier = BuiltInRegistries.BLOCK.getKey(block) ?: return null
        if (block !in definitionsByBlock) return null
        return Identifier(location.namespace, location.path)
    }

    public fun defaultState(definition: CustomTile<*>): BlockState = getNative(definition).defaultBlockState()

    public fun state(definition: CustomTile<*>, state: CustomBlockState): BlockState {
        val native = getNative(definition)
        return native.stateSchema.toNative(native.defaultBlockState(), state)
    }

    public fun customState(state: BlockState): CustomBlockState? =
        (state.block as? NativeCustomBlock)?.stateSchema?.toCustom(state)

    public fun setAt(block: BukkitBlock, definition: CustomTile<*>, state: CustomBlockState): CuTPlacedTile {
        NativeBlockLifecycle.requireActive()
        val world = (block.world as CraftWorld).handle
        val pos = BlockPos(block.x, block.y, block.z)
        check(world.setBlock(pos, state(definition, state), Block.UPDATE_ALL)) {
            "Minecraft rejected placement of custom block ${definition.id} at ${block.location}."
        }
        NativeBlockDisplayManager.refresh(block)
        return placedTile(block)
    }

    public fun placedTile(block: BukkitBlock): CuTPlacedTile {
        require(definition(block) != null) { "Block at ${block.location} is not a native custom block." }
        return CuTAPI.blockManager.getPlacedTile(block)
    }
}

public enum class NativeBlockState {
    Collecting,
    Installing,
    Frozen,
    Active,
    Stopped,
    Failed,
}

public object NativeBlockLifecycle {
    @Volatile
    public var state: NativeBlockState = NativeBlockState.Collecting
        internal set

    public fun requireActive() {
        check(state == NativeBlockState.Active) {
            "Native custom blocks are not active (current state: $state)."
        }
    }
}
