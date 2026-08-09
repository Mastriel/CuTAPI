package xyz.mastriel.cutapi.item

import org.bukkit.inventory.ItemType
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.attachment.isRepeatableAttachment
import xyz.mastriel.cutapi.attachment.schema
import xyz.mastriel.cutapi.attachment.schemaForAttachment
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.data.DebugViewProvider
import xyz.mastriel.cutapi.data.Variant
import xyz.mastriel.cutapi.data.VariantSerializer
import xyz.mastriel.cutapi.data.debugView
import xyz.mastriel.cutapi.data.requireRegistered
import xyz.mastriel.cutapi.registry.Deferred
import xyz.mastriel.cutapi.registry.DeferredRegistry
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.toIdentifier

@DslMarker
public annotation class ItemIdentityExtensionDsl

@PublishedApi
internal class ItemAttachmentProvider<T : ItemAttachment>(
    val schema: Schema<T>,
    private val producer: () -> T,
) {
    fun create(extensionId: Identifier): T {
        val attachment = producer()
        require(attachment.schema().id == schema.id) {
            "Item identity extension $extensionId provider for ${schema.id} created ${attachment.schema().id}."
        }
        return attachment
    }
}

@ItemIdentityExtensionDsl
public class ItemIdentityExtensionBuilder {
    @PublishedApi
    internal val attachmentProviders: MutableList<ItemAttachmentProvider<*>> = mutableListOf()

    public inline fun <reified T : ItemAttachment> attach(noinline attachment: () -> T) {
        attachmentProviders += ItemAttachmentProvider(schemaForAttachment<T>(), attachment)
    }
}

/** Contributes intrinsic attachments to item identities that are registered elsewhere. */
public class ItemIdentityExtension @PublishedApi internal constructor(
    override val id: Identifier,
    public val targets: Set<ItemIdentity>,
    internal val attachmentProviders: List<ItemAttachmentProvider<*>>,
) : Identifiable {
    public companion object :
        IdentifierRegistry<ItemIdentityExtension>(id("cutapi:registry/item_identity_extension")),
        DebugViewProvider<ItemIdentityExtension> by debugView(
            id("cutapi:item_identity_extension"),
            {
                extends { Identifiable }
                property("targets", VariantSerializer.List) { extension ->
                    extension.targets.map { Variant.Identifier(it.logicalId) }
                }
                property("attachments", VariantSerializer.List) { extension ->
                    extension.attachmentProviders.map { Variant.Identifier(it.schema.id) }
                }
            },
        ) {
        private enum class State { Collecting, Initializing, Active, Failed }

        private var state: State = State.Collecting
        private var attachmentIndex: Map<ItemIdentity, List<IndexedProvider>> = emptyMap()

        override fun initialize() {
            check(state == State.Collecting) { "Item identity extensions are already $state." }
            state = State.Initializing
            try {
                super.initialize()
                attachmentIndex = buildIndex()
                state = State.Active
            } catch (failure: Throwable) {
                state = State.Failed
                throw failure
            }
        }

        internal fun resolve(identity: ItemIdentity): List<ItemAttachment> {
            return resolveWithSources(identity).map(IntrinsicItemAttachment::attachment)
        }

        internal fun resolveWithSources(identity: ItemIdentity): List<IntrinsicItemAttachment> {
            check(state == State.Active) {
                "Intrinsic item attachments are unavailable while item identity extensions are $state."
            }
            val customIdentity = identity as? ItemIdentity.Custom
            val descriptorAttachments = customIdentity
                ?.item
                ?.descriptor
                ?.attachments
                .orEmpty()
                .map { attachment ->
                    IntrinsicItemAttachment(
                        attachment = attachment,
                        source = ItemAttachmentSource.Descriptor(requireNotNull(customIdentity).item.id),
                    )
                }
            val extensionAttachments = attachmentIndex[identity].orEmpty().map { indexed ->
                IntrinsicItemAttachment(
                    attachment = indexed.provider.create(indexed.extensionId),
                    source = ItemAttachmentSource.Extension(indexed.extensionId),
                )
            }
            return descriptorAttachments + extensionAttachments
        }

        internal fun knownTargetIdentities(): Set<ItemIdentity> = buildSet {
            addAll(attachmentIndex.keys)
            addAll(CustomItem.getAllValues().map(CustomItem<*>::identity))
        }

        internal fun hasIntrinsicAttachment(identity: ItemIdentity, schemaId: Identifier): Boolean {
            check(state == State.Active) {
                "Intrinsic item attachments are unavailable while item identity extensions are $state."
            }
            val descriptorProvidesSchema = (identity as? ItemIdentity.Custom)
                ?.item
                ?.descriptor
                ?.attachments
                ?.any { it.schema().id == schemaId }
                ?: false
            return descriptorProvidesSchema || attachmentIndex[identity]
                .orEmpty()
                .any { it.provider.schema.id == schemaId }
        }

        private fun buildIndex(): Map<ItemIdentity, List<IndexedProvider>> {
            val extensions = getAllValues().sortedBy { it.id.toString() }
            val byTarget = linkedMapOf<ItemIdentity, MutableList<IndexedProvider>>()

            for (extension in extensions) {
                require(extension.targets.isNotEmpty()) {
                    "Item identity extension ${extension.id} must target at least one item."
                }
                extension.attachmentProviders.forEach { provider ->
                    provider.schema.requireRegistered()
                    provider.create(extension.id)
                }
                for (target in extension.targets) {
                    validateTarget(extension.id, target)
                    val providers = byTarget.getOrPut(target) { mutableListOf() }
                    providers += extension.attachmentProviders.map { IndexedProvider(extension.id, it) }
                }
            }

            val identities = buildSet {
                addAll(byTarget.keys)
                addAll(CustomItem.getAllValues().map(CustomItem<*>::identity))
            }
            for (identity in identities) validateConflicts(identity, byTarget[identity].orEmpty())
            return byTarget.mapValues { (_, providers) -> providers.toList() }
        }

        private fun validateTarget(extensionId: Identifier, target: ItemIdentity) {
            when (target) {
                is ItemIdentity.Vanilla -> require(target.type != ItemType.AIR) {
                    "Item identity extension $extensionId cannot target minecraft:air."
                }
                is ItemIdentity.Custom -> require(CustomItem.getOrNull(target.item.id) === target.item) {
                    "Item identity extension $extensionId targets unregistered custom item ${target.item.id}."
                }
            }
        }

        private fun validateConflicts(identity: ItemIdentity, indexedProviders: List<IndexedProvider>) {
            val sources = mutableListOf<AttachmentSource>()
            if (identity is ItemIdentity.Custom) {
                sources += identity.item.descriptor.attachments.map { attachment ->
                    AttachmentSource(
                        schemaId = attachment.schema().id,
                        repeatable = attachment.isRepeatableAttachment(),
                        source = "descriptor ${identity.item.id}",
                    )
                }
            }
            sources += indexedProviders.map { indexed ->
                val attachment = indexed.provider.create(indexed.extensionId)
                AttachmentSource(
                    schemaId = attachment.schema().id,
                    repeatable = attachment.isRepeatableAttachment(),
                    source = "extension ${indexed.extensionId}",
                )
            }

            for ((schemaId, matchingSources) in sources.groupBy(AttachmentSource::schemaId)) {
                if (matchingSources.size <= 1 || matchingSources.all(AttachmentSource::repeatable)) continue
                error(
                    "Item identity ${identity.logicalId} has multiple intrinsic providers for non-repeatable " +
                        "attachment $schemaId: ${matchingSources.joinToString { it.source }}."
                )
            }
        }

        private data class IndexedProvider(
            val extensionId: Identifier,
            val provider: ItemAttachmentProvider<*>,
        )

        private data class AttachmentSource(
            val schemaId: Identifier,
            val repeatable: Boolean,
            val source: String,
        )
    }
}

