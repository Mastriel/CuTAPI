package xyz.mastriel.cutapi.block.inventory

import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.gui.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class BlockInventoryDefinitionTest : MockBukkitTest() {
    @Test
    public fun `contextual block presentations require and retain a typed context producer`() {
        val port = GuiSlotPort("contextual")
        val contextualGui: GuiDefinition<TestPresentationContext, InventoryView> = gui(
            id = id("test:contextual_machine_gui"),
            type = GuiType.Chest(1),
            context = guiContext<TestPresentationContext>(),
        ) {
            boundSlot(port, 0) {
                placeholder { ItemStack(Material.PAPER, context.level) }
            }
        }
        val tile = customTileEntity(id("test:contextual_machine")) {
            inventory(1) {
                slot(0, BlockInventorySlotAccess.Storage)
                presentation(
                    gui = { contextualGui },
                    context = {
                        TestPresentationContext(
                            viewerName = player.name,
                            level = 4,
                        )
                    },
                ) {
                    bind(0, port)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val presentation = assertNotNull(tile.descriptor.inventory?.presentation) as
            BlockInventoryPresentation<TestPresentationContext, InventoryView>
        assertSame(contextualGui, presentation.gui)
        assertEquals(mapOf(0 to port), presentation.bindings)
    }

    @Test
    public fun `default and overridden bound slot access resolve player permissions`() {
        assertTrue(GuiBoundSlotAccess.Default.permitsPlayerInsertion(BlockInventorySlotAccess.Input))
        assertTrue(GuiBoundSlotAccess.Default.permitsPlayerExtraction(BlockInventorySlotAccess.Input))
        assertFalse(GuiBoundSlotAccess.Default.permitsPlayerInsertion(BlockInventorySlotAccess.Output))
        assertTrue(GuiBoundSlotAccess.Default.permitsPlayerExtraction(BlockInventorySlotAccess.Output))
        assertTrue(GuiBoundSlotAccess.Default.permitsPlayerInsertion(BlockInventorySlotAccess.Storage))
        assertTrue(GuiBoundSlotAccess.Default.permitsPlayerExtraction(BlockInventorySlotAccess.Storage))

        BlockInventorySlotAccess.entries.forEach { role ->
            assertTrue(GuiBoundSlotAccess.ReadWrite.permitsPlayerInsertion(role))
            assertTrue(GuiBoundSlotAccess.ReadWrite.permitsPlayerExtraction(role))
            assertTrue(GuiBoundSlotAccess.InsertOnly.permitsPlayerInsertion(role))
            assertFalse(GuiBoundSlotAccess.InsertOnly.permitsPlayerExtraction(role))
            assertFalse(GuiBoundSlotAccess.ExtractOnly.permitsPlayerInsertion(role))
            assertTrue(GuiBoundSlotAccess.ExtractOnly.permitsPlayerExtraction(role))
            assertFalse(GuiBoundSlotAccess.ReadOnly.permitsPlayerInsertion(role))
            assertFalse(GuiBoundSlotAccess.ReadOnly.permitsPlayerExtraction(role))
        }
    }

    @Test
    public fun `tile inventory roles faces and presentation bindings build independently from the GUI`() {
        val inputPort = GuiSlotPort("input")
        val bufferPort = GuiSlotPort("buffer")
        val outputPort = GuiSlotPort("output")
        val presentation = gui(id("test:machine_gui"), GuiType.Chest(3)) {
            slot(0) { item { ItemStack(Material.GRAY_STAINED_GLASS_PANE) } }
            boundSlot(inputPort, 10) { placeholder { ItemStack(Material.IRON_NUGGET) } }
            boundSlot(bufferPort, 13)
            boundSlot(outputPort, 16) { placeholder { ItemStack(Material.GOLD_NUGGET) } }
        }
        val tile = customTileEntity(id("test:machine")) {
            inventory(size = 3) {
                breakPolicy = BlockInventoryBreakPolicy.KeepContents
                slot(0, BlockInventorySlotAccess.Input) {
                    accepts { stack -> stack.type == Material.IRON_INGOT }
                    insertFaces(BlockFace.UP)
                }
                slot(1, BlockInventorySlotAccess.Storage)
                slot(2, BlockInventorySlotAccess.Output) {
                    extractFaces(BlockFace.DOWN)
                }
                presentation(gui = { presentation }) {
                    bind(0, inputPort)
                    bind(1, bufferPort)
                    bind(2, outputPort)
                }
            }
        }

        val inventory = requireNotNull(tile.descriptor.inventory)
        assertEquals(tile.id / "inventory", inventory.id)
        assertEquals(BlockInventorySlotAccess.Input, inventory.slot(0).access)
        assertEquals(setOf(BlockFace.UP), inventory.slot(0).insertFaces)
        assertEquals(setOf(BlockFace.DOWN), inventory.slot(2).extractFaces)
        assertEquals(mapOf(0 to inputPort, 1 to bufferPort, 2 to outputPort), inventory.presentation?.bindings)
        assertEquals(
            listOf(0, 10, 13, 16),
            presentation.managedSlots.toList().map { (it as xyz.mastriel.cutapi.gui.GuiSlot.Top).index })
    }

    @Test
    public fun `every logical block inventory slot requires one declaration`() {
        assertFailsWith<IllegalArgumentException> {
            customTileEntity(id("test:missing_slot")) {
                inventory(size = 2) {
                    slot(0, BlockInventorySlotAccess.Storage)
                }
            }
        }
    }

    @Test
    public fun `invalid access face combinations fail and presentation bindings override GUI elements`() {
        assertFailsWith<IllegalArgumentException> {
            customTileEntity(id("test:output_insert_face")) {
                inventory(size = 1) {
                    slot(0, BlockInventorySlotAccess.Output) { insertFaces(BlockFace.UP) }
                }
            }
        }

        val storagePort = GuiSlotPort("storage")
        val gui = gui(id("test:collision_gui"), GuiType.Chest(1)) {
            slot(0) { item { ItemStack(Material.GRAY_STAINED_GLASS_PANE) } }
            boundSlot(storagePort, 0) { placeholder { ItemStack(Material.BARRIER) } }
        }
        val tile = customTileEntity(id("test:presentation_override")) {
            inventory(size = 1) {
                slot(0, BlockInventorySlotAccess.Storage)
                presentation({ gui }) { bind(0, storagePort) }
            }
        }

        assertEquals(mapOf(0 to storagePort), tile.descriptor.inventory?.presentation?.bindings)
        assertEquals(listOf(GuiSlot.Top(0)), gui.managedSlots.toList())
    }

    @Test
    public fun `presentations enforce declared required unique ports and allow optional ports`() {
        val required = GuiSlotPort("required")
        val optional = GuiSlotPort("optional")
        val unknown = GuiSlotPort("unknown")
        val gui = gui(id("test:ports"), GuiType.Chest(1)) {
            boundSlot(required, 0)
            boundSlot(optional, 1) { this.required = false }
        }

        assertFailsWith<IllegalArgumentException> {
            customTileEntity(id("test:missing_required_port")) {
                inventory(1) {
                    slot(0, BlockInventorySlotAccess.Storage)
                    presentation({ gui }) { }
                }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            customTileEntity(id("test:unknown_port")) {
                inventory(1) {
                    slot(0, BlockInventorySlotAccess.Storage)
                    presentation({ gui }) { bind(0, unknown) }
                }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            customTileEntity(id("test:duplicate_port_binding")) {
                inventory(2) {
                    slot(0, BlockInventorySlotAccess.Storage)
                    slot(1, BlockInventorySlotAccess.Storage)
                    presentation({ gui }) {
                        bind(0, required)
                        bind(1, required)
                    }
                }
            }
        }

        val optionalOmitted = customTileEntity(id("test:optional_omitted")) {
            inventory(1) {
                slot(0, BlockInventorySlotAccess.Storage)
                presentation({ gui }) { bind(0, required) }
            }
        }
        assertEquals(mapOf(0 to required), optionalOmitted.descriptor.inventory?.presentation?.bindings)
    }

    @Test
    public fun `KeepContents requires a consumable placement item`() {
        assertFailsWith<IllegalArgumentException> {
            customTileEntity(id("test:no_placement")) {
                itemPolicy = BlockItemPolicy.None
                inventory(size = 1) {
                    breakPolicy = BlockInventoryBreakPolicy.KeepContents
                    slot(0, BlockInventorySlotAccess.Storage)
                }
            }
        }
    }

    @Test
    public fun `BlockContents version one round trips sparse stacks defensively`() {
        val originalStack = ItemStack(Material.DIAMOND, 4)
        val contents = BlockContents.of(id("test:machine/inventory"), 9, mapOf(3 to originalStack))
        originalStack.amount = 1

        val encoded = BlockContents.serialize(contents).getOrThrow()
        val decoded = BlockContents.deserialize(encoded)
        val value = assertIs<DeserializeResult.Success<BlockContents>>(decoded).value

        assertEquals(BlockContents.CurrentFormatVersion, value.formatVersion)
        assertEquals(4, value.entries.getValue(3).amount)
        val firstRead = value.entries.getValue(3)
        val secondRead = value.entries.getValue(3)
        assertNotSame(firstRead, secondRead)
        firstRead.amount = 2
        assertEquals(4, value.entries.getValue(3).amount)
        assertTrue(value.entries.keys == setOf(3))
    }
}

private data class TestPresentationContext(
    val viewerName: String,
    val level: Int,
)
