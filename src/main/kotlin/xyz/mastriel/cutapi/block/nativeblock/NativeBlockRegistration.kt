@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.nativeblock

import org.bukkit.Bukkit
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.item.nativeitem.NativeItemCodecInstrumentation
import xyz.mastriel.cutapi.registry.DeferredRegistry
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.RegistryPriority
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Collects native-capable standard deferred registries while STARTUP plugins are enabling.
 * The public registration surface remains [xyz.mastriel.cutapi.registry.DeferredRegistry].
 */
internal object NativeBlockRegistration {
    private val deferredRegistries: MutableSet<NativeBlockDeferredRegistry<*>> =
        Collections.newSetFromMap(IdentityHashMap())
    private val definitionsById: MutableMap<Identifier, CustomTile<*>> = linkedMapOf()
    private var failure: Throwable? = null

    @Synchronized
    fun <T : CustomTile<*>> createDeferredRegistry(
        registry: IdentifierRegistry<T>,
        priority: RegistryPriority,
    ): DeferredRegistry<T> {
        requireCollecting("create a deferred registry for '${registry.id}'")
        return NativeBlockDeferredRegistry(registry, priority).also(deferredRegistries::add)
    }

    @Synchronized
    fun requireCommitAllowed(registry: NativeBlockDeferredRegistry<*>) {
        requirePrimaryThread("commit native custom blocks")
        check(registry in deferredRegistries) { "Native deferred registry was not created by CuTAPI." }
        requireCollecting("commit native deferred registry '${registry.targetRegistryId}'")
    }

    @Synchronized
    fun commitBatch(
        registry: NativeBlockDeferredRegistry<*>,
        definitions: List<CustomTile<*>>,
        contributeDefinitions: () -> Unit,
    ) {
        requireCommitAllowed(registry)
        val duplicateInstalled = definitions.map(CustomTile<*>::id).filter(definitionsById::containsKey)
        check(duplicateInstalled.isEmpty()) {
            "Native custom block IDs were committed by more than one deferred registry: " +
                duplicateInstalled.distinct().sortedBy(Identifier::toString).joinToString()
        }

        try {
            NativeBlockLifecycle.state = NativeBlockState.Installing
            if (definitions.isNotEmpty()) NativeBlockRegistry.installAll(definitions)
            contributeDefinitions()
            definitions.forEach { definition -> definitionsById[definition.id] = definition }
            NativeBlockLifecycle.state = NativeBlockState.Collecting
        } catch (throwable: Throwable) {
            fail(throwable)
            throw throwable
        }
    }

    @Synchronized
    fun fail(throwable: Throwable) {
        if (failure == null) failure = throwable
        NativeBlockLifecycle.state = NativeBlockState.Failed
    }

    /** Called by the injected CraftServer hook immediately after STARTUP plugins finish enabling. */
    @Synchronized
    fun finishStartupPluginLoading() {
        requirePrimaryThread("finalize native custom blocks")
        failure?.let { cause ->
            throw IllegalStateException("Native custom block registration previously failed.", cause)
        }

        try {
            check(NativeBlockLifecycle.state == NativeBlockState.Collecting) {
                "Cannot finalize native custom blocks in state ${NativeBlockLifecycle.state}."
            }
            val pending = deferredRegistries
                .asSequence()
                .filter(NativeBlockDeferredRegistry<*>::hasUncommittedEntries)
                .flatMap { registry -> registry.pendingDescriptions().asSequence() }
                .sorted()
                .toList()
            check(pending.isEmpty()) {
                "Native-capable deferred registries still contain uncommitted definitions at the end of " +
                    "STARTUP plugin loading: ${pending.joinToString()}. Call commitToRegistry() from a " +
                    "STARTUP plugin's onEnable()."
            }

            val definitions = definitionsById.values.toList()
            NativeBlockLifecycle.state = NativeBlockState.Frozen
            NativeBlockRegistry.finalizeVanillaBlockStateCount(definitions)
            NativeBlockRegistry.verifyStateIds(definitions)
            NativeBlockRegistry.installBukkitMappings(definitions)
            NativeItemCodecInstrumentation.bind()
        } catch (throwable: Throwable) {
            fail(throwable)
            throw throwable
        }
    }

    @Synchronized
    fun validateRegisteredDefinitions(definitions: Collection<CustomTile<*>>) {
        check(NativeBlockLifecycle.state == NativeBlockState.Frozen) {
            "Cannot validate native custom blocks in state ${NativeBlockLifecycle.state}."
        }

        val registeredById = definitions.associateBy(CustomTile<*>::id)
        val missingNative = registeredById.keys - definitionsById.keys
        check(missingNative.isEmpty()) {
            "Custom block definitions reached the identifier registry without native registration: " +
                missingNative.sortedBy(Identifier::toString).joinToString() +
                ". Use CustomBlock.defer() or CustomTileEntity.defer() and commitToRegistry() from " +
                "a STARTUP plugin's onEnable()."
        }

        val missingDefinitions = definitionsById.keys - registeredById.keys
        check(missingDefinitions.isEmpty()) {
            "Native custom blocks did not reach the CuTAPI definition registry: " +
                missingDefinitions.sortedBy(Identifier::toString).joinToString()
        }

        for ((id, nativeDefinition) in definitionsById) {
            check(registeredById[id] === nativeDefinition) {
                "CuTAPI definition $id is not the same object that was installed natively."
            }
            check(NativeBlockTypes.isInstalled(id)) { "Native custom block $id was not installed." }
        }
        NativeBlockRegistry.verifyStateIds(definitionsById.values)
    }

    private fun requireCollecting(action: String) {
        failure?.let { cause ->
            throw IllegalStateException("Cannot $action because native registration previously failed.", cause)
        }
        check(NativeBlockLifecycle.state == NativeBlockState.Collecting) {
            "Cannot $action while native custom blocks are ${NativeBlockLifecycle.state}. " +
                "Definitions must be committed synchronously from a STARTUP plugin's onEnable() and require a restart."
        }
    }

    private fun requirePrimaryThread(action: String) {
        check(Bukkit.isPrimaryThread()) {
            "CuTAPI must $action on the primary server thread."
        }
    }
}