public fun itemIdentityExtension(
    id: Identifier,
    targets: Set<ItemIdentity>,
    configure: ItemIdentityExtensionBuilder.() -> Unit,
): ItemIdentityExtension {
    val builder = ItemIdentityExtensionBuilder().apply(configure)
    return ItemIdentityExtension(id, targets.toSet(), builder.attachmentProviders.toList())
}

public fun DeferredRegistry<ItemIdentityExtension>.registerItemIdentityExtension(
    id: Identifier,
    targets: Set<ItemIdentity>,
    configure: ItemIdentityExtensionBuilder.() -> Unit,
): Deferred<ItemIdentityExtension> = register { itemIdentityExtension(id, targets, configure) }

public fun DeferredRegistry<ItemIdentityExtension>.registerItemIdentityExtension(
    id: Identifier,
    target: ItemIdentity,
    configure: ItemIdentityExtensionBuilder.() -> Unit,
): Deferred<ItemIdentityExtension> = registerItemIdentityExtension(id, setOf(target), configure)

public fun DeferredRegistry<ItemIdentityExtension>.registerItemIdentityExtension(
    id: Identifier,
    target: ItemType,
    configure: ItemIdentityExtensionBuilder.() -> Unit,
): Deferred<ItemIdentityExtension> = registerItemIdentityExtension(id, target.asIdentity(), configure)

public fun DeferredRegistry<ItemIdentityExtension>.registerItemIdentityExtension(
    id: Identifier,
    target: CustomItem<*>,
    configure: ItemIdentityExtensionBuilder.() -> Unit,
): Deferred<ItemIdentityExtension> = registerItemIdentityExtension(id, target.asIdentity(), configure)

internal val ItemIdentity.logicalId: Identifier
    get() = when (this) {
        is ItemIdentity.Custom -> item.id
        is ItemIdentity.Vanilla -> type.key.toIdentifier()
    }

internal sealed interface ItemAttachmentSource {
    val id: Identifier

    data class Descriptor(override val id: Identifier) : ItemAttachmentSource

    data class Extension(override val id: Identifier) : ItemAttachmentSource

    data object Stack : ItemAttachmentSource {
        override val id: Identifier = id("cutapi:stack")
    }
}

internal data class IntrinsicItemAttachment(
    val attachment: ItemAttachment,
    val source: ItemAttachmentSource,
)

internal data class ResolvedItemAttachment(
    val attachment: ItemAttachment,
    val source: ItemAttachmentSource,
)
