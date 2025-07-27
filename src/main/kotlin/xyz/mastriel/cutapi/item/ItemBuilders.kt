package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.*
import org.bukkit.*
import xyz.mastriel.cutapi.item.behaviors.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.personalized.*
import kotlin.reflect.*


public fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    block: (ItemDescriptorBuilder.() -> Unit)?
): CustomItem<CuTItemStack> {
    if (block == null) return CustomItem(id, bukkitMaterial, CuTItemStack::class, defaultItemDescriptor())
    val descriptor = ItemDescriptorBuilder().apply(block).build()

    return CustomItem(id, bukkitMaterial, CuTItemStack::class, descriptor)
}

public fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    descriptor: ItemDescriptor
): CustomItem<CuTItemStack> {
    return CustomItem(id, bukkitMaterial, CuTItemStack::class, descriptor)
}

@JvmName("customItemWithStackType")
public inline fun <reified T : CuTItemStack> customItem(
    id: Identifier,
    bukkitMaterial: Material,
    noinline block: (ItemDescriptorBuilder.() -> Unit)?
): CustomItem<T> {
    if (block == null) return CustomItem(id, bukkitMaterial, T::class, defaultItemDescriptor())
    val descriptor = ItemDescriptorBuilder().apply(block).build()

    return CustomItem(id, bukkitMaterial, T::class, descriptor)
}

@JvmName("customItemWithStackType")
public fun <T : CuTItemStack> typedCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    itemStackClass: KClass<T>,
    block: (ItemDescriptorBuilder.() -> Unit)?
): CustomItem<T> {
    if (block == null) return CustomItem(id, bukkitMaterial, itemStackClass, defaultItemDescriptor())
    val descriptor = ItemDescriptorBuilder().apply(block).build()

    return CustomItem(id, bukkitMaterial, itemStackClass, descriptor)
}

@JvmName("registerCustomItemWithStackType")
public inline fun <reified T : CuTItemStack> DeferredRegistry<CustomItem<*>>.registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    noinline block: (ItemDescriptorBuilder.() -> Unit)?
): Deferred<CustomItem<T>> {
    val customItem = customItem<T>(id, bukkitMaterial, block)
    @Suppress("UNCHECKED_CAST")
    return register { customItem } as Deferred<CustomItem<T>>
}

public fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>
): CustomItem<CuTItemStack> {
    return customItem(id, bukkitMaterial) {
        display {
            this.name = name.withViewer(viewer)
        }
    }
}

public fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>,
    behaviors: Collection<ItemBehavior>
): CustomItem<CuTItemStack> {
    return customItem(id, bukkitMaterial) {
        behavior(behaviors)

        display {
            this.name = name.withViewer(viewer)
        }
    }
}

public fun DeferredRegistry<CustomItem<*>>.registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    block: ItemDescriptorBuilder.() -> Unit
): Deferred<CustomItem<CuTItemStack>> {
    val customItem = customItem(id, bukkitMaterial, block)
    @Suppress("UNCHECKED_CAST")
    return register { customItem } as Deferred<CustomItem<CuTItemStack>>
}

public fun DeferredRegistry<CustomItem<*>>.registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    descriptor: ItemDescriptor
): Deferred<CustomItem<CuTItemStack>> {
    val customItem = customItem(id, bukkitMaterial, descriptor)
    @Suppress("UNCHECKED_CAST")
    return register { customItem } as Deferred<CustomItem<CuTItemStack>>
}

public fun DeferredRegistry<CustomItem<*>>.registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>
): Deferred<CustomItem<CuTItemStack>> {
    val customItem = customItem(id, bukkitMaterial, name)
    @Suppress("UNCHECKED_CAST")
    return register { customItem } as Deferred<CustomItem<CuTItemStack>>
}


public fun DeferredRegistry<CustomItem<*>>.registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>,
    behaviors: Collection<ItemBehavior>
): Deferred<CustomItem<CuTItemStack>> {
    val customItem = customItem(id, bukkitMaterial, name, behaviors)
    @Suppress("UNCHECKED_CAST")
    return register { customItem } as Deferred<CustomItem<CuTItemStack>>
}