@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item

import io.papermc.paper.datacomponent.DataComponentType
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.attachment.schema
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.data.requireRegistered
import xyz.mastriel.cutapi.registry.Deferred
import xyz.mastriel.cutapi.registry.DeferredRegistry
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.RegistryEvent
import xyz.mastriel.cutapi.registry.RegistryPriority
import xyz.mastriel.cutapi.registry.id

/** A persistent Minecraft trait domain that an attachment materializer may own. */
public sealed interface ItemTraitClaim {
    public data class Component(public val type: DataComponentType) : ItemTraitClaim

    public data object KeyedAttributeModifiers : ItemTraitClaim
}

/** A single, provenance-aware attachment snapshot supplied to a materializer. */
public class ItemMaterializationContext<T : ItemAttachment> internal constructor(
    public val item: CuTItemStack,
    public val attachments: List<T>,
    public val hasStackValues: Boolean,
)

/**
 * Declaratively maps one attachment schema to persistent Minecraft item traits.
 *
 * Implementations must only contribute through [output]. The item in [context] is a detached
 * snapshot so accidental direct changes are never committed to the authoritative stack.
 */
public interface ItemAttachmentMaterializer<T : ItemAttachment> : Identifiable {
    public val schema: Schema<T>
    public val revision: Int
    public val claims: Set<ItemTraitClaim>

    public fun contribute(
        context: ItemMaterializationContext<T>,
        output: ItemTraitPatchBuilder,
    )

    public companion object :
        IdentifierRegistry<ItemAttachmentMaterializer<*>>(
            id("cutapi:registry/item_attachment_materializer"),
        ) {
        private enum class State { Collecting, Initializing, Active, Failed }

        private var state: State = State.Collecting
        private var bySchema: Map<Identifier, ItemAttachmentMaterializer<*>> = emptyMap()

        override fun modifyRegistry(
            priority: RegistryPriority,
            handler: RegistryEvent<ItemAttachmentMaterializer<*>>.() -> Unit,
        ) {
            check(state == State.Collecting && isOpen) {
                "Item attachment materializers no longer accept contributions while the registry is $state."
            }
            super.modifyRegistry(priority, handler)
        }

        override fun defer(priority: RegistryPriority): DeferredRegistry<ItemAttachmentMaterializer<*>> {
            check(state == State.Collecting && isOpen) {
                "Item attachment materializers no longer accept deferred registries while the registry is $state."
            }
            return super.defer(priority)
        }

        override fun initialize() {
            check(state == State.Collecting) { "Item attachment materializers are already $state." }
            state = State.Initializing
            try {
                super.initialize()
                val materializers = getAllValues().sortedBy { it.id.toString() }
                materializers.forEach(::validateMaterializer)
                bySchema = materializers.associateBy { it.schema.id }
                require(bySchema.size == materializers.size) {
                    val duplicates = materializers.groupBy { it.schema.id }
                        .filterValues { it.size > 1 }
                        .entries
                        .joinToString { (schema, values) ->
                            "$schema (${values.joinToString { it.id.toString() }})"
                        }
                    "Only one item attachment materializer may be registered per schema: $duplicates."
                }
                validateIntrinsicClaims()
                state = State.Active
            } catch (failure: Throwable) {
                state = State.Failed
                throw failure
            }
        }

        internal fun isActive(): Boolean = state == State.Active

        internal fun requireActive() {
            check(state == State.Active) {
                "Item attachment materializers are unavailable while the registry is $state."
            }
        }

        internal fun forSchema(schemaId: Identifier): ItemAttachmentMaterializer<*>? {
            requireActive()
            return bySchema[schemaId]
        }

        private fun validateMaterializer(materializer: ItemAttachmentMaterializer<*>) {
            materializer.schema.requireRegistered()
            require(materializer.revision > 0) {
                "Item attachment materializer ${materializer.id} must have a positive revision."
            }
            require(materializer.claims.isNotEmpty()) {
                "Item attachment materializer ${materializer.id} must declare at least one trait claim."
            }
            materializer.claims.filterIsInstance<ItemTraitClaim.Component>().forEach { claim ->
                require(claim.type.isPersistent) {
                    "Item attachment materializer ${materializer.id} cannot claim transient component ${claim.type.key}."
                }
                require(claim.type.key.toString() != "minecraft:custom_data") {
                    "Item attachment materializer ${materializer.id} cannot claim minecraft:custom_data, " +
                        "which stores attachment and materialization state."
                }
            }
        }

        private fun validateIntrinsicClaims() {
            for (identity in ItemIdentityExtension.knownTargetIdentities()) {
                val attachments = ItemIdentityExtension.resolveWithSources(identity)
                val componentOwners = linkedMapOf<DataComponentType, MutableList<String>>()
                for ((schemaId, intrinsicValues) in attachments.groupBy { it.attachment.schema().id }) {
                    val materializer = bySchema[schemaId] ?: continue
                    for (claim in materializer.claims) {
                        if (claim !is ItemTraitClaim.Component) continue
                        componentOwners.getOrPut(claim.type) { mutableListOf() } +=
                            "${materializer.id} for $schemaId from " +
                                intrinsicValues.joinToString { it.source.id.toString() }
                    }
                }
                for ((type, owners) in componentOwners) {
                    if (owners.size <= 1) continue
                    error(
                        "Item identity ${identity.logicalId} has multiple intrinsic materializers for " +
                            "component ${type.key}: ${owners.joinToString()}.",
                    )
                }
            }
        }
    }
}

