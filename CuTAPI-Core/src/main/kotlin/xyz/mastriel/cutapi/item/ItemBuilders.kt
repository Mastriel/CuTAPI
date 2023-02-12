package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import org.bukkit.Material
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import xyz.mastriel.cutapi.utils.personalized.withViewer


fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    block: (ItemDescriptorBuilder.() -> Unit)?
): CustomItem<CuTItemStack> {
    if (block == null) return CustomItem(id, bukkitMaterial, CuTItemStack::class, defaultItemDescriptor())
    val descriptor = ItemDescriptorBuilder().apply(block).build()

    return CustomItem(id, bukkitMaterial, CuTItemStack::class, descriptor)
}

@JvmName("customItemWithStackType")
inline fun <reified T: CuTItemStack> customItem(
    id: Identifier,
    bukkitMaterial: Material,
    noinline block: (ItemDescriptorBuilder.() -> Unit)?
): CustomItem<T> {
    if (block == null) return CustomItem(id, bukkitMaterial, T::class, defaultItemDescriptor())
    val descriptor = ItemDescriptorBuilder().apply(block).build()

    return CustomItem(id, bukkitMaterial, T::class, descriptor)
}

@JvmName("registerCustomItemWithStackType")
inline fun <reified T: CuTItemStack> registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    noinline block: (ItemDescriptorBuilder.() -> Unit)?
): CustomItem<T> {
    val customItem = customItem<T>(id, bukkitMaterial, block)
    CustomItem.register(customItem)
    return customItem
}

fun customItem(
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

fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>,
    behaviors: Collection<ItemBehavior>
): CustomItem<CuTItemStack> {
    return customItem(id, bukkitMaterial) {
        this.itemBehaviors.addAll(behaviors)

        display {
            this.name = name.withViewer(viewer)
        }
    }
}

fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    block: ItemDescriptorBuilder.() -> Unit
): CustomItem<CuTItemStack> {
    val customItem = customItem(id, bukkitMaterial, block)
    CustomItem.register(customItem)
    return customItem
}

fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>
): CustomItem<CuTItemStack> {
    val customItem = customItem(id, bukkitMaterial, name)
    CustomItem.register(customItem)
    return customItem
}



fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>,
    behaviors: Collection<ItemBehavior>
): CustomItem<CuTItemStack> {
    val customItem = customItem(id, bukkitMaterial, name, behaviors)
    CustomItem.register(customItem)
    return customItem
}