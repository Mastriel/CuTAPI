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
public inline fun <reified T : CuTItemStack> registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    noinline block: (ItemDescriptorBuilder.() -> Unit)?
): CustomItem<T> {
    val customItem = customItem<T>(id, bukkitMaterial, block)
    CustomItem.register(customItem)
    return customItem
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

public fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    block: ItemDescriptorBuilder.() -> Unit
): CustomItem<CuTItemStack> {
    val customItem = customItem(id, bukkitMaterial, block)
    CustomItem.register(customItem)
    return customItem
}

public fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    descriptor: ItemDescriptor
): CustomItem<CuTItemStack> {
    val customItem = customItem(id, bukkitMaterial, descriptor)
    CustomItem.register(customItem)
    return customItem
}

public fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>
): CustomItem<CuTItemStack> {
    val customItem = customItem(id, bukkitMaterial, name)
    CustomItem.register(customItem)
    return customItem
}


public fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>,
    behaviors: Collection<ItemBehavior>
): CustomItem<CuTItemStack> {
    val customItem = customItem(id, bukkitMaterial, name, behaviors)
    CustomItem.register(customItem)
    return customItem
}