/** Collects the trait patch contributed by one materializer invocation. */
public class ItemTraitPatchBuilder internal constructor(
    private val declaredClaims: Set<ItemTraitClaim>,
) {
    internal val componentOperations: MutableMap<DataComponentType, ComponentTraitOperation> = linkedMapOf()
    internal val attributeOperations: MutableMap<org.bukkit.NamespacedKey, AttributeTraitOperation> = linkedMapOf()

    public fun <T : Any> set(type: DataComponentType.Valued<T>, value: T) {
        contributeComponent(type) { stack -> stack.setData(type, value) }
    }

    public fun set(type: DataComponentType.NonValued) {
        contributeComponent(type) { stack -> stack.setData(type) }
    }

    public fun unset(type: DataComponentType) {
        contributeComponent(type) { stack -> stack.unsetData(type) }
    }

    public fun setAttributeModifier(attribute: Attribute, modifier: AttributeModifier) {
        require(ItemTraitClaim.KeyedAttributeModifiers in declaredClaims) {
            "This materializer did not declare the keyed attribute-modifier claim."
        }
        val previous = attributeOperations.put(
            modifier.key,
            AttributeTraitOperation(attribute, modifier),
        )
        require(previous == null) {
            "This materializer contributed attribute modifier ${modifier.key} more than once."
        }
    }

    /**
     * Contributes a component-owned stack edit when the Paper component value is exposed through mutable item meta.
     * The edit runs against the detached materialization target and may only change the declared component.
     */
    public fun setUsing(type: DataComponentType, operation: (ItemStack) -> Unit) {
        contributeComponent(type, operation)
    }

    private fun contributeComponent(type: DataComponentType, operation: (ItemStack) -> Unit) {
        require(ItemTraitClaim.Component(type) in declaredClaims) {
            "This materializer did not declare component ${type.key}."
        }
        require(componentOperations.put(type, ComponentTraitOperation(type, operation)) == null) {
            "This materializer contributed component ${type.key} more than once."
        }
    }
}

internal data class ComponentTraitOperation(
    val type: DataComponentType,
    val apply: (ItemStack) -> Unit,
)

internal data class AttributeTraitOperation(
    val attribute: Attribute,
    val modifier: AttributeModifier,
)

private class FunctionalItemAttachmentMaterializer<T : ItemAttachment>(
    override val id: Identifier,
    override val schema: Schema<T>,
    override val revision: Int,
    override val claims: Set<ItemTraitClaim>,
    private val contribution: (ItemMaterializationContext<T>, ItemTraitPatchBuilder) -> Unit,
) : ItemAttachmentMaterializer<T> {
    override fun contribute(
        context: ItemMaterializationContext<T>,
        output: ItemTraitPatchBuilder,
    ) {
        contribution(context, output)
    }
}

/** Constructs a complete materializer without registering it. */
public fun <T : ItemAttachment> itemAttachmentMaterializer(
    id: Identifier,
    schema: Schema<T>,
    revision: Int,
    claims: Set<ItemTraitClaim>,
    contribute: (ItemMaterializationContext<T>, ItemTraitPatchBuilder) -> Unit,
): ItemAttachmentMaterializer<T> = FunctionalItemAttachmentMaterializer(
    id = id,
    schema = schema,
    revision = revision,
    claims = claims.toSet(),
    contribution = contribute,
)

/** Adds a complete materializer to a deferred registry contribution. */
public fun <T : ItemAttachment> DeferredRegistry<ItemAttachmentMaterializer<*>>.registerItemAttachmentMaterializer(
    materializer: () -> ItemAttachmentMaterializer<T>,
): Deferred<ItemAttachmentMaterializer<T>> = register(materializer)
