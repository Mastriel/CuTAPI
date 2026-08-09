@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.registry.Deferred
import xyz.mastriel.cutapi.registry.DeferredRegistry
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import xyz.mastriel.cutapi.utils.personalized.withViewer
import kotlin.reflect.KClass

/** Creates a fully configured item before it is handed to the identifier registry. */
public fun customItem(
    id: Identifier,
    backingItem: ItemType,
    configure: ItemDescriptorBuilder.() -> Unit = {},
): CustomItem<CuTItemStack> =
    CustomItem(id, backingItem, CuTItemStack::class, ItemDescriptorBuilder().apply(configure).build())

/** Producer-based variant for callers that already construct an [ItemDescriptor]. */
public fun customItemFromDescriptor(
    id: Identifier,
    backingItem: ItemType,
    descriptor: () -> ItemDescriptor = ::defaultItemDescriptor,
): CustomItem<CuTItemStack> = CustomItem(id, backingItem, CuTItemStack::class, descriptor())

@JvmName("customItemWithStackType")
public inline fun <reified T : CuTItemStack> customItem(
    id: Identifier,
    backingItem: ItemType,
    noinline configure: ItemDescriptorBuilder.() -> Unit = {},
): CustomItem<T> =
    CustomItem(id, backingItem, T::class, ItemDescriptorBuilder().apply(configure).build())

@JvmName("customItemFromDescriptorWithStackType")
public inline fun <reified T : CuTItemStack> customItemFromDescriptor(
    id: Identifier,
    backingItem: ItemType,
    noinline descriptor: () -> ItemDescriptor = ::defaultItemDescriptor,
): CustomItem<T> = CustomItem(id, backingItem, T::class, descriptor())

public fun <T : CuTItemStack> typedCustomItem(
    id: Identifier,
    backingItem: ItemType,
    itemStackClass: KClass<T>,
    configure: ItemDescriptorBuilder.() -> Unit = {},
): CustomItem<T> =
    CustomItem(id, backingItem, itemStackClass, ItemDescriptorBuilder().apply(configure).build())

public fun <T : CuTItemStack> typedCustomItemFromDescriptor(
    id: Identifier,
    backingItem: ItemType,
    itemStackClass: KClass<T>,
    descriptor: () -> ItemDescriptor = ::defaultItemDescriptor,
): CustomItem<T> = CustomItem(id, backingItem, itemStackClass, descriptor())

@JvmName("registerCustomItemWithStackType")
public inline fun <reified T : CuTItemStack> DeferredRegistry<CustomItem<*>>.registerCustomItem(
    id: Identifier,
    backingItem: ItemType,
    noinline configure: ItemDescriptorBuilder.() -> Unit = {},
): Deferred<CustomItem<T>> = register { customItem<T>(id, backingItem, configure) }

@JvmName("registerCustomItemFromDescriptorWithStackType")
public inline fun <reified T : CuTItemStack> DeferredRegistry<CustomItem<*>>.registerCustomItemFromDescriptor(
    id: Identifier,
    backingItem: ItemType,
    noinline descriptor: () -> ItemDescriptor = ::defaultItemDescriptor,
): Deferred<CustomItem<T>> = register { customItemFromDescriptor<T>(id, backingItem, descriptor) }

public fun DeferredRegistry<CustomItem<*>>.registerCustomItem(
    id: Identifier,
    backingItem: ItemType,
    configure: ItemDescriptorBuilder.() -> Unit = {},
): Deferred<CustomItem<CuTItemStack>> = register { customItem(id, backingItem, configure) }

public fun DeferredRegistry<CustomItem<*>>.registerCustomItemFromDescriptor(
    id: Identifier,
    backingItem: ItemType,
    descriptor: () -> ItemDescriptor = ::defaultItemDescriptor,
): Deferred<CustomItem<CuTItemStack>> = register { customItemFromDescriptor(id, backingItem, descriptor) }

public fun customItem(
    id: Identifier,
    backingItem: ItemType,
    name: PersonalizedWithDefault<Component>,
): CustomItem<CuTItemStack> = customItem(id, backingItem) {
    display { this.name = name.withViewer(viewer) }
}

public fun customItem(
    id: Identifier,
    backingItem: ItemType,
    name: PersonalizedWithDefault<Component>,
    attachments: Collection<ItemAttachment>,
): CustomItem<CuTItemStack> = customItem(id, backingItem) {
    attach(attachments)
    display { this.name = name.withViewer(viewer) }
}
