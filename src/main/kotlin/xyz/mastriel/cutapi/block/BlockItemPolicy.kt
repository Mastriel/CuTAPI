@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block

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
        requestedBackingItem: ItemType?,
        private val descriptorProducer: (() -> ItemDescriptor)?,
    ) : BlockItemPolicy() {

        /** Generated block items use a behaviorless glistering melon slice as their server backing. */
        public val backingItem: ItemType = defaultBackingItem()

        init {
            require(requestedBackingItem == null || requestedBackingItem == backingItem) {
                "Generated block items must use ${backingItem.key}; use BlockItemPolicy.Item for a custom backing item."
            }
        }

        @Deprecated(
            message = "Use Generate.fromDescriptor and keep the descriptor producer at the call site.",
            level = DeprecationLevel.WARNING,
        )
        public val descriptor: ItemDescriptor? get() = descriptorProducer?.invoke()

        public constructor() : this(null, null)

        public constructor(configure: ItemDescriptorBuilder.() -> Unit) :
            this(null, descriptorProducer = { ItemDescriptorBuilder().apply(configure).build() })

        @Deprecated(
            message = "Generated block items are glistering-melon-backed; use BlockItemPolicy.Item for a custom backing item.",
            replaceWith = ReplaceWith("BlockItemPolicy.Generate()"),
            level = DeprecationLevel.WARNING,
        )
        public constructor(backingItem: ItemType) : this(backingItem, null)

        @Deprecated(
            message = "Generated block items are glistering-melon-backed; use BlockItemPolicy.Item for a custom backing item.",
            replaceWith = ReplaceWith("BlockItemPolicy.Generate(configure)"),
            level = DeprecationLevel.WARNING,
        )
        public constructor(
            backingItem: ItemType,
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
            val placement = BlockPlaceAttachment(customTile)
            val baseDescriptor = ItemDescriptor(
                display = {
                    if (viewer != null) name = tileDescriptor.name?.withViewer(viewer)
                },
                attachments = listOf(
                    placement,
                    DisplayAs(defaultClientItem()),
                ),
                forcedItemModelId = generatedBlockItemModelId(customTile),
            )
            val configuredDescriptor = descriptorProducer?.invoke()
            val combinedDescriptor = if (configuredDescriptor == null) {
                baseDescriptor
            } else {
                mergeGeneratedDescriptor(baseDescriptor, configuredDescriptor, placement)
            }
            val generatedItem = customItemFromDescriptor(customTile.id / "item", backingItem) {
                combinedDescriptor
            }
            return PreparedBlockItem(generatedItem, contributeToRegistry = true)
        }

        public companion object {
            /** Creates a generated-item policy whose descriptor is produced per tile definition. */
            public fun fromDescriptor(
                descriptor: () -> ItemDescriptor = ::defaultItemDescriptor,
            ): Generate = Generate(null, descriptor)

            @Deprecated(
                message = "Generated block items are glistering-melon-backed; use BlockItemPolicy.Item for a custom backing item.",
                replaceWith = ReplaceWith("BlockItemPolicy.Generate.fromDescriptor(descriptor)"),
                level = DeprecationLevel.WARNING,
            )
            public fun fromDescriptor(
                backingItem: ItemType,
                descriptor: () -> ItemDescriptor = ::defaultItemDescriptor,
            ): Generate = Generate(backingItem, descriptor)

            /**
             * Placement is supplied by [BlockPlaceAttachment], so the authoritative native item uses a
             * plain glistering melon slice with no use-on-block behavior. Client copies project as a spawn
             * egg, which produces the expected use-on-block hand swing without predicting a temporary
             * block. The generated item model controls the held and inventory visuals.
             */
            internal fun defaultBackingItem(): ItemType = ItemType.GLISTERING_MELON_SLICE

            internal fun defaultClientItem(): ItemType = ItemType.PIG_SPAWN_EGG

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
                    forcedItemModelId = base.forcedItemModelId,
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
        forcedItemModelId = current.forcedItemModelId,
    )
}

internal fun generatedBlockItemModelId(tile: CustomTile<*>): Identifier = tile.id / "item"
