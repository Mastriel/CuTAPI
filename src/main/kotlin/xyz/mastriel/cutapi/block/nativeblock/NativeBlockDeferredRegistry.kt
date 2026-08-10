package xyz.mastriel.cutapi.block.nativeblock

import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.registry.BasicDeferredRegistry
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.RegistryPriority

/** A native-aware implementation of the ordinary deferred-registry contract. */
internal class NativeBlockDeferredRegistry<T : CustomTile<*>>(
    registry: IdentifierRegistry<T>,
    priority: RegistryPriority,
    private val requireCommitAllowed: (NativeBlockDeferredRegistry<T>) -> Unit =
        NativeBlockRegistration::requireCommitAllowed,
    private val commitBatch: (
        registry: NativeBlockDeferredRegistry<T>,
        definitions: List<T>,
        contributeDefinitions: () -> Unit,
    ) -> Unit = { deferred, definitions, contribute ->
        NativeBlockRegistration.commitBatch(deferred, definitions, contribute)
    },
    private val onFailure: (Throwable) -> Unit = NativeBlockRegistration::fail,
) : BasicDeferredRegistry<T>(registry, priority) {
    internal val targetRegistryId: Identifier
        get() = registry.id

    internal val hasUncommittedEntries: Boolean
        get() = isOpen && items.isNotEmpty()

    override fun commitToRegistry() {
        requireCanCommit()
        requireCommitAllowed(this)

        try {
            val definitions = materializeAndValidate()
            commitBatch(this, definitions) {
                registry.modifyRegistry(priority) {
                    definitions.forEach(::register)
                }
            }
            isOpen = false
        } catch (throwable: Throwable) {
            onFailure(throwable)
            throw throwable
        }
    }

    internal fun pendingDescriptions(): List<String> = items.mapIndexed { index, entry ->
        entry.declaredId?.toString() ?: runCatching { entry.producer.produce().id.toString() }
            .getOrElse { "${registry.id}#${index + 1} (ID producer failed: ${it.message})" }
    }

    private fun materializeAndValidate(): List<T> {
        val definitions = items.map { entry ->
            entry.producer.produce().also { definition ->
                entry.declaredId?.let { declaredId ->
                    check(definition.id == declaredId) {
                        "Deferred native definition declared $declaredId but produced ${definition.id}."
                    }
                }
                entry.delegate.id = definition.id
            }
        }
        val duplicates = definitions.groupingBy(CustomTile<*>::id).eachCount().filterValues { it > 1 }.keys
        check(duplicates.isEmpty()) {
            "Deferred registry '${registry.id}' contains duplicate native block IDs: " +
                duplicates.sortedBy(Identifier::toString).joinToString()
        }
        return definitions
    }
}
