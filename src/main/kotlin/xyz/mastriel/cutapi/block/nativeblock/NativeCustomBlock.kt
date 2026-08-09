@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.block.BlockVisualMethod

internal open class NativeCustomBlock(
    internal val definition: CustomTile<*>,
    properties: BlockBehaviour.Properties,
) : Block(properties) {
    internal val stateSchema: NativeBlockStateSchema = NativeBlockConstruction.schema()

    init {
        registerDefaultState(stateSchema.toNative(defaultBlockState(), definition.descriptor.states.defaultState))
    }

    final override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        NativeBlockConstruction.schema().contribute(builder)
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = carrierState(state).getShape(level, pos, context)

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = carrierState(state).getCollisionShape(level, pos, context)

    override fun getOcclusionShape(state: BlockState): VoxelShape =
        carrierState(state).getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)

    override fun onPlace(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        oldState: BlockState,
        movedByPiston: Boolean,
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston)
        refreshDisplay(level, pos)
    }

    override fun affectNeighborsAfterRemoval(
        state: BlockState,
        level: ServerLevel,
        pos: BlockPos,
        movedByPiston: Boolean,
    ) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston)
        refreshDisplay(level, pos)
    }

    private fun refreshDisplay(level: Level, pos: BlockPos) {
        val serverLevel = level as? ServerLevel ?: return
        NativeBlockDisplayManager.refresh(serverLevel.world.getBlockAt(pos.x, pos.y, pos.z))
    }

    private fun carrierState(state: BlockState): BlockState {
        val customState = stateSchema.toCustom(state)
        val carrier = when (val method = definition.descriptor.visualMethod(customState)) {
            is BlockVisualMethod.Vanilla -> method.state.nmsState()
            BlockVisualMethod.NoteBlock -> Blocks.NOTE_BLOCK.defaultBlockState()
            BlockVisualMethod.Mushroom -> Blocks.RED_MUSHROOM_BLOCK.defaultBlockState()
            is BlockVisualMethod.DisplayEntity -> method.carrier.nmsState()
        }
        check(carrier.block !is NativeCustomBlock) {
            "Custom block ${definition.id} cannot use another native custom block as its client shape carrier."
        }
        return carrier
    }

    companion object {
        fun create(
            definition: CustomTile<*>,
            key: ResourceKey<Block>,
            schema: NativeBlockStateSchema,
        ): NativeCustomBlock = NativeBlockConstruction.with(schema) {
            NativeCustomBlock(definition, properties(definition, key))
        }

        internal fun properties(
            definition: CustomTile<*>,
            key: ResourceKey<Block>,
        ): BlockBehaviour.Properties {
            val settings = definition.descriptor.settings
            var properties = BlockBehaviour.Properties.of()
                .setId(key)
                .strength(settings.hardness, settings.explosionResistance)
                .noLootTable()
            if (settings.requiresCorrectToolForDrops) properties = properties.requiresCorrectToolForDrops()
            return properties
        }
    }
}

internal class NativeCustomTileBlock private constructor(
    definition: CustomTileEntity<*>,
    properties: BlockBehaviour.Properties,
) : NativeCustomBlock(definition, properties), EntityBlock {
    private val tileDefinition: CustomTileEntity<*> get() = definition as CustomTileEntity<*>

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        NativeCustomBlockEntity(tileDefinition, pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (type !== NativeBlockTypes.getBlockEntityType(tileDefinition.id)) return null
        return BlockEntityTicker { _, _, _, entity ->
            (entity as? NativeCustomBlockEntity)?.tick()
        }
    }

    companion object {
        fun create(
            definition: CustomTileEntity<*>,
            key: ResourceKey<Block>,
            schema: NativeBlockStateSchema,
        ): NativeCustomTileBlock = NativeBlockConstruction.with(schema) {
            NativeCustomTileBlock(definition, properties(definition, key))
        }
    }
}
