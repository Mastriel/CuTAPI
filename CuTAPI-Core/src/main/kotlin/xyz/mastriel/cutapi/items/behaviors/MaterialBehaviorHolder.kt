package xyz.mastriel.cutapi.items.behaviors

import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.items.CustomMaterial
import kotlin.reflect.KClass

private class MaterialBehaviorHolder(material: CustomMaterial) : BehaviorHolder<MaterialBehavior> {

    private val behaviors = material.descriptor.behaviors

    override fun hasBehavior(behavior: KClass<out MaterialBehavior>): Boolean {
        return getBehaviorOrNull(behavior) != null
    }

    override fun <T : MaterialBehavior> getBehavior(behavior: KClass<T>): T {
        return getBehaviorOrNull(behavior) ?: error("Component ${behavior.qualifiedName} not found in component list!")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : MaterialBehavior> getBehaviorOrNull(behavior: KClass<T>): T? {
        return behaviors.find { it::class == behavior } as? T?
    }

    override fun getAllBehaviors(): Set<MaterialBehavior> {
        return behaviors.toSet()
    }
}

fun materialBehaviorHolder(material: CustomMaterial) : BehaviorHolder<MaterialBehavior> = MaterialBehaviorHolder(material)