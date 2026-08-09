package xyz.mastriel.cutapi.block

import org.bukkit.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.attachments.*

/**
 * Defines how a block has a relationship to items.
 * This will only do things when the block is registered.
 *
 * In simpler terms, this determines if the block should:
 * a. modify an existing item to make it place the block
 * b. create a new item based on the block
 * c. do nothing, you can figure it out manually
 */
public sealed class BlockItemPolicy {

    internal abstract fun tileCreate(tileDescriptor: TileDescriptor, customTile: CustomTile<*>): CustomItem<*>?

    /**
     * Generate a new item and register it. You can supply your own item descriptor which will be combined
     * with a pre-generated one.
     */
    public data class Generate(val descriptor: ItemDescriptor? = null) : BlockItemPolicy() {
        public constructor(descriptor: ItemDescriptorBuilder.() -> Unit) :
            this(ItemDescriptorBuilder().apply(descriptor).build())

        override fun tileCreate(tileDescriptor: TileDescriptor, customTile: CustomTile<*>): CustomItem<*> {
            val material = when (val strategy = customTile.descriptor.blockStrategy) {
                is BlockStrategy.Vanilla -> strategy.material
                else -> Material.STONE
            }
            val backingItem = material.asItemType()
                ?: error("Block material $material cannot back an item.")
            if (descriptor != null) {
                return customItemFromDescriptor(customTile.id / "item", backingItem) { descriptor }
            }
            return customItem(customTile.id / "item", backingItem) {
                attach(BlockPlaceAttachment(customTile))

                display {
                    if (viewer == null) return@display
                    name = tileDescriptor.name?.withViewer(viewer)
                }
            }
        }
    }

    /**
     * Using this will modify the attachments of [item] to include a [BlockPlaceAttachment].
     * If it does already have one, a warning will be printed. You shouldn't use this with an item that has one!
     */
    public data class Item(val item: CustomItem<*>, val consumesItem: Boolean = true) : BlockItemPolicy() {
        override fun tileCreate(tileDescriptor: TileDescriptor, customTile: CustomTile<*>): CustomItem<*> {
            val attachments = item.descriptor.attachments as? MutableList<ItemAttachment>
                ?: error("${item.id} does not have its attachments as a MutableList!")

            attachments.add(BlockPlaceAttachment(customTile, consumesItem))

            return item
        }
    }

    /**
     * This does nothing to create relationships between your block and any items.
     */
    public data object None : BlockItemPolicy() {
        override fun tileCreate(tileDescriptor: TileDescriptor, customTile: CustomTile<*>) = null
    }

}
