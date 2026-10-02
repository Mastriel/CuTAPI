@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import xyz.mastriel.cutapi.attachment.BlockAttachment
import xyz.mastriel.cutapi.block.nativeblock.blockStateModelPath
import xyz.mastriel.cutapi.block.nativeblock.blockTextureModelLocation
import xyz.mastriel.cutapi.block.nativeblock.blockTextureResourcePath
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.data.VariantSerializer
import xyz.mastriel.cutapi.data.schema
import xyz.mastriel.cutapi.registry.RegistryPriority
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.testing.MockBukkitTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertIs
import kotlin.test.assertTrue

public class BlockStateVisualApiTest : MockBukkitTest() {
    @Test
    public fun `state definition atomically captures typed defaults and every permutation`() {
        val lit = BlockStateType.Boolean("lit")
        val facing = BlockStateType.Enum<TestFacing>("facing")
        val explicitFacing = BlockStateType.Enum(TestFacing::class, "explicit_facing")
        var defaults = 0
        val definition = blockStates {
            define(lit) { defaults++; false }
            define(facing) { defaults++; TestFacing.North }
        }

        assertEquals(2, defaults)
        assertEquals(TestFacing::class, facing.enumClass)
        assertEquals(TestFacing::class, explicitFacing.enumClass)
        assertEquals(6, definition.permutationCount)
        assertFalse(definition.defaultState[lit])
        assertEquals(TestFacing.North, definition.defaultState[facing])
        assertEquals(
            setOf("facing=north,lit=false", "facing=north,lit=true"),
            definition.permutations.filter { it[facing] == TestFacing.North }.map { it.canonicalValues() }.toSet(),
        )
        assertEquals("facing-north__lit-false", blockStateModelPath(definition.defaultState))
        assertEquals("default", blockStateModelPath(BlockStateDefinition.Empty.defaultState))
    }

    @Test
    public fun `cubic block textures use the block atlas`() {
        assertEquals(
            "block/cutapi/blocks/device_terminal_top",
            blockTextureResourcePath("blocks/device_terminal_top"),
        )
        assertEquals(
            "cutapi:block/cutapi/blocks/device_terminal_top",
            blockTextureModelLocation("cutapi", "blocks/device_terminal_top"),
        )
    }

    @Test
    public fun `state default producers run independently for each descriptor`() {
        val lit = BlockStateType.Boolean("lit")
        var invocations = 0
        val first = customBlock(id("test:first_state_default")) {
            states { define(lit) { invocations++; false } }
        }
        val second = customBlock(id("test:second_state_default")) {
            states { define(lit) { invocations++; false } }
        }

        assertEquals(2, invocations)
        assertNotSame(first.descriptor.states, second.descriptor.states)
    }

    @Test
    public fun `visual allocation is deterministic and shared identities reuse one slot`() {
        val first = request("test:a", "lit=false", "shared")
        val second = request("test:b", "lit=true", "shared")
        val third = request("test:c", "", null)

        val forward = BlockVisualAllocator.allocate(listOf(first, second, third), "1.21.11", "hash")
        val reverse = BlockVisualAllocator.allocate(listOf(third, second, first), "1.21.11", "hash")

        assertEquals(forward.assignments, reverse.assignments)
        assertEquals(
            forward.assignments.getValue(first.key),
            forward.assignments.getValue(second.key),
        )
        assertEquals(1_149, forward.capacity.getValue(BlockVisualCarrier.NoteBlock))
        assertEquals(189, forward.capacity.getValue(BlockVisualCarrier.Mushroom))
    }

    @Test
    public fun `finite visual allocation fails instead of switching methods`() {
        val requests = (0..BlockVisualCarrier.NoteBlock.capacity).map { index ->
            request("test:block_$index", "", null)
        }
        val failure = assertFailsWith<IllegalStateException> {
            BlockVisualAllocator.allocate(requests, "1.21.11", "hash")
        }
        assertTrue(failure.message.orEmpty().contains("capacity exhausted"))
    }

    @Test
    public fun `display entity allocation retains explicit brightness`() {
        val brightness = Display.Brightness(15, 15)
        val request = BlockVisualAllocationRequest(
            BlockVisualKey(id("test:lit_display"), ""),
            BlockVisualMethod.DisplayEntity(
                Material.BARRIER.createBlockData(),
                ItemDisplay.ItemDisplayTransform.FIXED,
                brightness,
            ),
        )

        val allocation = assertIs<BlockVisualAllocation.DisplayEntity>(
            BlockVisualAllocator.allocate(listOf(request), "1.21.11", "hash")
                .assignments.getValue(request.key),
        )

        assertEquals(brightness, allocation.brightness)
    }

    @Test
    public fun `display entity defaults to raw block model scale`() {
        val method = BlockVisualMethod.DisplayEntity(Material.BARRIER.createBlockData())

        assertEquals(ItemDisplay.ItemDisplayTransform.NONE, method.transform)
    }

    @Test
    public fun `descriptor exposes mining settings attachments and per state visuals`() {
        val powered = BlockStateType.Boolean("powered")
        val definition = customTileEntity(id("test:configured_tile")) {
            itemPolicy = BlockItemPolicy.None
            states { define(powered) { false } }
            settings {
                hardness = 3.0f
                explosionResistance = 6.0f
                requiresCorrectToolForDrops = true
                occludes = false
            }
            attach(TestBlockAttachment(4))
            visuals { state ->
                BlockVisualMethod.Vanilla(
                    (if (state[powered]) Material.REDSTONE_BLOCK else Material.COPPER_BLOCK).createBlockData(),
                )
            }
        }

        assertEquals(3.0f, definition.descriptor.settings.hardness)
        assertEquals(6.0f, definition.descriptor.settings.explosionResistance)
        assertFalse(definition.descriptor.settings.occludes)
        assertEquals(TestBlockAttachment(4), definition.getAttachment(TestBlockAttachment))
        assertEquals(
            Material.REDSTONE_BLOCK,
            (definition.descriptor.visualMethod(definition.descriptor.states.state(mapOf(powered to true))) as
                BlockVisualMethod.Vanilla).state.material,
        )
    }

    @Test
    public fun `block settings occlude by default`() {
        assertTrue(defaultBlockDescriptor().settings.occludes)
    }

    @Test
    public fun `visual producers remain deferred until visual resolution`() {
        var invocations = 0
        val definition = customBlock(id("test:deferred_visual")) {
            visual {
                invocations++
                BlockVisualMethod.NoteBlock
            }
        }

        assertEquals(0, invocations)
        assertEquals(
            BlockVisualMethod.NoteBlock,
            definition.descriptor.visualMethod(definition.descriptor.states.defaultState),
        )
        assertEquals(1, invocations)
    }

    @Test
    public fun `tile systems extend block systems`() {
        assertTrue(BlockSystem::class.java.isAssignableFrom(TileSystem::class.java))
    }

    private fun request(id: String, state: String, identity: String?): BlockVisualAllocationRequest =
        BlockVisualAllocationRequest(
            BlockVisualKey(xyz.mastriel.cutapi.registry.id(id), state),
            BlockVisualMethod.NoteBlock,
            identity,
        )
}

private enum class TestFacing {
    North,
    East,
    South,
}

private data class TestBlockAttachment(val value: Int) : BlockAttachment {
    companion object : Schema<TestBlockAttachment> by schema(id("test:block_attachment"), {
        property(TestBlockAttachment::value, VariantSerializer.Int)
    })
}
