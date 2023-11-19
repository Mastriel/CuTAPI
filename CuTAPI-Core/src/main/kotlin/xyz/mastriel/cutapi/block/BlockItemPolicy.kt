package xyz.mastriel.cutapi.block

import org.bukkit.Material
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.behaviors.BlockPlaceBehavior
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.registry.id

/**
 * Defines how a block has a relationship to items.
 * This will only do things when the block is registered.
 *
 * In simpler terms, this determines if the block should:
 * a. modify an existing item to make it place the block
 * b. create a new item based on the block
 * c. do nothing, you can figure it out manually
 */
sealed class BlockItemPolicy {

    internal abstract fun tileRegister(tileDescriptor: TileDescriptor, customTile: CustomTile<*>) : CustomItem<*>?

    /**
     * Generate a new item and register it. You can supply your own item descriptor which will be combined
     * with a pre-generated one.
     */
    data class Generate(val descriptor: ItemDescriptor = defaultItemDescriptor()) : BlockItemPolicy() {
        constructor(descriptor: ItemDescriptorBuilder.() -> Unit) :
                this(ItemDescriptorBuilder().apply(descriptor).build())

        override fun tileRegister(tileDescriptor: TileDescriptor, customTile: CustomTile<*>) : CustomItem<*> {
            val material = when (val strategy = customTile.descriptor.blockStrategy) {
                is BlockStrategy.Vanilla -> strategy.material
                else -> Material.STONE
            }
            return registerCustomItem(customTile.id / "item", material) {
                // todo finish
            }
        }
    }

    /**
     * Using this will modify the behaviors of [item] to include a BlockPlaceBehavior if it doesn't already have one.
     * If it does already have one, a warning will be printed. You shouldn't use this with an item that has one!
     */
    data class Item(val item: CustomItem<*>, val consumesItem: Boolean = true) : BlockItemPolicy() {
        override fun tileRegister(tileDescriptor: TileDescriptor, customTile: CustomTile<*>) : CustomItem<*> {
            val behaviors = item.descriptor.itemBehaviors as? MutableList<ItemBehavior> ?:
                error("${item.id} does not have its itemBehaviors as a MutableList!")

            behaviors.add(BlockPlaceBehavior(customTile))

            return item
        }
    }

    /**
     * This does nothing to create relationships between your block and any items.
     */
    data object None : BlockItemPolicy() {
        override fun tileRegister(tileDescriptor: TileDescriptor, customTile: CustomTile<*>) = null
    }

}
