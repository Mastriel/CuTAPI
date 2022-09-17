package xyz.mastriel.cutapi.items

import net.kyori.adventure.text.Component
import org.bukkit.Material
import xyz.mastriel.cutapi.items.behaviors.MaterialBehavior
import xyz.mastriel.cutapi.registry.Identifier


fun customMaterial(
    id: Identifier,
    bukkitMaterial: Material,
    block: (MaterialDescriptorBuilder.() -> Unit)?
): CustomMaterial {
    if (block == null) return CustomMaterial(id, bukkitMaterial, defaultMaterialDescriptor())
    val descriptor = MaterialDescriptorBuilder().apply(block).build()

    return CustomMaterial(id, bukkitMaterial, descriptor)
}

fun customMaterial(
    id: Identifier,
    bukkitMaterial: Material,
    name: Component
): CustomMaterial {
    return customMaterial(id, bukkitMaterial) {
        this.name = name
    }
}

fun customMaterial(
    id: Identifier,
    bukkitMaterial: Material,
    name: Component,
    behaviors: Collection<MaterialBehavior>
): CustomMaterial {
    return customMaterial(id, bukkitMaterial) {
        this.name = name
        this.behaviors.addAll(behaviors)
    }
}

fun registerCustomMaterial(
    id: Identifier,
    bukkitMaterial: Material,
    block: MaterialDescriptorBuilder.() -> Unit
): CustomMaterial {
    val material = customMaterial(id, bukkitMaterial, block)
    CustomMaterial.register(material)
    return material
}

fun registerCustomMaterial(
    id: Identifier,
    bukkitMaterial: Material,
    name: Component
): CustomMaterial {
    val material = customMaterial(id, bukkitMaterial, name)
    CustomMaterial.register(material)
    return material
}

fun registerCustomMaterial(
    id: Identifier,
    bukkitMaterial: Material,
    name: Component,
    behaviors: Collection<MaterialBehavior>
): CustomMaterial {
    val material = customMaterial(id, bukkitMaterial, name, behaviors)
    CustomMaterial.register(material)
    return material
}