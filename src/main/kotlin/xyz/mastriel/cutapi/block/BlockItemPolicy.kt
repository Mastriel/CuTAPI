@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.registry.*

internal data class PreparedBlockItem(
    val item: CustomItem<*>,
    val contributeToRegistry: Boolean,
)

/** Defines the relationship between a custom tile definition and its placeable item. */
public sealed class BlockItemPolicy {

    internal abstract fun prepare(
        tileDescriptor: TileDescriptor,
        customTile: CustomTile<*>,
    ): PreparedBlockItem?

    /** Generates and contributes a new custom item when the tile definition is registered. */
    public class Generate private constructor(
        public val backingItem: ItemType?,
        private val descriptorProducer: (() -> ItemDescriptor)?,
    ) : BlockItemPolicy() {

        @Deprecated(
            message = "Use Generate.fromDescriptor and keep the descriptor producer at the call site.",
            level = DeprecationLevel.WARNING,
        )
        public val descriptor: ItemDescriptor? get() = descriptorProducer?.invoke()

        public constructor(backingItem: ItemType? = null) : this(backingItem, null)

        public constructor(
            backingItem: ItemType? = null,
            configure: ItemDescriptorBuilder.() -> Unit,
        ) : this(backingItem, descriptorProducer = { ItemDescriptorBuilder().apply(configure).build() })

        @Deprecated(
            message = "Pass a descriptor producer to Generate.fromDescriptor instead.",
            replaceWith = ReplaceWith("BlockItemPolicy.Generate.fromDescriptor { descriptor ?: defaultItemDescriptor() }"),
            level = DeprecationLevel.WARNING,
        )
        public constructor(descriptor: ItemDescriptor?) : this(null, descriptorProducer = descriptor?.let { { it } })

        override fun prepare(
            tileDescriptor: TileDescriptor,
            customTile: CustomTile<*>,
        ): PreparedBlockItem {
            val resolvedBackingItem = backingItem ?: defaultBackingItem()
            val placement = BlockPlaceAttachment(customTile)
            val baseDescriptor = ItemDescriptor(
                display = {
                    if (viewer != null) name = tileDescriptor.name?.withViewer(viewer)
                },
                attachments = listOf(placement),
            )
            val configuredDescriptor = descriptorProducer?.invoke()
            val combinedDescriptor = if (configuredDescriptor == null) {
                baseDescriptor
            } else {
                mergeGeneratedDescriptor(baseDescriptor, configuredDescriptor, placement)
            }
            val generatedItem = customItemFromDescriptor(customTile.id / "item", resolvedBackingItem) {
                combinedDescriptor
            }
            return PreparedBlockItem(generatedItem, contributeToRegistry = true)
        }

        public companion object {
            /** Creates a generated-item policy whose descriptor is produced per tile definition. */
            public fun fromDescriptor(
                backingItem: ItemType? = null,
                descriptor: () -> ItemDescriptor = ::defaultItemDescriptor,
            ): Generate = Generate(backingItem, descriptor)

            /**
             * Placement is supplied by [BlockPlaceAttachment], so the native item must not inherit a
             * vanilla BlockItem's identity-bound placement behavior. The generated model controls visuals.
             */
            private fun defaultBackingItem(): ItemType = ItemType.PAPER

            private fun mergeGeneratedDescriptor(
                base: ItemDescriptor,
                configured: ItemDescriptor,
                placement: BlockPlaceAttachment,
            ): ItemDescriptor {
                val mergedAttachments = mergeAttachments(base.attachments, configured.attachments)
                    .filterNot { it.schema().id == BlockPlaceAttachment.id } + placement
                return ItemDescriptor(
                    display = configured.display ?: base.display,
                    attachments = mergedAttachments,
                    onRegister = configured.onRegister,
                )
            }
        }
    }

    /** Uses an existing custom item and prepares it to place this tile before item registration. */
    public class Item(
        public val consumesItem: Boolean = true,
        item: () -> CustomItem<*>,
    ) : BlockItemPolicy() {
        private val itemProducer: SingleProducer<CustomItem<*>> = SingleProducer(item)

        public val item: CustomItem<*> get() = itemProducer.produce()

        @Deprecated(
            message = "Pass an item producer so deferred items are resolved during tile registration.",
            replaceWith = ReplaceWith("BlockItemPolicy.Item(consumesItem) { item }"),
            level = DeprecationLevel.WARNING,
        )
        public constructor(item: CustomItem<*>, consumesItem: Boolean = true) : this(consumesItem, { item })

        override fun prepare(
            tileDescriptor: TileDescriptor,
            customTile: CustomTile<*>,
        ): PreparedBlockItem {
            val resolvedItem = itemProducer.produce()
            val placement = BlockPlaceAttachment(customTile, consumesItem)
            resolvedItem.prepareDescriptor { current ->
                prepareBlockPlacementDescriptor(current, resolvedItem.id, placement)
            }
            return PreparedBlockItem(resolvedItem, contributeToRegistry = false)
        }
    }

    /** Creates no automatic item relationship for this tile. */
    public data object None : BlockItemPolicy() {
        override fun prepare(
            tileDescriptor: TileDescriptor,
            customTile: CustomTile<*>,
        ): PreparedBlockItem? = null
    }
}

private fun mergeAttachments(
    base: List<ItemAttachment>,
    configured: List<ItemAttachment>,
): List<ItemAttachment> {
    val merged = base.toMutableList()
    for (attachment in configured) {
        if (!attachment.isRepeatableAttachment()) {
            merged.removeIf { it.schema().id == attachment.schema().id }
        }
        merged += attachment
    }
    return merged.toList()
}

internal fun prepareBlockPlacementDescriptor(
    current: ItemDescriptor,
    itemId: Identifier,
    placement: BlockPlaceAttachment,
): ItemDescriptor {
    val existing = current.attachments.filter { it.schema().id == BlockPlaceAttachment.id }
    require(existing.isEmpty() || existing == listOf(placement)) {
        "Custom item $itemId is already associated with a different block placement policy; " +
            "it cannot also place ${placement.tileId}."
    }
    if (existing.isNotEmpty()) return current
    return ItemDescriptor(
        display = current.display,
        attachments = current.attachments + placement,
        onRegister = current.onRegister,
    )
}
