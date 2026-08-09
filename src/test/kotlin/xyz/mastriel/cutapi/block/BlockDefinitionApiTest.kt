@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block

import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.block.behaviors.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.testing.*
import xyz.mastriel.cutapi.utils.*
import kotlin.test.*

public class BlockDefinitionApiTest : MockBukkitTest() {

    @Test
    public fun `block factories preserve descriptor and placed wrapper types`() {
        val direct = customBlock(id("test:direct_block"))
        val reified = customBlock<TestPlacedBlock>(id("test:reified_block"))
        val typed = typedCustomBlock(id("test:typed_block"), TestPlacedBlock::class)
        val descriptor = blockDescriptor {
            blockStrategy = BlockStrategy.Vanilla(Material.COPPER_BLOCK)
            behavior(FirstBehavior)
        }
        val fromDescriptor = typedCustomBlockFromDescriptor(
            id("test:descriptor_block"),
            TestPlacedBlock::class,
        ) { descriptor }

        assertEquals(CuTPlacedBlock::class, direct.placedBlockTypeClass)
        assertEquals(TestPlacedBlock::class, reified.placedBlockTypeClass)
        assertEquals(TestPlacedBlock::class, typed.placedBlockTypeClass)
        assertSame(descriptor, fromDescriptor.descriptor)
        assertEquals(BlockStrategy.Vanilla(Material.COPPER_BLOCK), fromDescriptor.descriptor.blockStrategy)
        assertEquals(listOf(FirstBehavior), fromDescriptor.descriptor.behaviors)
    }

    @Test
    public fun `tile entity factories preserve descriptor and placed wrapper types`() {
        val direct = customTileEntity(id("test:direct_tile_entity"))
        val reified = customTileEntity<TestPlacedTileEntity>(id("test:reified_tile_entity"))
        val typed = typedCustomTileEntity(id("test:typed_tile_entity"), TestPlacedTileEntity::class)
        val descriptor = tileEntityDescriptor {
            blockStrategy = BlockStrategy.NoteBlock
            behavior(TileEntityTestBehavior)
        }
        val fromDescriptor = typedCustomTileEntityFromDescriptor(
            id("test:descriptor_tile_entity"),
            TestPlacedTileEntity::class,
        ) { descriptor }

        assertEquals(CuTPlacedTileEntity::class, direct.placedBlockTypeClass)
        assertEquals(TestPlacedTileEntity::class, reified.placedBlockTypeClass)
        assertEquals(TestPlacedTileEntity::class, typed.placedBlockTypeClass)
        assertSame(descriptor, fromDescriptor.descriptor)
        assertEquals(BlockStrategy.NoteBlock, fromDescriptor.descriptor.blockStrategy)
        assertEquals(listOf(TileEntityTestBehavior), fromDescriptor.descriptor.behaviors)
    }

    @Test
    public fun `descriptor producers and builder collections are independent`() {
        var blockDescriptorInvocations = 0
        val blockProducer = {
            blockDescriptorInvocations++
            defaultBlockDescriptor()
        }
        val first = customBlockFromDescriptor(id("test:first_produced_block"), blockProducer)
        val second = customBlockFromDescriptor(id("test:second_produced_block"), blockProducer)

        val builder = BlockDescriptorBuilder()
        builder.behavior(FirstBehavior)
        val snapshot = builder.build()
        builder.behavior(SecondBehavior)

        assertEquals(2, blockDescriptorInvocations)
        assertNotSame(first.descriptor, second.descriptor)
        assertEquals(listOf(FirstBehavior), snapshot.behaviors)
        assertEquals(listOf(FirstBehavior, SecondBehavior), builder.behaviors)
    }

