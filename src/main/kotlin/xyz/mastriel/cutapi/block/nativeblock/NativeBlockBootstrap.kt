@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.nativeblock

import xyz.mastriel.cutapi.block.CustomBlock
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.RegistryPriority

/**
 * Installs custom block registry entries during Paper's bootstrap phase.
 *
 * Consumer plugins must call [submit] from their [io.papermc.paper.plugin.bootstrap.PluginBootstrap].
 * Definitions submitted after CuTAPI begins enabling are rejected because worlds may already depend on
 * the native registry IDs.
 */
public object NativeBlockBootstrap {
    private val submitted: MutableMap<Identifier, CustomTile<*>> = linkedMapOf()

    /**
     * Installs [definition] in Minecraft's native registries and contributes the same configured object to
     * CuTAPI's definition registry. Registration never configures or mutates the definition itself.
     */
    @Synchronized
    public fun <T : CustomTile<*>> submit(definition: T): T {
        check(NativeBlockLifecycle.state == NativeBlockState.Collecting) {
            "Native custom block ${definition.id} was submitted outside Paper's bootstrap phase " +
                "(current state: ${NativeBlockLifecycle.state}). Definitions are bootstrap-only and require a restart."
        }
        check(definition.id !in submitted) { "Native custom block ${definition.id} was submitted more than once." }

        try {
            NativeBlockLifecycle.state = NativeBlockState.Installing
            NativeBlockRegistry.installAll(listOf(definition))
            contributeDefinition(definition)
            submitted[definition.id] = definition
            NativeBlockLifecycle.state = NativeBlockState.Collecting
            return definition
        } catch (failure: Throwable) {
            NativeBlockLifecycle.state = NativeBlockState.Failed
            throw failure
        }
    }

    @Synchronized
    internal fun finishCollection() {
        check(NativeBlockLifecycle.state == NativeBlockState.Collecting) {
            "Cannot finish native block bootstrap in state ${NativeBlockLifecycle.state}."
        }
        NativeBlockLifecycle.state = NativeBlockState.Frozen
        NativeBlockRegistry.finalizeVanillaBlockStateCount(submitted.values)
        NativeBlockRegistry.installBukkitMappings(submitted.values)
    }

    @Synchronized
    internal fun validateRegisteredDefinitions(definitions: Collection<CustomTile<*>>) {
        check(NativeBlockLifecycle.state == NativeBlockState.Frozen) {
            "Cannot validate native block bootstrap in state ${NativeBlockLifecycle.state}."
        }

        val registeredById = definitions.associateBy(CustomTile<*>::id)
        val missingNative = registeredById.keys - submitted.keys
        check(missingNative.isEmpty()) {
            "Custom block definitions were registered after Paper bootstrap and have no native registry entry: " +
                missingNative.sortedBy(Identifier::toString).joinToString() +
                ". Submit every definition with NativeBlockBootstrap.submit(...) from PluginBootstrap and restart."
        }

        val missingDefinitions = submitted.keys - registeredById.keys
        check(missingDefinitions.isEmpty()) {
            "Native custom blocks were submitted but did not reach the CuTAPI definition registry: " +
                missingDefinitions.sortedBy(Identifier::toString).joinToString()
        }

        for ((id, submittedDefinition) in submitted) {
            check(registeredById[id] === submittedDefinition) {
                "CuTAPI definition $id is not the same object that was submitted during Paper bootstrap."
            }
            check(NativeBlockTypes.isInstalled(id)) { "Native custom block $id was not installed." }
        }
        NativeBlockRegistry.verifyStateIds(submitted.values)
    }

    private fun contributeDefinition(definition: CustomTile<*>) {
        val priority = RegistryPriority(Int.MAX_VALUE)
        when (definition) {
            is CustomBlock<*> -> CustomBlock.modifyRegistry(priority) { register(definition) }
            is CustomTileEntity<*> -> CustomTileEntity.modifyRegistry(priority) { register(definition) }
        }
    }
}
