package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import org.bukkit.Material
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault


fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    block: (ItemDescriptorBuilder.() -> Unit)?
): CustomItem {
    if (block == null) return CustomItem(id, bukkitMaterial, defaultItemDescriptor())
    val descriptor = ItemDescriptorBuilder().apply(block).build()

    return CustomItem(id, bukkitMaterial, descriptor)
}

fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>
): CustomItem {
    return customItem(id, bukkitMaterial) {
        this.name = name
    }
}

fun customItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>,
    behaviors: Collection<ItemBehavior>
): CustomItem{
    return customItem(id, bukkitMaterial) {
        this.name = name
        this.itemBehaviors.addAll(behaviors)
    }
}

fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    block: ItemDescriptorBuilder.() -> Unit
): CustomItem {
    val material = customItem(id, bukkitMaterial, block)
    CustomItem.register(material)
    return material
}

fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>
): CustomItem {
    val material = customItem(id, bukkitMaterial, name)
    CustomItem.register(material)
    return material
}

fun registerCustomItem(
    id: Identifier,
    bukkitMaterial: Material,
    name: PersonalizedWithDefault<Component>,
    behaviors: Collection<ItemBehavior>
): CustomItem {
    val material = customItem(id, bukkitMaterial, name, behaviors)
    CustomItem.register(material)
    return material
}