    @Test
    public fun `deferred registration is lazy for blocks and tile entities`() {
        val blockRegistry = IdentifierRegistry<CustomBlock<*>>(id("test:registry/deferred_blocks"))
        val deferredBlocks = blockRegistry.defer()
        var blockBuilds = 0
        val deferredBlock = deferredBlocks.registerCustomBlock<TestPlacedBlock>(id("test:deferred_block")) {
            blockBuilds++
            itemPolicy = BlockItemPolicy.None
        }

        val tileRegistry = IdentifierRegistry<CustomTileEntity<*>>(id("test:registry/deferred_tile_entities"))
        val deferredTiles = tileRegistry.defer()
        var tileBuilds = 0
        val deferredTile = deferredTiles.registerCustomTileEntity<TestPlacedTileEntity>(id("test:deferred_tile_entity")) {
            tileBuilds++
            itemPolicy = BlockItemPolicy.None
        }

        deferredBlocks.commitToRegistry()
        deferredTiles.commitToRegistry()
        assertEquals(0, blockBuilds)
        assertEquals(0, tileBuilds)

        blockRegistry.initialize()
        tileRegistry.initialize()

        assertEquals(1, blockBuilds)
        assertEquals(1, tileBuilds)
        assertSame(deferredBlock.get(), blockRegistry.get(id("test:deferred_block")))
        assertSame(deferredTile.get(), tileRegistry.get(id("test:deferred_tile_entity")))
        assertEquals(TestPlacedBlock::class, deferredBlock.get().placedBlockTypeClass)
        assertEquals(TestPlacedTileEntity::class, deferredTile.get().placedBlockTypeClass)
    }

    @Test
    public fun `generated item policy derives and overrides backing items`() {
        val vanilla = customBlock(id("test:vanilla_backing")) {
            blockStrategy = BlockStrategy.Vanilla(Material.COPPER_BLOCK)
        }
        val fallback = customBlock(id("test:fallback_backing")) {
            blockStrategy = BlockStrategy.FakeEntity
        }
        val explicit = customBlock(id("test:explicit_backing")) {
            blockStrategy = BlockStrategy.FakeEntity
            itemPolicy = BlockItemPolicy.Generate(ItemType.DIAMOND)
        }

        val vanillaItem = requireNotNull(vanilla.descriptor.itemPolicy.prepare(vanilla.descriptor, vanilla)).item
        val fallbackItem = requireNotNull(fallback.descriptor.itemPolicy.prepare(fallback.descriptor, fallback)).item
        val explicitItem = requireNotNull(explicit.descriptor.itemPolicy.prepare(explicit.descriptor, explicit)).item

        assertEquals(Material.COPPER_BLOCK.asItemType(), vanillaItem.backingItem)
        assertEquals(ItemType.STONE, fallbackItem.backingItem)
        assertEquals(ItemType.DIAMOND, explicitItem.backingItem)
        assertEquals(vanilla.id / "item", vanillaItem.id)
    }

    @Test
    public fun `generated descriptor producer runs per tile and system placement wins`() {
        val conflictingTile = customBlock(id("test:conflicting_placement")) {
            itemPolicy = BlockItemPolicy.None
        }
        var invocations = 0
        val policy = BlockItemPolicy.Generate.fromDescriptor(ItemType.EMERALD) {
            invocations++
            itemDescriptor {
                noDisplay()
                attach(GeneratedBlockAttachment())
                attach(BlockPlaceAttachment(conflictingTile))
            }
        }
        val first = customBlock(id("test:first_policy_block")) { itemPolicy = policy }
        val second = customBlock(id("test:second_policy_block")) { itemPolicy = policy }

        val firstItem = requireNotNull(policy.prepare(first.descriptor, first)).item
        val secondItem = requireNotNull(policy.prepare(second.descriptor, second)).item
        val firstPlacements = firstItem.descriptor.attachments.filterIsInstance<BlockPlaceAttachment>()
        val secondPlacements = secondItem.descriptor.attachments.filterIsInstance<BlockPlaceAttachment>()

        assertEquals(2, invocations)
        assertNotSame(firstItem.descriptor, secondItem.descriptor)
        assertEquals(listOf(BlockPlaceAttachment(first)), firstPlacements)
        assertEquals(listOf(BlockPlaceAttachment(second)), secondPlacements)
        assertTrue(firstItem.descriptor.attachments.contains(GeneratedBlockAttachment()))
    }

