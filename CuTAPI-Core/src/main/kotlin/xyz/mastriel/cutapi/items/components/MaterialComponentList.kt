package xyz.mastriel.cutapi.items.components

import xyz.mastriel.cutapi.items.CustomMaterial
import kotlin.reflect.KClass

private class MaterialComponentList(material: CustomMaterial) : ComponentHolder {

    private val components = material.descriptor.components

    override fun hasComponent(component: KClass<out MaterialComponent>): Boolean {
        return getComponentOrNull(component) != null
    }

    override fun <T : MaterialComponent> getComponent(component: KClass<T>): T {
        return getComponentOrNull(component) ?: error("Component ${component.qualifiedName} not found in component list!")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : MaterialComponent> getComponentOrNull(component: KClass<T>): T? {
        return components.find { it::class == component } as? T?
    }

    override fun getAllComponents(): Set<MaterialComponent> {
        return components.toSet()
    }
}

fun materialComponentList(material: CustomMaterial) : ComponentHolder = MaterialComponentList(material)