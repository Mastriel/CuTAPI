@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.core.Direction
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import org.bukkit.block.BlockFace
import xyz.mastriel.cutapi.block.CustomBlockState
import xyz.mastriel.cutapi.block.TileDescriptor

internal fun TileDescriptor.modelRotation(state: CustomBlockState): Rotation =
    when (orientation?.modelYRotation(state) ?: 0) {
        0 -> Rotation.NONE
        90 -> Rotation.CLOCKWISE_90
        180 -> Rotation.CLOCKWISE_180
        270 -> Rotation.COUNTERCLOCKWISE_90
        else -> error("Unsupported model rotation for ${state.canonicalValues()}.")
    }

internal fun TileDescriptor.orientCarrier(
    carrier: BlockState,
    state: CustomBlockState,
): BlockState = carrier.rotate(modelRotation(state))

internal fun TileDescriptor.rotateCustomState(
    state: CustomBlockState,
    rotation: Rotation,
): CustomBlockState {
    val orientation = orientation ?: return state
    val rotated = rotation.rotate(orientation.facing(state).toNmsDirection()).toBukkitFace()
    return orientation.withFacing(states, state, rotated)
}

internal fun TileDescriptor.mirrorCustomState(
    state: CustomBlockState,
    mirror: Mirror,
): CustomBlockState {
    val orientation = orientation ?: return state
    val mirrored = mirror.mirror(orientation.facing(state).toNmsDirection()).toBukkitFace()
    return orientation.withFacing(states, state, mirrored)
}

private fun BlockFace.toNmsDirection(): Direction = when (this) {
    BlockFace.NORTH -> Direction.NORTH
    BlockFace.EAST -> Direction.EAST
    BlockFace.SOUTH -> Direction.SOUTH
    BlockFace.WEST -> Direction.WEST
    else -> error("$this is not a horizontal block face.")
}

private fun Direction.toBukkitFace(): BlockFace = when (this) {
    Direction.NORTH -> BlockFace.NORTH
    Direction.EAST -> BlockFace.EAST
    Direction.SOUTH -> BlockFace.SOUTH
    Direction.WEST -> BlockFace.WEST
    else -> error("$this is not a horizontal direction.")
}