    @Test
    public fun `existing item placement preparation is idempotent and rejects conflicts`() {
        val firstTile = customBlock(id("test:first_existing_item_tile")) { itemPolicy = BlockItemPolicy.None }
        val secondTile = customBlock(id("test:second_existing_item_tile")) { itemPolicy = BlockItemPolicy.None }
        val itemId = id("test:existing_item")
        val initial = defaultItemDescriptor()
        val firstPlacement = BlockPlaceAttachment(firstTile, consumesItem = false)

        val prepared = prepareBlockPlacementDescriptor(initial, itemId, firstPlacement)
        val repeated = prepareBlockPlacementDescriptor(prepared, itemId, firstPlacement)

        assertEquals(listOf(firstPlacement), prepared.attachments.filterIsInstance<BlockPlaceAttachment>())
        assertSame(prepared, repeated)
        assertFailsWith<IllegalArgumentException> {
            prepareBlockPlacementDescriptor(prepared, itemId, BlockPlaceAttachment(secondTile))
        }
    }

    @Test
    public fun `existing item policy resolves its producer once`() {
        val existingItem = customItem(id("test:existing_policy_item"), ItemType.STICK)
        var invocations = 0
        val policy = BlockItemPolicy.Item(consumesItem = false) {
            invocations++
            existingItem
        }

        assertSame(existingItem, policy.item)
        assertSame(existingItem, policy.item)
        assertEquals(1, invocations)
        assertFalse(policy.consumesItem)
    }

    @Test
    public fun `placed wrapper types must be registered before placement`() {
        val manager = CustomBlockManager()
        val world = server.addSimpleWorld("block_definition_api")
        val block = customBlock<TestPlacedBlock>(id("test:wrapper_validation")) {
            itemPolicy = BlockItemPolicy.None
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            manager.placeTile(world.spawnLocation, block)
        }
        assertContains(failure.message.orEmpty(), TestPlacedBlock::class.qualifiedName.orEmpty())

        val placedTypeId = id("test:placed_block_type")
        manager.registerPlacedTileType(
            placedTypeId,
            TestPlacedBlock::class,
            ::TestPlacedBlock,
        )
        assertEquals(placedTypeId, manager.getType(TestPlacedBlock::class))
    }

    @Test
    public fun `definition preparation contributes items validates identity and fires handlers once`() {
        var blockRegistrations = 0
        val block = customBlock(id("test:prepared_block")) {
            onRegister(SimpleEventHandler { blockRegistrations++ })
        }
        var tileRegistrations = 0
        val tile = customTileEntity(id("test:prepared_tile_entity")) {
            onRegister(SimpleEventHandler { tileRegistrations++ })
        }
        val contributed = linkedMapOf<Identifier, CustomItem<*>>()

        block.prepareDefinition { contributed[it.id] = it }
        tile.prepareDefinition { contributed[it.id] = it }
        block.validatePreparedItem(contributed::get)
        tile.validatePreparedItem(contributed::get)

        assertEquals(1, blockRegistrations)
        assertEquals(1, tileRegistrations)
        assertEquals(setOf(block.id / "item", tile.id / "item"), contributed.keys)
        assertFailsWith<IllegalStateException> { block.prepareDefinition { } }
        assertFailsWith<IllegalStateException> { block.validatePreparedItem { null } }
    }
}

private class TestPlacedBlock(handle: Block) : CuTPlacedBlock(handle)

private class TestPlacedTileEntity(handle: Block) : CuTPlacedTileEntity(handle)

private data object FirstBehavior : BlockBehavior(id("test:behavior/first"))

private data object SecondBehavior : BlockBehavior(id("test:behavior/second"))

private data object TileEntityTestBehavior : TileEntityBehavior(id("test:behavior/tile_entity"))

private data class GeneratedBlockAttachment(val value: Int = 0) : ItemAttachment {
    companion object : Schema<GeneratedBlockAttachment> by schema(id("test:attachment/generated_block"), {
        property(GeneratedBlockAttachment::value, VariantSerializer.Int)
    })
}
