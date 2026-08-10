package xyz.mastriel.cutapi.block.nativeblock

import xyz.mastriel.cutapi.block.BlockItemPolicy
import xyz.mastriel.cutapi.block.CustomBlock
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.block.customBlock
import xyz.mastriel.cutapi.block.registerCustomBlock
import xyz.mastriel.cutapi.block.registerCustomTileEntity
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.RegistryPriority
import xyz.mastriel.cutapi.registry.id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

public class NativeBlockDeferredRegistryTest {
    @Test
    public fun `custom block companions expose native capable standard deferred registries`() {
        assertIs<NativeBlockDeferredRegistry<*>>(CustomBlock.defer())
        assertIs<NativeBlockDeferredRegistry<*>>(CustomTileEntity.defer())
    }

    @Test
    public fun `native block registry installs and contributes the exact produced object`() {
        val target = IdentifierRegistry<CustomBlock<*>>(id("test:registry/native_blocks"))
        var installed: List<CustomBlock<*>> = emptyList()
        val deferred = NativeBlockDeferredRegistry(
            registry = target,
            priority = RegistryPriority.High,
            requireCommitAllowed = {},
            commitBatch = { _, definitions, contribute ->
                installed = definitions
                contribute()
            },
            onFailure = {},
        )
        val definitionId = id("test:native_block")
        var builds = 0
        val reference = deferred.registerCustomBlock(definitionId) {
            builds++
            itemPolicy = BlockItemPolicy.None
        }

        assertEquals(listOf(definitionId.toString()), deferred.pendingDescriptions())
        assertEquals(0, builds)
        val early = reference.get()
        deferred.commitToRegistry()
        target.initialize()

        assertEquals(1, builds)
        assertFalse(deferred.isOpen)
        assertSame(early, installed.single())
        assertSame(early, target.get(definitionId))
        assertFailsWith<IllegalStateException> { deferred.commitToRegistry() }
    }

    @Test
    public fun `native tile registry materializes every producer before installation`() {
        val target = IdentifierRegistry<CustomTileEntity<*>>(id("test:registry/native_tiles"))
        var producerCompleted = false
        var installerObservedCompletion = false
        val deferred = NativeBlockDeferredRegistry(
            registry = target,
            priority = RegistryPriority.Medium,
            requireCommitAllowed = {},
            commitBatch = { _, _, contribute ->
                installerObservedCompletion = producerCompleted
                contribute()
            },
            onFailure = {},
        )
        val definitionId = id("test:native_tile")
        deferred.registerCustomTileEntity(definitionId) {
            itemPolicy = BlockItemPolicy.None
            producerCompleted = true
        }

        deferred.commitToRegistry()
        target.initialize()

        assertTrue(installerObservedCompletion)
        assertEquals(definitionId, target.get(definitionId).id)
    }

    @Test
    public fun `native batch validation rejects duplicate IDs before installation`() {
        val target = IdentifierRegistry<CustomBlock<*>>(id("test:registry/native_duplicates"))
        var installerCalled = false
        var recordedFailure: Throwable? = null
        val deferred = NativeBlockDeferredRegistry(
            registry = target,
            priority = RegistryPriority.Medium,
            requireCommitAllowed = {},
            commitBatch = { _, _, _ -> installerCalled = true },
            onFailure = { recordedFailure = it },
        )
        val duplicateId = id("test:duplicate_native_block")
        deferred.register { customBlock(duplicateId) { itemPolicy = BlockItemPolicy.None } }
        deferred.register { customBlock(duplicateId) { itemPolicy = BlockItemPolicy.None } }

        val failure = assertFailsWith<IllegalStateException> { deferred.commitToRegistry() }

        assertFalse(installerCalled)
        assertTrue(deferred.isOpen)
        assertSame(failure, recordedFailure)
    }
}
