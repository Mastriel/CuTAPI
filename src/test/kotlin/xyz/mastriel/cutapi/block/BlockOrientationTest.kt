package xyz.mastriel.cutapi.block

import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.testing.MockBukkitTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import java.lang.reflect.Proxy

public class BlockOrientationTest : MockBukkitTest() {
    @Test
    public fun `horizontal orientation defaults to facing towards the placer`() {
        val block = customBlock(id("test:default_orientation")) {
            itemPolicy = BlockItemPolicy.None
            states { define(HorizontalFacingState) { HorizontalFacingState.North } }
            orientation { horizontal() }
        }

        val orientation = requireNotNull(block.descriptor.orientation)

        assertSame(PlacementFacing.TowardsPlacer, orientation.placementFacing)
        assertSame(HorizontalFacingState, orientation.facingState)
    }

    @Test
    public fun `placement facing constants are relative to the placer`() {
        val world = server.addSimpleWorld("placement_facing")
        val placer = placerFacing(BlockFace.NORTH)
        val context = PlacementFacingContext(placer, world.getBlockAt(0, 64, 0))

        assertEquals(placer.facing.oppositeFace, PlacementFacing.TowardsPlacer.resolve(context))
        assertEquals(placer.facing, PlacementFacing.AwayFromPlacer.resolve(context))
    }

    @Test
    public fun `custom placement facing selects the authoritative initial state`() {
        val block = customBlock(id("test:custom_orientation")) {
            itemPolicy = BlockItemPolicy.None
            states { define(HorizontalFacingState) { HorizontalFacingState.North } }
            orientation {
                horizontal()
                placementFacing(PlacementFacing { BlockFace.WEST })
            }
        }
        val world = server.addSimpleWorld("custom_placement_facing")
        val context = PlacementFacingContext(server.addPlayer(), world.getBlockAt(0, 64, 0))
        val descriptor = block.descriptor
        val orientation = requireNotNull(descriptor.orientation)

        val placed = orientation.stateForPlacement(descriptor.states, descriptor.states.defaultState, context)

        assertEquals(HorizontalFacingState.West, placed[HorizontalFacingState])
        assertEquals(270, orientation.modelYRotation(placed))
    }

    @Test
    public fun `horizontal orientation maps every facing to minecraft model rotation`() {
        val descriptor = blockDescriptor {
            itemPolicy = BlockItemPolicy.None
            states { define(HorizontalFacingState) { HorizontalFacingState.North } }
            orientation { horizontal() }
        }
        val orientation = requireNotNull(descriptor.orientation)

        assertEquals(0, orientation.modelYRotation(descriptor.states.state(mapOf(HorizontalFacingState to HorizontalFacingState.North))))
        assertEquals(90, orientation.modelYRotation(descriptor.states.state(mapOf(HorizontalFacingState to HorizontalFacingState.East))))
        assertEquals(180, orientation.modelYRotation(descriptor.states.state(mapOf(HorizontalFacingState to HorizontalFacingState.South))))
        assertEquals(270, orientation.modelYRotation(descriptor.states.state(mapOf(HorizontalFacingState to HorizontalFacingState.West))))
    }

    @Test
    public fun `built in full facing state exposes all six native permutations`() {
        val states = blockStates {
            define(FacingState) { FacingState.North }
        }

        assertEquals(FacingState.entries.toList(), FacingState.values)
        assertEquals(6, states.permutationCount)
        assertEquals(FacingState.North, states.defaultState[FacingState])
    }

    @Test
    public fun `horizontal orientation rejects incomplete direction enums`() {
        val facing = BlockStateType.Enum<IncompleteFacing>("facing")

        val failure = assertFailsWith<IllegalArgumentException> {
            blockDescriptor {
                states { define(facing) { IncompleteFacing.North } }
                orientation { horizontal(facing) }
            }
        }

        assertEquals(true, failure.message.orEmpty().contains("exactly North, East, South, and West"))
    }
}

private fun placerFacing(face: BlockFace): Player =
    Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getFacing" -> face
            "toString" -> "PlacementFacingTestPlacer"
            else -> error("Unexpected Player method ${method.name}.")
        }
    } as Player

private enum class IncompleteFacing {
    North,
    South,
}
