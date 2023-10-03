package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.item.*

sealed class BlockItemPolicy {

    internal abstract fun descriptorBuild(tileDescriptor: TileDescriptor) : CustomItem<*>?

    data class Generate(val descriptor: ItemDescriptor = defaultItemDescriptor()) : BlockItemPolicy() {
        constructor(descriptor: ItemDescriptorBuilder.() -> Unit) :
                this(ItemDescriptorBuilder().apply(descriptor).build())

        override fun descriptorBuild(tileDescriptor: TileDescriptor) : CustomItem<*> {
            val descriptor = itemDescriptor {

            }
        }
    }

    /**
     * Using this will modify the behaviors of [item] to include a BlockPlaceBehavior if it doesn't already have one.
     * If it does already have one, a warning will be printed. You shouldn't use this with an item that has one!
     */
    data class Item(val item: CustomItem<*>, val consumesItem: Boolean = true) : BlockItemPolicy() {
        override fun descriptorBuild(tileDescriptor: TileDescriptor) : CustomItem<*>? {
            return null
        }
    }

    data object None : BlockItemPolicy() {
        override fun descriptorBuild(tileDescriptor: TileDescriptor) = null
    }

}
